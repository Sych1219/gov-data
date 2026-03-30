# Register Gov API v2 - API Specification

## Overview
This API allows you to register government APIs by uploading an OpenAPI 3.x specification. The service will validate, parse, and store your API metadata for discovery and integration purposes.

---

## Endpoint Information

| Property | Value |
|----------|-------|
| **Path** | `/api/v2/gov/apis/openapi` |
| **Method** | `POST` |
| **Content-Type** | `application/json` |
| **Response-Type** | `application/json` |

---

## Request Format

### Request Body
Submit a valid OpenAPI 3.x specification as the request body. The specification should follow the OpenAPI standards and include all required fields.

**Example Request:**
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
        "parameters": [
          {
            "name": "date",
            "in": "query",
            "schema": {
              "type": "string",
              "format": "date"
            }
          }
        ],
        "responses": {
          "200": {
            "description": "Successful response",
            "content": {
              "application/json": {
                "schema": {
                  "type": "object"
                }
              }
            }
          }
        }
      }
    }
  }
}
```

### Request Headers
| Header | Required | Description |
|--------|----------|-------------|
| `Content-Type` | Yes | Must be `application/json` |
| `X-Request-Id` | No | Optional request tracking ID |

---

## Validation Requirements

Your OpenAPI specification must meet the following criteria:

### Required Fields
- ✅ `openapi`: Must be version `3.0.x` or `3.1.x`
- ✅ `info`: Must include `title` and `version`
- ✅ `servers`: At least one server URL (HTTP or HTTPS)
- ✅ `paths`: At least one API path defined

### Validation Rules
- **Server URLs**: Must use `http://` or `https://` protocol
- **File Size**: Maximum 5MB
- **Endpoint Count**: Maximum 50 endpoints per specification
- **Unique Registration**: API with same `title` + `base URL` must not already exist
- **Schema References**: All `$ref` references must be resolvable

---

## Response Formats

### Success Response (201 Created)

When your API is successfully registered:

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

**Response Fields:**
| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | Unique identifier for the registered API |
| `title` | String | API title from `info.title` |
| `version` | String | API version from `info.version` |
| `baseUrl` | String | Base URL from `servers[0].url` |
| `endpointCount` | Integer | Number of endpoints registered |
| `endpoints` | Array | Summary of registered endpoints |
| `status` | String | Registration status (always "REGISTERED") |
| `createdAt` | ISO-8601 | Registration timestamp |

---

### Error Responses

#### 400 Bad Request - Invalid Specification
When the OpenAPI specification is invalid or malformed:

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

**Common Validation Errors:**
- Missing required fields (`openapi`, `info`, `paths`, `servers`)
- Unsupported OpenAPI version
- Invalid server URL protocol
- Missing or invalid schema references
- Empty paths object
- Malformed JSON

---

#### 409 Conflict - Duplicate Registration
When an API with the same title and base URL already exists:

```json
{
  "error": "DUPLICATE_API_REGISTRATION",
  "message": "API with title 'Real-time API weather services' and base URL 'https://api-open.data.gov.sg/v2/real-time/api' already exists",
  "existingApiId": "a7f22b44-9dfd-4e56-b0cc-493968bc1b3c",
  "timestamp": "2024-07-20T10:15:23Z",
  "requestId": "req-123456"
}
```

---

#### 413 Payload Too Large
When the OpenAPI specification exceeds the size limit:

```json
{
  "error": "PAYLOAD_TOO_LARGE",
  "message": "OpenAPI specification exceeds maximum size of 5MB",
  "maxSizeBytes": 5242880,
  "actualSizeBytes": 6000000,
  "timestamp": "2024-07-20T10:15:23Z"
}
```

---

## HTTP Status Codes

| Status Code | Description |
|-------------|-------------|
| `201 Created` | API successfully registered |
| `400 Bad Request` | Invalid OpenAPI specification or malformed JSON |
| `409 Conflict` | API already registered (duplicate title + base URL) |
| `413 Payload Too Large` | Specification exceeds 5MB limit |
| `415 Unsupported Media Type` | Content-Type is not `application/json` |
| `500 Internal Server Error` | Server error during registration |

---

## Best Practices

