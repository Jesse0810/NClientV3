package com.maxwai.nclientv3.api.components;


import android.content.Context;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;

import androidx.annotation.NonNull;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.api.enums.TagType;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.ContentTagTranslations;
import com.maxwai.nclientv3.utility.EntityTagTranslations;

import java.io.IOException;
import java.util.Locale;

@SuppressWarnings("unused")
public class Tag implements Parcelable {
    public static final Creator<Tag> CREATOR = new Creator<>() {
        @Override
        public Tag createFromParcel(Parcel in) {
            return new Tag(in);
        }

        @Override
        public Tag[] newArray(int size) {
            return new Tag[size];
        }
    };
    private String name;
    private int count, id;
    private TagType type;
    private TagStatus status = TagStatus.DEFAULT;

    public Tag(String text) {
        this.count = Integer.parseInt(text.substring(0, text.indexOf(',')));
        text = text.substring(text.indexOf(',') + 1);
        this.id = Integer.parseInt(text.substring(0, text.indexOf(',')));
        text = text.substring(text.indexOf(',') + 1);
        this.type = TagType.values[Integer.parseInt(text.substring(0, text.indexOf(',')))];
        this.name = text.substring(text.indexOf(',') + 1);
    }

    public Tag(String name, int count, int id, TagType type, TagStatus status) {
        this.name = name;
        this.count = count;
        this.id = id;
        this.type = type;
        this.status = status;
    }

    public Tag(JsonReader jr) throws IOException {
        jr.beginObject();
        while (jr.peek() != JsonToken.END_OBJECT) {
            switch (jr.nextName()) {
                case "count":
                    count = jr.nextInt();
                    break;
                case "type":
                    type = TagType.typeByName(jr.nextString());
                    break;
                case "id":
                    id = jr.nextInt();
                    break;
                case "name":
                    name = jr.nextString();
                    break;
                case "slug":
                    LogUtility.d("Tag slug: " + jr.nextString());
                    break;
                default:
                    jr.skipValue();
                    break;
            }
        }
        jr.endObject();
    }

    private Tag(Parcel in) {
        name = in.readString();
        count = in.readInt();
        id = in.readInt();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            type = in.readParcelable(TagType.class.getClassLoader(), TagType.class);
        } else {
            type = in.readParcelable(TagType.class.getClassLoader());
        }
        status = TagStatus.values()[in.readByte()];
    }

    public String toQueryTag(TagStatus status) {
        StringBuilder builder = new StringBuilder();
        if (status == TagStatus.AVOIDED)
            builder.append('-');
        builder
            .append(type.getSingle())
            .append(':')
            .append('"')
            .append(name)
            .append('"');
        return builder.toString();
    }

    public String toQueryTag() {
        return toQueryTag(status);
    }

    public String getName() {
        return name;
    }

    /** Returns a localized UI label while keeping the original name for queries and storage. */
    public String getDisplayName(Context context) {
        if (TagType.TAG.equals(type))
            return ContentTagTranslations.getDisplayName(context, name);
        if (EntityTagTranslations.isEntityType(type))
            return EntityTagTranslations.getDisplayName(context, type, name);
        int resource = 0;
        if (TagType.LANGUAGE.equals(type)) {
            switch (name) {
                case "japanese":
                    resource = R.string.tag_language_japanese;
                    break;
                case "english":
                    resource = R.string.tag_language_english;
                    break;
                case "translated":
                    resource = R.string.tag_language_translated;
                    break;
                case "chinese":
                    resource = R.string.tag_language_chinese;
                    break;
                case "arabic":
                    resource = R.string.tag_language_arabic;
                    break;
                case "czech":
                    resource = R.string.tag_language_czech;
                    break;
                case "turkish":
                    resource = R.string.tag_language_turkish;
                    break;
                case "ukrainian":
                    resource = R.string.tag_language_ukrainian;
                    break;
                case "greek":
                    resource = R.string.tag_language_greek;
                    break;
                case "romanian":
                    resource = R.string.tag_language_romanian;
                    break;
                case "hebrew":
                    resource = R.string.tag_language_hebrew;
                    break;
                case "khmer":
                    resource = R.string.tag_language_khmer;
                    break;
                case "textless":
                    resource = R.string.tag_language_textless;
                    break;
                case "textless narrative":
                    resource = R.string.tag_language_textless_narrative;
                    break;
                case "japanese | definition revised please read the wiki":
                    resource = R.string.tag_language_japanese_definition;
                    break;
            }
        } else if (TagType.CATEGORY.equals(type)) {
            switch (name) {
                case "doujinshi":
                    resource = R.string.tag_category_doujinshi;
                    break;
                case "manga":
                    resource = R.string.tag_category_manga;
                    break;
                case "non-h":
                    resource = R.string.tag_category_non_h;
                    break;
                case "western":
                    resource = R.string.tag_category_western;
                    break;
                case "artistcg":
                    resource = R.string.tag_category_artistcg;
                    break;
                case "misc":
                    resource = R.string.tag_category_misc;
                    break;
            }
        }
        return resource == 0 ? name : context.getString(resource);
    }

    public int getCount() {
        return count;
    }

    public TagStatus getStatus() {
        return status;
    }

    public void setStatus(TagStatus status) {
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public TagStatus updateStatus() {
        switch (status) {
            case AVOIDED:
                return status = TagStatus.DEFAULT;
            case DEFAULT:
                return status = TagStatus.ACCEPTED;
            case ACCEPTED:
                return status = TagStatus.AVOIDED;
        }
        return null;
    }

    void writeJson(JsonWriter writer) throws IOException {
        writer.beginObject();
        writer.name("count").value(count);
        writer.name("type").value(getTypeSingleName());
        writer.name("id").value(id);
        writer.name("name").value(name);
        writer.endObject();
    }

    public TagType getType() {
        return type;
    }

    public String getTypeSingleName() {
        return type.getSingle();
    }


    @NonNull
    @Override
    public String toString() {
        return "Tag{" +
            "name='" + name + '\'' +
            ", count=" + count +
            ", id=" + id +
            ", type=" + type +
            ", status=" + status +
            '}';
    }

    public String toScrapedString() {
        return String.format(Locale.US, "%d,%d,%d,%s", count, id, type.getId(), name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tag tag = (Tag) o;

        return id == tag.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(name);
        parcel.writeInt(count);
        parcel.writeInt(id);
        parcel.writeParcelable(type, flags);
        parcel.writeByte((byte) status.ordinal());
    }
}
