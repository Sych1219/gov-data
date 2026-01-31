# Endpoint Schema API – LLM-Friendly Metadata for Request Construction

## 1. Overview

This API provides simplified endpoint metadata designed specifically for LLM consumption. It enables AI agents to understand API endpoints and construct valid trigger requests by providing high-level descriptions and parameter details in natural language.

## 2. Objective

- Provide LLM-readable endpoint documentation
- Return only essential metadata: endpoint ID, description, and parameters
- Include natural language guidance for parameter extraction from user input
- Enable LLM to construct valid `queryParams` and `bodyParams` for trigger API
- Keep response structure flat and simple for easy LLM parsing

## 3. REST Endpoint

| Item | Value | Notes |
| --- | --- | --- |
| Path | `/api/v2/gov/apis/schemas` | Returns all registered endpoint schemas |
| Method | `GET` | Read-only metadata retrieval |
| Produces | `application/json` | Array of simplified schemas |
| Purpose | Return all endpoint configurations for LLM discovery and request construction | |

## 4. Response Structure

### 4.1 Simplified Schema Response

```json
{
  "schemas": [
    {
      "id": "e8f33c55-6def-4f67-c1dd-394a69cd2c4d",
      "description": "High-level description of what this endpoint does and when to use it",
      "parameters": [
        {
          "name": "parameterName",
          "location": "query | body",
          "required": true | false,
          "type": "string | integer | boolean | number | array | object",
          "description": "LLM-friendly description with format hints and extraction guidance"
        }
      ]
    }
  ]
}
```

### 4.2 Response Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `schemas` | Array | Yes | List of all registered endpoint schemas |

### 4.3 Schema Object Structure

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | UUID | Yes | Endpoint identifier used in trigger API path |
| `description` | String | Yes | High-level endpoint purpose with usage hints for LLM |
| `parameters` | Array | Yes | List of all parameters (query + body) needed for this endpoint |

### 4.4 Parameter Object Structure

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | String | Yes | Parameter name (used as key in `queryParams` or `bodyParams`) |
| `location` | String | Yes | Where to place parameter: `"query"` or `"body"` |
| `required` | Boolean | Yes | Whether this parameter must be provided |
| `type` | String | Yes | Data type: `string`, `integer`, `boolean`, `number`, `array`, `object` |
| `description` | String | Yes | LLM-friendly description including format requirements, examples, and extraction hints |

## 5. Example Responses

### 5.1 Complete Response Example

**Request:**
```http
GET /api/v2/gov/apis/schemas
```

**Response:**
```json
{
  "schemas": [
    {
      "id": "e8f33c55-6def-4f67-c1dd-394a69cd2c4d",
      "description": "Returns current air temperature readings from weather stations across Singapore. Data is updated every 5 minutes. Use this API when users ask for temperature, weather conditions, or climate data in Singapore.",
      "parameters": [
        {
          "name": "date",
          "location": "query",
          "required": true,
          "type": "string",
          "description": "Date for temperature readings. Format: YYYY-MM-DD (e.g., 2024-06-20). Use current date if user says 'today', yesterday's date if user says 'yesterday'."
        },
        {
          "name": "date_time",
          "location": "query",
          "required": false,
          "type": "string",
          "description": "Specific timestamp for readings. Format: ISO 8601 with timezone (e.g., 2024-06-20T10:00:00+08:00). Optional - only include if user specifies exact time."
        }
      ]
    },
    {
      "id": "a1b2c3d4-5678-90ab-cdef-1234567890ab",
      "description": "Creates a new resource in the system. Use this when user wants to add, create, or register new data.",
      "parameters": [
        {
          "name": "title",
          "location": "body",
          "required": true,
          "type": "string",
          "description": "Title or name of the resource. Must be 1-200 characters. Extract from user's input the main subject or name they want to create."
        },
        {
          "name": "description",
          "location": "body",
          "required": false,
          "type": "string",
          "description": "Optional description of the resource. Include if user provides details about what they're creating."
        },
        {
          "name": "active",
          "location": "body",
          "required": false,
          "type": "boolean",
          "description": "Whether resource is active. Default is true. Set to false only if user explicitly says 'inactive' or 'disabled'. Optional."
        }
      ]
    },
    {
      "id": "b2c3d4e5-6789-01ab-cdef-234567890abc",
      "description": "Creates a new weather observation record. Use this when user wants to submit or record weather data.",
      "parameters": [
        {
          "name": "format",
          "location": "query",
          "required": false,
          "type": "string",
          "description": "Response format preference. Allowed values: json, xml. Default is json. Optional."
        },
        {
          "name": "station_id",
          "location": "body",
          "required": true,
          "type": "string",
          "description": "Weather station identifier. Extract station name or location from user input and map to station ID."
        },
        {
          "name": "temperature",
          "location": "body",
          "required": true,
          "type": "number",
          "description": "Temperature reading in Celsius. Must be between -50 and 60. Extract numeric value from user input."
        },
        {
          "name": "timestamp",
          "location": "body",
          "required": true,
          "type": "string",
          "description": "Observation timestamp. Format: ISO 8601 (e.g., 2024-06-20T10:00:00+08:00). Use current timestamp if user says 'now'."
        }
      ]
    }
  ]
}
```

