# Semantic Layer

`semantic-layer` is a Spring Boot service for shared semantic metadata and governance operations.

It currently provides APIs and service logic for:

- schema and connection registry reads
- object registration and object exposure reads
- relationship registration
- filter lookup registration, preview, binding, certification, and reads
- workflow task approval
- governance policy preset reads
- attribute pairing registration and attribute pairing resolution
- logical-to-physical resolution reads for downstream engines

## Stack

- Java 17
- Spring Boot 3.2.5
- Spring Web
- Spring Validation
- Spring JDBC
- Spring Data Neo4j
- Flyway PostgreSQL module
- PostgreSQL JDBC driver

## Architecture

The service follows a strict layered structure:

```text
controller -> service -> dao -> dao.impl
```

Package layout:

```text
src/main/java/com/lextr/semanticlayer
  api/
  dao/
  dao/impl/
  dto/
  exception/
  model/
  service/
  service/impl/
  util/
```

### Implementation conventions

- JDBC access uses `NamedParameterJdbcTemplate`.
- SQL is externalized in `src/main/resources/queries.properties`.
- SQL is loaded through `SQLQueryLoaderUtil`.
- Jackson is configured for `SNAKE_CASE`.
- DTOs, model records, and exceptions are kept separate from service logic.
- Business-policy decisions are kept outside the core service code as policy assets and client interfaces.

## API surface

Current controller groups:

- `/api/registry`
  - schema and connection discovery
- `/api/objects`
  - object registration
  - object exposure reads
- `/api/relationships`
  - relationship registration
- `/api/filter-lookups`
  - lookup registration
  - lookup preview
  - lookup binding
  - lookup certification
  - lookup reads
- `/api/governance/policy-presets`
  - effective governance preset reads
- `/api/workflow-tasks`
  - workflow approval
- `/api/attribute-pairings`
  - pairing registration
  - pairing resolution
- `/api/logical-physical-resolutions`
  - logical attribute resolution
  - consumption outbound grain resolution

## Core modules

### Registry and object metadata

- registry read services for schemas and connections
- object registration service and DAO
- object exposure read service and DAO

### Relationship management

- relationship registration service
- JDBC relationship write DAO
- Neo4j projection client seam

### Filter lookup management

- lookup registration
- lookup preview with execution logging
- lookup binding
- lookup certification
- effective review and read flows

### Workflow and governance

- workflow approval service and DAO
- governance policy preset read service

### Attribute pairing

- pairing registration with object/attribute validation, index gate, and workflow/audit writes
- pairing resolution with active pairing lookup, cache lookup, and cache upsert/hit tracking

## SQL and policy assets

### SQL

All JDBC SQL lives in:

- `src/main/resources/queries.properties`

This includes SQL for:

- registry reads
- object registration and exposure
- relationship registration
- filter lookup registration, read, preview, binding, certification, and execution logs
- workflow approval
- attribute pairing registration, resolution, index checks, and cache operations
- logical-physical resolution queries for governed downstream engine mapping

### Policy assets

Rego assets live under `src/main/resources/opa`:

- `taxonomy/jurisdiction_valid.rego`
- `object_exposure/access_control.rego`
- `object_exposure/classification_exposure.rego`
- `relationship/cross_engine_block.rego`
- `relationship/cross_engine_pairing.rego`
- `relationship/cross_engine_query.rego`
- `filter_lookup/review_period_floor.rego`
- `filter_lookup/overdue_binding.rego`
- `filter_lookup/stale_value_certification.rego`

The codebase also exposes policy client interfaces such as:

- `TaxonomyPolicyClient`
- `RelationshipPolicyClient`
- `FilterLookupPolicyClient`
- `WorkflowPolicyClient`
- `AttributePairingPolicyClient`

## Runtime configuration

Checked-in application configuration is intentionally minimal:

- `src/main/resources/application.yaml`

Current defaults:

- application name: `semantic-layer`
- Jackson property naming strategy: `SNAKE_CASE`

For full runtime behavior, external environment-specific configuration is expected for services such as PostgreSQL and Neo4j.

## Testing

Tests live under `src/test/java/com/lextr/semanticlayer` and are split across:

- controller tests
- wire-through API tests
- DAO tests
- service tests
- SQL asset tests
- Rego policy asset tests

Current notable wire-through coverage includes:

- object registration
- object exposure
- registry reads
- relationship registration
- filter lookup registration
- filter lookup preview
- filter lookup binding
- filter lookup certification
- governance policy preset reads
- workflow approval
- attribute pairing

### Coverage gate

The Maven build enforces a JaCoCo line coverage minimum of `0.90` at bundle level.

Excluded from the coverage rule:

- bootstrap class
- DTOs
- model records
- exception classes

## Build and development

### Run tests

```bash
./mvnw test
```

### Run coverage gate

```bash
./mvnw verify
```

### Run the application

```bash
./mvnw spring-boot:run
```

## Related dependency

This service depends on its database contract being provisioned by the migration repository that owns the `semantic-service` Flyway migrations. That migration ownership is external to this repository; this repo only contains the runtime service code, SQL assets, and tests.
