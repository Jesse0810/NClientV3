package com.maxwai.nclientv3.utility;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonToken;

import com.maxwai.nclientv3.api.enums.TagType;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Makes colliding labels distinguishable without changing canonical tag names or queries. */
public final class TagLabelDisambiguation {
    private static final String ASSET_PATH = "tag_label_collisions_zh_CN.json";
    private static final TagType[] TYPES = {
        TagType.TAG, TagType.PARODY, TagType.CHARACTER, TagType.ARTIST, TagType.GROUP
    };
    private static volatile Index index;

    private TagLabelDisambiguation() {
    }

    public static String getDisplayName(Context context, TagType type, String rawName, String displayName) {
        if (!isSupportedType(type) || !ContentTagTranslations.isEnabled(context)) return displayName;
        Index current = getIndex(context);
        // If the index is unavailable, disambiguate every translated label instead of silently
        // hiding its identity. Untranslated names already identify their original query value.
        boolean needsOriginal = current.failed ? !rawName.equals(displayName)
            : current.names.get(type).contains(rawName);
        return needsOriginal ? displayName + "（" + rawName + "）" : displayName;
    }

    private static boolean isSupportedType(TagType type) {
        for (TagType supported : TYPES) if (supported.equals(type)) return true;
        return false;
    }

    private static Index getIndex(Context context) {
        Index result = index;
        if (result == null) {
            synchronized (TagLabelDisambiguation.class) {
                result = index;
                if (result == null) {
                    result = readIndex(context);
                    index = result;
                }
            }
        }
        return result;
    }

    private static Index readIndex(Context context) {
        try (JsonReader reader = new JsonReader(new InputStreamReader(
            context.getAssets().open(ASSET_PATH), StandardCharsets.UTF_8))) {
            Map<TagType, Set<String>> loaded = new HashMap<>();
            reader.beginObject();
            while (reader.hasNext()) {
                TagType type = TagType.typeByName(reader.nextName());
                if (!isSupportedType(type) || loaded.containsKey(type) || reader.peek() != JsonToken.BEGIN_ARRAY)
                    throw new IOException("Invalid or duplicate label collision namespace");
                Set<String> names = new HashSet<>();
                reader.beginArray();
                while (reader.hasNext()) {
                    if (reader.peek() != JsonToken.STRING)
                        throw new IOException("Label collision name must be a string");
                    String name = reader.nextString();
                    if (name.trim().isEmpty() || !names.add(name))
                        throw new IOException("Empty or duplicate label collision name");
                }
                reader.endArray();
                loaded.put(type, Collections.unmodifiableSet(names));
            }
            reader.endObject();
            if (loaded.size() != TYPES.length || reader.peek() != JsonToken.END_DOCUMENT)
                throw new IOException("Incomplete or trailing label collision index");
            return new Index(Collections.unmodifiableMap(loaded), false);
        } catch (IOException | RuntimeException error) {
            // Cache failures too: log once and keep translated options identifiable.
            LogUtility.w("Unable to load label collision index", error);
            return new Index(Collections.emptyMap(), true);
        }
    }

    private static final class Index {
        private final Map<TagType, Set<String>> names;
        private final boolean failed;

        private Index(Map<TagType, Set<String>> names, boolean failed) {
            this.names = names;
            this.failed = failed;
        }
    }
}
