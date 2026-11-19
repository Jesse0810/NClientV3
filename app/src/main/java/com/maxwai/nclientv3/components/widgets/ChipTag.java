package com.maxwai.nclientv3.components.widgets;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.util.AttributeSet;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.SearchActivity;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.api.enums.TagType;
import com.google.android.material.chip.Chip;

import java.text.NumberFormat;
import java.util.Locale;

public class ChipTag extends Chip {
    private static final int[] DISABLED = {-android.R.attr.state_enabled};
    private Tag tag;
    private boolean canBeAvoided = true;
    private ColorStateList neutralBackground, neutralText, neutralIcon, neutralCloseIcon;

    public ChipTag(Context context) {
        super(context);
    }

    public ChipTag(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ChipTag(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void init(Tag t, boolean close, boolean canBeAvoided) {
        if (tag == null) {
            neutralBackground = getChipBackgroundColor();
            neutralText = getTextColors();
            neutralIcon = getChipIconTint();
            neutralCloseIcon = getCloseIconTint();
        }
        tag = t;
        String name = t.getFilterDisplayName(getContext());
        String count = getCountLabel(getContext(), t);
        setText(count.isEmpty() ? name : name + " · " + count);
        // Keep the count at the end even when a long label needs to be shortened.
        setEllipsize(count.isEmpty() ? TextUtils.TruncateAt.END : TextUtils.TruncateAt.MIDDLE);
        setContentDescription(count.isEmpty() ? name : getContext().getString(R.string.filter_chip_count_description, name, count));
        setCloseIconVisible(close);
        this.canBeAvoided = canBeAvoided;
        applyStatus();
    }

    @Override
    public Tag getTag() {
        return tag;
    }

    public static boolean hasKnownCount(Tag tag) {
        if (tag.getCount() < 0 || tag.getId() <= 0 || tag.getId() >= SearchActivity.CUSTOM_ID_START) return false;
        // The initial language/category rows use zero placeholders until metadata has been loaded.
        return tag.getCount() != 0 || (!TagType.LANGUAGE.equals(tag.getType()) && !TagType.CATEGORY.equals(tag.getType()));
    }

    public static String getCountLabel(Context context, Tag tag) {
        if (!hasKnownCount(tag)) return "";
        Locale locale = context.getResources().getConfiguration().getLocales().get(0);
        return NumberFormat.getIntegerInstance(locale == null ? Locale.getDefault() : locale).format(tag.getCount());
    }

    public void changeStatus(TagStatus status) {
        tag.setStatus(status);
        applyStatus();
    }

    public void updateStatus() {
        switch (tag.getStatus()) {
            case DEFAULT:
                changeStatus(TagStatus.ACCEPTED);
                break;
            case ACCEPTED:
                changeStatus(canBeAvoided ? TagStatus.AVOIDED : TagStatus.DEFAULT);
                break;
            case AVOIDED:
                changeStatus(TagStatus.DEFAULT);
                break;
        }
    }

    private void applyStatus() {
        Context context = getContext();
        TagStatus status = tag.getStatus();
        if (status == TagStatus.DEFAULT) {
            setChipIconResource(R.drawable.ic_void);
            setChipBackgroundColor(neutralBackground);
            setTextColor(neutralText);
            setChipIconTint(neutralIcon);
            setCloseIconTint(neutralCloseIcon);
            ViewCompat.setStateDescription(this, null);
        } else {
            boolean excluded = status == TagStatus.AVOIDED;
            int background = ContextCompat.getColor(context, excluded ? R.color.bookmark_exclude_background : R.color.bookmark_include_background);
            int foreground = ContextCompat.getColor(context, excluded ? R.color.bookmark_exclude_foreground : R.color.bookmark_include_foreground);
            setChipIconResource(excluded ? R.drawable.ic_close : R.drawable.ic_check);
            setChipBackgroundColor(enabledColor(background, neutralBackground));
            setTextColor(enabledColor(foreground, neutralText));
            setChipIconTint(enabledColor(foreground, neutralIcon == null ? neutralText : neutralIcon));
            setCloseIconTint(enabledColor(foreground, neutralCloseIcon == null ? neutralText : neutralCloseIcon));
            ViewCompat.setStateDescription(this, context.getString(excluded ? R.string.filter_chip_excluded : R.string.filter_chip_included));
        }
    }

    private static ColorStateList enabledColor(int color, ColorStateList original) {
        int disabled = original == null ? color : original.getColorForState(DISABLED, original.getDefaultColor());
        return new ColorStateList(new int[][]{DISABLED, {}}, new int[]{disabled, color});
    }
}
