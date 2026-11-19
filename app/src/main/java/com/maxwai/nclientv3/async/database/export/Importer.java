package com.maxwai.nclientv3.async.database.export;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.JsonReader;
import android.util.JsonToken;

import androidx.annotation.NonNull;

import com.maxwai.nclientv3.async.database.export.BackupArchive.InvalidBackupException;
import com.maxwai.nclientv3.settings.Database;
import com.maxwai.nclientv3.utility.LogUtility;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class Importer {
    private static SharedPreferences.Editor readPreferences(Context context, String sharedName, InputStream stream)
        throws InvalidBackupException {
        SharedPreferences.Editor editor = context.getSharedPreferences(sharedName, Context.MODE_PRIVATE).edit();
        try (JsonReader reader = new JsonReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                reader.beginObject();
                Exporter.SharedType type = Exporter.SharedType.valueOf(reader.nextName());
                switch (type) {
                    case STRING:
                        editor.putString(name, reader.nextString());
                        break;
                    case INT:
                        editor.putInt(name, reader.nextInt());
                        break;
                    case FLOAT:
                        editor.putFloat(name, (float) reader.nextDouble());
                        break;
                    case LONG:
                        editor.putLong(name, reader.nextLong());
                        break;
                    case BOOLEAN:
                        editor.putBoolean(name, reader.nextBoolean());
                        break;
                    case STRING_SET:
                        Set<String> strings = new HashSet<>();
                        reader.beginArray();
                        while (reader.hasNext()) strings.add(reader.nextString());
                        reader.endArray();
                        editor.putStringSet(name, strings);
                        break;
                }
                reader.endObject();
            }
            reader.endObject();
            requireEndOfDocument(reader);
        } catch (IOException | IllegalStateException | IllegalArgumentException error) {
            throw new InvalidBackupException("Invalid settings JSON", error);
        }
        return editor;
    }

    private static void importDatabase(InputStream stream) throws IOException {
        SQLiteDatabase db = Database.getDatabase();
        if (db == null) throw new IOException("Database connection is not ready");
        db.beginTransaction();
        try {
            readDatabase(db, stream);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void readDatabase(SQLiteDatabase db, InputStream stream) throws IOException {
        Set<String> knownTables = new HashSet<>(Arrays.asList(Exporter.SCHEMAS));
        Set<String> restoredTables = new HashSet<>();
        try (JsonReader reader = new JsonReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String table = reader.nextName();
                if (!knownTables.contains(table)) {
                    reader.skipValue();
                    continue;
                }
                if (!restoredTables.add(table)) throw new InvalidBackupException("Repeated database table");
                readTable(db, reader, table);
            }
            reader.endObject();
            requireEndOfDocument(reader);
            if (restoredTables.isEmpty()) throw new InvalidBackupException("No known database tables");
        } catch (IllegalStateException | IllegalArgumentException error) {
            throw new InvalidBackupException("Invalid database JSON", error);
        } catch (IOException error) {
            if (error instanceof InvalidBackupException) throw error;
            throw new InvalidBackupException("Invalid database JSON", error);
        }
    }

    private static void readTable(SQLiteDatabase db, JsonReader reader, String table) throws IOException {
        Set<String> columns;
        try (Cursor cursor = db.query(table, null, null, null, null, null, null, "0")) {
            columns = new HashSet<>(Arrays.asList(cursor.getColumnNames()));
        }
        reader.beginArray();
        db.delete(table, null, null);
        while (reader.hasNext()) {
            ContentValues values = new ContentValues();
            reader.beginObject();
            while (reader.hasNext()) {
                String column = reader.nextName();
                if (!columns.contains(column)) throw new InvalidBackupException("Unknown database column");
                switch (reader.peek()) {
                    case NULL:
                        values.putNull(column);
                        reader.nextNull();
                        break;
                    case NUMBER:
                        String number = reader.nextString();
                        if (number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0)
                            values.put(column, Double.parseDouble(number));
                        else
                            values.put(column, Long.parseLong(number));
                        break;
                    case STRING:
                        values.put(column, reader.nextString());
                        break;
                    default:
                        throw new InvalidBackupException("Unsupported database field value");
                }
            }
            reader.endObject();
            if (db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE) == -1)
                throw new InvalidBackupException("Unable to restore database row");
        }
        reader.endArray();
    }

    private static void requireEndOfDocument(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.END_DOCUMENT) throw new InvalidBackupException("Unexpected trailing JSON data");
    }

    public static void importData(@NonNull Context context, Uri selectedFile) throws IOException {
        File staged = File.createTempFile("nclient-import-", ".zip", context.getCacheDir());
        try {
            try (InputStream input = context.getContentResolver().openInputStream(selectedFile)) {
                if (input == null) throw new IOException("Cannot open backup file");
                try (FileOutputStream output = new FileOutputStream(staged)) {
                    BackupArchive.copy(input, output);
                }
            }
            List<SharedPreferences.Editor> preferences = new ArrayList<>();
            try (BackupArchive archive = new BackupArchive(staged)) {
                for (String name : Exporter.SHARED_FILES) {
                    InputStream settings = archive.open(name + ".json");
                    if (settings != null) preferences.add(readPreferences(context, name, settings));
                }
                try (InputStream database = archive.open(Exporter.DB_ZIP_FILE)) {
                    if (database != null) importDatabase(database);
                }
            }
            for (SharedPreferences.Editor editor : preferences) editor.apply();
        } finally {
            if (!staged.delete()) LogUtility.d("Unable to remove temporary import file");
        }
    }
}
