package org.allsparks.pulse.ftc;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class FtcArchitectureTest {

    private static final List<String> FORBIDDEN =
            Arrays.asList("BumbleBee", "Sparkee", "front_left_drive", "yeeter", ".setPower(", ".setVelocity(");

    @Test
    void ftcAdapterHasNoTeamCodeOrActuatorWrites() throws IOException {
        Path main = ftcMain();
        List<String> hits = new ArrayList<>();
        try (java.util.stream.Stream<Path> walk = Files.walk(main)) {
            walk.forEach(path -> {
                if (!path.toString().endsWith(".java") || !Files.isRegularFile(path)) {
                    return;
                }
                String text = read(path);
                String[] lines = text.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String trimmed = lines[i].trim();
                    if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                        continue;
                    }
                    for (int t = 0; t < FORBIDDEN.size(); t++) {
                        if (text.contains(FORBIDDEN.get(t)) && lines[i].contains(FORBIDDEN.get(t))) {
                            hits.add(main.relativize(path).toString().replace('\\', '/')
                                    + ":"
                                    + (i + 1)
                                    + " "
                                    + FORBIDDEN.get(t));
                        }
                    }
                }
            });
        }
        if (!hits.isEmpty()) {
            fail("pulse-ftc must not contain TeamCode names or actuator writes:\n" + String.join("\n", hits));
        }
    }

    private static Path ftcMain() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path cur = cwd;
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve("pulse-ftc/src/main/java");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            cur = cur.getParent();
        }
        return cwd.resolve("src/main/java");
    }

    private static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }
}
