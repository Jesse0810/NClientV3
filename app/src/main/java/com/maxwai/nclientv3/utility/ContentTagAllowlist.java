package com.maxwai.nclientv3.utility;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Fixed content-tag names approved for management and localized display labels. */
public final class ContentTagAllowlist {
    private static final String ASSET_PATH = "content_tag_allowlist.json";
    private static volatile Set<String> allowedNames;

    private ContentTagAllowlist() {
    }

    public static Set<String> getAllowedNames(Context context) {
        Set<String> result = allowedNames;
        if (result == null) {
            synchronized (ContentTagAllowlist.class) {
                result = allowedNames;
                if (result == null) {
                    result = readAllowedNames(context);
                    allowedNames = result;
                }
            }
        }
        return result;
    }

    public static boolean isAllowed(Context context, String name) {
        return getAllowedNames(context).contains(name);
    }

    private static Set<String> readAllowedNames(Context context) {
        try (JsonReader reader = new JsonReader(new InputStreamReader(
            context.getAssets().open(ASSET_PATH), StandardCharsets.UTF_8))) {
            Set<String> loaded = new HashSet<>();
            reader.beginArray();
            while (reader.hasNext()) {
                if (reader.peek() != JsonToken.STRING)
                    throw new IOException("Approved content-tag name must be a string");
                String name = reader.nextString();
                if (name.trim().isEmpty() || !loaded.add(name))
                    throw new IOException("Approved content-tag names must be nonempty and unique");
            }
            reader.endArray();
            if (reader.peek() != JsonToken.END_DOCUMENT)
                throw new IOException("Unexpected data after approved content-tag names");
            return Collections.unmodifiableSet(loaded);
        } catch (IOException | RuntimeException e) {
            // Fail closed for management/translation; detail pages still show every raw tag.
            LogUtility.w("Unable to load approved content-tag names", e);
            return Collections.emptySet();
        }
    }
}
