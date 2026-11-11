package com.maxwai.nclientv3.adapters;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.maxwai.nclientv3.BookmarkActivity;
import com.maxwai.nclientv3.MainActivity;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.SearchActivity;
import com.maxwai.nclientv3.api.InspectorV3;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.components.classes.Bookmark;
import com.maxwai.nclientv3.utility.IntentUtility;
import com.maxwai.nclientv3.settings.Global;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.ViewHolder> {
    private static final int LAYOUT = R.layout.bookmark_layout;

    private final List<Bookmark> bookmarks;
    private final BookmarkActivity bookmarkActivity;

    public BookmarkAdapter(BookmarkActivity bookmarkActivity) {
        this.bookmarkActivity = bookmarkActivity;
        this.bookmarks = Queries.BookmarkTable.getBookmarks();
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(LAYOUT, parent, false));
    }


    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int pos) {
        Bookmark bookmark = bookmarks.get(pos);

        holder.queryText.setText(bookmark.getDisplayTitle(bookmarkActivity));
        holder.conditionsText.setText(bookmark.getDisplayConditions(bookmarkActivity));
        holder.detailsButton.setOnClickListener(v -> new MaterialAlertDialogBuilder(bookmarkActivity)
            .setTitle(bookmark.getDisplayTitle(bookmarkActivity))
            .setMessage(bookmark.getDisplayConditions(bookmarkActivity))
            .setPositiveButton(R.string.ok, null).show());
        holder.pageLabel.setText(bookmarkActivity.getString(R.string.bookmark_page_format, bookmark.page));

        holder.deleteButton.setOnClickListener(v -> removeBookmarkAtPosition(holder.getBindingAdapterPosition()));
        holder.editButton.setOnClickListener(v -> {
            Intent intent = new Intent(bookmarkActivity, SearchActivity.class);
            intent.putExtra(SearchActivity.EXTRA_BOOKMARK_URL, bookmark.url);
            bookmarkActivity.startActivity(intent);
        });
        Global.setTint(bookmarkActivity, holder.editButton.getDrawable());
        Global.setTint(bookmarkActivity, holder.deleteButton.getDrawable());

        holder.rootLayout.setOnClickListener(v -> loadBookmark(bookmark));
    }

    /**
     * Start an {@link MainActivity} with <code>bookmark</code> as query and page
     *
     * @param bookmark bookmark to load
     */
    private void loadBookmark(Bookmark bookmark) {
        InspectorV3 inspector = bookmark.createInspector(bookmarkActivity, null);
        if (inspector == null) {
            Toast.makeText(bookmarkActivity, R.string.bookmark_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(bookmarkActivity, MainActivity.class);
        i.putExtra(bookmarkActivity.getPackageName() + ".BYBOOKMARK", true);
        i.putExtra(bookmarkActivity.getPackageName() + ".INSPECTOR", inspector);
        i.putExtra(bookmarkActivity.getPackageName() + ".BOOKMARK_TITLE", bookmark.getDisplayTitle(bookmarkActivity));
        IntentUtility.startAnotherActivity(bookmarkActivity, i);
    }

    /**
     * remove bookmark from the adapter at <code>position</code>
     *
     * @param position index to delete
     */
    private void removeBookmarkAtPosition(int position) {
        if (position < 0 || position >= bookmarks.size()) return;
        Bookmark bookmark = bookmarks.get(position);
        bookmark.deleteBookmark();
        bookmarks.remove(bookmark);
        bookmarkActivity.runOnUiThread(() -> notifyItemRemoved(position));
    }

    @Override
    public int getItemCount() {
        return bookmarks.size();
    }

    public void reload() {
        bookmarks.clear();
        bookmarks.addAll(Queries.BookmarkTable.getBookmarks());
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final AppCompatImageButton deleteButton;
        final AppCompatImageButton editButton;
        final TextView queryText;
        final TextView conditionsText;
        final TextView detailsButton;
        final TextView pageLabel;
        final ConstraintLayout rootLayout;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            deleteButton = itemView.findViewById(R.id.remove_button);
            editButton = itemView.findViewById(R.id.edit_bookmark);
            pageLabel = itemView.findViewById(R.id.page);
            queryText = itemView.findViewById(R.id.title);
            conditionsText = itemView.findViewById(R.id.bookmark_conditions);
            detailsButton = itemView.findViewById(R.id.bookmark_details);
            rootLayout = itemView.findViewById(R.id.master_layout);
        }
    }

}
