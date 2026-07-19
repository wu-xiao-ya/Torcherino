package com.sci.torcherino.diagnostics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiagnosticLogWriterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void writesOneJsonObjectPerLine() throws Exception {
        Path path = temporaryDirectory.resolve(
            "torcherino-performance.log"
        );
        DiagnosticLogWriter writer =
            new DiagnosticLogWriter(path, 4096L, 2);
        try {
            JsonObject event = new JsonObject();
            event.addProperty("type", "summary");
            event.addProperty("virtualTicks", 324);
            writer.write(event);
        } finally {
            writer.close();
        }

        List<String> lines = Files.readAllLines(
            path,
            StandardCharsets.UTF_8
        );
        assertEquals(1, lines.size());
        JsonObject parsed = new JsonParser()
            .parse(lines.get(0))
            .getAsJsonObject();
        assertEquals("summary", parsed.get("type").getAsString());
        assertEquals(324, parsed.get("virtualTicks").getAsInt());
    }

    @Test
    void rotatesBeforeTheConfiguredLimitIsExceeded() throws Exception {
        Path path = temporaryDirectory.resolve(
            "torcherino-performance.log"
        );
        DiagnosticLogWriter writer =
            new DiagnosticLogWriter(path, 1024L, 2);
        try {
            for (int index = 0; index < 4; index++) {
                JsonObject event = new JsonObject();
                event.addProperty("type", "payload");
                event.addProperty("index", index);
                event.addProperty("data", repeated('x', 700));
                writer.write(event);
            }
        } finally {
            writer.close();
        }

        assertTrue(Files.exists(path));
        assertTrue(
            Files.exists(
                temporaryDirectory.resolve(
                    "torcherino-performance.1.log"
                )
            )
        );
        assertTrue(
            Files.exists(
                temporaryDirectory.resolve(
                    "torcherino-performance.2.log"
                )
            )
        );
        for (String line : Files.readAllLines(
            path,
            StandardCharsets.UTF_8
        )) {
            assertTrue(new JsonParser().parse(line).isJsonObject());
        }
    }

    private static String repeated(char value, int length) {
        char[] characters = new char[length];
        Arrays.fill(characters, value);
        return new String(characters);
    }
}
