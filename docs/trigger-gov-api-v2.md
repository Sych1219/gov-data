## Trigger Gov API v2 – Simplified OpenAPI-Based Trigger

### 1. Objective
- Enable internal callers to execute a registered OpenAPI endpoint via its endpoint ID
- Use stored OpenAPI specification metadata to construct the HTTP request automatically
- Accept simplified query parameters and body parameters without complex nesting
- Validate runtime parameters against the stored OpenAPI parameter schema
- Return standardized response with upstream API results

### 2. Key Differences from V1

| Aspect | V1 | V2 |
|--------|----|----|
| Data Source | `gov_api_registration` table | `gov_openapi_endpoint` + `gov_openapi_registration` |
| Request Format | Nested `query.filters`, `useExampleDefaults`, `headerOverrides` | Flat `queryParams` and `bodyParams` only |
| Parameter Schema | Custom stored schema | OpenAPI 3.x specification schema |
| Headers | Supports runtime overrides | Uses stored headers only |
| Validation | Custom validation logic | OpenAPI schema-based validation |

### 3. REST Endpoint

| Item | Value | Notes |
| --- | --- | --- |
| Path | `/api/v2/gov/apis/endpoints/{endpointId}/trigger` | `endpointId` is the UUID from `gov_openapi_endpoint.id` |
| Method | `POST` | Always POST because it initiates a trigger action |
| Consumes | `application/json` | Simplified runtime parameter payload |
| Produces | `application/json` | Standardized response envelope |

### 4. Request Model

**Simplified V2 Structure:**
```json
{
  "queryParams": {
    "date": "2024-06-20",
    "date_time": "2024-06-20T10:00:00+08:00"
  },
  "bodyParams": {
    "fieldName": "value",
    "anotherField": 123
  }
}
```

**Field Descriptions:**
- `queryParams`: Optional flat map of query parameter key-value pairs
  - Keys must match parameter names defined in the OpenAPI spec
  - Values are automatically coerced to the correct type (string, integer, boolean, etc.)
  - Only include this for endpoints that accept query parameters
- `bodyParams`: Optional flat map of request body field key-value pairs
  - Keys must match schema properties in the OpenAPI requestBody definition
  - Values are validated against the OpenAPI schema
  - Only include this for POST/PUT/PATCH endpoints with request bodies

**Examples:**

**GET Request (query params only):**
```json
{
  "queryParams": {
    "date": "2024-06-20",
    "date_time": "2024-06-20T10:00:00+08:00"
  }
}
```

**POST Request (body params only):**
```json
{
  "bodyParams": {
    "title": "New Resource",
    "description": "Resource description",
    "active": true
  }
}
```

**POST Request (both query and body):**
```json
{
  "queryParams": {
    "format": "json",
    "locale": "en-US"
  },
  "bodyParams": {
    "data": "payload content"
  }
}
```

### 5. Validation Rules

#### 5.1 Endpoint Validation
- `endpointId` must be a valid UUID; malformed IDs return `400 BAD_REQUEST`
- Endpoint must exist in `gov_openapi_endpoint` table; missing returns `404 NOT_FOUND`
- Parent OpenAPI registration must exist in `gov_openapi_registration` table

#### 5.2 Parameter Validation
- **Query Parameters:**
  - All provided keys in `queryParams` must match parameter names in `parameters_json`
  - Unknown/extra parameters are rejected with `400 BAD_REQUEST`
  - Required parameters (per OpenAPI spec) must be provided
  - Values are validated against parameter schema (type, format, pattern, enum, etc.)
  
- **Body Parameters:**
  - All provided keys in `bodyParams` must match properties in `request_body_json` schema
  - Unknown/extra properties are rejected with `400 BAD_REQUEST`
  - Required properties (per OpenAPI spec) must be provided
  - Values are validated against property schema (type, format, minimum, maximum, etc.)

#### 5.3 HTTP Method Validation
- GET/DELETE endpoints: `bodyParams` must not be provided (return `400 BAD_REQUEST`)
- POST/PUT/PATCH endpoints: `bodyParams` is optional (depends on OpenAPI spec)

#### 5.4 Security Validation
- If endpoint requires authentication (`security_json` is not empty), validate stored credentials exist
- Reject trigger if authentication is required but credentials are missing

### 6. Response Contracts

