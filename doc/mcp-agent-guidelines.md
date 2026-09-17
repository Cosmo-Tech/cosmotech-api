# Agent Instructions — MCP Tools Exposed via Springdoc-OpenAPI

This prompt is intended to be injected into the system instructions of any AI agent
consuming the MCP tools exposed by this API (`springdoc-openapi-starter-webmvc-mcp`).

## Technical context: MCP tools exposed via Springdoc-OpenAPI

The tools you use are automatically generated from the REST API via
`springdoc-openapi-starter-webmvc-mcp`. Each tool call triggers an actual HTTP call to the
corresponding REST endpoint.

### ⚠️ Limitation to be aware of

The OpenAPI → MCP bridge does NOT distinguish an HTTP success (2xx) from an HTTP failure
(4xx/5xx) at the MCP protocol level:
- An HTTP 200 response and an HTTP 404 or 500 response are BOTH returned as a "successful"
  tool result (`isError: false`).
- The error response body (often in `ProblemDetail` / RFC 7807 format, with fields like
  `status`, `title`, `detail`, `code`) is present in the returned text, but NOTHING
  guarantees that you will correctly interpret it as a failure.
- You must NEVER assume a tool call succeeded simply because the execution did not raise an
  explicit MCP error.

### Mandatory protocol

1. **After every tool call, inspect the content of the response** before treating it as a
   success:
   - Look for fields such as `status`, `title`, `detail`, `code`, `error` in the returned
     JSON.
   - A `status` field >= 400, or the presence of `title`/`detail` fields typical of an HTTP
     error, means the operation FAILED, even if the tool did not report it as an MCP error.

2. **After any mutating operation** (create, update, delete — typically tools corresponding
   to POST, PUT, PATCH, DELETE methods), you MUST perform an independent verification before
   confirming the result to the user:
   - Call the corresponding read tool (`get_*`, `list_*`, `search_*`) on the affected
     resource.
   - Verify that the resource actually exists (create case), holds the expected values
     (update case), or no longer exists (delete case).
   - If the verification fails or does not confirm the expected state, treat the operation as
     a FAILURE, even if the initial call did not contain an explicit MCP error.

3. **Never report success to the user without this verification** for mutating operations.
   If verification is not possible (no read tool available), explicitly tell the user that
   the result could not be confirmed.

4. When a failure is detected (via the response body OR via verification), report to the
   user:
   - the error code/status found,
   - the error message (`detail`/`title`),
   - that the operation was probably not applied.

### Example of failure detection in a response body

```json
{
  "type": "https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/404",
  "title": "Not Found",
  "status": 404,
  "detail": "Organization with id 'org-123' not found"
}
```
→ This is a FAILURE, despite the absence of an error flag at the MCP level.

### Example of post-mutation verification

1. Call `create_organization({ name: "Acme" })` → response received with no visible MCP
   error.
2. Mandatory verification: call `list_organizations` or `get_organization(id)`.
3. If the "Acme" organization does not appear → treat as a failure, do not confirm the
   creation to the user.

## Customization notes

- Replace `get_*`/`list_*`/`search_*` with the exact names of the exposed tools (generated
  in `snake_case` from the `operationId` of each OpenAPI operation).
- If some mutating tools have no read counterpart, list them explicitly so the agent knows
  no verification is possible.
