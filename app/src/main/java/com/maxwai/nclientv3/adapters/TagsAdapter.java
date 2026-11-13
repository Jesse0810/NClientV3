package com.maxwai.nclientv3.adapters;

import android.database.Cursor;
import android.util.JsonWriter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.TagFilterActivity;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.api.enums.TagType;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.Login;
import com.maxwai.nclientv3.settings.TagV2;
import com.maxwai.nclientv3.utility.ContentTagAllowlist;
import com.maxwai.nclientv3.utility.ContentTagTranslations;
import com.maxwai.nclientv3.utility.EntityTagTranslations;
import com.maxwai.nclientv3.utility.ImageDownloadUtility;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.Utility;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TagsAdapter extends RecyclerView.Adapter<TagsAdapter.ViewHolder> implements Filterable {
    @NonNull
    private final TagFilterActivity context;
    private final boolean logged = Login.isLogged();
    private final TagType type;
    private final TagMode tagMode;
    private String lastQuery = null;
    private Cursor cursor = null;
    private Filter filter;

    public TagsAdapter(@NonNull TagFilterActivity cont, String query, boolean online) {
        this.context = cont;
        this.type = null;
        this.tagMode = online ? TagMode.ONLINE : TagMode.OFFLINE;
        getFilter().filter(query);
    }

    public TagsAdapter(@NonNull TagFilterActivity cont, String query, TagType type) {
        this.context = cont;
        this.type = type;
        this.tagMode = TagMode.TYPE;
        getFilter().filter(query);
    }

    @Override
    public Filter getFilter() {
        if (filter != null) return filter;
        filter = new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                if (constraint == null) constraint = "";

                boolean searchDisplayNamesOnly = TagType.TAG.equals(type)
                    && ContentTagTranslations.isEnabled(context);
                lastQuery = searchDisplayNamesOnly ? constraint.toString().trim() : constraint.toString();
                List<String> translatedNames = Collections.emptyList();
                if (searchDisplayNamesOnly)
                    translatedNames = ContentTagTranslations.getMatchingDisplayNames(context, lastQuery);
                else if (type == null || TagType.TAG.equals(type))
                    translatedNames = ContentTagTranslations.getMatchingNames(context, lastQuery);
                Set<String> allowedNames = type == null || TagType.TAG.equals(type)
                    ? ContentTagAllowlist.getAllowedNames(context)
                    : Collections.emptySet();
                Cursor tags = Queries.TagTable.getFilterCursor(lastQuery, type, tagMode == TagMode.ONLINE,
                    TagV2.isSortedByName(), translatedNames, allowedNames, searchDisplayNamesOnly,
                    EntityTagTranslations.getMatchingNames(context, lastQuery, type));
                tags = Queries.TagTable.mergeContentTagCursor(tags);
                results.count = tags.getCount();
                results.values = tags;

                LogUtility.d(results.count + "," + results.values);
                return results;
            }

            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results.count == -1) return;
                Cursor newCursor = (Cursor) results.values;
                int oldCount = getItemCount(), newCount = results.count;
                if (cursor != null) cursor.close();
                cursor = newCursor;
                if (newCount > oldCount) notifyItemRangeInserted(oldCount, newCount - oldCount);
                else notifyItemRangeRemoved(newCount, oldCount - newCount);
                notifyItemRangeChanged(0, Math.min(newCount, oldCount));
            }
        };
        return filter;
    }

    @NonNull
    @Override
    public TagsAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new TagsAdapter.ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.entry_tag_layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull final TagsAdapter.ViewHolder holder, int position) {
        cursor.moveToPosition(position);
        final Tag ent = Queries.TagTable.cursorToTag(cursor);
        holder.title.setText(ent.getDisplayName(context));
        holder.count.setText(String.format(Locale.US, "%d", ent.getCount()));
        holder.master.setOnClickListener(v -> {
            switch (tagMode) {
                case OFFLINE:
                case TYPE:
                    if (TagV2.maxTagReached() && ent.getStatus() == TagStatus.DEFAULT) {
                        context.runOnUiThread(() -> Toast.makeText(context, context.getString(R.string.tags_max_reached, TagV2.MAXTAGS), Toast.LENGTH_LONG).show());
                    } else {
                        TagV2.updateStatus(ent);
                        updateLogo(holder.imgView, ent.getStatus());
                        getFilter().filter(lastQuery);
                    }
                    break;
                case ONLINE:
                    try {
                        onlineTagUpdate(ent, !isAnyFamilyOnline(ent), holder.imgView);
                    } catch (IOException e) {
                        LogUtility.e("Error setting tag", e);
                    }
                    break;
            }

        });
        if (tagMode != TagMode.ONLINE && logged) holder.master.setOnLongClickListener(view -> {
            if (!isEntireFamilyOnline(ent)) showBlacklistDialog(ent, holder.imgView);
            else
                Toast.makeText(context, R.string.tag_already_in_blacklist, Toast.LENGTH_SHORT).show();
            return true;
        });
        updateLogo(holder.imgView, tagMode == TagMode.ONLINE ? TagStatus.AVOIDED : ent.getStatus());
    }

    @Override
    public int getItemCount() {
        return cursor == null ? 0 : cursor.getCount();
    }

    private void showBlacklistDialog(final Tag tag, final ImageView imgView) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setIcon(R.drawable.ic_star_border).setTitle(R.string.add_to_online_blacklist).setMessage(R.string.are_you_sure);
        builder.setPositiveButton(R.string.yes, (dialogInterface, i) -> {
            try {
                onlineTagUpdate(tag, true, imgView);
            } catch (IOException e) {
                LogUtility.w("Error adding to online blacklist", e);
                Toast.makeText(context, R.string.online_blacklist_add_error, Toast.LENGTH_SHORT).show();
            }
        }).setNegativeButton(R.string.no, null).show();
    }

    private boolean isAnyFamilyOnline(Tag tag) {
        for (Tag member : Queries.TagTable.getContentTagFamily(tag))
            if (Login.isOnlineTags(member)) return true;
        return false;
    }

    private boolean isEntireFamilyOnline(Tag tag) {
        for (Tag member : Queries.TagTable.getContentTagFamily(tag))
            if (!Login.isOnlineTags(member)) return false;
        return true;
    }

    private void onlineTagUpdate(final Tag tag, final boolean add, final ImageView imgView) throws IOException {
        if (!Login.isLogged()) return;
        final List<Tag> family = Queries.TagTable.getContentTagFamily(tag);
        StringWriter sw = new StringWriter();
        JsonWriter jw = new JsonWriter(sw);
        jw.beginObject().name("added").beginArray();
        if (add) for (Tag member : family) jw.value(member.getId());
        jw.endArray().name("removed").beginArray();
        if (!add) for (Tag member : family) jw.value(member.getId());
        jw.endArray().endObject();

        final String url = Utility.getApiBaseUrl() + "blacklist";
        final RequestBody ss = RequestBody.create(sw.toString(), MediaType.get("application/json"));
        Global.getClient(context)
            .newCall(new Request.Builder().url(url).post(ss).build())
            .enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try (response) {
                        if (!response.isSuccessful() || response.body() == null
                            || !new JSONObject(response.body().string()).optBoolean("success", false)) return;
                        for (Tag member : family) {
                            if (add) Login.addOnlineTag(member);
                            else Login.removeOnlineTag(member);
                        }
                        context.runOnUiThread(() -> {
                            if (!context.isFinishing() && !context.isDestroyed()) getFilter().filter(lastQuery);
                        });
                    } catch (JSONException error) {
                        LogUtility.w("Invalid blacklist update response", error);
                    }
                }
            });
    }

    private void updateLogo(ImageView img, TagStatus s) {
        context.runOnUiThread(() -> {
            switch (s) {
                case DEFAULT:
                    img.setImageDrawable(null);
                    break;//ImageDownloadUtility.loadImage(R.drawable.ic_void,img); break;
                case ACCEPTED:
                    ImageDownloadUtility.loadImage(R.drawable.ic_check, img);
                    Global.setTint(context, img.getDrawable());
                    break;
                case AVOIDED:
                    ImageDownloadUtility.loadImage(R.drawable.ic_close, img);
                    Global.setTint(context, img.getDrawable());
                    break;
            }
        });

    }

    public void addItem() {
        getFilter().filter(lastQuery);
    }

    private enum TagMode {ONLINE, OFFLINE, TYPE}

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView imgView;
        final TextView title, count;
        final ConstraintLayout master;

        ViewHolder(View v) {
            super(v);
            imgView = v.findViewById(R.id.image);
            title = v.findViewById(R.id.title);
            count = v.findViewById(R.id.count);
            master = v.findViewById(R.id.master_layout);
        }
    }


}
