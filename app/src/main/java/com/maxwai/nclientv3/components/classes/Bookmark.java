package com.maxwai.nclientv3.components.classes;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.InspectorV3;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.ApiRequestType;
import com.maxwai.nclientv3.api.enums.SortType;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.utility.BookmarkQueryFormatter;

public class Bookmark {
    public final String url, name;
    public final int page, tag;
    private final ApiRequestType requestType;
    private final Uri uri;

    public Bookmark(String url, int page, ApiRequestType requestType, int tag) {
        this(url, page, requestType, tag, "");
    }

    public Bookmark(String url, int page, ApiRequestType requestType, int tag, String name) {
        this.url = url;
        this.page = page;
        this.requestType = requestType;
        this.tag = tag;
        this.name = name == null ? "" : name;
        this.uri = Uri.parse(url);
    }

    public static Bookmark fromInspector(InspectorV3 inspector, String name) {
        Tag tag = inspector.getTag();
        return new Bookmark(inspector.getUrl(), inspector.getPage(), inspector.getRequestType(),
            tag == null ? 0 : tag.getId(), name);
    }

    public Bookmark withName(String name) {
        return new Bookmark(url, page, requestType, tag, name);
    }

    public ApiRequestType getRequestType() {
        return requestType;
    }

    public boolean isFavorite() {
        return requestType == ApiRequestType.FAVORITE;
    }

    @Nullable
    public String getQuery() {
        String query = uri.getQueryParameter("query");
        if (query == null) query = uri.getQueryParameter("q");
        if (query != null) return query;
        if (requestType == ApiRequestType.BYTAG) {
            Tag original = Queries.TagTable.getTagById(tag);
            return original == null ? null : original.toQueryTag(TagStatus.ACCEPTED);
        }
        if (requestType == ApiRequestType.BYSEARCH || requestType == ApiRequestType.BYALL || isFavorite()) return "";
        return null;
    }

    @Nullable
    public String getEditQuery() {
        String query = getQuery();
        return "-nclientv3".equals(query) && getSortType() != SortType.RECENT_ALL_TIME ? "" : query;
    }

    public SortType getSortType() {
        String sort = uri.getQueryParameter("sort");
        for (SortType type : SortType.values())
            if (type.getUrlAddition() != null && type.getUrlAddition().equals(sort)) return type;
        return SortType.RECENT_ALL_TIME;
    }

    @Nullable
    public InspectorV3 createInspector(Context context, InspectorV3.InspectorResponse response) {
        String query = getQuery();
        return query == null ? null
            : InspectorV3.bookmarkInspector(context, query, page, getSortType(), isFavorite(), response);
    }

    public String getDisplayTitle(Context context) {
        return name.trim().isEmpty() ? context.getString(R.string.bookmark_unnamed) : name;
    }

    public String getDisplayConditions(Context context) {
        String query = getEditQuery();
        String conditions = query == null ? context.getString(R.string.bookmark_unavailable)
            : query.isEmpty() ? context.getString(R.string.bookmark_all_content)
            : BookmarkQueryFormatter.format(context, query);
        String scope = context.getString(isFavorite() ? R.string.bookmark_scope_favorites : R.string.bookmark_scope_all);
        return scope + " · " + conditions + (isFavorite() ? "" : "\n" +
            context.getString(R.string.bookmark_sort_format, context.getString(getSortType().getNameId())));
    }

    public void deleteBookmark() {
        Queries.BookmarkTable.deleteBookmark(url);
    }

    @NonNull
    @Override
    public String toString() {
        String query = getQuery();
        return query == null ? url : query;
    }
}