### 5.2 Filtering by Keywords (Optional Enhancement)

LLM can filter schemas client-side based on user intent by matching keywords in the `description` field:

```
User Query: "What's the temperature today?"

LLM Processing:
1. Calls GET /api/v2/gov/apis/schemas
2. Receives all schemas
3. Filters by keywords: "temperature", "weather"
4. Finds schema with id "e8f33c55-6def-4f67-c1dd-394a69cd2c4d"
5. Proceeds with that endpoint
```

## 6. LLM Usage Flow

### 6.1 End-to-End Flow

```
User Query: "What's the temperature in Singapore today?"
                    ↓
LLM Agent:
Step 1: Retrieve all endpoint schemas
        GET /api/v2/gov/apis/schemas
                    ↓
Step 2: Filter schemas by user intent
        Searches descriptions for: "temperature" + "Singapore" + "weather"
        → Finds schema with id "e8f33c55-6def-4f67-c1dd-394a69cd2c4d"
        → Determines this endpoint matches user intent
                    ↓
Step 3: Extract parameter values from user query
        Parameter: "date" (required, query)
        User said: "today"
        → Extract as: "2024-06-20" (current date)
        
        Parameter: "date_time" (optional, query)
        User didn't specify time
        → Omit this parameter
                    ↓
Step 4: Construct trigger request
        {
          "queryParams": {
            "date": "2024-06-20"
          }
        }
                    ↓
Step 5: Execute trigger
        POST /api/v2/gov/apis/endpoints/{id}/trigger
        Body: { "queryParams": { "date": "2024-06-20" } }
                    ↓
Step 6: Return formatted result to user
```

### 6.2 Parameter Location Mapping

The `location` field determines where parameters go in the trigger request:

| Location Value | Maps To | Example |
|----------------|---------|---------|
| `"query"` | `queryParams` in trigger request | `{ "queryParams": { "date": "2024-06-20" } }` |
| `"body"` | `bodyParams` in trigger request | `{ "bodyParams": { "title": "New Item" } }` |

### 6.3 Complex Example: Mixed Parameters

**User Query:** "Create a weather report for Clementi station with temperature 28.5, return as JSON"

**LLM Processing:**
1. Retrieves schema, sees mixed query + body parameters
2. Extracts values:
   - `format` (query) → "json" (from "return as JSON")
   - `station_id` (body) → "S50" (mapped from "Clementi")
   - `temperature` (body) → 28.5 (from "temperature 28.5")
   - `timestamp` (body) → "2024-06-20T10:15:30+08:00" (current time)

3. Constructs trigger request:
```json
{
  "queryParams": {
    "format": "json"
  },
  "bodyParams": {
    "station_id": "S50",
    "temperature": 28.5,
    "timestamp": "2024-06-20T10:15:30+08:00"
  }
}
```

## 7. Description Field Design

### 7.1 Description Components

The `description` field should include:

1. **Primary Purpose**: What the endpoint does
2. **Data Details**: What kind of data it returns/accepts
3. **Update Frequency**: How often data refreshes (if applicable)
4. **Usage Hints**: Keywords that help LLM match user intent

**Template:**
```
[Primary purpose]. [Data details]. [Update frequency]. Use this API when users ask for [usage hints].
```

**Examples:**

