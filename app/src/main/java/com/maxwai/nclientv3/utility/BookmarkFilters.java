package com.maxwai.nclientv3.utility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Editable parts of a saved query; ambiguous expressions remain wholly raw. */
public final class BookmarkFilters {
    private static final Pattern RANGE = Pattern.compile("(-?)(pages|uploaded):(.*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOUND = Pattern.compile("(>=|<=)?([0-9]+)([a-z]*)", Pattern.CASE_INSENSITIVE);
    public final String keywords, minAgeUnit, maxAgeUnit;
    public final List<BookmarkQuery.Token> tags;
    public final int minPages, maxPages, minAge, maxAge;
    public final boolean rawExpression;

    private BookmarkFilters(String keywords, List<BookmarkQuery.Token> tags, Bounds pages, Bounds age, boolean rawExpression) {
        this.keywords = keywords;
        this.tags = Collections.unmodifiableList(new ArrayList<>(tags));
        minPages = pages.min;
        maxPages = pages.max;
        minAge = age.min;
        maxAge = age.max;
        minAgeUnit = age.minUnit;
        maxAgeUnit = age.maxUnit;
        this.rawExpression = rawExpression;
    }

    public static BookmarkFilters parse(String query) {
        String raw = query == null ? "" : query;
        if (!BookmarkQuery.canEditAsTags(raw)) return raw(raw);
        List<BookmarkQuery.Token> tags = new ArrayList<>();
        Set<String> seenTags = new HashSet<>();
        Bounds pages = new Bounds(), age = new Bounds();
        StringBuilder keywords = new StringBuilder();
        int copiedUntil = 0;
        for (BookmarkQuery.Token token : BookmarkQuery.tokenize(raw)) {
            boolean extracted = false;
            Matcher range = RANGE.matcher(token.raw);
            if (range.matches()) {
                if (!range.group(1).isEmpty()) return raw(raw);
                boolean uploaded = range.group(2).equalsIgnoreCase("uploaded");
                if (!(uploaded ? age : pages).add(range.group(3), uploaded)) return raw(raw);
                extracted = true;
            } else if (token.namespace != null) {
                if (!seenTags.add(token.namespace + ":" + token.value.toLowerCase(Locale.ROOT))) return raw(raw);
                tags.add(token);
                extracted = true;
            }
            if (extracted) {
                keywords.append(raw, copiedUntil, token.start);
                copiedUntil = token.end;
            }
        }
        if (pages.reversed() || age.reversed()) return raw(raw);
        keywords.append(raw, copiedUntil, raw.length());
        return new BookmarkFilters(keywords.toString().trim(), tags, pages, age, false);
    }

    private static BookmarkFilters raw(String query) {
        return new BookmarkFilters(query, Collections.emptyList(), new Bounds(), new Bounds(), true);
    }

    private static final class Bounds {
        int min = -1, max = -1;
        String minUnit = "", maxUnit = "";

        boolean add(String expression, boolean uploaded) {
            Matcher match = BOUND.matcher(expression);
            if (!match.matches()) return false;
            String unit = match.group(3).toLowerCase(Locale.ROOT);
            if (uploaded ? !unit.matches("[hdwmy]") : !unit.isEmpty()) return false;
            int value;
            try { value = Integer.parseInt(match.group(2)); }
            catch (NumberFormatException e) { return false; }
            String operator = match.group(1);
            if (operator == null) {
                if (min != -1 || max != -1) return false;
                min = max = value;
                minUnit = maxUnit = unit;
            } else if (operator.equals(">=")) {
                if (min != -1) return false;
                min = value;
                minUnit = unit;
            } else {
                if (max != -1) return false;
                max = value;
                maxUnit = unit;
            }
            return true;
        }

        boolean reversed() {
            if (min == -1 || max == -1) return false;
            if (minUnit.equals(maxUnit)) return min > max;
            long a = hours(minUnit), b = hours(maxUnit);
            return a != 0 && b != 0 && min * a > max * b;
        }

        private static long hours(String unit) {
            switch (unit) {
                case "h": return 1;
                case "d": return 24;
                case "w": return 168;
                default: return 0;
            }
        }
    }
}
