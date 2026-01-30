## Register Gov API v2 – OpenAPI Specification Upload

### 1. Objective
- Accept OpenAPI Specification (OAS) JSON files from users
- Validate the uploaded file is a valid OpenAPI 3.x specification
- Parse and extract API metadata (endpoints, parameters, methods, etc.)
- Store the complete OpenAPI spec and extracted metadata in the database
- Reject invalid or malformed specifications with detailed error messages

### 2. REST Endpoint
| Item | Value |
| --- | --- |
| Path | `/api/v2/gov/apis/openapi` |
| Method | `POST` |
| Consumes | `application/json` |
| Produces | `application/json` |

### 3. Request Model

The endpoint accepts raw OpenAPI JSON specification directly in the request body:

```json
{
  "openapi": "3.0.3",
  "info": {
    "title": "Real-time API weather services",
    "version": "1.0.11",
    "description": "Real-time API documentation of weather services"
  },
  "servers": [
    {
      "url": "https://api-open.data.gov.sg/v2/real-time/api"
    }
  ],
  "paths": {
    "/air-temperature": {
      "get": {
        "summary": "Get air temperature readings",
        "parameters": [...],
        "responses": {...}
      }
    }
  }
}
```

All metadata (title, version, description, baseUrl) will be extracted directly from the OpenAPI specification.

### 4. Validation Rules

#### 4.1 OpenAPI Specification Validation
- **OpenAPI Version**: Must be `3.0.x` or `3.1.x`
- **Required Fields**: `openapi`, `info`, `paths`
- **Info Object**: Must contain `title` and `version`
- **Servers**: At least one server URL must be present and must use HTTP or HTTPS protocol
- **Paths**: At least one path must be defined
- **Schema Validation**: All referenced schemas must exist in `components.schemas`
- **Parameter Validation**: Parameters must have valid types and locations (query, header, path, cookie)

#### 4.2 Business Validation
- **Unique Registration**: Check if API with same `info.title` and `servers[0].url` already exists
- **Security Requirements**: If API requires authentication, validate security schemes are properly defined
- **File Size**: Max 5MB for OpenAPI JSON file
- **Endpoint Count**: Max 50 endpoints per specification (to prevent abuse)

### 5. Validation Implementation Strategy

#### 5.1 Use Swagger Parser Library
```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.swagger.parser.v3</groupId>
    <artifactId>swagger-parser</artifactId>
    <version>2.1.21</version>
</dependency>
```

#### 5.2 Validation Service Interface
```java
public interface OpenApiValidationService {
    OpenApiValidationResult validate(String openApiJson);
}

public class OpenApiValidationResult {
    private boolean valid;
    private List<String> errors;
    private List<String> warnings;
    private ParsedOpenApiSpec parsedSpec; // if valid
}
```

### 6. Database Schema Design

#### 6.1 Main Table: `gov_openapi_registration`
```sql
CREATE TABLE IF NOT EXISTS gov_openapi_registration
(
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                 VARCHAR(255)        NOT NULL,
    title                VARCHAR(255)        NOT NULL, -- from info.title
    version              VARCHAR(50)         NOT NULL, -- from info.version
    description          TEXT,
    category             VARCHAR(100),
    base_url             VARCHAR(512)        NOT NULL, -- from servers[0].url
    openapi_version      VARCHAR(20)         NOT NULL, -- e.g., "3.0.3"
    openapi_spec_json    TEXT                NOT NULL, -- full OpenAPI JSON
    endpoint_count       INTEGER             DEFAULT 0,
    tags                 TEXT[],             -- array of tags
    created_at           TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_openapi_title_url UNIQUE (title, base_url)
);

-- Index for searching
CREATE INDEX idx_openapi_title ON gov_openapi_registration (title);
CREATE INDEX idx_openapi_category ON gov_openapi_registration (category);
CREATE INDEX idx_openapi_tags ON gov_openapi_registration USING GIN (tags);
```

