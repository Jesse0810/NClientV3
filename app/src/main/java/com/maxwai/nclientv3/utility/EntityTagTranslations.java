package com.maxwai.nclientv3.utility;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonToken;

import com.maxwai.nclientv3.api.enums.TagType;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Display names only: every mapping retains its original namespace and API name. */
public final class EntityTagTranslations {
    private static final String ASSET_PATH = "entity_translations_zh_CN.json";
    private static final TagType[] TYPES = {
        TagType.PARODY, TagType.CHARACTER, TagType.ARTIST, TagType.GROUP
    };
    private static volatile Map<TagType, Map<String, String>> translations;

    private EntityTagTranslations() {
    }

    public static boolean isEntityType(TagType type) {
        for (TagType entityType : TYPES) if (entityType.equals(type)) return true;
        return false;
    }

    public static String getDisplayName(Context context, TagType type, String name) {
        if (!isEntityType(type) || !ContentTagTranslations.isEnabled(context)) return name;
        Map<String, String> labels = getTranslations(context).get(type);
        if (labels == null) return name;
        String translated = labels.get(name);
        return translated == null ? name : translated;
    }

    /** Return matching canonical names grouped by type, including every alias with the same label. */
    public static Map<TagType, List<String>> getMatchingNames(Context context, String query, TagType type) {
        if (query == null || query.trim().isEmpty() || (type != null && !isEntityType(type))
            || !ContentTagTranslations.isEnabled(context)) return Collections.emptyMap();
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        Map<TagType, List<String>> matches = new HashMap<>();
        for (Map.Entry<TagType, Map<String, String>> namespace : getTranslations(context).entrySet()) {
            if (type != null && !type.equals(namespace.getKey())) continue;
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, String> entry : namespace.getValue().entrySet())
                if (entry.getValue().toLowerCase(Locale.ROOT).contains(normalized)) names.add(entry.getKey());
            if (!names.isEmpty()) matches.put(namespace.getKey(), Collections.unmodifiableList(names));
        }
        return Collections.unmodifiableMap(matches);
    }

    private static Map<TagType, Map<String, String>> getTranslations(Context context) {
        Map<TagType, Map<String, String>> result = translations;
        if (result == null) {
            synchronized (EntityTagTranslations.class) {
                result = translations;
                if (result == null) {
                    result = readTranslations(context);
                    translations = result;
                }
            }
        }
        return result;
    }

    private static Map<TagType, Map<String, String>> readTranslations(Context context) {
        try (JsonReader reader = new JsonReader(new InputStreamReader(
            context.getAssets().open(ASSET_PATH), StandardCharsets.UTF_8))) {
            Map<TagType, Map<String, String>> loaded = new HashMap<>();
            reader.beginObject();
            while (reader.hasNext()) {
                TagType type = TagType.typeByName(reader.nextName());
                if (!isEntityType(type) || loaded.containsKey(type) || reader.peek() != JsonToken.BEGIN_OBJECT)
                    throw new IOException("Invalid or duplicate entity translation namespace");
                Map<String, String> labels = new HashMap<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (reader.peek() != JsonToken.STRING)
                        throw new IOException("Entity translation must be a string");
                    String label = reader.nextString();
                    if (name.trim().isEmpty() || label.trim().isEmpty() || labels.containsKey(name))
                        throw new IOException("Empty or duplicate entity translation");
                    labels.put(name, label);
                }
                reader.endObject();
                loaded.put(type, Collections.unmodifiableMap(labels));
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT)
                throw new IOException("Unexpected data after entity translations");
            return Collections.unmodifiableMap(loaded);
        } catch (IOException | RuntimeException error) {
            // Cache failure too, so a missing or malformed bundled file logs only once.
            LogUtility.w("Unable to load entity tag translations", error);
            return Collections.emptyMap();
        }
    }
}
