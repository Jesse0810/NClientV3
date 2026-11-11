package com.maxwai.nclientv3.utility;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lossless token spans for displaying saved queries without changing their syntax. */
public final class BookmarkQuery {
    private static final Pattern TAG_TERM = Pattern.compile(
        "(-?)(tag|artist|group|parody|character|language|category):"
            + "(?:\"([^\"\\\\]+)\"|([^\\s\"\\\\()|]+))", Pattern.CASE_INSENSITIVE);

    private BookmarkQuery() {}

    public static final class Token {
        public final String raw, namespace, value;
        public final boolean excluded;
        public final int start, end;

        private Token(String query, int start, int end) {
            this.start = start;
            this.end = end;
            raw = query.substring(start, end);
            Matcher matcher = TAG_TERM.matcher(raw);
            if (matcher.matches()) {
                namespace = matcher.group(2).toLowerCase(Locale.ROOT);
                value = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
                excluded = !matcher.group(1).isEmpty();
            } else {
                namespace = null;
                value = null;
                excluded = false;
            }
        }
    }

    public static List<Token> tokenize(String query) {
        List<Token> tokens = new ArrayList<>();
        if (query == null) return tokens;
        int index = 0;
        while (index < query.length()) {
            char current = query.charAt(index);
            if (Character.isWhitespace(current)) {
                index++;
                continue;
            }
            if (isDelimiter(current)) {
                tokens.add(new Token(query, index, index + 1));
                index++;
                continue;
            }
            int start = index;
            boolean quoted = false, escaped = false;
            while (index < query.length()) {
                current = query.charAt(index);
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = !quoted;
                else if (!quoted && (Character.isWhitespace(current) || isDelimiter(current))) break;
                index++;
            }
            tokens.add(new Token(query, start, index));
        }
        return tokens;
    }

    /** Explicit Boolean logic stays in the keyword field instead of becoming unrelated chips. */
    public static boolean canEditAsTags(String query) {
        if (query == null) return true;
        boolean quoted = false;
        for (int i = 0; i < query.length(); i++) {
            char current = query.charAt(i);
            if (current == '\\') return false;
            if (current == '"') quoted = !quoted;
            else if (!quoted && isDelimiter(current)) return false;
        }
        if (quoted) return false;
        for (Token token : tokenize(query))
            if (token.raw.equalsIgnoreCase("OR") || token.raw.equalsIgnoreCase("AND")
                || token.raw.equalsIgnoreCase("NOT")) return false;
        return true;
    }

    private static boolean isDelimiter(char value) {
        return value == '(' || value == ')' || value == '|';
    }
}
