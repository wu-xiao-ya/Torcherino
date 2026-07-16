package com.sci.torcherino.compat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class StructuralSignature {
    private StructuralSignature() {
    }

    static String firstAvailable(String[] classNames) {
        ClassLoader loader = StructuralSignature.class.getClassLoader();
        for (String className : classNames) {
            String resource = className.replace('.', '/') + ".class";
            InputStream input = loader.getResourceAsStream(resource);
            if (input == null) {
                continue;
            }
            try {
                return className + "#" + digest(input);
            } catch (IOException e) {
                return className + "#io-error";
            } finally {
                try {
                    input.close();
                } catch (IOException ignored) {
                }
            }
        }
        return "no-probe-class";
    }

    private static String digest(InputStream input) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder value = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                value.append(String.format("%02x", hash[i] & 0xFF));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
