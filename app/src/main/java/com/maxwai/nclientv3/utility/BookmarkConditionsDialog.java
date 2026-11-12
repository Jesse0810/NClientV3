package com.maxwai.nclientv3.utility;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.api.enums.TagType;
import com.maxwai.nclientv3.components.classes.Bookmark;

/** Read-only, field-based presentation. Never changes the stored query or tag preferences. */
public final class BookmarkConditionsDialog {
    private static final int[] TYPE_NAMES = {0, R.string.parodies, R.string.characters, R.string.tags,
        R.string.artists, R.string.groups, R.string.languages, R.string.categories};

    private BookmarkConditionsDialog() {}

    public static void show(AppCompatActivity activity, Bookmark bookmark) {
        View view = activity.getLayoutInflater().inflate(R.layout.bookmark_conditions_dialog, null);
        LinearLayout fields = view.findViewById(R.id.bookmark_fields);
        if (bookmark.isFavorite())
            addValue(activity, addField(activity, fields, R.string.bookmark_scope_label),
                activity.getString(R.string.bookmark_scope_favorites), null, null);
        if (!bookmark.isFavorite())
            addValue(activity, addField(activity, fields, R.string.bookmark_sort_label),
                activity.getString(bookmark.getSortType().getNameId()), null, null);

        String query = bookmark.getEditQuery();
        if (query == null) {
            addText(activity, fields, R.string.bookmark_conditions_label, activity.getString(R.string.bookmark_unavailable));
        } else if (query.trim().isEmpty() || "-nclientv3".equals(query.trim())) {
            addText(activity, fields, R.string.bookmark_conditions_label, activity.getString(R.string.bookmark_all_content));
        } else {
            BookmarkFilters filters = BookmarkFilters.parse(query);
            if (filters.rawExpression) {
                // Preserve Boolean operators and grouping instead of implying an unrelated conjunction.
                addText(activity, fields, R.string.bookmark_expression_label, formatExpression(activity, query));
            } else {
                if (!filters.keywords.isEmpty())
                    addText(activity, fields, R.string.bookmark_keywords_label, filters.keywords);
                for (TagType type : TagType.values) {
                    if (type == TagType.UNKNOWN) continue;
                    ChipGroup group = null;
                    for (BookmarkQuery.Token token : filters.tags) {
                        if (!type.equals(TagType.typeByName(token.namespace))) continue;
                        if (group == null) group = addField(activity, fields, TYPE_NAMES[type.getId()]);
                        addValue(activity, group, displayName(activity, token), token.excluded,
                            activity.getString(TYPE_NAMES[type.getId()]));
                    }
                }
                addRange(activity, fields, R.string.page_range, filters.minPages, "", filters.maxPages, "");
                addRange(activity, fields, R.string.upload_time, filters.minAge, filters.minAgeUnit,
                    filters.maxAge, filters.maxAgeUnit);
            }
        }
        new MaterialAlertDialogBuilder(activity).setTitle(bookmark.getDisplayTitle(activity))
            .setView(view).setPositiveButton(R.string.ok, null).show();
    }