#### 6.2 Detail Table: `gov_openapi_endpoint`
```sql
CREATE TABLE IF NOT EXISTS gov_openapi_endpoint
(
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    openapi_id           UUID                NOT NULL REFERENCES gov_openapi_registration(id) ON DELETE CASCADE,
    path                 VARCHAR(512)        NOT NULL,
    http_method          VARCHAR(20)         NOT NULL,
    operation_id         VARCHAR(255),
    summary              TEXT,
    description          TEXT,
    parameters_json      TEXT,               -- JSON array of parameters
    request_body_json    TEXT,               -- JSON schema of request body
    responses_json       TEXT,               -- JSON object of responses
    security_json        TEXT,               -- JSON array of security requirements
    tags                 TEXT[],
    created_at           TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_endpoint_path_method UNIQUE (openapi_id, path, http_method)
);

CREATE INDEX idx_endpoint_openapi_id ON gov_openapi_endpoint (openapi_id);
CREATE INDEX idx_endpoint_method ON gov_openapi_endpoint (http_method);
```

### 7. Request Processing Flow

```
1. Receive POST request with OpenAPI JSON (Controller)
   ↓
2. Validate JSON format (is it valid JSON?)
   ↓
3. Validate OpenAPI specification structure (Controller)
   - Check required fields (openapi, info, paths)
   - Validate OpenAPI version (3.0.x or 3.1.x)
   - Validate servers array (at least one HTTP/HTTPS URL)
   ↓
4. Parse OpenAPI spec using Swagger Parser (Controller)
   - Resolve $ref references
   - Validate schemas
   - Check for circular references
   - Extract metadata (title, version, base URL, endpoints)
   ↓
5. Pass parsed data to Service layer
   - Service receives ParsedOpenApiSpec + raw JSON
   - No re-validation or re-parsing needed
   ↓
6. Business validation (Service)
   - Check for duplicate registration (title + base_url)
   - Validate endpoint count (max 50)
   ↓
7. Store in database (transactional - Service)
   - Insert into gov_openapi_registration
   - Insert all endpoints into gov_openapi_endpoint
   ↓
8. Return success response with ID and summary

Note: Fail-fast approach - validation/parsing failures at controller level 
return HTTP 400 immediately without entering business logic.
```

### 8. Response Contracts

#### 8.1 Success Response (201 Created)
```json
{
  "id": "a7f22b44-9dfd-4e56-b0cc-493968bc1b3c",
  "title": "Real-time API weather services",
  "version": "1.0.11",
  "baseUrl": "https://api-open.data.gov.sg/v2/real-time/api",
  "endpointCount": 1,
  "endpoints": [
    {
      "path": "/air-temperature",
      "method": "GET",
      "summary": "Get air temperature readings across Singapore"
    }
  ],
  "status": "REGISTERED",
  "createdAt": "2024-07-20T10:15:23Z"
}
```

#### 8.2 Validation Error Response (400 Bad Request)
```json
{
  "error": "INVALID_OPENAPI_SPEC",
  "message": "OpenAPI specification validation failed",
  "validationErrors": [
    "Missing required field: info.version",
    "Server URLs must use HTTP or HTTPS protocol",
    "Invalid schema reference: #/components/schemas/Temperature"
  ],
  "timestamp": "2024-07-20T10:15:23Z",
  "requestId": "req-123456"
}
```

#### 8.3 Conflict Response (409 Conflict)
```json
{
  "error": "DUPLICATE_API_REGISTRATION",
  "message": "API with title 'Real-time API weather services' and base URL 'https://api-open.data.gov.sg/v2/real-time/api' already exists",
  "existingApiId": "a7f22b44-9dfd-4e56-b0cc-493968bc1b3c",
  "timestamp": "2024-07-20T10:15:23Z",
  "requestId": "req-123456"
}
```

#### 8.4 Payload Too Large (413)
```json
{
  "error": "PAYLOAD_TOO_LARGE",
  "message": "OpenAPI specification exceeds maximum size of 5MB",
  "maxSizeBytes": 5242880,
  "actualSizeBytes": 6000000,
  "timestamp": "2024-07-20T10:15:23Z"
}
```

