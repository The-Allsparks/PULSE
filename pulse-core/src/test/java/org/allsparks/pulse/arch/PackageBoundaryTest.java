package org.allsparks.pulse.arch;

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

class PackageBoundaryTest {

    private static final List<String> FORBIDDEN_CORE = Arrays.asList(
            "com.qualcomm",
            "org.firstinspires.ftc",
            "android.",
            "androidx.",
            "org.allsparks.pulse.ftc",
            "org.allsparks.amper",
            "org.allsparks.trace",
            "org.allsparks.mimic",
            "org.allsparks.helm",
            "org.allsparks.vidar");

    @Test
    void coreDoesNotImportFtcOrSiblingRuntimes() throws IOException {
        List<String> hits = new ArrayList<>();
        Path main = coreMain();
        for (Path path : javaFiles(main)) {
            String[] lines = read(path).split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (!line.startsWith("import ")) {
                    continue;
                }
                String imported =
                        line.substring("import ".length()).replace(";", "").trim();
                if (imported.startsWith("static ")) {
                    imported = imported.substring("static ".length()).trim();
                }
                for (String prefix : FORBIDDEN_CORE) {
                    if (imported.equals(prefix) || imported.startsWith(prefix + ".")) {
                        hits.add(rel(main, path) + ":" + (i + 1) + " " + imported);
                    }
                }
            }
        }
        if (!hits.isEmpty()) {
            fail("pulse-core imported FTC, Android, or a sibling runtime:\n" + String.join("\n", hits));
        }
    }

    @Test
    void capturePathDoesNotStartThreadsOrWriteFiles() throws IOException {
        List<String> needles = Arrays.asList(
                "new Thread",
                "ExecutorService",
                "Executors.",
                "FileWriter",
                "FileOutputStream",
                "Socket",
                "HttpURLConnection",
                "ObjectMapper",
                "JSONObject");
        List<String> hits = new ArrayList<>();
        Path main = coreMain();
        for (Path path : javaFiles(main)) {
            String[] lines = read(path).split("\n");
            for (int i = 0; i < lines.length; i++) {
                String trimmed = lines[i].trim();
                if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
                    continue;
                }
                for (String needle : needles) {
                    if (trimmed.contains(needle)) {
                        hits.add(rel(main, path) + ":" + (i + 1) + " " + needle);
                    }
                }
            }
        }
        if (!hits.isEmpty()) {
            fail("production capture path used threads, files, or network:\n" + String.join("\n", hits));
        }
    }

    @Test
    void cachedIntReaderDoesNotAllocateOnGet() throws IOException {
        Path path = coreMain().resolve("org/allsparks/pulse/internal/PulseRuntime.java");
        String source = read(path);
        int start = source.indexOf("final class CachedIntReader");
        int end = source.indexOf("final class CachedLongReader");
        if (start < 0 || end < 0) {
            fail("CachedIntReader not found");
        }
        String body = source.substring(start, end);
        if (body.contains("new ") && body.contains("getAsInt")) {
            String method = body.substring(body.indexOf("getAsInt"));
            if (method.contains("new ") || method.contains("Integer.valueOf") || method.contains("valueOf(")) {
                fail("CachedIntReader.getAsInt appears to allocate:\n" + method);
            }
        }
        if (body.contains("Integer.valueOf")) {
            fail("CachedIntReader boxes integers");
        }
    }

    static Path coreMain() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path cur = cwd;
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve("pulse-core/src/main/java");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            cur = cur.getParent();
        }
        return cwd.resolve("src/main/java");
    }

    static List<Path> javaFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return files;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.forEach(path -> {
                if (path.toString().endsWith(".java") && Files.isRegularFile(path)) {
                    files.add(path);
                }
            });
        }
        return files;
    }

    static String read(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    static String rel(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }
}