#### 6.1 Success Response (200 OK)
```json
{
  "status": "SUCCESS",
  "endpointId": "e8f33c55-6def-4f67-c1dd-394a69cd2c4d",
  "externalStatus": 200,
  "invokedAt": "2026-01-31T10:15:13Z",
  "requestId": "auto-generated-uuid",
  "responseBody": {
    "items": [
      {
        "station_id": "S50",
        "value": 28.5
      }
    ],
    "metadata": {
      "stations": [
        {
          "id": "S50",
          "name": "Clementi"
        }
      ]
    }
  }
}
```

#### 6.2 Validation Error (400 BAD_REQUEST)
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Invalid query parameter: date format must be YYYY-MM-DD",
  "validationErrors": [
    "Parameter 'date': Invalid format, expected YYYY-MM-DD",
    "Parameter 'unknown_param': Not defined in OpenAPI specification"
  ],
  "timestamp": "2026-01-31T10:15:13Z",
  "requestId": "auto-generated-uuid"
}
```

#### 6.3 Endpoint Not Found (404 NOT_FOUND)
```json
{
  "error": "ENDPOINT_NOT_FOUND",
  "message": "No endpoint found with ID: e8f33c55-6def-4f67-c1dd-394a69cd2c4d",
  "timestamp": "2026-01-31T10:15:13Z",
  "requestId": "auto-generated-uuid"
}
```

#### 6.4 Upstream Error (502 BAD_GATEWAY)
```json
{
  "error": "UPSTREAM_ERROR",
  "message": "External API returned 500 Internal Server Error",
  "externalStatus": 500,
  "timestamp": "2026-01-31T10:15:13Z",
  "requestId": "auto-generated-uuid"
}
```

#### 6.5 Missing Required Parameter (400 BAD_REQUEST)
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Missing required parameters",
  "validationErrors": [
    "Required query parameter 'date' is missing",
    "Required body property 'title' is missing"
  ],
  "timestamp": "2026-01-31T10:15:13Z",
  "requestId": "auto-generated-uuid"
}
```

### 7. Processing Flow

```
1. Receive POST request (Controller)
   - Extract endpointId from path
   - Parse queryParams and bodyParams from request body
   ↓
2. Load endpoint metadata (Service)
   - Query gov_openapi_endpoint by endpointId
   - If not found → return 404
   - Load parent OpenAPI registration from gov_openapi_registration
   ↓
3. Parse OpenAPI metadata (Service)
   - Parse parameters_json → extract parameter definitions
   - Parse request_body_json → extract body schema
   - Parse security_json → identify auth requirements
   - Extract stored headers from registration
   ↓
4. Validate runtime parameters (Service)
   - Check queryParams keys match OpenAPI parameter names
   - Validate required query parameters are provided
   - Validate query parameter values against OpenAPI schema
   - Check bodyParams keys match OpenAPI schema properties
   - Validate required body properties are provided
   - Validate body values against OpenAPI schema
   - If validation fails → return 400 with detailed errors
   ↓
5. Construct outbound HTTP request (Service)
   - Base URL: from gov_openapi_registration.base_url
   - Path: from gov_openapi_endpoint.path
   - Method: from gov_openapi_endpoint.http_method
   - Query String: construct from validated queryParams
   - Headers: use stored headers from registration
   - Request Body: construct JSON from validated bodyParams
   ↓
6. Execute external API call (Service)
   - Use WebClient with configured timeout (20 seconds default)
   - Handle connection errors, timeouts, and HTTP errors
   - Capture response status code and body
   ↓
7. Build trigger response (Service)
   - Wrap upstream response in GovApiTriggerResponse
   - Include status, externalStatus, responseBody
   - Add timestamp and requestId for traceability
   ↓
8. Return response (Controller)
   - Success: 200 OK with wrapped response
   - Upstream error: 502 BAD_GATEWAY with error details
```

### 8. Data Model Integration

#### 8.1 Database Tables Used

**Primary Table: `gov_openapi_endpoint`**
```sql
SELECT 
    id,                    -- endpointId (used in API path)
    openapi_id,           -- FK to gov_openapi_registration
    path,                 -- e.g., "/air-temperature"
    http_method,          -- e.g., "GET"
    parameters_json,      -- OpenAPI parameter definitions
    request_body_json,    -- OpenAPI requestBody schema
    security_json         -- Security requirements
FROM gov_openapi_endpoint
WHERE id = ?
```