```
"Returns current air temperature readings from weather stations across Singapore. 
Data is updated every 5 minutes. 
Use this API when users ask for temperature, weather conditions, or climate data in Singapore."

"Creates a new user account in the system. 
Use this when user wants to register, sign up, or create a new account."

"Searches for products by name or category. 
Returns paginated results with product details. 
Use this when user wants to find, search, or browse products."
```

### 7.2 Description Generation Strategy

**Source Priority:**
1. Use `endpoint.description` from OpenAPI spec (most specific)
2. Fall back to `endpoint.summary` (if description missing)
3. Fall back to `registration.title` (last resort)
4. Enhance with keyword-based usage hints

**Keyword-Based Enhancement:**
| Keywords in Title/Summary | Auto-Add Usage Hint |
|---------------------------|---------------------|
| weather, temperature, climate | "Use this when users ask for temperature, weather conditions, or climate data" |
| create, add, register | "Use this when user wants to add, create, or register new data" |
| search, find, query, filter | "Use this when user wants to find, search, or filter data" |
| update, modify, edit | "Use this when user wants to update, modify, or change existing data" |
| delete, remove | "Use this when user wants to delete or remove data" |
| list, get all | "Use this when user wants to see all records or browse data" |

## 8. Parameter Description Design

### 8.1 Use OpenAPI Descriptions Directly

**Strategy:** Use existing parameter descriptions from OpenAPI specification as-is. These descriptions are already written for developers and contain:

1. **Purpose**: What the parameter represents
2. **Format Requirements**: Expected format with examples
3. **Constraints**: Allowed values, patterns, ranges
4. **Usage Notes**: When to use or omit the parameter

**Example from OpenAPI spec:**
```json
{
  "in": "query",
  "name": "date",
  "description": "SGT date for which to retrieve data (YYYY-MM-DD or YYYY-MM-DDTHH:MM:SS)",
  "schema": {
    "type": "string"
  }
}
```

**Maps directly to:**
```json
{
  "name": "date",
  "location": "query",
  "required": false,
  "type": "string",
  "description": "SGT date for which to retrieve data (YYYY-MM-DD or YYYY-MM-DDTHH:MM:SS)"
}
```

### 8.2 Fallback for Missing Descriptions

If a parameter lacks a description in the OpenAPI spec:
- Use the parameter name as fallback
- Add type information: `"[paramName] ({type})"`
- Example: `"date (string)"`

### 8.3 Description Templates by Type (For Reference Only)

**Note:** These templates are for reference only. In practice, use the descriptions from the OpenAPI specification directly, as they are already well-written and comprehensive.

#### Example OpenAPI Descriptions

**Date Parameter:**
```
"SGT date for which to retrieve data (YYYY-MM-DD or YYYY-MM-DDTHH:MM:SS)"
```

**Pagination Token:**
```
"Pagination token for retrieving subsequent data pages (only exists when there is a next page available for requests with date filters)"
```

**API Key Header:**
```
"API key for higher rate limits (optional)"
```

**Boolean Property:**
```
"Whether resource is active. Defaults to true if not provided."
```

**Numeric Property:**
```
"Temperature reading in Celsius. Valid range: -50 to 60."
```

## 9. Data Model Integration

### 9.1 Database Tables

**Primary Source: `gov_openapi_endpoint`**

```sql
SELECT 
    id,                    -- Returned as "id" in schema response
    summary,               -- Used in description generation
    description,           -- Primary source for description field
    parameters_json,       -- Parsed to extract query parameters
    request_body_json      -- Parsed to extract body parameters
FROM gov_openapi_endpoint
WHERE id = ?
```

**Referenced: `gov_openapi_registration`**

```sql
SELECT
    title,                 -- Used in description generation (fallback)
    base_url               -- Not exposed in schema response (internal detail)
FROM gov_openapi_registration
WHERE id = (SELECT openapi_id FROM gov_openapi_endpoint WHERE id = ?)
```

### 9.2 OpenAPI Metadata Parsing

