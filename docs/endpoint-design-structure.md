## Endpoint Design Structure Template

Use this template to author endpoint design specs so they stay consistent across services. Populate each section with the concrete details for the endpoint you are documenting.


### 1. Objective
- Summarize the business problem solved by the endpoint (1–3 bullets).
- Call out the primary consumers and the key data that must be captured.

### 2. REST Endpoint
| Item | Value | Notes |
| --- | --- | --- |
| Path | `/api/v1/...` | Include versioning and resource naming. |
| Method | `GET/POST/...` | Specify HTTP method(s) required. |
| Consumes | `application/json` | List accepted content types. |
| Produces | `application/json` | List response content types. |

### 3. Request Model
Provide a canonical JSON example covering headers, query params, body params, and descriptions. Include notes on optional nested structures (e.g., objects that expand into `children` arrays such as `filters`).

Notes:
- For `GET` endpoints, there is no request body; omit the `<bodyDefinition>` block entirely.
- For body-capable methods (`POST|PUT|PATCH|DELETE` when applicable), include `<bodyDefinition>` as shown below.

```json
{
  "<identifierField>": "<human readable name or code>",
  "<targetBlock>": {
    "<hostField>": "<domain or upstream service>",
    "<pathField>": "/resource/{id}",
    "<methodField>": "<HTTP_METHOD>"
  },
  "<headersArray>": [
    {"<keyField>": "<header-key-1>", "<valueField>": "<header-value-1>", "<requiredFlag>": true, "<notesField>": "<include when...>"},
    {"<keyField>": "<header-key-2>", "<valueField>": "<header-value-2>", "<requiredFlag>": false}
  ],
  "<queryParamsArray>": [
    {
      "<paramKeyField>": "<nested-param>",
      "<typeField>": "OBJECT",
      "<descriptionField>": "<parent container description>",
      "<childrenField>": [
        {"<paramKeyField>": "<child-param>", "<exampleField>": "<example>", "<typeField>": "STRING|INTEGER|BOOLEAN", "<descriptionField>": "<leaf description>"}
      ]
    },
    {
      "<paramKeyField>": "<flat-param>",
      "<exampleField>": "<example-value>",
      "<typeField>": "STRING|INTEGER|BOOLEAN",
      "<descriptionField>": "<single-level description>",
      "<requiredFlag>": false
    }
  ],
  "<bodyDefinition>": {
    "<contentTypeField>": "<application/json|application/xml|...>",
    "<fieldsArray>": [
      {"<pathField>": "payload.items[].id", "<typeField>": "<SCALAR|OBJECT|ARRAY>", "<exampleField>": "<example-or-null>", "<descriptionField>": "<why it is needed>"}
    ]
  },
  "<descriptionField>": "<summary of the action performed or data returned>"
}
```

### 4. Validation Rules
- List required fields and format checks (e.g., HTTPS-only URLs, enum constraints).
- Document rules for nested structures, duplicate detection, and optional arrays.
- Mention custom validators or annotations needed.

### 5. Response Contracts
Document every response scenario:
- **Success `<status>`**
  ```json
  {
    "id": "<uuid>",
    "name": "<resource name>",
    "status": "<domain status>",
    "createdAt": "2024-06-20T10:05:13Z"
  }
  ```
- **Validation Failure `<status>`**
  ```json
  {"error": "VALIDATION_ERROR", "message": "description"}
  ```
- **Domain-specific errors (e.g., Conflict 409)**: Explain trigger conditions and payload.

### 6. Persistence
- Describe the storage model (table/collection name). If the endpoint is stateless and does **not** persist data, explicitly note that and explain why (e.g., “transient proxy call”).
- Enumerate columns/fields, types, and purpose (e.g., `headers_json`, `query_params_json`) when storage exists.
- Call out indexes, foreign keys, or serialization formats, or specify `N/A` when there is no backing store.

### 7. Processing Steps
Outline the flow from controller to service:
1. Validation (framework annotations plus custom logic).
2. Transformation/normalization (e.g., convert lists to maps).
3. Persistence or downstream calls (service names, transactional notes).

### 8. Error Handling
- Identify which exceptions map to which HTTP responses.
- Note global interceptors/`@ControllerAdvice`, correlation IDs, and logging obligations.
- Clarify how timeouts or retries are treated if applicable.

### 9. Swagger / OpenAPI
- Specify annotations or YAML snippets to expose the endpoint.
- Document enums, request/response schemas, and nested objects.
- Mention any example payloads or descriptions to surface in generated docs.

### 10. Future Enhancements
- Capture roadmap items (e.g., support PATCH, ingest OpenAPI spec).
- Highlight dependencies or prerequisites for each enhancement.

---

When creating a new endpoint doc, replace the placeholders above with the endpoint-specific information while keeping the structure intact so future readers can quickly compare specs.
