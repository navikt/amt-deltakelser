# sim-nav fake implementation guide

This file documents the structure used by fakes in `sim-nav`, so new fakes can follow the same pattern.

## Quick checklist for any new fake

- [ ] Add one `PATH_PREFIX` constant (for example `"/my-service"`).
- [ ] Add one route function as `fun Route.myServiceFakeRoutes()`.
- [ ] Load fixtures from `src/main/resources` (JSON and/or GraphQL schema files).
- [ ] Register the fake in `simNavModule()` in `src/main/kotlin/SimNavApplication.kt`.
- [ ] If the fake needs a startup hint, add a `println(...)` in `src/main/kotlin/Main.kt`.

## Type 1: REST endpoint fakes

Use this for services where clients call HTTP endpoints directly (non-GraphQL).

### File pattern

- One file in `src/main/kotlin`, usually named `XxxFake.kt`.
- Top-level constants:
    - `X_PATH_PREFIX`
    - optional resource path constants for fixture files
- Route function:
    - `fun Route.xxxFakeRoutes()`
    - `route(X_PATH_PREFIX) { ... }`
- Optional private helpers for parsing query/body and looking up fixture data.

### Response and parsing conventions

- Use `respondJson(call, status, body)` for JSON responses.
- Use `respondEmpty(call, status)` for empty responses.
- Use `readRequestBody(call)` when request content matters.
- Use `respondGzipJson(...)` only when client expects compressed payloads.
- For missing entities, return `404` with JSON error body (`{"error":"entity not found"}`).

### Fixture conventions

- Keep fixture data in JSON under `src/main/resources/<service>/...` when service-specific,
  or directly under `src/main/resources/` when single-file and already established.
- Deserialize into private data classes in the fake file.

### Existing REST examples

- `src/main/kotlin/BronnoysundFake.kt`
- `src/main/kotlin/NorgFake.kt`
- `src/main/kotlin/VeilarboppfolgingFake.kt`
- `src/main/kotlin/PoaoTilgangFake.kt`
- `src/main/kotlin/UnleashFake.kt`
- `src/main/kotlin/KafkaFake.kt`

## Type 2: GraphQL service fakes

Use this for services where clients call one GraphQL endpoint.

### File pattern

- Usually two files per fake:
    - `XxxFake.kt` (HTTP route + fixture lookup + data fetchers)
    - `XxxGraphqlWiring.kt` (schema loading + runtime wiring)
- Route in `XxxFake.kt`:
    - `route(X_PATH_PREFIX) { ... }`
    - `post("graphql") { respondGraphqlFake(call, objectMapper, graphql) }`

### Schema and wiring conventions

- Keep schema files in `src/main/resources/<service>/*.graphqls`.
- `createXxxGraphql(...)` should build `GraphQL.newGraphQL(executableSchema).build()`.
- `createXxxExecutableSchema(...)` should:
    - parse schema resources
    - add needed scalar mappings (`Date`, `DateTime`, `Json`, `Long`, etc.)
    - bind `Query` fields to explicit `DataFetcher`s
- If unions/interfaces are used, add explicit type resolvers (see `NomGraphqlWiring.kt`).

### Fixture conventions

- Keep response fixture data in `src/main/resources/<service>/<service>-data.json`.
- Load fixtures once at file level and resolve by requested id argument.
- Prefer fallback defaults (`defaultIdent`, `defaultNavident`) to keep local runs robust.

### Existing GraphQL examples

- `src/main/kotlin/pdl/PdlFake.kt` + `src/main/kotlin/pdl/PdlGraphqlWiring.kt`
- `src/main/kotlin/nom/NomFake.kt` + `src/main/kotlin/nom/NomGraphqlWiring.kt`
- `src/main/kotlin/aooppfolgingskontor/AoOppfolgingskontorFake.kt` +
  `src/main/kotlin/aooppfolgingskontor/AoOppfolgingskontorGraphqlWiring.kt`

## Shared helpers and composition

- `src/main/kotlin/HttpResponses.kt` has common HTTP helpers.
- `src/main/kotlin/GraphqlFakeHttp.kt` has common GraphQL request handling and validation.
- `src/main/kotlin/shared/ResourceLoading.kt` has generic JSON resource loading.
- `src/main/kotlin/SimNavApplication.kt` is the single place that wires all fake routes.

## Practical rule when adding a new fake

1. Start from client code and copy the exact path/shape/field names used by the client.
2. Pick REST or GraphQL structure from sections above.
3. Reuse shared helpers before adding fake-specific utility code.
4. Keep behavior deterministic and simple (fixture-driven, no hidden randomness).
5. Register in `simNavModule()` and verify local startup.

