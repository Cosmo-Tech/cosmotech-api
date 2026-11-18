# Null values in the API

This guide describes the current handling of `null` in resource request and
response bodies. The JSON examples also apply to YAML bodies: a YAML property
with a `null` value is not an omitted property.

## Creating a resource

Required and nullable are separate concepts: a required property must be present,
while a nullable property can have a `null` value. An optional property is not
necessarily nullable according to its OpenAPI schema.

Required, non-nullable properties must have a value of the expected type. For
example, `POST /organizations` requires `name`; omitting it or sending
`{"name": null}` results in HTTP `400 Bad Request`.

Omitted optional properties use the request model or service default, if one is
defined. Otherwise, they can remain `null`. For example, an omitted or explicitly
`null` Workspace `description` remains `null` when the Workspace is created.
Defaults are field-specific: do not assume that explicitly sending `null`
selects a property's default.

**Compatibility note:** the current server accepts `null` for many optional
properties even when the OpenAPI schema does not declare `nullable: true`.
This is existing compatibility behavior, not a guarantee that every property
accepts `null`. Generated clients may enforce the schema more strictly.

## Updating a resource

The resource update request objects for Organizations, Solutions, Workspaces,
Datasets, and Runners use optional fields. In these `PATCH` requests, at the field
being updated:

| Request value | Effect |
| --- | --- |
| Property omitted | Keep the existing value. |
| `null` | Keep the existing value, even if it is currently non-null. |
| A non-null value | Apply the supplied value, subject to validation. |

These requests are not JSON Merge Patch: `null` does **not** remove a property
or reset it to its default. In particular, a top-level optional string cannot be
reset to `null` by sending `null`.

For example, consider
`PATCH /organizations/{organization_id}/workspaces/{workspace_id}` on a Workspace
with `description: "Original description"`, `tags: ["baseline"]`, and
`additionalData: {"source": "old"}`:

| JSON request body | Result |
| --- | --- |
| `{}` | These fields are unchanged. |
| `{"description": null, "tags": null, "additionalData": null}` | These fields are unchanged. |
| `{"description": ""}` | The description becomes an empty string. |
| `{"tags": []}` | The tags become an empty array. |
| `{"additionalData": {}}` | The additional data becomes an empty object. |
| `{"additionalData": {"source": "new"}}` | The additional data is replaced with the supplied object, not merged. |

Empty values are not interchangeable with `null`. They can clear fields that
allow them, but validation still applies: for example, an empty Organization
`name` is rejected with HTTP `400 Bad Request`. To delete a resource or a
Solution parameter, use its dedicated `DELETE` operation, not a `null` value.

### Nested objects and collections

The keep-existing-value rule is not a recursive merge rule. A supplied object or
collection can replace the existing value, and its own required fields must
still be provided.

For example, `"solution": null` in a Workspace update preserves the entire
existing Solution configuration. Supplying the following object replaces that
configuration:

```json
{
  "solution": {
    "solutionId": "sol-0123456789ab",
    "datasetId": null
  }
}
```

The supplied Solution must exist. In the replacement configuration, `datasetId`
is `null`, and the omitted `defaultParameterValues` is also `null`; their previous
values are not retained. Sending `"solution": {}` instead is invalid because
`solutionId` is required.

Similarly, a `null` collection property is not the same as an empty collection.
Do not use `null` entries inside an array as deletion markers; supply valid
items according to the item schema.

## Reading a resource

Optional response properties can contain `null` when no value is set. For
example, a Workspace created without a description can be returned with
`"description": null`. This is distinct from `"description": ""` or an empty
collection such as `"tags": []`.

A `null` response property describes the current value; it is not an instruction
for a later update. Sending that `null` back in a resource `PATCH` request keeps
the current field value rather than clearing it.

See the [Workspace request schemas](../workspace/src/main/openapi/workspace.yaml)
and [Organization request schemas](../organization/src/main/openapi/organization.yaml)
for the fields and validation constraints used in these examples.
