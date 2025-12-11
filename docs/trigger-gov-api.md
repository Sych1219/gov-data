## Trigger Gov Public API – Endpoint Design

### 1. Objective
- Allow internal callers to execute a previously registered government public API using its `id`.
- Use the stored metadata (base URL, HTTP method, headers, query/body parameter definitions) to construct a real HTTP request.
- Accept runtime values for query/body parameters and optional header overrides, then proxy the call to the external API.

### 2. REST Endpoint
| Item | Value |
| --- | --- |
| Path | `/api/v1/gov/apis/{apiId}/trigger` |
| Method | `POST` |
| Consumes | `application/json` |
| Produces | `application/json` |

- `apiId` is the UUID returned by the registration endpoint and maps to `gov_api_registration.id`.

### 3. Request Model
The trigger request focuses on passing runtime values that correspond to the metadata captured at registration time.

```json
{
  "query": {
    "filters": {
      "state": "CA",
      "district": "San Francisco"
    },
    "per_page": 50
  },
  "body": {
    "payloadField": "value-if-required-for-POST"
  },
  "headerOverrides": {
    "X-Request-Id": "job-20240620-001",
    "X-Caller": "batch-process-1"
  },
  "useExampleDefaults": true
}
```

- `query` is a generic JSON object whose structure should align with the tree defined in `GovApiRegistrationRequest.queryParams`:
  - For `type = OBJECT` entries, clients send nested JSON objects.
  - For non-OBJECT entries (STRING/INTEGER/FLOAT/BOOLEAN), clients send leaf values.
  - Example: if registration defined an OBJECT `filters` with children `state`, `district`, then callers send `query.filters.state` and `query.filters.district`.
- `body` is a JSON object whose fields should align with `GovApiRegistrationRequest.bodyParams` keys. Values are sent as strings and will be coerced according to the registered type where applicable.
- `headerOverrides` is an optional flat map of header key/value pairs:
  - Values here override headers defined at registration time with the same key.
  - New headers not defined in registration can be added for observability, tracing, etc.
- `useExampleDefaults`:
  - When `true`, missing `query`/`body` fields will fall back to the `exampleValue` defined in the registration metadata where available.
  - When `false`, only explicitly provided fields are sent downstream.

### 4. Resolution & Invocation Flow
1. **Load registration**
   - Look up `gov_api_registration` by `apiId`.
   - If not found, return `404 NOT_FOUND`.
2. **Prepare outbound URL**
   - Start from `base_url` stored in registration.
   - Merge runtime `query` object with `query_params_json`:
     - Validate that runtime keys exist in the metadata tree (or allow extra keys as a future enhancement).
     - For nested OBJECT entries, flatten to `filters[state]=CA`, `filters[district]=San Francisco`, etc.
     - Coerce runtime values to the registered `QueryParamType` when possible.
     - Apply `useExampleDefaults`: if a key is missing in `query` but has an `exampleValue` in metadata, include it (unless `useExampleDefaults = false`).
   - Build the final query string and append to `base_url`.
3. **Prepare headers**
   - Start from `headers_json` defined at registration.
   - Overlay with `headerOverrides` (runtime wins on conflicts).
   - Add platform headers (e.g. `X-Request-Id` if not provided, trace IDs, etc.).
4. **Prepare request body**
   - If registration `http_method` is `GET`, skip body.
   - For `POST/PUT/PATCH`:
     - Align runtime `body` with `body_params_json` definitions.
     - Coerce values according to the registered type, where applicable.
     - Apply `useExampleDefaults` logic similarly to query params.
   - Serialize as JSON.
5. **Execute outbound HTTP call**
   - Use reactive `WebClient` or similar non-blocking client.
   - Apply connect/read timeouts and retry policies at the client layer.
6. **Build trigger response**
   - Capture external HTTP status, headers (whitelisted), and response body.
   - Return a normalized response envelope to the caller (see below).

### 5. Response Contracts
**Success 200**
```json
{
  "status": "SUCCESS",
  "apiId": "c0f22b44-4dfd-4e56-a0bb-293968bc0b0c",
  "externalStatus": 200,
  "invokedAt": "2024-06-20T10:15:13Z",
  "requestId": "job-20240620-001",
  "responseBody": {
    "data": [
      {
        "school_name": "Example Elementary",
        "state": "CA"
      }
    ]
  }
}
```

**Validation Failure 400**
```json
{
  "error": "VALIDATION_ERROR",
  "message": "Unknown query parameter: invalidKey"
}
```

**Registration Not Found 404**
```json
{
  "error": "API_NOT_FOUND",
  "message": "No registration found for id c0f22b44-4dfd-4e56-a0bb-293968bc0b0c"
}
```

**External Call Failure 502**
```json
{
  "error": "UPSTREAM_ERROR",
  "message": "External API returned 500",
  "externalStatus": 500
}
```

### 6. Validation Rules
- `apiId` must be a valid UUID; if format is invalid, return 400.
- When `useExampleDefaults = false`:
  - Only keys present in `query`/`body` are used; unknown keys may be rejected (400) or ignored (configurable).
- When `useExampleDefaults = true`:
  - Keys missing in `query`/`body` but defined in metadata with an `exampleValue` are automatically included.
- Runtime values should be checked against the registered type where feasible:
  - INTEGER and FLOAT types must parse successfully.
  - BOOLEAN values must be recognizable (`true/false`, `1/0`, etc.), or the call fails with 400.
- Limit payload size for `query`, `body`, and `responseBody` (e.g. via Spring configuration) to protect against abuse.

### 7. Error Handling
- Use global `@ControllerAdvice` to map:
  - Validation exceptions → 400.
  - Missing registration → 404.
  - Upstream timeouts, connection failures, or 5xx → 502 with `UPSTREAM_ERROR`.
- Include `X-Request-Id` (either provided by caller or generated) in all responses for traceability.
- Log full outbound request metadata (without secrets) and truncated response bodies for debugging.

### 8. Swagger / OpenAPI
- Annotate controller method with `@Operation(summary = "Trigger registered government public API")`.
- Document path parameter `apiId` and trigger request/response models.
- Clarify the semantics of `useExampleDefaults`, `headerOverrides`, and how nested `query` objects map to registered `queryParams`.

### 9. Future Enhancements
- Support asynchronous trigger:
  - Return 202 + job id.
  - Process external call in background and persist result.
- Allow selecting a subset of fields from upstream response (projection).
- Add per-API rate limiting and circuit breaking configuration.
- Allow per-request overrides for timeout and retry policies (within safe boundaries).