**From `parameters_json`:**
```json
[
  {
    "name": "date",
    "in": "query",
    "required": false,
    "schema": {
      "type": "string"
    },
    "description": "SGT date for which to retrieve data (YYYY-MM-DD or YYYY-MM-DDTHH:MM:SS)"
  },
  {
    "name": "paginationToken",
    "in": "query",
    "required": false,
    "schema": {
      "type": "string"
    },
    "description": "Pagination token for retrieving subsequent data pages (only exists when there is a next page available for requests with date filters)"
  }
]
```

**Maps to parameters:**
```json
[
  {
    "name": "date",
    "location": "query",
    "required": false,
    "type": "string",
    "description": "SGT date for which to retrieve data (YYYY-MM-DD or YYYY-MM-DDTHH:MM:SS)"
  },
  {
    "name": "paginationToken",
    "location": "query",
    "required": false,
    "type": "string",
    "description": "Pagination token for retrieving subsequent data pages (only exists when there is a next page available for requests with date filters)"
  }
]
```

**From `request_body_json`:**
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
            "maxLength": 200,
            "description": "Title of the resource"
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

**Maps to parameters:**
```json
[
  {
    "name": "title",
    "location": "body",
    "required": true,
    "type": "string",
    "description": "Title of the resource. Must be 1-200 characters. Extract from user's input the main subject or name..."
  },
  {
    "name": "active",
    "location": "body",
    "required": false,
    "type": "boolean",
    "description": "Whether resource is active. Default is true. Set to false only if user explicitly says 'inactive'..."
  }
]
```

## 10. Service Layer Design

### 10.1 Service Interface

```java
public interface OpenApiSchemaService {
    
    /**
     * Retrieves LLM-friendly schemas for all registered endpoints
     * 
     * @return List of simplified schemas with descriptions and parameters
     */
    Mono<OpenApiSchemasResponse> getAllEndpointSchemas();
}
```

### 10.2 Key Processing Steps

```
1. Load all endpoint metadata
   - Query all records from gov_openapi_endpoint
   - For each endpoint, load parent registration for title/context
   ↓
2. For each endpoint, build high-level description
   - Use endpoint.description (preferred)
   - Fall back to endpoint.summary
   - Fall back to registration.title
   - Enhance with keyword-based usage hints
   ↓
3. Parse query parameters
   - Extract from parameters_json
   - For each parameter:
     * Set location = "query"
     * Set name, type, required from OpenAPI
     * Build LLM-friendly description with format hints
   ↓
4. Parse body parameters
   - Extract from request_body_json
   - Get schema properties and required fields
   - For each property:
     * Set location = "body"
     * Set name, type from schema
     * Set required based on required array
     * Build LLM-friendly description with constraints
   ↓
5. Combine into flat parameters array
   - All query params + all body params
   - Differentiated only by location field
   ↓
6. Return schema response
   - id: endpoint UUID
   - description: enhanced description
   - parameters: flat array
```

### 10.3 Description Enhancement Logic

**Pseudocode:**
```java
String buildLlmFriendlyDescription(String title, String summary, String description) {
    StringBuilder sb = new StringBuilder();
    
    // Primary content
    if (description != null && !description.isEmpty()) {
        sb.append(description);
    } else if (summary != null && !summary.isEmpty()) {
        sb.append(summary);
    } else {
        sb.append(title);
    }
    
    // Add usage hints based on keywords
    String combined = (title + " " + summary).toLowerCase();
    
    if (combined.contains("weather") || combined.contains("temperature")) {
        sb.append(" Use this API when users ask for temperature, weather conditions, or climate data.");
    } else if (combined.contains("create") || combined.contains("add")) {
        sb.append(" Use this when user wants to add, create, or register new data.");
    } else if (combined.contains("search") || combined.contains("query")) {
        sb.append(" Use this when user wants to find, search, or filter data.");
    }
    // ... more keyword patterns
    
    return sb.toString();
}
```

### 10.4 Parameter Description Extraction Logic

**Simplified Approach:** Use OpenAPI descriptions directly without enhancement.

**Pseudocode:**
```java
String extractParameterDescription(OpenApiParameter param) {
    // Use OpenAPI description as-is
    if (param.getDescription() != null && !param.getDescription().isEmpty()) {
        return param.getDescription();
    }
    
    // Fallback: use parameter name with type
    return param.getName() + " (" + param.getSchema().getType() + ")";
}

String extractBodyPropertyDescription(String name, Schema schema) {
    // Use schema description as-is
    if (schema.getDescription() != null && !schema.getDescription().isEmpty()) {
        return schema.getDescription();
    }
    
    // Fallback: use property name with type
    return name + " (" + schema.getType() + ")";
}
```

