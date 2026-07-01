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
        <mustache.version>0.9.14</mustache.version>
        <junit.version>5.10.3</junit.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson.dataformat</groupId>
            <artifactId>jackson-dataformat-yaml</artifactId>
            <version>${jackson.version}</version>
        </dependency>
        <dependency>
            <groupId>com.github.spullara.mustache.java</groupId>
            <artifactId>compiler</artifactId>
            <version>${mustache.version}</version>
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

## YAML Template

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

## Full Java Code

`src/main/java/YamlMigration.java`

```java
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class YamlMigration {
    private static final String INPUT_FILE_NAME = "app.yaml";
    private static final String OUTPUT_FILE_NAME = "service.yaml";
    private static final String TEMPLATE_FILE_NAME = "service.yaml.mustache";

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    private static final MustacheFactory MUSTACHE_FACTORY = new DefaultMustacheFactory();

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

        // Parse the source YAML into Jackson's tree model.
        JsonNode inputRoot = YAML_MAPPER.readTree(appYaml.toFile());

        // Read required values with JSON Pointer paths, then render the target template.
        String renderedYaml = renderServiceYaml(templateValues(inputRoot));

        // Parse the rendered YAML once before writing, so template mistakes fail early.
        validateYaml(renderedYaml);

        // Write service.yaml first. Only delete app.yaml after the write succeeds.
        Files.writeString(serviceYaml, renderedYaml, StandardCharsets.UTF_8);
        Files.delete(appYaml);
    }

    private static Map<String, Object> templateValues(JsonNode inputRoot) {
        return Map.of(
                "name", requiredField(inputRoot, "/app/id").asText(),
                "team", requiredField(inputRoot, "/app/owner").asText(),
                "port", requiredField(inputRoot, "/app/port").asText(),
                "dbHost", requiredField(inputRoot, "/app/database/host").asText(),
                "dbName", requiredField(inputRoot, "/app/database/name").asText(),
                "cpu", requiredField(inputRoot, "/app/resources/cpu").asText(),
                "memory", requiredField(inputRoot, "/app/resources/memory").asText()
        );
    }

    private static String renderServiceYaml(Map<String, Object> values) throws IOException {
        try (Reader templateReader = templateReader()) {
            Mustache mustache = MUSTACHE_FACTORY.compile(templateReader, TEMPLATE_FILE_NAME);
            StringWriter writer = new StringWriter();
            mustache.execute(writer, values).flush();
            return writer.toString();
        }
    }

    private static Reader templateReader() throws IOException {
        InputStream templateStream = YamlMigration.class
                .getClassLoader()
                .getResourceAsStream(TEMPLATE_FILE_NAME);

        if (templateStream == null) {
            throw new FileNotFoundException("Missing template resource: " + TEMPLATE_FILE_NAME);
        }

        return new InputStreamReader(templateStream, StandardCharsets.UTF_8);
    }

    private static void validateYaml(String yaml) throws IOException {
        YAML_MAPPER.readTree(yaml);
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
│       ├── java/
│       │   └── YamlMigration.java
│       └── resources/
│           └── service.yaml.mustache
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
│       ├── java/
│       │   └── YamlMigration.java
│       └── resources/
│           └── service.yaml.mustache
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

Then it puts those values into a Mustache template for the target format:

- `/app/id` becomes `/service/name`
- `/app/owner` becomes `/service/team`
- `/app/port` becomes `/service/runtime/port`
- `/app/database/host` becomes `/service/persistence/dbHost`
- `/app/database/name` becomes `/service/persistence/dbName`
- `/app/resources/cpu` becomes `/service/limits/cpu`
- `/app/resources/memory` becomes `/service/limits/memory`

Before writing the rendered text to `service.yaml`, the program parses the rendered YAML with Jackson. This validates that the template output is still valid YAML.

The code does not use regex or line scanning. It lets Jackson understand the YAML structure, which is safer and easier to extend.

## Optional Extension Ideas

- Add validation for numeric ports.
- Support default values for optional YAML fields.
- Write a backup file before deleting `app.yaml`.
- Add unit tests for successful and failed migrations.
- Support a dry-run mode that prints what would change without writing files.
