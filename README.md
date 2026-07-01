# java-yaml-migration-workshop

Suggested repository description:

> Java YAML migration workshop: migrating configuration files with Jackson YAML and Mustache templates.

  * references
    * https://github.com/FasterXML/jackson-dataformats-text/tree/2.17/yaml
    * https://github.com/FasterXML/jackson-databind
    * https://github.com/spullara/mustache.java
    * https://datatracker.ietf.org/doc/html/rfc6901
    * https://yaml.org/spec/1.2.2/

## preface

  * goals of this workshop:
    * introduction to YAML processing in Java
    * introduction to Jackson `JsonNode` tree model
    * introduction to JSON Pointer paths
    * introduction to template-based file generation with Mustache
    * migrate configuration files without regex and manual line scanning
    * validate generated YAML before replacing source files
  * workshop:
    * `src/main/java/YamlMigration.java`
    * `src/main/resources/service.yaml.mustache`
  * tests:
    * `src/test/java/YamlMigrationTest.java`

## problem

  * we have a fictional application configuration file: `app.yaml`
  * the old format is application-oriented:

```yaml
app:
  id: inventory-service
  owner: platform-team
  port: 8080
  database:
    host: db.internal
    name: inventory
  resources:
    cpu: 500m
    memory: 512Mi
```

  * we want to migrate it to a service-oriented format: `service.yaml`

```yaml
service:
  name: inventory-service
  team: platform-team
  runtime:
    port: 8080
  persistence:
    dbHost: db.internal
    dbName: inventory
  limits:
    cpu: 500m
    memory: 512Mi
```

## rules

  * recursively scan a base directory for every file named `app.yaml`
  * for each `app.yaml`, generate `service.yaml` in the same directory
  * after successful write of `service.yaml`, delete the original `app.yaml`
  * use `Files.walk(...)` for recursive scanning
  * use `Files.delete(...)` only after `service.yaml` has been written successfully
  * parse input YAML with Jackson, not with regex
  * read values from `JsonNode` using JSON Pointer paths
  * render output YAML from a template
  * parse rendered YAML with Jackson before writing it
  * one bad file should not stop migration of other files

## mapping

  * `/app/id` -> `name`
  * `/app/owner` -> `team`
  * `/app/port` -> `port`
  * `/app/database/host` -> `dbHost`
  * `/app/database/name` -> `dbName`
  * `/app/resources/cpu` -> `cpu`
  * `/app/resources/memory` -> `memory`

## template

`src/main/resources/service.yaml.mustache`

```yaml
service:
  name: {{name}}
  team: {{team}}
  runtime:
    port: {{port}}
  persistence:
    dbHost: {{dbHost}}
    dbName: {{dbName}}
  limits:
    cpu: {{cpu}}
    memory: {{memory}}
```

## why not regex

  * YAML is a structured format, not a line format
  * indentation changes meaning
  * scalars can be quoted, unquoted, multiline or typed
  * comments and ordering should not be treated as a parsing strategy
  * Jackson already understands YAML and exposes it as a tree
  * JSON Pointer makes field access explicit and easy to review

## validation

  * template rendering creates plain text
  * plain text can still be invalid YAML
  * therefore the rendered output is parsed again with Jackson
  * if validation fails:
    * `service.yaml` is not written
    * `app.yaml` is not deleted
    * the error is printed
    * migration continues with the next file

## tests

  * `migratesAppYamlToServiceYamlAndDeletesOriginal`
    * creates temporary `app.yaml`
    * runs migration
    * compares whole `service.yaml` text with expected YAML
    * checks that original `app.yaml` was deleted
  * `keepsMigratingWhenOneFileIsBad`
    * creates one valid and one invalid `app.yaml`
    * checks that valid file is migrated
    * checks that invalid file is left untouched

## exercises

  1. add support for optional fields with default values
