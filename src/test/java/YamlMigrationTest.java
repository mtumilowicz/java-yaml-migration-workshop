import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlMigrationTest {
    @TempDir
    Path tempDir;

    @Test
    void migratesAppYamlToServiceYamlAndDeletesOriginal() throws Exception {
        Path serviceDirectory = tempDir.resolve("services/inventory");
        Files.createDirectories(serviceDirectory);

        Path appYaml = serviceDirectory.resolve("app.yaml");
        Files.writeString(appYaml, validAppYaml());

        YamlMigration.main(new String[]{tempDir.toString()});

        Path serviceYaml = serviceDirectory.resolve("service.yaml");
        assertTrue(Files.exists(serviceYaml));
        assertFalse(Files.exists(appYaml));

        assertEquals(expectedServiceYaml(), normalizeLineEndings(Files.readString(serviceYaml)));
    }

    @Test
    void keepsMigratingWhenOneFileIsBad() throws Exception {
        Path goodDirectory = tempDir.resolve("services/inventory");
        Path badDirectory = tempDir.resolve("services/billing");
        Files.createDirectories(goodDirectory);
        Files.createDirectories(badDirectory);

        Path goodAppYaml = goodDirectory.resolve("app.yaml");
        Path badAppYaml = badDirectory.resolve("app.yaml");
        Files.writeString(goodAppYaml, validAppYaml());
        Files.writeString(badAppYaml, """
                app:
                  id: billing-service
                  port: 9000
                """);

        YamlMigration.main(new String[]{tempDir.toString()});

        assertTrue(Files.exists(goodDirectory.resolve("service.yaml")));
        assertFalse(Files.exists(goodAppYaml));

        assertTrue(Files.exists(badAppYaml));
        assertFalse(Files.exists(badDirectory.resolve("service.yaml")));
    }

    private static String validAppYaml() {
        return """
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
                """;
    }

    private static String expectedServiceYaml() {
        return """
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
                """;
    }

    private static String normalizeLineEndings(String text) {
        return text.replace("\r\n", "\n");
    }
}
