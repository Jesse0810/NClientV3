package com.maxwai.nclientv3.utility;

import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.InspectorV3;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.components.classes.Bookmark;

public final class BookmarkDialogs {
    private BookmarkDialogs() {}

    public static void showAdd(AppCompatActivity activity, InspectorV3 inspector) {
        if (inspector == null) return;
        if (!inspector.canAddBookmark()) {
            Toast.makeText(activity, R.string.bookmark_requires_filters, Toast.LENGTH_LONG).show();
            return;
        }
        Bookmark snapshot = Bookmark.fromInspector(inspector, "");
        if (snapshot.getQuery() == null) {
            Toast.makeText(activity, R.string.bookmark_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        Bookmark existing = Queries.BookmarkTable.findMatchingBookmark(snapshot, null);
        View view = activity.getLayoutInflater().inflate(R.layout.bookmark_name_dialog, null);
        TextInputLayout inputLayout = view.findViewById(R.id.bookmark_name_layout);
        TextInputEditText input = view.findViewById(R.id.bookmark_name);
        ((TextView) view.findViewById(R.id.bookmark_summary)).setText(snapshot.getDisplayConditions(activity));
        if (existing != null) input.setText(existing.name);
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
            .setTitle(existing == null ? R.string.add_bookmark : R.string.bookmark_edit)
            .setView(view).setPositiveButton(R.string.bookmark_save, null)
            .setNegativeButton(R.string.cancel, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText() == null ? "" : input.getText().toString().trim();
            if (name.isEmpty()) {
                inputLayout.setError(activity.getString(R.string.bookmark_name_required));
                return;
            }
            inputLayout.setError(null);
            Bookmark saved = (existing == null ? snapshot : existing).withName(name);
            if (!Queries.BookmarkTable.saveBookmark(existing == null ? null : existing.url, saved)) {
                Toast.makeText(activity, R.string.bookmark_save_failed, Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(activity, R.string.bookmark_saved, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
        input.requestFocus();
    }
}
