package com.maxwai.nclientv3.utility;

import android.content.Context;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.api.enums.TagType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Locale;

/** Display-only formatting; canonical queries are never rewritten. */
public final class BookmarkQueryFormatter {
    private static final int[] TYPE_NAMES = {0, R.string.parodies, R.string.characters, R.string.tags,
        R.string.artists, R.string.groups, R.string.languages, R.string.categories};
    private static final Pattern RANGE = Pattern.compile("(pages|uploaded):(>=|<=|>|<|=)?([0-9]+)([hdwmy]?)", Pattern.CASE_INSENSITIVE);

    private BookmarkQueryFormatter() {}

    public static String format(Context context, String query) {
        StringBuilder result = new StringBuilder();
        int previous = 0;
        for (BookmarkQuery.Token token : BookmarkQuery.tokenize(query)) {
            result.append(query, previous, token.start);
            if (token.namespace != null) {
                TagType type = TagType.typeByName(token.namespace);
                Tag tag = new Tag(token.value, 0, 0, type, token.excluded ? TagStatus.AVOIDED : TagStatus.ACCEPTED);
                result.append(context.getString(token.excluded ? R.string.bookmark_exclude_tag : R.string.bookmark_include_tag,
                    context.getString(TYPE_NAMES[type.getId()]), tag.getDisplayName(context)));
            } else {
                Matcher range = RANGE.matcher(token.raw);
                if (range.matches()) {
                    String value = range.group(3);
                    if (!range.group(4).isEmpty()) value += " " + context.getString(unitResource(range.group(4).toLowerCase(Locale.ROOT)));
                    result.append(context.getString("pages".equalsIgnoreCase(range.group(1)) ? R.string.page_range : R.string.upload_time))
                        .append(range.group(2) == null ? "" : range.group(2)).append(value);
                } else result.append(token.raw);
            }
            previous = token.end;
        }
        return result.append(query, previous, query.length()).toString();
    }

    private static int unitResource(String unit) {
        switch (unit) {
            case "h": return R.string.hours;
            case "d": return R.string.days;
            case "w": return R.string.weeks;
            case "m": return R.string.months;
            default: return R.string.years;
        }
    }
}
