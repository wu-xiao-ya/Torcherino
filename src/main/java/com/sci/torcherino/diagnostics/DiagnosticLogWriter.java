package com.sci.torcherino.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

final class DiagnosticLogWriter implements Closeable {
    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .create();

    private final Path path;
    private final long maxBytes;
    private final int backups;
    private BufferedWriter writer;
    private long bytesWritten;

    DiagnosticLogWriter(Path path, long maxBytes, int backups)
        throws IOException {
        this.path = path;
        this.maxBytes = Math.max(1024L, maxBytes);
        this.backups = Math.max(0, backups);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (Files.exists(path) && Files.size(path) >= this.maxBytes) {
            rotate();
        }
        open();
    }

    synchronized void write(JsonObject event) throws IOException {
        String line = GSON.toJson(event) + System.lineSeparator();
        byte[] encoded = line.getBytes(StandardCharsets.UTF_8);
        if (bytesWritten > 0L
            && bytesWritten + encoded.length > maxBytes) {
            rotate();
            open();
        }
        writer.write(line);
        writer.flush();
        bytesWritten += encoded.length;
    }

    synchronized void flush() throws IOException {
        if (writer != null) {
            writer.flush();
        }
    }

    Path getPath() {
        return path;
    }

    @Override
    public synchronized void close() throws IOException {
        closeWriter();
    }

    private void open() throws IOException {
        writer = Files.newBufferedWriter(
            path,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        );
        bytesWritten = Files.size(path);
    }

    private void rotate() throws IOException {
        closeWriter();
        if (backups <= 0) {
            Files.deleteIfExists(path);
            return;
        }
        Files.deleteIfExists(backupPath(backups));
        for (int index = backups - 1; index >= 1; index--) {
            Path source = backupPath(index);
            if (Files.exists(source)) {
                Files.move(
                    source,
                    backupPath(index + 1),
                    StandardCopyOption.REPLACE_EXISTING
                );
            }
        }
        if (Files.exists(path)) {
            Files.move(
                path,
                backupPath(1),
                StandardCopyOption.REPLACE_EXISTING
            );
        }
        bytesWritten = 0L;
    }

    private Path backupPath(int index) {
        String name = path.getFileName().toString();
        int extension = name.lastIndexOf('.');
        String backupName = extension <= 0
            ? name + "." + index
            : name.substring(0, extension)
                + "." + index
                + name.substring(extension);
        return path.resolveSibling(backupName);
    }

    private void closeWriter() throws IOException {
        if (writer != null) {
            writer.close();
            writer = null;
        }
    }
}
