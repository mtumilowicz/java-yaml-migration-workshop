# YAML Shape-Shifting in Java: Migrating Config Files with Jackson

## Workshop Goal

Learn how to migrate one YAML file format into another using Java and Jackson YAML.

This beginner-friendly example scans a directory tree for every file named `app.yaml`, converts each one to the new `service.yaml` format, writes the new file in the same directory, and then deletes the original `app.yaml`.

## Input YAML Example

`app.yaml`

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

## Output YAML Example

`service.yaml`

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

## Maven Setup

`pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>workshop</groupId>
    <artifactId>java-yaml-migration-workshop</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <jackson.version>2.17.2</jackson.version>
        <junit.version>5.10.3</junit.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson.dataformat</groupId>
            <artifactId>jackson-dataformat-yaml</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.3.0</version>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
</project>
```

## Full Java Code

`src/main/java/YamlMigration.java`

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class YamlMigration {
    private static final String INPUT_FILE_NAME = "app.yaml";
    private static final String OUTPUT_FILE_NAME = "service.yaml";

    private static final YAMLFactory YAML_FACTORY = YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .build();
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(YAML_FACTORY);

    public static void main(String[] args) throws IOException {
        Path baseDirectory = args.length > 0 ? Path.of(args[0]) : Path.of(".");

        List<Path> appYamlFiles = findAppYamlFiles(baseDirectory);

        if (appYamlFiles.isEmpty()) {
            System.out.println("No app.yaml files found under " + baseDirectory.toAbsolutePath().normalize());
            return;
        }

        int successCount = 0;
        int failureCount = 0;

        for (Path appYaml : appYamlFiles) {
            try {
                migrateSingleFile(appYaml);
                successCount++;
                System.out.println("Migrated " + appYaml + " -> " + appYaml.resolveSibling(OUTPUT_FILE_NAME));
            } catch (Exception e) {
                failureCount++;
                System.err.println("Failed to migrate " + appYaml + ": " + e.getMessage());
            }
        }

        System.out.println("Done. Successful: " + successCount + ", failed: " + failureCount);
    }

    private static List<Path> findAppYamlFiles(Path baseDirectory) throws IOException {
        // Walk through the whole directory tree and keep only files named app.yaml.
        try (Stream<Path> paths = Files.walk(baseDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> INPUT_FILE_NAME.equals(path.getFileName().toString()))
                    .toList();
        }
    }

    private static void migrateSingleFile(Path appYaml) throws IOException {
        Path serviceYaml = appYaml.resolveSibling(OUTPUT_FILE_NAME);

        // Parse YAML into Jackson's tree model. JsonNode works for YAML too.
        JsonNode inputRoot = YAML_MAPPER.readTree(appYaml.toFile());

        // Build the new YAML tree with ObjectNode instead of text replacement.
        ObjectNode outputRoot = YAML_MAPPER.createObjectNode();
        ObjectNode service = outputRoot.putObject("service");

        service.put("name", requiredField(inputRoot, "/app/id").asText());
        service.put("team", requiredField(inputRoot, "/app/owner").asText());

        ObjectNode runtime = service.putObject("runtime");
        runtime.set("port", requiredField(inputRoot, "/app/port"));

        ObjectNode persistence = service.putObject("persistence");
        persistence.put("dbHost", requiredField(inputRoot, "/app/database/host").asText());
        persistence.put("dbName", requiredField(inputRoot, "/app/database/name").asText());

        ObjectNode limits = service.putObject("limits");
        limits.put("cpu", requiredField(inputRoot, "/app/resources/cpu").asText());
        limits.put("memory", requiredField(inputRoot, "/app/resources/memory").asText());

        // Write service.yaml first. Only delete app.yaml after the write succeeds.
        YAML_MAPPER.writeValue(serviceYaml.toFile(), outputRoot);
        Files.delete(appYaml);
    }

    private static JsonNode requiredField(JsonNode root, String jsonPointer) {
        JsonNode value = root.at(jsonPointer);

        if (value.isMissingNode() || value.isNull()) {
            throw new IllegalArgumentException("Missing required field: " + jsonPointer);
        }

        return value;
    }
}
```

## Run Commands

Compile:

```bash
mvn compile
```

Run tests:

```bash
mvn test
```

Run against the sample folder:

```bash
mvn exec:java -Dexec.mainClass=YamlMigration -Dexec.args=examples
```

Alternative command using the compiled classpath:

```bash
mvn dependency:build-classpath -Dmdep.outputFile=classpath.txt
java -cp "target/classes:$(cat classpath.txt)" YamlMigration examples
```

## Tests

The project includes JUnit 5 tests in `src/test/java/YamlMigrationTest.java`.

The tests check that:

- a valid `app.yaml` is migrated into the expected `service.yaml` text
- the original `app.yaml` is deleted after a successful write
- a bad file does not stop another valid file from being migrated
- the bad `app.yaml` remains in place when migration fails

## Example Folder Structure Before

```text
java-yaml-migration-workshop/
├── pom.xml
├── src/
│   └── main/
│       └── java/
│           └── YamlMigration.java
└── examples/
    └── services/
        └── inventory/
            └── app.yaml
```

## Example Folder Structure After

```text
java-yaml-migration-workshop/
├── pom.xml
├── src/
│   └── main/
│       └── java/
│           └── YamlMigration.java
└── examples/
    └── services/
        └── inventory/
            └── service.yaml
```

## Expected Console Output

```text
Migrated examples/services/inventory/app.yaml -> examples/services/inventory/service.yaml
Done. Successful: 1, failed: 0
```

## Expected Result

After the program runs successfully:

- `service.yaml` exists in the same folder where `app.yaml` was found.
- The new file contains the migrated `service` structure.
- The original `app.yaml` has been deleted.
- If one file is invalid or missing required fields, the program reports that file and continues with the next one.

## How The Migration Works

The program uses `ObjectMapper` with `YAMLFactory`, so Jackson parses YAML into a `JsonNode` tree. It reads required values with JSON Pointer paths such as `/app/id` and `/app/database/host`.

Then it creates a new `ObjectNode` tree for the target format:

- `/app/id` becomes `/service/name`
- `/app/owner` becomes `/service/team`
- `/app/port` becomes `/service/runtime/port`
- `/app/database/host` becomes `/service/persistence/dbHost`
- `/app/database/name` becomes `/service/persistence/dbName`
- `/app/resources/cpu` becomes `/service/limits/cpu`
- `/app/resources/memory` becomes `/service/limits/memory`

The code does not use regex or line scanning. It lets Jackson understand the YAML structure, which is safer and easier to extend.

## Optional Extension Ideas

- Add validation for numeric ports.
- Support default values for optional YAML fields.
- Write a backup file before deleting `app.yaml`.
- Add unit tests for successful and failed migrations.
- Support a dry-run mode that prints what would change without writing files.
