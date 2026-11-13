package com.maxwai.nclientv3.utility;

import android.content.Context;
import android.util.JsonReader;
import android.util.JsonToken;

import com.maxwai.nclientv3.R;
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
import java.util.Set;

/** Localized UI labels for content tags; keys always remain the original API names. */
public final class ContentTagTranslations {
    private static final String ASSET_PATH = "tag_translations_zh_CN.json";
    private static volatile Map<String, String> translations;

    private ContentTagTranslations() {
    }

    public static String getDisplayName(Context context, String name) {
        if (!isEnabled(context) || !ContentTagAllowlist.isAllowed(context, name)) return name;
        String translated = getTranslations(context).get(name);
        return translated == null ? name : translated;
    }

    /** Returns all canonical names whose translated labels contain the query. */
    public static List<String> getMatchingNames(Context context, String query) {
        if (!isEnabled(context) || query == null || query.isEmpty())
            return Collections.emptyList();
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        Set<String> allowed = ContentTagAllowlist.getAllowedNames(context);
        List<String> matches = new ArrayList<>();
        for (Map.Entry<String, String> entry : getTranslations(context).entrySet()) {
            if (allowed.contains(entry.getKey())
                && entry.getValue().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                matches.add(entry.getKey());
        }
        return matches;
    }

    /** Matches the text actually shown in Simplified Chinese content-tag management. */
    public static List<String> getMatchingDisplayNames(Context context, String query) {
        if (!isEnabled(context) || query == null || query.trim().isEmpty())
            return Collections.emptyList();
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
        Map<String, String> labels = getTranslations(context);
        List<String> matches = new ArrayList<>();
        for (String name : ContentTagAllowlist.getAllowedNames(context)) {
            String displayName = labels.get(name);
            if (displayName == null) displayName = name;
            displayName = TagLabelDisambiguation.getDisplayName(context, TagType.TAG, name, displayName);
            if (displayName.toLowerCase(Locale.ROOT).contains(normalizedQuery))
                matches.add(name);
        }
        return matches;
    }

    public static boolean isEnabled(Context context) {
        return context.getResources().getBoolean(R.bool.content_tag_translation_enabled);
    }

    private static Map<String, String> getTranslations(Context context) {
        Map<String, String> result = translations;
        if (result == null) {
            synchronized (ContentTagTranslations.class) {
                result = translations;
                if (result == null) {
                    result = readTranslations(context);
                    translations = result;
                }
            }
        }
        return result;
    }

    private static Map<String, String> readTranslations(Context context) {
        try (JsonReader reader = new JsonReader(new InputStreamReader(
            context.getAssets().open(ASSET_PATH), StandardCharsets.UTF_8))) {
            Map<String, String> loaded = new HashMap<>();
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (reader.peek() != JsonToken.STRING)
                    throw new IOException("Content tag translation must be a string");
                String translated = reader.nextString();
                if (!name.isEmpty() && !translated.isEmpty())
                    loaded.put(name, translated);
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT)
                throw new IOException("Unexpected data after content tag translations");
            return Collections.unmodifiableMap(loaded);
        } catch (IOException | RuntimeException e) {
            // Cache the empty result as well: a broken/missing bundled asset logs only once.
            LogUtility.w("Unable to load content tag translations", e);
            return Collections.emptyMap();
        }
    }
}
