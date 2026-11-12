package com.maxwai.nclientv3.components.widgets;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.TagFilterActivity;
import com.maxwai.nclientv3.adapters.TagsAdapter;
import com.maxwai.nclientv3.api.enums.TagType;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.TagV2;

public class TagTypePage extends Fragment {
    private TagType type;
    private RecyclerView recyclerView;
    private TagFilterActivity activity;
    private String query;
    private TagsAdapter adapter;

    public TagTypePage() {
    }

    private static int getTag(int page) {
        switch (page) {
            case 0:
                return TagType.UNKNOWN.getId();//tags with status
            case 1:
                return TagType.TAG.getId();
            case 2:
                return TagType.ARTIST.getId();
            case 3:
                return TagType.CHARACTER.getId();
            case 4:
                return TagType.PARODY.getId();
            case 5:
                return TagType.GROUP.getId();
            case 6:
                return TagType.CATEGORY.getId();//online blacklisted tags
        }
        return -1;
    }

    public static TagTypePage newInstance(int page) {
        TagTypePage fragment = new TagTypePage();
        Bundle args = new Bundle();
        args.putInt("TAGTYPE", getTag(page));
        fragment.setArguments(args);
        return fragment;
    }

    public RecyclerView getRecyclerView() {
        return recyclerView;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        activity = (TagFilterActivity) getActivity();
        type = TagType.values[requireArguments().getInt("TAGTYPE")];
        View rootView = inflater.inflate(R.layout.fragment_tag_filter, container, false);
        recyclerView = rootView.findViewById(R.id.recycler);
        Global.applyFastScroller(recyclerView);
        loadTags();
        return rootView;
    }

    public void loadTags() {
        query = activity.getTagSearchQuery();
        recyclerView.setLayoutManager(new CustomGridLayoutManager(activity, getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 4 : 2));
        if (type.equals(TagType.UNKNOWN)) adapter = new TagsAdapter(activity, query, false);
        else if (type.equals(TagType.CATEGORY)) adapter = new TagsAdapter(activity, query, true);
        else adapter = new TagsAdapter(activity, query, type);
        recyclerView.setAdapter(adapter);
    }

    public void refilter(String newText) {
        query = newText == null ? "" : newText;
        String currentQuery = query;
        if (activity != null && adapter != null)
            activity.runOnUiThread(() -> adapter.getFilter().filter(currentQuery));
    }

    public void reset() {
        if (type == null || type.equals(TagType.CATEGORY)) return; // Online blacklist is separate.
        if (type.equals(TagType.UNKNOWN)) TagV2.resetAllStatus();
        else TagV2.resetStatusByType(type);
        // A cursor is a snapshot: rebinding its old rows does not reload the cleared status.
        refilter(activity == null ? query : activity.getTagSearchQuery());
    }

    public void changeSize() {
        refilter(query);
    }
}

