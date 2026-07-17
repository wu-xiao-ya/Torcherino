package com.sci.torcherino;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TorcherinoVersionTest {
    @Test
    void forgeVersionMatchesGradleProjectVersion() {
        assertEquals(
            System.getProperty("torcherino.expectedVersion"),
            Torcherino.VERSION
        );
    }
}