### 1. Prepare Your OpenAPI Specification
- Ensure compliance with OpenAPI 3.0.x or 3.1.x standards
- Validate your spec using tools like [Swagger Editor](https://editor.swagger.io/)
- Include comprehensive descriptions and examples
- Define all schema references in `components.schemas`

### 2. Minimize Specification Size
- Keep specifications under 5MB
- Limit to 50 endpoints or fewer
- Use schema references to avoid duplication
- Remove unnecessary examples or descriptions if size is an issue

### 3. Ensure Unique Registration
- Use unique combinations of `info.title` and `servers[0].url`
- Check existing registrations before submitting
- Use versioning in your API title if registering multiple versions

### 4. Use Request Tracking
- Include `X-Request-Id` header for debugging and support
- Store the request ID for correlation with logs and errors

---

## Integration Examples

### cURL Example
```bash
curl -X POST https://api.example.com/api/v2/gov/apis/openapi \
  -H "Content-Type: application/json" \
  -H "X-Request-Id: req-12345" \
  -d @openapi-spec.json
```

### JavaScript/Fetch Example
```javascript
const openApiSpec = {
  openapi: "3.0.3",
  info: {
    title: "My API",
    version: "1.0.0"
  },
  servers: [{ url: "https://api.example.com" }],
  paths: {
    "/users": {
      get: {
        summary: "Get users",
        responses: {
          "200": { description: "Success" }
        }
      }
    }
  }
};

fetch('https://api.example.com/api/v2/gov/apis/openapi', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-Request-Id': 'req-12345'
  },
  body: JSON.stringify(openApiSpec)
})
  .then(response => response.json())
  .then(data => console.log('Registered:', data))
  .catch(error => console.error('Error:', error));
```

### Python Example
```python
import requests
import json

with open('openapi-spec.json', 'r') as f:
    openapi_spec = json.load(f)

headers = {
    'Content-Type': 'application/json',
    'X-Request-Id': 'req-12345'
}

response = requests.post(
    'https://api.example.com/api/v2/gov/apis/openapi',
    headers=headers,
    json=openapi_spec
)

if response.status_code == 201:
    print('Successfully registered:', response.json())
else:
    print('Error:', response.status_code, response.json())
```

### Java Example
```java
import java.net.http.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public class ApiRegistration {
    public static void main(String[] args) throws Exception {
        String openApiJson = Files.readString(Path.of("openapi-spec.json"));
        
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.example.com/api/v2/gov/apis/openapi"))
            .header("Content-Type", "application/json")
            .header("X-Request-Id", "req-12345")
            .POST(HttpRequest.BodyPublishers.ofString(openApiJson))
            .build();
        
        HttpResponse<String> response = client.send(request, 
            HttpResponse.BodyHandlers.ofString());
        
        System.out.println("Status: " + response.statusCode());
        System.out.println("Response: " + response.body());
    }
}
```

---

## Troubleshooting

### Issue: 400 Bad Request - "Missing required field"
**Solution:** Ensure your OpenAPI spec includes all required fields:
- `openapi` (version string)
- `info.title` and `info.version`
- `servers` array with at least one entry
- `paths` object with at least one path

### Issue: 409 Conflict - "API already registered"
**Solution:** 
- Check if an API with the same title and base URL exists
- Update the title or use a different base URL
- Use versioning in your title (e.g., "My API v2")

### Issue: 413 Payload Too Large
**Solution:**
- Reduce the size of your OpenAPI specification
- Remove large examples or descriptions
- Split into multiple API registrations if necessary
- Use external `$ref` URLs (if supported)

### Issue: Schema validation errors
**Solution:**
- Validate your spec at [editor.swagger.io](https://editor.swagger.io/)
- Ensure all `$ref` references are defined in `components.schemas`
- Check that all schemas follow JSON Schema standards

---

## Retrieving Registered API

After registration, you can retrieve your API details using:

**Endpoint:** `GET /api/v2/gov/apis/openapi/{id}`

**Example:**
```bash
curl https://api.example.com/api/v2/gov/apis/openapi/a7f22b44-9dfd-4e56-b0cc-493968bc1b3c
```

**Response:** Same format as the registration success response (201).

---

## Support

For questions or issues:
- Check the validation error messages for specific guidance
- Include the `X-Request-Id` when contacting support
- Validate your OpenAPI spec before submitting
- Review this documentation for requirements and examples
