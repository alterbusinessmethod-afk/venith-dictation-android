package com.voiceflowkeyboard.ime;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Bounded streams and whole-file integrity checks before native model loading. */
final class ModelFiles {
    private ModelFiles() {}

    static File contained(File root, String name) throws IOException {
        if (name == null || name.isEmpty() || name.startsWith("/") || name.contains("\\")) {
            throw new IOException("Unsafe model entry");
        }
        File file = new File(root, name);
        if (!file.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator)) {
            throw new IOException("Unsafe model entry");
        }
        return file;
    }

    static long copy(InputStream in, File file, long limit) throws IOException {
        long total = 0;
        byte[] buffer = new byte[65536];
        try (FileOutputStream out = new FileOutputStream(file)) {
            for (int n; (n = in.read(buffer)) != -1;) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("Model setup canceled");
                total += n;
                if (total > limit) throw new IOException("Model size limit exceeded");
                out.write(buffer, 0, n);
            }
            out.getFD().sync();
        }
        return total;
    }

    static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            try (InputStream in = new FileInputStream(file)) {
                for (int n; (n = in.read(buffer)) != -1;) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Model check canceled");
                    digest.update(buffer, 0, n);
                }
            }
            StringBuilder result = new StringBuilder();
            for (byte b : digest.digest()) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }

    static boolean verified(File file, long size, String hash) throws IOException {
        return file.isFile() && file.length() == size && hash.equals(sha256(file));
    }
}
