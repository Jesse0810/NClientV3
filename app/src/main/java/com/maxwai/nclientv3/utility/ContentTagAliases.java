package com.maxwai.nclientv3.utility;

import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.api.enums.TagType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Reviewed spelling/romanization aliases. Raw database names and IDs are never rewritten. */
public final class ContentTagAliases {
    private static final Map<String, List<String>> GROUPS;
    private static final Map<String, String> CANONICAL_NAMES;

    static {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        Map<String, String> canonicalNames = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : ContentTagAliasData.groups().entrySet()) {
            String canonical = entry.getKey();
            List<String> names = new ArrayList<>(entry.getValue());
            if (canonical == null || canonical.isEmpty() || names.size() < 2
                || !canonical.equals(names.get(0)))
                throw new IllegalArgumentException("Invalid content alias group");
            for (String name : names) {
                if (name == null || name.trim().isEmpty() || name.indexOf('"') >= 0
                    || name.indexOf('\\') >= 0 || canonicalNames.put(name, canonical) != null)
                    throw new IllegalArgumentException("Invalid or overlapping content alias name");
            }
            groups.put(canonical, Collections.unmodifiableList(names));
        }
        GROUPS = Collections.unmodifiableMap(groups);
        CANONICAL_NAMES = Collections.unmodifiableMap(canonicalNames);
    }

    private ContentTagAliases() {
    }

    public static String canonicalName(String name) {
        Objects.requireNonNull(name, "name");
        String canonical = CANONICAL_NAMES.get(name);
        return canonical == null ? name : canonical;
    }

    /** Canonical name first; the returned list is immutable, including ungrouped names. */
    public static List<String> members(String name) {
        List<String> names = GROUPS.get(canonicalName(name));
        return names == null ? Collections.singletonList(name) : names;
    }

    /** Logical content equality only; other namespaces keep their existing raw-ID equality. */
    public static boolean sameTag(Tag first, Tag second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        if (TagType.TAG.equals(first.getType()) && TagType.TAG.equals(second.getType()))
            return canonicalName(first.getName()).equals(canonicalName(second.getName()));
        return first.equals(second);
    }

    /**
     * Collapse only reviewed content groups, preserving their first position in the input.
     * The representative always has a real input name/ID, preferring the canonical member.
     * Counts use the maximum, not a sum of possibly overlapping gallery sets. A legacy
     * excluded preference wins over included/default states until the user changes the group.
     * Input objects are never modified; unrelated tags are passed through unchanged.
     */
    public static List<Tag> mergeTags(Collection<Tag> tags) {
        Objects.requireNonNull(tags, "tags");
        List<Tag> merged = new ArrayList<>(tags.size());
        Map<String, Integer> positions = new HashMap<>();
        for (Tag tag : tags) {
            Objects.requireNonNull(tag, "tag");
            String canonical = TagType.TAG.equals(tag.getType())
                ? CANONICAL_NAMES.get(tag.getName()) : null;
            if (canonical == null) {
                merged.add(tag);
                continue;
            }
            Integer position = positions.get(canonical);
            if (position == null) {
                positions.put(canonical, merged.size());
                merged.add(copy(tag, tag.getCount(), tag.getStatus()));
                continue;
            }
            Tag previous = merged.get(position);
            Tag representative = prefer(tag, previous, canonical) ? tag : previous;
            merged.set(position, copy(representative, Math.max(previous.getCount(), tag.getCount()),
                mergedStatus(previous.getStatus(), tag.getStatus())));
        }
        return merged;
    }

    private static boolean prefer(Tag candidate, Tag previous, String canonical) {
        boolean candidateCanonical = canonical.equals(candidate.getName());
        boolean previousCanonical = canonical.equals(previous.getName());
        if (candidateCanonical != previousCanonical) return candidateCanonical;
        // Use name/ID order when only old spellings are available. This does not fabricate
        // a canonical ID and is stable even when a database query changes its row order.
        int nameOrder = candidate.getName().compareTo(previous.getName());
        return nameOrder < 0 || (nameOrder == 0 && candidate.getId() < previous.getId());
    }

    private static Tag copy(Tag tag, int count, TagStatus status) {
        return new Tag(tag.getName(), count, tag.getId(), tag.getType(), status);
    }

    private static TagStatus mergedStatus(TagStatus first, TagStatus second) {
        if (first == TagStatus.AVOIDED || second == TagStatus.AVOIDED) return TagStatus.AVOIDED;
        if (first == TagStatus.ACCEPTED || second == TagStatus.ACCEPTED) return TagStatus.ACCEPTED;
        return TagStatus.DEFAULT;
    }

}
