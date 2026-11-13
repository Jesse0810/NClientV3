package com.maxwai.nclientv3.utility;

import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.TagStatus;

import java.util.HashSet;
import java.util.Set;

/** Execution-only alias exclusions. Positive old-spelling searches retain their exact value. */
public final class ContentTagAliasQueries {
    private ContentTagAliasQueries() {
    }

    /**
     * A merged excluded content tag excludes every reviewed spelling. Never emits OR,
     * rewrites positive tags, or changes opaque/Boolean expressions saved by older versions.
     * The logical bookmark query remains unexpanded and can still be edited as tag chips.
     */
    public static String expandExclusions(String query) {
        if (query == null || query.isEmpty() || !BookmarkQuery.canEditAsTags(query)) return query;
        StringBuilder expanded = new StringBuilder(query.length());
        Set<String> emittedGroups = new HashSet<>();
        int copiedUntil = 0;
        for (BookmarkQuery.Token token : BookmarkQuery.tokenize(query)) {
            if (!"tag".equals(token.namespace) || !token.excluded
                || ContentTagAliases.members(token.value).size() < 2) continue;
            expanded.append(query, copiedUntil, token.start);
            String canonical = ContentTagAliases.canonicalName(token.value);
            if (emittedGroups.add(canonical)) {
                boolean first = true;
                for (String name : ContentTagAliases.members(token.value)) {
                    if (!first) expanded.append(' ');
                    expanded.append("-tag:\"").append(name).append('"');
                    first = false;
                }
            }
            copiedUntil = token.end;
        }
        if (copiedUntil == 0) return query;
        expanded.append(query, copiedUntil, query.length());
        return expanded.toString();
    }

    /** Only a complete term with the same polarity and namespace is redundant. */
    public static boolean hasSimpleTerm(String query, Tag tag) {
        if (query == null || !BookmarkQuery.canEditAsTags(query)) return false;
        boolean excluded = tag.getStatus() == TagStatus.AVOIDED;
        for (BookmarkQuery.Token token : BookmarkQuery.tokenize(query)) {
            if (tag.getTypeSingleName().equals(token.namespace)
                && tag.getName().equals(token.value) && excluded == token.excluded) return true;
        }
        return false;
    }
}
