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

import java.util.ArrayList;
import java.util.List;

public class Bookmark {
    public final String url, name;
    public final int tag;
    private final ApiRequestType requestType;
    private final Uri uri;

    public Bookmark(String url, ApiRequestType requestType, int tag) {
        this(url, requestType, tag, "");
    }

    public Bookmark(String url, ApiRequestType requestType, int tag, String name) {
        this.url = url;
        this.requestType = requestType;
        this.tag = tag;
        this.name = name == null ? "" : name;
        this.uri = Uri.parse(url);
    }

    public static Bookmark fromInspector(InspectorV3 inspector, String name) {
        Tag tag = inspector.getTag();
        return new Bookmark(withoutBrowsingState(Uri.parse(inspector.getUrl())), inspector.getRequestType(),
            tag == null ? 0 : tag.getId(), name);
    }

    public Bookmark withName(String name) {
        return new Bookmark(url, requestType, tag, name);
    }

    /** Keep legacy URLs as row identities while matching only their saved filters. */
    public String getFilterUrl() {
        return withoutBrowsingState(uri);
    }

    private static String withoutBrowsingState(Uri uri) {
        String encodedQuery = uri.getEncodedQuery();
        if (encodedQuery == null) return uri.toString();
        List<String> parameters = new ArrayList<>();
        boolean removed = false;
        for (String parameter : encodedQuery.split("&", -1)) {
            int separator = parameter.indexOf('=');
            String key = separator < 0 ? parameter : parameter.substring(0, separator);
            String name = Uri.decode(key);
            if ("page".equals(name) || "sort".equals(name)) removed = true;
            else parameters.add(parameter);
        }
        // Preserve the encoded filters verbatim; page and sort belong to the browsing session.
        return removed ? uri.buildUpon().encodedQuery(parameters.isEmpty() ? null : String.join("&", parameters))
            .build().toString() : uri.toString();
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
        return query != null && "-nclientv3".equals(query.trim()) ? "" : query;
    }

    @Nullable
    public InspectorV3 createInspector(Context context, InspectorV3.InspectorResponse response) {
        String query = getEditQuery();
        return query == null ? null
            : InspectorV3.bookmarkInspector(context, query, 1, SortType.RECENT_ALL_TIME, isFavorite(), response);
    }

    public String getDisplayTitle(Context context) {
        return name.trim().isEmpty() ? context.getString(R.string.bookmark_unnamed) : name;
    }

    public String getDisplayConditions(Context context) {
        String query = getEditQuery();
        String conditions = query == null ? context.getString(R.string.bookmark_unavailable)
            : query.isEmpty() ? context.getString(R.string.bookmark_all_content)
            : BookmarkQueryFormatter.format(context, query);
        String scope = isFavorite() ? context.getString(R.string.bookmark_scope_favorites) + " · " : "";
        return scope + conditions;
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
