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

        String renderedYaml = renderServiceYaml(templateValues(inputRoot));

        validateYaml(renderedYaml);

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
