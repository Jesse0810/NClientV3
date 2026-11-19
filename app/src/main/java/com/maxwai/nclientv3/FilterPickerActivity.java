package com.maxwai.nclientv3;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.IntentCompat;
import androidx.core.os.BundleCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.api.enums.TagType;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.components.activities.GeneralActivity;
import com.maxwai.nclientv3.components.widgets.ChipTag;
import com.maxwai.nclientv3.utility.ContentTagAliases;
import com.maxwai.nclientv3.utility.ContentTagAllowlist;
import com.maxwai.nclientv3.utility.LogUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Edits an isolated draft of one field. Apply returns the selected raw tags to the caller. */
public class FilterPickerActivity extends GeneralActivity {
    private static final String EXTRA_TYPE = "com.maxwai.nclientv3.extra.FILTER_PICKER_TYPE";
    private static final String EXTRA_SELECTION = "com.maxwai.nclientv3.extra.FILTER_PICKER_SELECTION";
    private static final String STATE_QUERY = "filterPickerQuery";
    private static final String STATE_SELECTED_ONLY = "filterPickerSelectedOnly";
    private static final String STATE_LEGACY = "filterPickerLegacySpellings";
    private static final String STATE_SCROLL = "filterPickerScroll";

    private static final Comparator<Choice> CHOICE_ORDER = Comparator
        .comparing((Choice choice) -> choice.displayName, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(choice -> choice.tag.getName());

    private final Map<String, Tag> selected = new LinkedHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Future<?> filtering;
    private int filterGeneration;
    private TagType type;
    private boolean selectedOnly;
    private boolean legacySpellings;
    private boolean catalogFailed;
    private Catalog catalog;
    private Parcelable savedScroll;
    private TextInputEditText search;
    private TabLayout tabs;
    private TextView hint;
    private TextView spelling;
    private TextView message;
    private View clear;
    private LinearLayoutManager layout;
    private ChoiceAdapter adapter;

    public static Intent createIntent(Context context, TagType type, ArrayList<Tag> selection) {
        return new Intent(context, FilterPickerActivity.class)
            .putExtra(EXTRA_TYPE, (int) type.getId())
            .putParcelableArrayListExtra(EXTRA_SELECTION, selection);
    }

    public static TagType getType(@Nullable Intent intent) {
        int id = intent == null ? -1 : intent.getIntExtra(EXTRA_TYPE, -1);
        return id > 0 && id < TagType.values.length ? TagType.values[id] : TagType.UNKNOWN;
    }

    @Nullable
    public static ArrayList<Tag> getSelection(@Nullable Intent intent) {
        return intent == null ? null : IntentCompat.getParcelableArrayListExtra(intent, EXTRA_SELECTION, Tag.class);
    }

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        type = getType(getIntent());
        if (TagType.UNKNOWN.equals(type)) {
            finish();
            return;
        }
        setContentView(R.layout.activity_filter_picker);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.select_existing_tag, getString(fieldName())));
        }

        ArrayList<Tag> initial = state == null
            ? getSelection(getIntent()) : BundleCompat.getParcelableArrayList(state, EXTRA_SELECTION, Tag.class);
        if (initial != null) for (Tag tag : initial) {
            if (tag != null && type.equals(tag.getType()) && tag.getName() != null
                && tag.getStatus() != TagStatus.DEFAULT)
                selected.put(tag.getName(), withStatus(tag, tag.getStatus()));
        }
        if (state != null) {
            selectedOnly = state.getBoolean(STATE_SELECTED_ONLY);
            legacySpellings = state.getBoolean(STATE_LEGACY);
            savedScroll = BundleCompat.getParcelable(state, STATE_SCROLL, Parcelable.class);
        }

        RecyclerView recycler = findViewById(R.id.recycler);
        layout = new LinearLayoutManager(this);
        adapter = new ChoiceAdapter();
        recycler.setLayoutManager(layout);
        recycler.setAdapter(adapter);
        hint = findViewById(R.id.filter_picker_hint);
        spelling = findViewById(R.id.filter_picker_spelling);
        message = findViewById(R.id.filter_picker_message);
        clear = findViewById(R.id.filter_picker_clear);
        clear.setOnClickListener(view -> {
            selected.clear();
            updateSelectionSummary();
            updateResults(false);
        });
        findViewById(R.id.filter_picker_apply).setOnClickListener(view -> apply());
        spelling.setOnClickListener(view -> {
            legacySpellings = !legacySpellings;
            updateSpellingToggle();
            updateResults(true);
        });

        search = findViewById(R.id.filter_picker_search);
        if (state != null) search.setText(state.getString(STATE_QUERY, ""));
        search.setOnEditorActionListener((view, action, event) -> {
            InputMethodManager keyboard = getSystemService(InputMethodManager.class);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(view.getWindowToken(), 0);
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                updateResults(true);
            }
            @Override public void afterTextChanged(Editable text) {}
        });

        tabs = findViewById(R.id.filter_picker_tabs);
        tabs.addTab(tabs.newTab().setText(R.string.filter_picker_all), !selectedOnly);
        tabs.addTab(tabs.newTab(), selectedOnly);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                selectedOnly = tab.getPosition() == 1;
                updateSpellingToggle();
                updateResults(true);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
        updateSpellingToggle();
        updateSelectionSummary();
        loadCatalog();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        if (search != null) {
            state.putString(STATE_QUERY, search.getText() == null ? "" : search.getText().toString());
            state.putBoolean(STATE_SELECTED_ONLY, selectedOnly);
            state.putBoolean(STATE_LEGACY, legacySpellings);
            state.putParcelableArrayList(EXTRA_SELECTION, new ArrayList<>(selected.values()));
            state.putParcelable(STATE_SCROLL, savedScroll != null ? savedScroll : layout.onSaveInstanceState());
        }
        super.onSaveInstanceState(state);
    }

    private void apply() {
        Intent result = new Intent()
            .putExtra(EXTRA_TYPE, (int) type.getId())
            .putParcelableArrayListExtra(EXTRA_SELECTION, new ArrayList<>(selected.values()));
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadCatalog() {
        catalogFailed = false;
        updateResults(false);
        worker.execute(() -> {
            Catalog loaded;
            try {
                loaded = readCatalog();
            } catch (RuntimeException failure) {
                LogUtility.w("Unable to load filter options", failure);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    catalogFailed = true;
                    updateResults(false);
                });
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                catalog = loaded;
                updateResults(false);
            });
        });
    }

    private Catalog readCatalog() {
        boolean content = TagType.TAG.equals(type);
        Map<String, Tag> rows = new HashMap<>();
        for (Tag tag : Queries.TagTable.getTrueAllType(type)) {
            String name = tag.getName();
            if (name == null || name.trim().isEmpty() || name.indexOf('"') >= 0 || name.indexOf('\\') >= 0)
                continue;
            Tag current = rows.get(name);
            // Repeated IDs for the same raw name must remain one query option. Keep the
            // preferred identity stable and use the largest known count for that name.
            if (current == null) rows.put(name, tag);
            else {
                Tag identity = current.getId() <= tag.getId() ? current : tag;
                rows.put(name, new Tag(name, Math.max(current.getCount(), tag.getCount()),
                    identity.getId(), type, TagStatus.DEFAULT));
            }
        }
        Map<String, Choice> byName = new HashMap<>();
        Map<String, Choice> representatives = new HashMap<>();
        ArrayList<Choice> oldSpellings = new ArrayList<>();
        for (Tag tag : rows.values()) {
            if (Thread.currentThread().isInterrupted()) return Catalog.empty();
            Choice choice = new Choice(this, tag, content);
            byName.put(tag.getName(), choice);
            if (!content) continue;
            if (!ContentTagAllowlist.isAllowed(this, tag.getName())) continue;
            String canonical = ContentTagAliases.canonicalName(tag.getName());
            if (!canonical.equals(tag.getName())) oldSpellings.add(choice);
            Choice previous = representatives.get(canonical);
            if (previous == null || canonical.equals(tag.getName())
                || (!canonical.equals(previous.tag.getName()) && tag.getName().compareTo(previous.tag.getName()) < 0))
                representatives.put(canonical, choice);
        }
        ArrayList<Choice> standard = new ArrayList<>(content ? representatives.values() : byName.values());
        standard.sort(CHOICE_ORDER);
        oldSpellings.sort(CHOICE_ORDER);
        return new Catalog(byName, standard, oldSpellings);
    }

    private void updateResults(boolean fromStart) {
        if (fromStart) savedScroll = null;
        if (filtering != null) filtering.cancel(true);
        final int generation = ++filterGeneration;
        if (catalog == null && !selectedOnly) {
            adapter.setRows(Collections.emptyList());
            if (catalogFailed) showMessage(R.string.filter_picker_retry, this::loadCatalog);
            else showMessage(R.string.tag_choices_loading, null);
            return;
        }
        List<Choice> source;
        if (selectedOnly) {
            source = new ArrayList<>(selected.size());
            for (Tag tag : selected.values()) {
                Choice choice = catalog == null ? null : catalog.byName.get(tag.getName());
                if (choice == null) choice = new Choice(this, tag, TagType.TAG.equals(type));
                source.add(choice);
            }
            source.sort(CHOICE_ORDER);
        } else source = legacySpellings ? catalog.oldSpellings : catalog.standard;

        String query = search.getText() == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        filtering = worker.submit(() -> {
            List<Choice> rows = source;
            if (!query.isEmpty()) {
                rows = new ArrayList<>();
                for (Choice choice : source) {
                    if (Thread.currentThread().isInterrupted()) return;
                    if (choice.matches(query)) rows.add(choice);
                }
            }
            List<Choice> result = rows;
            runOnUiThread(() -> {
                if (generation != filterGeneration || isFinishing() || isDestroyed()) return;
                adapter.setRows(result);
                if (result.isEmpty()) {
                    int text = selectedOnly && selected.isEmpty() ? R.string.filter_picker_no_selection
                        : !query.isEmpty() ? R.string.filter_picker_no_match : R.string.tag_choices_empty;
                    showMessage(text, null);
                } else {
                    message.setVisibility(View.GONE);
                    if (savedScroll != null) {
                        layout.onRestoreInstanceState(savedScroll);
                        savedScroll = null;
                    } else if (fromStart) layout.scrollToPositionWithOffset(0, 0);
                }
            });
        });
    }

    private void showMessage(@StringRes int text, @Nullable Runnable action) {
        message.setText(text);
        message.setVisibility(View.VISIBLE);
        message.setOnClickListener(action == null ? null : view -> action.run());
        message.setClickable(action != null);
    }

    private void updateSpellingToggle() {
        boolean visible = TagType.TAG.equals(type) && !selectedOnly;
        spelling.setVisibility(visible ? View.VISIBLE : View.GONE);
        spelling.setText(legacySpellings ? R.string.filter_picker_standard_spellings : R.string.filter_picker_old_spellings);
        hint.setText(visible && legacySpellings ? R.string.legacy_content_tag_hint : R.string.filter_picker_hint);
    }

    private void updateSelectionSummary() {
        TabLayout.Tab selectedTab = tabs.getTabAt(1);
        if (selectedTab != null) selectedTab.setText(getString(R.string.filter_picker_selected, selected.size()));
        clear.setEnabled(!selected.isEmpty());
    }

    private void changeSelection(Choice choice, TagStatus status) {
        String name = choice.tag.getName();
        Tag prior = selected.get(name);
        if (prior != null && prior.getStatus() == status) selected.remove(name);
        else {
            if (TagType.TAG.equals(type)) {
                // The selected raw spelling replaces alternatives rather than intersecting
                // two different searches for the same reviewed concept.
                for (String alternative : ContentTagAliases.members(name))
                    if (!name.equals(alternative)) selected.remove(alternative);
            }
            selected.put(name, withStatus(prior == null ? choice.tag : prior, status));
        }
        updateSelectionSummary();
        if (selectedOnly) updateResults(false);
        else adapter.refreshSelection(name);
    }

    private static Tag withStatus(Tag tag, TagStatus status) {
        return new Tag(tag.getName(), tag.getCount(), tag.getId(), tag.getType(), status);
    }

    @StringRes
    private int fieldName() {
        if (TagType.PARODY.equals(type)) return R.string.parodies;
        if (TagType.CHARACTER.equals(type)) return R.string.characters;
        if (TagType.ARTIST.equals(type)) return R.string.artists;
        if (TagType.GROUP.equals(type)) return R.string.groups;
        if (TagType.LANGUAGE.equals(type)) return R.string.languages;
        if (TagType.CATEGORY.equals(type)) return R.string.categories;
        return R.string.tags;
    }

    private static final class Choice {
        private final Tag tag;
        private final String displayName, searchDisplay, searchName;

        private Choice(Context context, Tag tag, boolean content) {
            this.tag = tag;
            displayName = tag.getFilterDisplayName(context);
            searchDisplay = displayName.toLowerCase(Locale.ROOT);
            String rawName = tag.getName();
            searchName = (content && ContentTagAliases.canonicalName(rawName).equals(rawName)
                ? String.join(" ", ContentTagAliases.members(rawName)) : rawName).toLowerCase(Locale.ROOT);
        }

        private boolean matches(String query) {
            return searchName.contains(query) || searchDisplay.contains(query);
        }
    }

    private static final class Catalog {
        private final Map<String, Choice> byName;
        private final List<Choice> standard, oldSpellings;

        private Catalog(Map<String, Choice> byName, List<Choice> standard, List<Choice> oldSpellings) {
            this.byName = byName;
            this.standard = standard;
            this.oldSpellings = oldSpellings;
        }

        private static Catalog empty() {
            return new Catalog(Collections.emptyMap(), Collections.emptyList(), Collections.emptyList());
        }
    }

    private final class ChoiceAdapter extends RecyclerView.Adapter<ChoiceHolder> {
        private List<Choice> rows = Collections.emptyList();

        private void setRows(List<Choice> choices) {
            rows = choices;
            notifyDataSetChanged();
        }

        private void refreshSelection(String changed) {
            List<String> affected = TagType.TAG.equals(type) ? ContentTagAliases.members(changed)
                : Collections.singletonList(changed);
            for (int index = 0; index < rows.size(); index++)
                if (affected.contains(rows.get(index).tag.getName())) notifyItemChanged(index);
        }

        @NonNull
        @Override
        public ChoiceHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.filter_picker_row, parent, false);
            return new ChoiceHolder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull ChoiceHolder holder, int position) {
            holder.bind(rows.get(position));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private final class ChoiceHolder extends RecyclerView.ViewHolder {
        private final TextView name, count;
        private final ImageButton include, exclude;
        private final GradientDrawable background = new GradientDrawable();

        private ChoiceHolder(View row) {
            super(row);
            name = row.findViewById(R.id.filter_picker_name);
            count = row.findViewById(R.id.filter_picker_count);
            include = row.findViewById(R.id.filter_picker_include);
            exclude = row.findViewById(R.id.filter_picker_exclude);
            background.setCornerRadius(12 * row.getResources().getDisplayMetrics().density);
            row.setBackground(background);
        }

        private void bind(Choice choice) {
            Tag current = selected.get(choice.tag.getName());
            boolean included = current != null && current.getStatus() == TagStatus.ACCEPTED;
            boolean excluded = current != null && current.getStatus() == TagStatus.AVOIDED;
            int fill = ContextCompat.getColor(FilterPickerActivity.this, included ? R.color.bookmark_include_background
                : excluded ? R.color.bookmark_exclude_background : R.color.bookmark_neutral_background);
            int foreground = ContextCompat.getColor(FilterPickerActivity.this, included ? R.color.bookmark_include_foreground
                : excluded ? R.color.bookmark_exclude_foreground : R.color.bookmark_neutral_foreground);
            int includeTint = ContextCompat.getColor(FilterPickerActivity.this, R.color.bookmark_include_foreground);
            int excludeTint = ContextCompat.getColor(FilterPickerActivity.this, R.color.bookmark_exclude_foreground);
            background.setColor(fill);
            name.setText(choice.displayName);
            name.setTextColor(foreground);
            String number = ChipTag.getCountLabel(FilterPickerActivity.this, choice.tag);
            count.setVisibility(number.isEmpty() ? View.GONE : View.VISIBLE);
            count.setText(number);
            count.setTextColor(foreground);
            count.setContentDescription(number.isEmpty() ? null : getString(R.string.filter_picker_gallery_count, number));
            include.setImageResource(included ? R.drawable.ic_check_circle : R.drawable.ic_check);
            exclude.setImageResource(excluded ? R.drawable.ic_filter_exclude_selected : R.drawable.ic_close);
            include.setColorFilter(includeTint);
            exclude.setColorFilter(excludeTint);
            include.setSelected(included);
            exclude.setSelected(excluded);
            include.setContentDescription(included ? getString(R.string.filter_picker_undo_include, choice.displayName)
                : getString(R.string.bookmark_include_tag, getString(fieldName()), choice.displayName));
            exclude.setContentDescription(excluded ? getString(R.string.filter_picker_undo_exclude, choice.displayName)
                : getString(R.string.bookmark_exclude_tag, getString(fieldName()), choice.displayName));
            include.setOnClickListener(view -> changeSelection(choice, TagStatus.ACCEPTED));
            exclude.setOnClickListener(view -> changeSelection(choice, TagStatus.AVOIDED));
        }
    }
}