### 9. DTO Design

#### 9.1 Request DTO
```java
// The request body is the raw OpenAPI specification as JsonNode
// No wrapper DTO needed - controller directly accepts JsonNode

// Controller method signature:
@PostMapping(value = "/openapi", consumes = MediaType.APPLICATION_JSON_VALUE)
public ResponseEntity<OpenApiRegistrationResponse> registerOpenApi(
        @RequestBody @NotNull JsonNode openApiSpec,
        @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
    // Process raw OpenAPI JSON directly
}
```

#### 9.2 Response DTO
```java
@Getter
@Setter
public class OpenApiRegistrationResponse {
    private UUID id;
    private String title;
    private String version;
    private String baseUrl;
    private Integer endpointCount;
    private List<EndpointSummary> endpoints;
    private String status;
    private Instant createdAt;
    
    @Getter
    @Setter
    public static class EndpointSummary {
        private String path;
        private String method;
        private String summary;
    }
}
```

#### 9.3 Validation Error Response DTO
```java
@Getter
@Setter
@AllArgsConstructor
public class OpenApiValidationErrorResponse {
    private String error;
    private String message;
    private List<String> validationErrors;
    private Instant timestamp;
    private String requestId;
}
```

### 10. Service Layer Design

#### 10.1 OpenApiValidationService
```java
public interface OpenApiValidationService {
    
    /**
     * Validates OpenAPI specification
     * @param openApiJson Raw OpenAPI JSON
     * @return Validation result with errors/warnings
     */
    OpenApiValidationResult validate(String openApiJson);
    
    /**
     * Parses and extracts metadata from OpenAPI spec
     * @param openApiJson Valid OpenAPI JSON
     * @return Parsed specification with metadata
     */
    ParsedOpenApiSpec parse(String openApiJson);
}
```

#### 10.2 OpenApiRegistrationService
```java
public interface OpenApiRegistrationService {
    
    /**
     * Registers a new OpenAPI specification
     * @param parsedSpec Already validated and parsed OpenAPI specification
     * @param openApiJson Raw OpenAPI JSON string for storage
     * @return Registration response with ID and summary
     * @throws ConflictException if API already registered
     */
    OpenApiRegistrationResponse register(ParsedOpenApiSpec parsedSpec, String openApiJson);
    
    /**
     * Checks if API is already registered
     * @param title API title (from info.title)
     * @param baseUrl Base URL (from servers[0].url)
     * @return true if already exists
     */
    boolean isAlreadyRegistered(String title, String baseUrl);
    
    /**
     * Retrieves OpenAPI spec by ID
     * @param id Registration ID
     * @return Complete OpenAPI specification
     */
    OpenApiRegistrationResponse getById(UUID id);
}
```

### 11. Validation Logic Details

#### 11.1 OpenAPI Structure Validation
```java
private void validateOpenApiStructure(JsonNode spec) {
    // Check required root fields
    if (!spec.has("openapi")) {
        throw new ValidationException("Missing required field: openapi");
    }
    
    String version = spec.get("openapi").asText();
    if (!version.startsWith("3.0") && !version.startsWith("3.1")) {
        throw new ValidationException("Unsupported OpenAPI version: " + version + 
            ". Only 3.0.x and 3.1.x are supported");
    }
    
    // Validate info object
    if (!spec.has("info")) {
        throw new ValidationException("Missing required field: info");
    }
    JsonNode info = spec.get("info");
    if (!info.has("title") || !info.has("version")) {
        throw new ValidationException("info object must contain title and version");
    }
    
    // Validate servers
    if (!spec.has("servers") || !spec.get("servers").isArray() || 
        spec.get("servers").size() == 0) {
        throw new ValidationException("At least one server must be defined");
    }
    
    // Validate server URLs are HTTP or HTTPS
    JsonNode servers = spec.get("servers");
    for (JsonNode server : servers) {
        String url = server.get("url").asText();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new ValidationException("Server URLs must use HTTP or HTTPS protocol: " + url);
        }
    }
    
    // Validate paths
    if (!spec.has("paths")) {
        throw new ValidationException("Missing required field: paths");
    }
    JsonNode paths = spec.get("paths");
    if (paths.size() == 0) {
        throw new ValidationException("At least one path must be defined");
    }
}
```