**Referenced Table: `gov_openapi_registration`**
```sql
SELECT
    id,
    base_url,             -- e.g., "https://api-open.data.gov.sg/v2"
    openapi_spec_json,    -- Full OpenAPI spec (for headers extraction)
    title,
    version
FROM gov_openapi_registration
WHERE id = ?
```

#### 8.2 OpenAPI Metadata Parsing

**parameters_json Format:**
```json
[
  {
    "name": "date",
    "in": "query",
    "required": true,
    "schema": {
      "type": "string",
      "format": "date"
    },
    "description": "Date in YYYY-MM-DD format"
  },
  {
    "name": "date_time",
    "in": "query",
    "required": false,
    "schema": {
      "type": "string",
      "format": "date-time"
    }
  }
]
```

**request_body_json Format:**
```json
{
  "required": true,
  "content": {
    "application/json": {
      "schema": {
        "type": "object",
        "required": ["title"],
        "properties": {
          "title": {
            "type": "string",
            "minLength": 1,
            "maxLength": 200
          },
          "description": {
            "type": "string"
          },
          "active": {
            "type": "boolean",
            "default": true
          }
        }
      }
    }
  }
}
```

**security_json Format:**
```json
[
  {
    "apiKey": []
  }
]
```

### 9. Service Layer Design

#### 9.1 OpenApiTriggerService Interface
```java
public interface OpenApiTriggerService {
    
    /**
     * Triggers an OpenAPI endpoint with provided parameters
     * 
     * @param endpointId UUID of the endpoint to trigger
     * @param request Trigger request with queryParams and bodyParams
     * @param requestId Correlation ID for tracing
     * @return Response with upstream API results
     * @throws EndpointNotFoundException if endpoint doesn't exist
     * @throws ValidationException if parameters don't match schema
     * @throws UpstreamException if external API call fails
     */
    Mono<GovApiTriggerResponse> trigger(
        UUID endpointId, 
        GovApiTriggerRequestV2 request, 
        String requestId
    );
}
```

#### 9.2 Key Service Methods

```java
// Load endpoint and parent registration
private Mono<OpenApiEndpointContext> loadEndpointContext(UUID endpointId);

// Validate query parameters against OpenAPI schema
private void validateQueryParams(
    Map<String, Object> queryParams,
    List<OpenApiParameter> parameterDefs
);

// Validate body parameters against OpenAPI schema
private void validateBodyParams(
    Map<String, Object> bodyParams,
    OpenApiRequestBodySchema bodySchema
);

// Construct full URL with query string
private URI buildRequestUri(
    String baseUrl,
    String path,
    Map<String, Object> queryParams
);

// Build request body JSON
private String buildRequestBody(
    Map<String, Object> bodyParams,
    OpenApiRequestBodySchema bodySchema
);

// Execute external HTTP call
private Mono<UpstreamCallResult> invokeExternal(
    HttpMethod method,
    URI uri,
    Map<String, String> headers,
    String requestBody
);

// Convert upstream response to trigger response
private GovApiTriggerResponse buildTriggerResponse(
    UUID endpointId,
    UpstreamCallResult result,
    String requestId
);
```

### 10. DTO Design

#### 10.1 Request DTO: GovApiTriggerRequestV2
```java
@Getter
@Setter
public class GovApiTriggerRequestV2 {
    
    private Map<String, Object> queryParams;
    private Map<String, Object> bodyParams;
    
    // Validation: maps cannot be empty if provided
    @AssertTrue(message = "queryParams must not be empty when provided")
    public boolean isQueryParamsValid() {
        return queryParams == null || !queryParams.isEmpty();
    }
    
    @AssertTrue(message = "bodyParams must not be empty when provided")
    public boolean isBodyParamsValid() {
        return bodyParams == null || !bodyParams.isEmpty();
    }
}
```

#### 10.2 Response DTO: Reuse GovApiTriggerResponse
```java
// Existing DTO can be reused, but add endpointId field

@Getter
@Setter
@Builder
public class GovApiTriggerResponse {
    private String status;              // "SUCCESS" or "ERROR"
    private UUID apiId;                 // For V1 compatibility (can be null in V2)
    private UUID endpointId;            // NEW: for V2 OpenAPI endpoints
    private Integer externalStatus;     // HTTP status from upstream API
    private OffsetDateTime invokedAt;   // Timestamp of invocation
    private String requestId;           // Correlation ID
    private Object responseBody;        // Raw response from upstream API
}
```

### 11. Controller Design

