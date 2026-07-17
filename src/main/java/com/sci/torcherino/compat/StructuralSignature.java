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
        for (String className : classNames) {
            String digest = digestForClass(className);
            if (digest != null) {
                return className + "#" + digest.substring(0, 12);
            }
        }
        return "no-probe-class";
    }

    static String digestForClass(String className) {
        ClassLoader loader = StructuralSignature.class.getClassLoader();
        String resource = className.replace('.', '/') + ".class";
        InputStream input = loader.getResourceAsStream(resource);
        if (input == null) {
            return null;
        }
        try {
            return digest(input);
        } catch (IOException e) {
            return "io-error";
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {
            }
        }
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
            for (int i = 0; i < hash.length; i++) {
                value.append(String.format("%02x", hash[i] & 0xFF));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