**Benefits:**
- **Preserves Original Intent**: OpenAPI authors know their APIs best
- **No Over-Engineering**: Avoids artificial description generation
- **Consistency**: Uses the same descriptions developers see in OpenAPI docs
- **Maintainability**: Changes to OpenAPI descriptions automatically reflect in schema
- **Simplicity**: Minimal code, easier to maintain and test

## 11. DTO Design

### 11.1 Response Wrapper DTO

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenApiSchemasResponse {
    
    /**
     * List of all endpoint schemas
     */
    private List<OpenApiEndpointSchema> schemas;
}
```

### 11.2 Schema DTO

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenApiEndpointSchema {
    
    /**
     * Endpoint UUID for triggering via /trigger API
     */
    private UUID id;
    
    /**
     * High-level description of what this endpoint does and when to use it
     * Written in LLM-friendly language with usage hints
     */
    private String description;
    
    /**
     * List of all parameters (query + body) needed for this endpoint
     * Flat array with location field differentiating query vs body
     */
    private List<Parameter> parameters;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Parameter {
        
        /**
         * Parameter name (used as key in queryParams or bodyParams)
         */
        private String name;
        
        /**
         * Where to place this parameter: "query" or "body"
         */
        private String location;
        
        /**
         * Whether this parameter must be provided
         */
        private Boolean required;
        
        /**
         * Data type: "string", "integer", "boolean", "number", "array", "object"
         */
        private String type;
        
        /**
         * LLM-friendly description including:
         * - What this parameter represents
         * - Format requirements (with examples)
         * - How to extract from user input
         * - Default behavior if not provided
         */
        private String description;
    }
}
```

## 12. Controller Design

### 12.1 Endpoint Handler

```java
@RestController
@RequestMapping("/api/v2/gov/apis")
public class OpenApiRegistrationController {
    
    private final OpenApiSchemaService schemaService;
    
    /**
     * Get all endpoint schemas for LLM consumption
     * 
     * GET /api/v2/gov/apis/schemas
     * 
     * @return All registered endpoint schemas with descriptions and parameters
     */
    @GetMapping("/schemas")
    @Operation(
        summary = "Get all endpoint schemas for LLM",
        description = "Returns all endpoint descriptions and parameters in LLM-friendly format for discovery and request construction"
    )
    public Mono<ResponseEntity<OpenApiSchemasResponse>> getAllEndpointSchemas() {
        
        log.info("Retrieving all endpoint schemas for LLM");
        
        return schemaService.getAllEndpointSchemas()
                .map(ResponseEntity::ok);
    }
}
```

### 12.2 Error Responses

**500 Internal Server Error (Database Issue):**
```json
{
  "error": "INTERNAL_SERVER_ERROR",
  "message": "Failed to retrieve endpoint schemas",
  "timestamp": "2026-01-31T10:15:13Z",
  "requestId": "auto-generated-uuid"
}
```

**200 OK with Empty Array (No Endpoints Registered):**
```json
{
  "schemas": []
}
```

## 13. Key Design Decisions

### 13.1 Flat Parameter Array

**Decision:** All parameters (query + body) in single flat array

**Rationale:**
- Simpler LLM parsing - no nested objects
- Clear differentiation via `location` field
- Consistent structure regardless of HTTP method
- Easier to iterate over all parameters

**Alternative Considered:** Separate `queryParameters` and `bodyParameters` objects
- Rejected due to added complexity
- Would require LLM to handle two different paths

### 13.2 Location Field for Parameter Placement

**Decision:** Use `location` field with values `"query"` or `"body"`

**Rationale:**
- Explicit mapping to trigger request structure
- LLM can directly map: `location:"query"` → `queryParams`
- Avoids ambiguity about where parameters go

### 13.3 Enhanced Descriptions

**Decision:** Embed format hints, constraints, and extraction guidance in descriptions

**Rationale:**
- LLMs excel at natural language understanding
- Avoids separate `constraints`, `format`, `example` fields
- More flexible than rigid schema fields
- Allows contextual hints (e.g., "'today' → current date")

