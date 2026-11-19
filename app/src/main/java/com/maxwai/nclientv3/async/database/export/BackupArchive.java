package com.maxwai.nclientv3.async.database.export;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/** Opens a complete backup and checks recognized entries before any app data changes. */
final class BackupArchive implements Closeable {
    private final ZipFile zip;
    private final Map<String, ZipEntry> entries = new HashMap<>();

    BackupArchive(File file) throws IOException {
        try {
            // ZipInputStream alone can read local entries even when the ZIP directory is missing.
            zip = new ZipFile(file);
        } catch (ZipException error) {
            throw new InvalidBackupException("Missing or invalid ZIP directory", error);
        }
        try {
            Enumeration<? extends ZipEntry> candidates = zip.entries();
            while (candidates.hasMoreElements()) {
                ZipEntry entry = candidates.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                String basename = name.substring(name.lastIndexOf('/') + 1);
                if (!isBackupEntry(basename)) continue;
                if (entries.putIfAbsent(basename, entry) != null)
                    throw new InvalidBackupException("Repeated backup entry");
                checkEntry(entry);
            }
            if (entries.isEmpty()) throw new InvalidBackupException("No app data in backup");
        } catch (IOException | RuntimeException error) {
            try {
                zip.close();
            } catch (IOException closeError) {
                error.addSuppressed(closeError);
            }
            throw error;
        }
    }

    private static boolean isBackupEntry(String name) {
        if (Exporter.DB_ZIP_FILE.equals(name)) return true;
        for (String shared : Exporter.SHARED_FILES)
            if ((shared + ".json").equals(name)) return true;
        return false;
    }

    private void checkEntry(ZipEntry entry) throws IOException {
        CRC32 crc = new CRC32();
        long size = 0;
        byte[] buffer = new byte[32 * 1024];
        try (InputStream input = zip.getInputStream(entry)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                crc.update(buffer, 0, read);
                size += read;
                if (size > entry.getSize()) throw new InvalidBackupException("Invalid backup entry length");
            }
        } catch (IOException error) {
            throw new InvalidBackupException("Unreadable backup entry", error);
        }
        if (size != entry.getSize() || crc.getValue() != entry.getCrc())
            throw new InvalidBackupException("Backup entry checksum or length mismatch");
    }

    InputStream open(String canonicalName) throws IOException {
        ZipEntry entry = entries.get(canonicalName);
        return entry == null ? null : zip.getInputStream(entry);
    }

    static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[32 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
    }

    @Override public void close() throws IOException {
        zip.close();
    }

    static final class InvalidBackupException extends IOException {
        InvalidBackupException(String message) { super(message); }
        InvalidBackupException(String message, Throwable cause) { super(message, cause); }
    }
}