```java
@RestController
@RequestMapping("/api/v2/gov/apis")
@Validated
public class OpenApiRegistrationController {
    
    private final OpenApiTriggerService triggerService;
    
    /**
     * Trigger an OpenAPI endpoint with simplified parameters
     * 
     * POST /api/v2/gov/apis/endpoints/{endpointId}/trigger
     */
    @PostMapping(
        path = "/endpoints/{endpointId}/trigger",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Trigger registered OpenAPI endpoint")
    public Mono<ResponseEntity<GovApiTriggerResponse>> triggerEndpoint(
            @PathVariable("endpointId") UUID endpointId,
            @Valid @RequestBody GovApiTriggerRequestV2 request,
            ServerWebExchange exchange) {
        
        String requestId = resolveRequestId(exchange);
        
        return triggerService.trigger(endpointId, request, requestId)
                .map(ResponseEntity::ok);
    }
    
    private String resolveRequestId(ServerWebExchange exchange) {
        Object attribute = exchange.getAttribute(CorrelationIdFilter.HEADER);
        return attribute != null ? attribute.toString() : UUID.randomUUID().toString();
    }
}
```

### 12. Error Handling

#### 12.1 Exception Hierarchy
```
BusinessException (base)
├── NotFoundException
│   └── EndpointNotFoundException (404)
├── ValidationException (400)
│   ├── ParameterValidationException
│   └── SchemaValidationException
└── UpstreamException (502)
    ├── UpstreamTimeoutException
    └── UpstreamServerException
```

#### 12.2 Global Exception Handler
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(EndpointNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEndpointNotFound(
            EndpointNotFoundException ex, HttpServletRequest request) {
        
        ErrorResponse response = ErrorResponse.builder()
            .error("ENDPOINT_NOT_FOUND")
            .message(ex.getMessage())
            .timestamp(Instant.now())
            .requestId(request.getHeader("X-Request-Id"))
            .build();
            
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<OpenApiValidationErrorResponse> handleValidation(
            ValidationException ex, HttpServletRequest request) {
        
        OpenApiValidationErrorResponse response = new OpenApiValidationErrorResponse(
            "VALIDATION_ERROR",
            ex.getMessage(),
            ex.getValidationErrors(),
            Instant.now(),
            request.getHeader("X-Request-Id")
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(UpstreamException.class)
    public ResponseEntity<ErrorResponse> handleUpstream(
            UpstreamException ex, HttpServletRequest request) {
        
        ErrorResponse response = ErrorResponse.builder()
            .error("UPSTREAM_ERROR")
            .message(ex.getMessage())
            .externalStatus(ex.getUpstreamStatus())
            .timestamp(Instant.now())
            .requestId(request.getHeader("X-Request-Id"))
            .build();
            
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }
}
```

### 13. OpenAPI Schema Validation

#### 13.1 Parameter Type Coercion
```java
private Object coerceValue(Object value, OpenApiParameter param) {
    String type = param.getSchema().getType();
    
    switch (type) {
        case "integer":
            return value instanceof Number 
                ? ((Number) value).intValue() 
                : Integer.parseInt(value.toString());
                
        case "number":
            return value instanceof Number
                ? ((Number) value).doubleValue()
                : Double.parseDouble(value.toString());
                
        case "boolean":
            return value instanceof Boolean
                ? value
                : Boolean.parseBoolean(value.toString());
                
        case "string":
            return value.toString();
            
        case "array":
            // Handle array values (if needed)
            return value;
            
        default:
            return value;
    }
}
```

#### 13.2 Schema Validation
```java
private void validateAgainstSchema(
        Object value, 
        Schema schema, 
        String paramName) {
    
    // Type validation
    if (!isCorrectType(value, schema.getType())) {
        throw new ValidationException(
            "Parameter '" + paramName + "': Expected type " + schema.getType()
        );
    }
    
    // Format validation (date, date-time, email, uri, etc.)
    if (schema.getFormat() != null) {
        validateFormat(value, schema.getFormat(), paramName);
    }
    
    // Enum validation
    if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
        if (!schema.getEnum().contains(value)) {
            throw new ValidationException(
                "Parameter '" + paramName + "': Value must be one of " + schema.getEnum()
            );
        }
    }
    
    // String validations
    if ("string".equals(schema.getType())) {
        String str = value.toString();
        if (schema.getMinLength() != null && str.length() < schema.getMinLength()) {
            throw new ValidationException(
                "Parameter '" + paramName + "': Minimum length is " + schema.getMinLength()
            );
        }
        if (schema.getMaxLength() != null && str.length() > schema.getMaxLength()) {
            throw new ValidationException(
                "Parameter '" + paramName + "': Maximum length is " + schema.getMaxLength()
            );
        }
        if (schema.getPattern() != null && !str.matches(schema.getPattern())) {
            throw new ValidationException(
                "Parameter '" + paramName + "': Must match pattern " + schema.getPattern()
            );
        }
    }
    
    // Number validations
    if ("integer".equals(schema.getType()) || "number".equals(schema.getType())) {
        double num = ((Number) value).doubleValue();
        if (schema.getMinimum() != null && num < schema.getMinimum().doubleValue()) {
            throw new ValidationException(
                "Parameter '" + paramName + "': Minimum value is " + schema.getMinimum()
            );
        }
        if (schema.getMaximum() != null && num > schema.getMaximum().doubleValue()) {
            throw new ValidationException(
                "Parameter '" + paramName + "': Maximum value is " + schema.getMaximum()
            );
        }
    }
}
```

### 14. Real-World Example

#### 14.1 Singapore Weather API Registration

**OpenAPI Specification (Stored):**
```json
{
  "openapi": "3.0.3",
  "info": {
    "title": "Real-time API weather services",
    "version": "1.0.11"
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
        "parameters": [
          {
            "name": "date",
            "in": "query",
            "required": true,
            "schema": {
              "type": "string",
              "format": "date"
            }
          },
          {
            "name": "date_time",
            "in": "query",
            "schema": {
              "type": "string",
              "format": "date-time"
            }
          }
        ]
      }
    }
  }
}
```

**V2 Trigger Request:**
```http
POST /api/v2/gov/apis/endpoints/e8f33c55-6def-4f67-c1dd-394a69cd2c4d/trigger
Content-Type: application/json