#### 11.2 Schema Reference Validation
```java
private void validateSchemaReferences(OpenAPI openAPI) {
    Map<String, Schema> schemas = openAPI.getComponents() != null 
        ? openAPI.getComponents().getSchemas() 
        : new HashMap<>();
    
    List<String> unresolvedRefs = new ArrayList<>();
    
    // Check all operation responses and request bodies
    for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
        PathItem path = pathEntry.getValue();
        
        for (Operation operation : path.readOperations()) {
            // Check request body schemas
            if (operation.getRequestBody() != null) {
                checkContentSchemas(operation.getRequestBody().getContent(), 
                    schemas, unresolvedRefs);
            }
            
            // Check response schemas
            if (operation.getResponses() != null) {
                for (ApiResponse response : operation.getResponses().values()) {
                    if (response.getContent() != null) {
                        checkContentSchemas(response.getContent(), 
                            schemas, unresolvedRefs);
                    }
                }
            }
        }
    }
    
    if (!unresolvedRefs.isEmpty()) {
        throw new ValidationException("Unresolved schema references: " + 
            String.join(", ", unresolvedRefs));
    }
}
```

### 12. Controller Design

```java
@RestController
@RequestMapping("/api/v2/gov/apis")
@Validated
public class OpenApiRegistrationController {
    
    private final OpenApiRegistrationService registrationService;
    private final OpenApiValidationService validationService;
    
    @PostMapping(value = "/openapi", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OpenApiRegistrationResponse> registerOpenApi(
            @RequestBody @NotNull JsonNode openApiSpec,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        
        // Validate and parse specification (fail-fast at API boundary)
        String openApiJson = openApiSpec.toString();
        OpenApiValidationResult validationResult = validationService.validate(openApiJson);
        
        if (!validationResult.isValid()) {
            throw new ValidationException("OpenAPI specification validation failed", 
                validationResult.getErrors());
        }
        
        // Parse the validated specification to extract metadata
        ParsedOpenApiSpec parsedSpec = validationService.parse(openApiJson);
        
        // Register the API with pre-parsed data
        OpenApiRegistrationResponse response = registrationService.register(parsedSpec, openApiJson);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/openapi/{id}")
    public ResponseEntity<OpenApiRegistrationResponse> getOpenApi(@PathVariable UUID id) {
        OpenApiRegistrationResponse response = registrationService.getById(id);
        return ResponseEntity.ok(response);
    }
}
```

### 13. Error Handling

```java
@ControllerAdvice
public class OpenApiExceptionHandler {
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<OpenApiValidationErrorResponse> handleValidationException(
            ValidationException ex, HttpServletRequest request) {
        
        OpenApiValidationErrorResponse response = new OpenApiValidationErrorResponse(
            "INVALID_OPENAPI_SPEC",
            ex.getMessage(),
            ex.getValidationErrors(),
            Instant.now(),
            request.getHeader("X-Request-Id")
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflictException(
            ConflictException ex, HttpServletRequest request) {
        
        ErrorResponse response = new ErrorResponse(
            "DUPLICATE_API_REGISTRATION",
            ex.getMessage(),
            Instant.now(),
            request.getHeader("X-Request-Id")
        );
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }
    
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleFileSizeException(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        
        ErrorResponse response = new ErrorResponse(
            "PAYLOAD_TOO_LARGE",
            "OpenAPI specification exceeds maximum size of 5MB",
            Instant.now(),
            request.getHeader("X-Request-Id")
        );
        
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }
}
```

### 14. Configuration

