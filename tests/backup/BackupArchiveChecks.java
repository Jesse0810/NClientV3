package com.maxwai.nclientv3.async.database.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class BackupArchiveChecks {
    private static final String DATABASE = "Database.json";
    private static final String SETTINGS = "Settings.json";
    private static final String SCRAPED_TAGS = "ScrapedTags.json";
    private static final String BOOKMARKS = "{\"Bookmark\":[{\"name\":\"测试书签\"}]}";
    private static final String PREFERENCES = "{\"enable_beta\":{\"BOOLEAN\":true}}";
    private static final String TAGS = "{\"date\":{\"LONG\":1760000000000}}";
    private static int assertions;

    private static void check(boolean passed, String context) {
        if (!passed) throw new AssertionError(context);
        assertions++;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] zip(boolean stored, String... namesAndContents) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream output = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                ZipEntry entry = new ZipEntry(namesAndContents[i]);
                byte[] payload = bytes(namesAndContents[i + 1]);
                if (stored) {
                    CRC32 crc = new CRC32();
                    crc.update(payload);
                    entry.setMethod(ZipEntry.STORED);
                    entry.setSize(payload.length);
                    entry.setCrc(crc.getValue());
                }
                output.putNextEntry(entry);
                output.write(payload);
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static Path write(Path directory, String name, byte[] data) throws IOException {
        return Files.write(directory.resolve(name + ".zip"), data);
    }

    private static String read(BackupArchive archive, String name) throws IOException {
        try (InputStream input = archive.open(name)) {
            return input == null ? null : new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void invalid(Path directory, String name, byte[] data) throws IOException {
        Path file = write(directory, name, data);
        try (BackupArchive ignored = new BackupArchive(file.toFile())) {
            throw new AssertionError("Accepted invalid backup: " + name);
        } catch (BackupArchive.InvalidBackupException expected) {
            assertions++;
        }
    }

    private static int indexOf(byte[] data, byte[] needle) {
        search:
        for (int offset = 0; offset <= data.length - needle.length; offset++) {
            for (int i = 0; i < needle.length; i++) {
                if (data[offset + i] != needle[i]) continue search;
            }
            return offset;
        }
        throw new AssertionError("Fixture marker not found");
    }

    public static void main(String[] args) throws IOException {
        Path directory = Path.of(args[0]);
        byte[] current = zip(false, DATABASE, BOOKMARKS, SETTINGS, PREFERENCES, SCRAPED_TAGS, TAGS);
        try (BackupArchive archive = new BackupArchive(write(directory, "current", current).toFile())) {
            check(BOOKMARKS.equals(read(archive, DATABASE)), "Current database payload including Unicode");
            check(PREFERENCES.equals(read(archive, SETTINGS)), "Current settings payload");
            check(TAGS.equals(read(archive, SCRAPED_TAGS)), "Current tag preference payload");
            check(BOOKMARKS.equals(read(archive, DATABASE)), "Validation and earlier reads do not consume entry");
            check(read(archive, "Other.json") == null, "An unrecognized name cannot be opened");
        }

        byte[] legacy = zip(true, DATABASE, BOOKMARKS);
        try (BackupArchive archive = new BackupArchive(write(directory, "legacy", legacy).toFile())) {
            check(BOOKMARKS.equals(read(archive, DATABASE)), "Legacy database-only backup accepted");
            check(read(archive, SETTINGS) == null && read(archive, SCRAPED_TAGS) == null,
                "Absent optional entries stay absent");
        }

        byte[] wrapped = zip(false,
            "__MACOSX/", "", "__MACOSX/._Database.json", "Ignored resource fork",
            "backup/", "", "backup/2026/Database.json", BOOKMARKS,
            "backup/2026/Settings.json/", "", "backup/2026/Settings.json", PREFERENCES,
            "backup/2026/note.txt", "Ignored auxiliary file");
        try (BackupArchive archive = new BackupArchive(write(directory, "wrapped", wrapped).toFile())) {
            check(BOOKMARKS.equals(read(archive, DATABASE)), "Recognize wrapped path by basename");
            check(PREFERENCES.equals(read(archive, SETTINGS)), "A directory entry is ignored even with a known basename");
            check(read(archive, "note.txt") == null, "Auxiliary file ignored");
        }

        invalid(directory, "not-zip", bytes("This is not a ZIP file"));
        invalid(directory, "empty-zip", zip(true));
        invalid(directory, "no-backup-data", zip(true, "other.json", "{}", "Settings.json/", ""));
        invalid(directory, "duplicate-database", zip(true, DATABASE, BOOKMARKS, "nested/Database.json", "{}"));
        invalid(directory, "duplicate-preferences", zip(true,
            DATABASE, BOOKMARKS, "first/Settings.json", PREFERENCES, "second/Settings.json", "{}"));
        invalid(directory, "missing-end-of-central-directory", Arrays.copyOf(current, current.length - 22));
        invalid(directory, "truncated-archive", Arrays.copyOf(current, current.length / 2));
        invalid(directory, "truncated-directory-end", Arrays.copyOf(current, current.length - 5));

        StringBuilder manyTags = new StringBuilder("{\"Tags\":[");
        for (int id = 1; id <= 1000; id++) {
            if (id > 1) manyTags.append(',');
            manyTags.append("{\"idTag\":").append(id).append(",\"name\":\"fixture-").append(id).append("\"}");
        }
        byte[] compressedTable = zip(false, DATABASE, manyTags.append("]}").toString());
        int dataStart = 30 + bytes(DATABASE).length; // Local header with no extra fields.
        int descriptor = indexOf(compressedTable, new byte[]{0x50, 0x4b, 0x07, 0x08});
        check(descriptor > dataStart + 100, "Fixture has a local header and a substantial compressed payload");
        invalid(directory, "truncated-inside-deflate", Arrays.copyOf(compressedTable, (dataStart + descriptor) / 2));
        invalid(directory, "deflate-without-descriptor", Arrays.copyOf(compressedTable, descriptor));

        byte[] corruptData = legacy.clone();
        corruptData[indexOf(corruptData, bytes(BOOKMARKS))] ^= 1;
        invalid(directory, "bad-database-crc", corruptData);

        byte[] corruptSettings = zip(true, DATABASE, BOOKMARKS, SETTINGS, PREFERENCES);
        corruptSettings[indexOf(corruptSettings, bytes(PREFERENCES))] ^= 1;
        invalid(directory, "bad-settings-crc", corruptSettings);

        byte[] wrongSize = legacy.clone();
        int directoryHeader = indexOf(wrongSize, new byte[]{0x50, 0x4b, 0x01, 0x02});
        // Declared uncompressed size in the central directory, leaving local data intact.
        wrongSize[directoryHeader + 24]++;
        invalid(directory, "wrong-uncompressed-size", wrongSize);

        System.out.println("PASS: " + assertions + " backup archive format and integrity checks (production Java)");
    }
}
