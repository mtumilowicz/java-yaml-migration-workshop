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

        JsonNode inputRoot = YAML_MAPPER.readTree(appYaml.toFile());

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