```yaml
# application.yml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 5MB

gov-api:
  openapi:
    max-endpoints: 50
    supported-versions:
      - "3.0"
      - "3.1"
    validation:
      strict-mode: true
      allow-warnings: false
```

### 15. Testing Strategy

#### 15.1 Unit Tests
- Test OpenAPI validation logic with valid/invalid specs
- Test schema reference resolution
- Test endpoint extraction logic
- Test duplicate detection

#### 15.2 Integration Tests
- Test complete registration flow
- Test with real OpenAPI specs from data.gov.sg
- Test error scenarios (invalid JSON, missing fields, etc.)
- Test conflict detection

### 16. Future Enhancements

1. **Spec Versioning**: Support multiple versions of same API
2. **Diff Detection**: Show changes between versions
3. **Import from URL**: Fetch OpenAPI spec from URL
4. **Auto-sync**: Periodically refresh specs from source
5. **Testing**: Auto-generate test cases from OpenAPI spec
6. **Client Generation**: Generate client code on-demand
7. **Mock Server**: Create mock endpoints based on spec
8. **API Gateway Integration**: Auto-configure API gateway routes
9. **Rate Limit Extraction**: Parse `x-ratelimit` extensions
10. **Cost Estimation**: Estimate costs based on quota extensions

### 17. Migration from V1

For backward compatibility, maintain V1 endpoint alongside V2:
- V1: Manual field-by-field registration
- V2: OpenAPI spec upload (auto-extract fields)
- Both store in same database tables
- V2 generates `openapi_spec_json` field; V1 leaves it null

### 18. API Documentation

```yaml
openapi: 3.0.3
info:
  title: Gov API Registration Service
  version: 2.0.0
paths:
  /api/v2/gov/apis/openapi:
    post:
      summary: Register API via OpenAPI Specification
      description: Upload and register a government API using OpenAPI 3.x specification
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/OpenApiRegistrationRequest'
      responses:
        '201':
          description: API registered successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/OpenApiRegistrationResponse'
        '400':
          description: Invalid OpenAPI specification
        '409':
          description: API already registered
        '413':
          description: Payload too large
```

### 19. Security Considerations

1. **Input Validation**: Strict validation of all inputs
2. **Size Limits**: Prevent DOS attacks with size limits
3. **Sanitization**: Sanitize all extracted metadata before storage
4. **Rate Limiting**: Limit registration requests per user
5. **Authentication**: Require API key for registration
6. **Protocol Validation**: Accept HTTP and HTTPS server URLs
7. **Schema Bomb Prevention**: Limit nested schema depth
8. **SSRF Prevention**: Don't auto-fetch external $ref URLs

### 20. Monitoring & Logging

```java
@Slf4j
public class OpenApiRegistrationServiceImpl implements OpenApiRegistrationService {
    
    public OpenApiRegistrationResponse register(ParsedOpenApiSpec parsedSpec, String openApiJson) {
        String requestId = UUID.randomUUID().toString();
        
        log.info("Starting OpenAPI registration. RequestId: {}", requestId);
        
        try {
            // Extract metadata (already validated and parsed in controller)
            String title = parsedSpec.getTitle();
            String baseUrl = parsedSpec.getBaseUrl();
            
            log.debug("Registering OpenAPI spec: {} ({}). RequestId: {}", title, baseUrl, requestId);
            
            // Check for duplicates
            if (isAlreadyRegistered(title, baseUrl)) {
                throw new ConflictException("API already registered: " + title);
            }
            
            // Store in database
            log.info("Saving API to database: {}. RequestId: {}", title, requestId);
            UUID apiId = saveToDatabase(parsedSpec, openApiJson);
            
            // Success
            log.info("Successfully registered API: {}. ID: {}. RequestId: {}", 
                title, apiId, requestId);
            
            return buildResponse(apiId, parsedSpec);
            
        } catch (Exception e) {
            log.error("Failed to register OpenAPI. RequestId: {}. Error: {}", 
                requestId, e.getMessage(), e);
            throw e;
        }
    }
}
```