{
  "queryParams": {
    "date": "2024-06-20",
    "date_time": "2024-06-20T10:00:00+08:00"
  }
}
```

**Backend Constructs:**
```http
GET https://api-open.data.gov.sg/v2/real-time/api/air-temperature?date=2024-06-20&date_time=2024-06-20T10:00:00+08:00
```

**V2 Response:**
```json
{
  "status": "SUCCESS",
  "endpointId": "e8f33c55-6def-4f67-c1dd-394a69cd2c4d",
  "externalStatus": 200,
  "invokedAt": "2026-01-31T10:15:13Z",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "responseBody": {
    "items": [
      {
        "station_id": "S50",
        "value": 28.5
      },
      {
        "station_id": "S107",
        "value": 29.2
      }
    ],
    "metadata": {
      "stations": [
        {
          "id": "S50",
          "device_id": "S50",
          "name": "Clementi",
          "location": {
            "latitude": 1.3337,
            "longitude": 103.7768
          }
        }
      ]
    }
  }
}
```

### 15. Configuration

```yaml
# application.yml
gov-api:
  trigger:
    v2:
      timeout-seconds: 20
      max-response-size-bytes: 65536  # 64KB
      retry:
        enabled: true
        max-attempts: 2
        backoff-millis: 1000
      validation:
        strict-mode: true
        allow-additional-properties: false
```

### 16. Monitoring & Observability

#### 16.1 Logging
```java
log.info("Triggering OpenAPI endpoint. EndpointId: {}, RequestId: {}", 
    endpointId, requestId);
    
log.debug("Request params - Query: {}, Body: {}, RequestId: {}", 
    queryParams.keySet(), bodyParams.keySet(), requestId);
    
log.info("External API call completed. Status: {}, Duration: {}ms, RequestId: {}", 
    externalStatus, duration, requestId);
    
log.error("External API call failed. EndpointId: {}, Error: {}, RequestId: {}", 
    endpointId, error.getMessage(), requestId);
```

#### 16.2 Metrics
- Counter: `gov_api_trigger_v2_total{status, endpoint_id}`
- Timer: `gov_api_trigger_v2_duration{endpoint_id}`
- Counter: `gov_api_trigger_v2_validation_failures{error_type}`
- Counter: `gov_api_trigger_v2_upstream_failures{status_code}`

### 17. Testing Strategy

#### 17.1 Unit Tests
- Test parameter validation against various OpenAPI schemas
- Test type coercion (string to integer, boolean, etc.)
- Test required parameter enforcement
- Test unknown parameter rejection
- Test URL construction with query parameters
- Test request body construction

#### 17.2 Integration Tests
- Test complete trigger flow with real OpenAPI endpoints
- Test error scenarios (missing endpoint, validation failures, upstream errors)
- Test GET, POST, PUT, PATCH, DELETE methods
- Test with Singapore data.gov.sg APIs

#### 17.3 Test Data
```java
// Mock OpenAPI endpoint with parameters
GovOpenApiEndpoint endpoint = GovOpenApiEndpoint.builder()
    .id(UUID.randomUUID())
    .path("/air-temperature")
    .httpMethod("GET")
    .parametersJson("[{\"name\":\"date\",\"in\":\"query\",\"required\":true,\"schema\":{\"type\":\"string\",\"format\":\"date\"}}]")
    .build();