**Trade-off:** Less structured but more LLM-friendly

### 13.4 Omit Internal Details

**Decision:** Don't expose `httpMethod`, `path`, `baseUrl`, `headers`

**Rationale:**
- LLM doesn't need internal routing details
- Trigger API handles HTTP mechanics automatically
- Reduces response size
- Focuses LLM on parameter construction only

### 13.5 No Response Schema

**Decision:** Don't include response body schema in this endpoint

**Rationale:**
- LLM only needs to construct request, not parse response
- Response parsing is handled separately (by code or another LLM call)
- Keeps schema response focused and minimal
- Future enhancement if needed

## 14. Integration with Trigger API

### 14.1 Three-Step Flow

```
Step 1: GET /api/v2/gov/apis/schemas
        → LLM retrieves all available endpoint schemas
        
Step 2: LLM filters and selects appropriate endpoint
        → Matches user intent with endpoint descriptions
        → Extracts endpoint ID
        
Step 3: POST /api/v2/gov/apis/endpoints/{id}/trigger
        → LLM sends constructed queryParams/bodyParams
```

### 14.2 Parameter Location Mapping

| Schema Response | Trigger Request | Example |
|----------------|----------------|---------|
| `"location": "query"` | Goes into `queryParams` | `{ "queryParams": { "date": "..." } }` |
| `"location": "body"` | Goes into `bodyParams` | `{ "bodyParams": { "title": "..." } }` |

### 14.3 Complete Example

**Schema Response:**
```json
{
  "id": "abc-123",
  "description": "Get weather data...",
  "parameters": [
    {
      "name": "date",
      "location": "query",
      "required": true,
      "type": "string",
      "description": "Date in YYYY-MM-DD format..."
    },
    {
      "name": "station_id",
      "location": "query",
      "required": false,
      "type": "string",
      "description": "Specific station ID. Optional."
    }
  ]
}
```

**LLM Constructs Trigger Request:**
```json
{
  "queryParams": {
    "date": "2024-06-20",
    "station_id": "S50"
  }
}
```

**Sends to:**
```http
POST /api/v2/gov/apis/endpoints/abc-123/trigger
Content-Type: application/json

{
  "queryParams": {
    "date": "2024-06-20",
    "station_id": "S50"
  }
}
```

## 15. Testing Strategy

### 15.1 Unit Tests

**Test Cases:**
- Description generation from various source combinations
- Parameter description enhancement with different formats (date, date-time, enum)
- Query parameter parsing and mapping
- Body parameter parsing and mapping
- Required vs optional parameter handling
- Mixed query + body parameter scenarios
- Null/empty description handling

### 15.2 Integration Tests

**Test Cases:**
- Retrieve all schemas successfully
- Return empty array when no endpoints registered
- Verify response includes GET endpoints (query params)
- Verify response includes POST endpoints (body params)
- Verify response includes mixed query + body endpoints
- Handle database connection errors gracefully

### 15.3 LLM Integration Tests

**Test Cases:**
- Given schema response, verify LLM can construct valid trigger request
- Test with various user queries (natural language)
- Verify LLM extracts correct values from user input
- Verify LLM maps parameters to correct location (query vs body)

## 16. Future Enhancements

### 16.1 Response Schema (Optional)

Add optional response schema to help LLM understand what to expect:

```json
{
  "id": "abc-123",
  "description": "...",
  "parameters": [...],
  "responseSchema": {
    "type": "object",
    "description": "Returns temperature readings with station metadata",
    "properties": [
      {
        "name": "items",
        "type": "array",
        "description": "List of temperature readings"
      }
    ]
  }
}
```

### 16.2 Example Values

Add explicit example values to parameters:

```json
{
  "name": "date",
  "location": "query",
  "required": true,
  "type": "string",
  "description": "Date in YYYY-MM-DD format...",
  "example": "2024-06-20"
}
```

### 16.3 Single Endpoint Schema Retrieval

Add optional endpoint to retrieve schema for a specific endpoint (if needed for performance):

```http
GET /api/v2/gov/apis/endpoints/{endpointId}/schema
```

Useful when:
- LLM already knows the endpoint ID
- Reduces response size for subsequent calls
- Caching already contains all schemas

### 16.4 LLM-Specific Optimizations