    private static ChipGroup addField(AppCompatActivity activity, LinearLayout fields, int title) {
        View row = activity.getLayoutInflater().inflate(R.layout.sub_tag_layout, fields, false);
        TextView label = row.findViewById(R.id.title);
        label.setText(title);
        label.setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Subtitle2);
        LinearLayout.LayoutParams labelParams = (LinearLayout.LayoutParams) label.getLayoutParams();
        labelParams.width = dp(activity, 80);
        label.setLayoutParams(labelParams);
        ChipGroup group = row.findViewById(R.id.chip_group);
        LinearLayout.LayoutParams groupParams = (LinearLayout.LayoutParams) group.getLayoutParams();
        groupParams.width = 0;
        groupParams.weight = 1;
        group.setLayoutParams(groupParams);
        group.setChipSpacingHorizontal(dp(activity, 6));
        group.setChipSpacingVertical(dp(activity, 6));
        group.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = right - left - group.getPaddingLeft() - group.getPaddingRight();
            if (width <= 0) return;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView value = (TextView) group.getChildAt(index);
                if (value.getMaxWidth() != width) value.setMaxWidth(width);
            }
        });
        fields.addView(row);
        return group;
    }

    private static void addValue(Context context, ChipGroup group, String text, Boolean excluded, String field) {
        TextView value = new AppCompatTextView(context);
        value.setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Body2);
        value.setText(text);
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8));
        int background = ContextCompat.getColor(context, excluded == null ? R.color.bookmark_neutral_background
            : excluded ? R.color.bookmark_exclude_background : R.color.bookmark_include_background);
        int foreground = ContextCompat.getColor(context, excluded == null ? R.color.bookmark_neutral_foreground
            : excluded ? R.color.bookmark_exclude_foreground : R.color.bookmark_include_foreground);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(context, 12));
        shape.setColor(background);
        value.setBackground(shape);
        value.setTextColor(foreground);
        if (excluded != null) {
            Drawable icon = ContextCompat.getDrawable(context, excluded ? R.drawable.ic_close : R.drawable.ic_check);
            if (icon != null) {
                icon = DrawableCompat.wrap(icon).mutate();
                DrawableCompat.setTint(icon, foreground);
                icon.setBounds(0, 0, dp(context, 16), dp(context, 16));
                value.setCompoundDrawablesRelative(icon, null, null, null);
                value.setCompoundDrawablePadding(dp(context, 5));
            }
            // Spoken descriptions retain polarity without adding visible explanatory labels.
            value.setContentDescription(context.getString(excluded ? R.string.bookmark_exclude_tag
                : R.string.bookmark_include_tag, field, text));
        }
        group.addView(value);
    }

    private static void addText(AppCompatActivity activity, LinearLayout fields, int title, CharSequence text) {
        ChipGroup group = addField(activity, fields, title);
        TextView value = new AppCompatTextView(activity);
        value.setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Body2);
        value.setText(text);
        value.setTextIsSelectable(true);
        value.setPadding(0, dp(activity, 8), 0, dp(activity, 8));
        group.addView(value);
    }

    private static void addRange(AppCompatActivity activity, LinearLayout fields, int title,
                                 int minimum, String minUnit, int maximum, String maxUnit) {
        if (minimum < 0 && maximum < 0) return;
        ChipGroup group = addField(activity, fields, title);
        if (minimum >= 0 && minimum == maximum && minUnit.equals(maxUnit)) {
            addValue(activity, group, unitValue(activity, minimum, minUnit), null, null);
        } else {
            if (minimum >= 0) addValue(activity, group, "≥ " + unitValue(activity, minimum, minUnit), null, null);
            if (maximum >= 0) addValue(activity, group, "≤ " + unitValue(activity, maximum, maxUnit), null, null);
        }
    }

    private static String unitValue(Context context, int number, String unit) {
        int label;
        switch (unit) {
            case "h": label = R.string.hours; break;
            case "d": label = R.string.days; break;
            case "w": label = R.string.weeks; break;
            case "m": label = R.string.months; break;
            case "y": label = R.string.years; break;
            default: return String.valueOf(number);
        }
        return number + " " + context.getString(label);
    }

    private static String displayName(Context context, BookmarkQuery.Token token) {
        return new Tag(token.value, 0, 0, TagType.typeByName(token.namespace),
            token.excluded ? TagStatus.AVOIDED : TagStatus.ACCEPTED).getDisplayName(context);
    }

    private static CharSequence formatExpression(Context context, String query) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        int previous = 0;
        for (BookmarkQuery.Token token : BookmarkQuery.tokenize(query)) {
            text.append(query, previous, token.start);
            if (token.namespace == null) {
                text.append(token.raw);
            } else {
                // Retain the signed namespace and all surrounding operators; localize only its value.
                text.append(token.raw.substring(0, token.raw.indexOf(':') + 1)).append('"');
                int start = text.length();
                text.append(displayName(context, token));
                // NOT and nested groups determine polarity; individual literals use neutral colors.
                text.setSpan(new BackgroundColorSpan(ContextCompat.getColor(context,
                    R.color.bookmark_neutral_background)), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(context,
                    R.color.bookmark_neutral_foreground)), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.append('"');
            }
            previous = token.end;
        }
        return text.append(query, previous, query.length());
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
