package com.sci.torcherino;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;

final class DedicatedServerLinkageTest {
    @Test
    void commonEventHandlerDoesNotReferenceClientClasses() throws IOException {
        assertNoClientReference("com/sci/torcherino/network/EventHandler.class");
    }

    @Test
    void commonBlockRegistryDoesNotReferenceClientClasses() throws IOException {
        assertNoClientReference("com/sci/torcherino/blocks/ModBlocks.class");
    }

    private static void assertNoClientReference(String resource) throws IOException {
        byte[] classBytes;
        try (InputStream input = DedicatedServerLinkageTest.class
            .getClassLoader()
            .getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing compiled class resource " + resource);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            classBytes = output.toByteArray();
        }
        String constantPool = new String(classBytes, StandardCharsets.ISO_8859_1);
        assertFalse(
            constantPool.contains("net/minecraft/client"),
            resource + " contains a dedicated-server unsafe Minecraft client reference"
        );
        assertFalse(
            constantPool.contains("net/minecraftforge/client"),
            resource + " contains a dedicated-server unsafe Forge client reference"
        );
    }
}