- Add `llmHints` field with common extraction patterns
- Include `sampleRequest` showing complete trigger request example
- Add `keywords` array for better intent matching

### 16.5 Caching

- Cache schema responses (immutable after registration)
- Add ETag support for conditional requests
- Redis caching for frequently accessed schemas

## 17. Security Considerations

1. **No Authentication Details**: Don't expose API keys or credentials
2. **Rate Limiting**: Protect against schema scraping
3. **Access Control**: Optionally restrict schema access to authenticated users
4. **Audit Logging**: Log schema retrievals for monitoring

## 18. Monitoring

### 18.1 Metrics

- Counter: `endpoint_schema_requests_total{status}`
- Histogram: `endpoint_schema_duration_seconds`
- Counter: `endpoint_schema_errors{error_type}`

### 18.2 Logging

```java
log.info("All schemas retrieved successfully. TotalEndpoints: {}", 
    schemas.size());
    
log.warn("No endpoints registered in system");
    
log.error("Failed to retrieve schemas: {}", error.getMessage());
```

## 19. API Documentation (OpenAPI Spec)

```yaml
openapi: 3.0.3
info:
  title: Gov API Schema Service
  version: 2.0.0
paths:
  /api/v2/gov/apis/schemas:
    get:
      summary: Get all endpoint schemas for LLM
      description: Returns all endpoint metadata for LLM discovery and request construction
      tags:
        - OpenAPI Schema
      responses:
        '200':
          description: Schemas retrieved successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/OpenApiSchemasResponse'
              examples:
                allSchemas:
                  summary: All registered endpoints
                  value:
                    schemas:
                      - id: "e8f33c55-6def-4f67-c1dd-394a69cd2c4d"
                        description: "Returns air temperature readings..."
                        parameters:
                          - name: "date"
                            location: "query"
                            required: true
                            type: "string"
                            description: "Date in YYYY-MM-DD format..."
                      - id: "a1b2c3d4-5678-90ab-cdef-1234567890ab"
                        description: "Creates a new resource..."
                        parameters:
                          - name: "title"
                            location: "body"
                            required: true
                            type: "string"
                            description: "Title of the resource. Must be 1-200 characters..."
                emptySchemas:
                  summary: No endpoints registered
                  value:
                    schemas: []
        '500':
          description: Internal server error
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ErrorResponse'

components:
  schemas:
    OpenApiSchemasResponse:
      type: object
      required:
        - schemas
      properties:
        schemas:
          type: array
          description: List of all registered endpoint schemas
          items:
            $ref: '#/components/schemas/OpenApiEndpointSchema'
    
    OpenApiEndpointSchema:
      type: object
      required:
        - id
        - description
        - parameters
      properties:
        id:
          type: string
          format: uuid
          description: Endpoint identifier for trigger API
        description:
          type: string
          description: High-level endpoint purpose with LLM usage hints
        parameters:
          type: array
          description: Flat list of all parameters (query + body)
          items:
            $ref: '#/components/schemas/Parameter'
    
    Parameter:
      type: object
      required:
        - name
        - location
        - required
        - type
        - description
      properties:
        name:
          type: string
          description: Parameter name
        location:
          type: string
          enum: [query, body]
          description: Where to place parameter in trigger request
        required:
          type: boolean
          description: Whether parameter is required
        type:
          type: string
          enum: [string, integer, boolean, number, array, object]
          description: Parameter data type
        description:
          type: string
          description: LLM-friendly description with format hints and extraction guidance
    
    ErrorResponse:
      type: object
      properties:
        error:
          type: string
        message:
          type: string
        timestamp:
          type: string
          format: date-time
        requestId:
          type: string
```

## 20. Summary

This endpoint schema API provides a **minimal, LLM-optimized interface** for discovering endpoint metadata. By focusing on just three fields (`id`, `description`, `parameters`) and embedding guidance in natural language descriptions, it enables LLMs to:

1. **Understand** endpoint purpose from high-level descriptions
2. **Extract** parameter values from user queries using embedded hints
3. **Construct** valid trigger requests by mapping parameters to correct locations
4. **Execute** API calls via the trigger endpoint

The flat structure and natural language approach prioritize **LLM comprehension** over schema rigidity, making it ideal for AI-powered API orchestration.