// Valid trigger request
GovApiTriggerRequestV2 request = new GovApiTriggerRequestV2();
request.setQueryParams(Map.of("date", "2024-06-20"));

// Test validation
triggerService.trigger(endpointId, request, "test-request-id");
```

### 18. Security Considerations

1. **Input Validation**: Strict validation against OpenAPI schema
2. **Injection Prevention**: Parameterized query construction
3. **Size Limits**: Enforce max response size to prevent memory exhaustion
4. **Timeout**: Prevent hanging on slow upstream APIs
5. **Header Security**: Only use pre-registered headers (no runtime injection)
6. **Credential Storage**: Securely store API keys/tokens in registration
7. **Rate Limiting**: Implement per-endpoint rate limits
8. **Audit Logging**: Log all trigger requests with correlation IDs

### 19. Migration Path

#### 19.1 V1 vs V2 Coexistence
- Both V1 and V2 endpoints can coexist
- V1: `/api/v1/gov/apis/{apiId}/trigger` (uses `gov_api_registration`)
- V2: `/api/v2/gov/apis/endpoints/{endpointId}/trigger` (uses `gov_openapi_endpoint`)
- Different data sources, no conflicts

#### 19.2 Migration Guide for Clients
```
Step 1: Register API via OpenAPI upload (V2 registration endpoint)
        POST /api/v2/gov/apis/openapi
        
Step 2: Note the returned endpoint IDs from registration response

Step 3: Update trigger calls to use V2 format
        Before: POST /api/v1/gov/apis/{apiId}/trigger
        After:  POST /api/v2/gov/apis/endpoints/{endpointId}/trigger
        
Step 4: Simplify request payload
        Before: { "query": { "filters": { "state": "CA" } }, "useExampleDefaults": true }
        After:  { "queryParams": { "state": "CA" } }
```

### 20. Future Enhancements

1. **Async Triggers**: Return 202 Accepted with job ID, process in background
2. **Batch Triggers**: Trigger multiple endpoints in one call
3. **Response Transformation**: Apply JSONPath/JMESPath transformations
4. **Caching**: Cache GET responses with configurable TTL
5. **Webhooks**: Trigger callback URLs with results
6. **Scheduled Triggers**: Cron-based automatic triggers
7. **Circuit Breaker**: Automatic failure detection and recovery
8. **Request Replay**: Replay failed requests with same parameters
9. **Response Validation**: Validate upstream responses against OpenAPI schema
10. **API Versioning**: Handle multiple versions of same API endpoint

### 21. API Documentation (OpenAPI)

```yaml
openapi: 3.0.3
info:
  title: Gov API Trigger Service V2
  version: 2.0.0
paths:
  /api/v2/gov/apis/endpoints/{endpointId}/trigger:
    post:
      summary: Trigger OpenAPI endpoint with simplified parameters
      tags:
        - OpenAPI Trigger
      parameters:
        - name: endpointId
          in: path
          required: true
          schema:
            type: string
            format: uuid
          description: UUID of the registered OpenAPI endpoint
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                queryParams:
                  type: object
                  additionalProperties: true
                  description: Flat map of query parameter key-value pairs
                bodyParams:
                  type: object
                  additionalProperties: true
                  description: Flat map of request body field key-value pairs
            examples:
              getWithQuery:
                summary: GET request with query parameters
                value:
                  queryParams:
                    date: "2024-06-20"
                    date_time: "2024-06-20T10:00:00+08:00"
              postWithBody:
                summary: POST request with body parameters
                value:
                  bodyParams:
                    title: "New Resource"
                    active: true
      responses:
        '200':
          description: Trigger successful
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/GovApiTriggerResponse'
        '400':
          description: Validation error
        '404':
          description: Endpoint not found
        '502':
          description: Upstream API error
```
