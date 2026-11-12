package com.maxwai.nclientv3;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Filter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.RecyclerView;

import com.maxwai.nclientv3.adapters.HistoryAdapter;
import com.maxwai.nclientv3.api.InspectorV3;
import com.maxwai.nclientv3.api.components.Ranges;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.Language;
import com.maxwai.nclientv3.api.enums.SpecialTagIds;
import com.maxwai.nclientv3.api.enums.SortType;
import com.maxwai.nclientv3.api.enums.TagStatus;
import com.maxwai.nclientv3.api.enums.TagType;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.components.activities.GeneralActivity;
import com.maxwai.nclientv3.components.classes.Bookmark;
import com.maxwai.nclientv3.components.widgets.ChipTag;
import com.maxwai.nclientv3.components.widgets.CustomLinearLayoutManager;
import com.maxwai.nclientv3.settings.DefaultDialogs;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.settings.Login;
import com.maxwai.nclientv3.utility.LogUtility;
import com.maxwai.nclientv3.utility.BookmarkQuery;
import com.maxwai.nclientv3.utility.BookmarkFilters;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class SearchActivity extends GeneralActivity {
    public static final String EXTRA_BOOKMARK_URL = "com.maxwai.nclientv3.extra.EDIT_BOOKMARK_URL";
    public static final String EXTRA_CREATE_BOOKMARK = "com.maxwai.nclientv3.extra.CREATE_BOOKMARK";
    public static final int CUSTOM_ID_START = 100000000;
    private static int customId = CUSTOM_ID_START;
    private Ranges ranges = new Ranges();
    private final ArrayList<ChipTag> tags = new ArrayList<>();
    private final Chip[] addChip = new Chip[TagType.values.length];
    private ChipGroup[] groups;
    private SearchView searchView;
    private AppCompatAutoCompleteTextView autoComplete;
    private TagType loadedTag = null;
    private HistoryAdapter adapter;
    private boolean advanced = false;
    private Ranges.TimeUnit temporaryUnit;
    private InputMethodManager inputMethodManager;
    private AlertDialog alertDialog;
    private TagSuggestion selectedTagSuggestion;
    private boolean bookmarkMode;
    private boolean creatingBookmark;
    private Bookmark originalBookmark;
    private TextInputEditText bookmarkName;
    private String initialBookmarkQuery;

    public void setQuery(String str, boolean submit) {
        runOnUiThread(() -> searchView.setQuery(str, submit));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        creatingBookmark = getIntent().getBooleanExtra(EXTRA_CREATE_BOOKMARK, false);
        bookmarkMode = creatingBookmark || getIntent().hasExtra(EXTRA_BOOKMARK_URL);
        if (bookmarkMode && !creatingBookmark) {
            originalBookmark = Queries.BookmarkTable.getBookmark(getIntent().getStringExtra(EXTRA_BOOKMARK_URL));
            if (originalBookmark == null) {
                Toast.makeText(this, R.string.bookmark_unavailable, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        }
        //Global.initActivity(this);
        setContentView(bookmarkMode ? R.layout.activity_bookmark_editor : R.layout.activity_search);
        //init toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        assert getSupportActionBar() != null;
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(bookmarkMode);
        if (bookmarkMode) getSupportActionBar().setTitle(creatingBookmark ? R.string.bookmark_new : R.string.bookmark_edit);

        inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        //find IDs
        searchView = findViewById(R.id.search);
        RecyclerView recyclerView = findViewById(R.id.recycler);

        groups = new ChipGroup[]{
            null,
            findViewById(R.id.parody_group),
            findViewById(R.id.character_group),
            findViewById(R.id.tag_group),
            findViewById(R.id.artist_group),
            findViewById(R.id.group_group),
            findViewById(R.id.language_group),
            findViewById(R.id.category_group),
        };
        initRanges();
        if (!bookmarkMode) adapter = new HistoryAdapter(this);
        autoComplete = (AppCompatAutoCompleteTextView) getLayoutInflater().inflate(R.layout.autocomplete_entry, findViewById(R.id.appbar), false);
        autoComplete.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                selectedTagSuggestion = null;
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        autoComplete.setOnItemClickListener((parent, view, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            selectedTagSuggestion = item instanceof TagSuggestion ? (TagSuggestion) item : null;
        });
        autoComplete.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                if (createChip()) alertDialog.dismiss();
                return true;
            }
            return false;
        });

        //init recyclerview
        if (!bookmarkMode) {
            recyclerView.setLayoutManager(new CustomLinearLayoutManager(this));
            recyclerView.setAdapter(adapter);
            recyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        }


        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (bookmarkMode) {
                    saveBookmark();
                    return true;
                }
                query = query.trim();
                if (query.isEmpty() && !advanced) return true;
                if (!query.isEmpty()) adapter.addHistory(query);
                final Intent i = new Intent(SearchActivity.this, MainActivity.class);
                i.putExtra(getPackageName() + ".SEARCHMODE", true);
                i.putExtra(getPackageName() + ".QUERY", query);
                i.putExtra(getPackageName() + ".ADVANCED", advanced);
                if (advanced) {
                    ArrayList<Tag> tt = new ArrayList<>(tags.size());
                    for (ChipTag t : tags)
                        if (t.getTag().getStatus() != TagStatus.DEFAULT) tt.add(t.getTag());
                    i.putParcelableArrayListExtra(getPackageName() + ".TAGS", tt);
                    i.putExtra(getPackageName() + ".RANGES", ranges);
                }
                SearchActivity.this.runOnUiThread(() -> {
                    startActivity(i);
                    finish();
                });
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        populateGroup();
        if (bookmarkMode) initializeBookmarkEditor(savedInstanceState);
        else searchView.requestFocus();
    }

    private void initializeBookmarkEditor(Bundle savedState) {
        bookmarkName = findViewById(R.id.bookmark_name);
        bookmarkName.setText(creatingBookmark ? "" : originalBookmark.name);
        if (!creatingBookmark && originalBookmark.isFavorite()) {
            TextView scope = findViewById(R.id.bookmark_scope);
            scope.setText(getString(R.string.bookmark_scope_format, getString(R.string.bookmark_scope_favorites)));
            scope.setVisibility(View.VISIBLE);
        }
        String originalQuery = creatingBookmark ? "" : originalBookmark.getEditQuery();
        BookmarkFilters filters = BookmarkFilters.parse(originalQuery);
        searchView.setQuery(filters.keywords, false);
        ranges.setFromPage(filters.minPages);
        ranges.setToPage(filters.maxPages);
        ranges.setFromDate(filters.minAge);
        ranges.setToDate(filters.maxAge);
        ranges.setFromDateUnit(findTimeUnit(filters.minAgeUnit));
        ranges.setToDateUnit(findTimeUnit(filters.maxAgeUnit));
        for (BookmarkQuery.Token term : filters.tags) {
            TagType type = TagType.typeByName(term.namespace);
            Tag tag = Queries.TagTable.searchTag(term.value, type);
            if (tag == null) tag = new Tag(term.value, 0, customId++, type, TagStatus.DEFAULT);
            tag.setStatus(term.excluded ? TagStatus.AVOIDED : TagStatus.ACCEPTED);
            restoreBookmarkTag(tag);
        }
        initialBookmarkQuery = getBookmarkEditorQuery();
        TextView hint = findViewById(R.id.bookmark_complex_hint);
        hint.setVisibility(filters.rawExpression || originalQuery == null ? View.VISIBLE : View.GONE);
        if (originalQuery == null) hint.setText(R.string.bookmark_unavailable);
        if (savedState != null && savedState.containsKey("bookmarkDraftName")) {
            bookmarkName.setText(savedState.getString("bookmarkDraftName", ""));
            searchView.setQuery(savedState.getString("bookmarkDraftKeywords", ""), false);
            for (ChipTag chip : tags) chip.changeStatus(TagStatus.DEFAULT);
            ArrayList<Tag> savedTags = savedState.getParcelableArrayList("bookmarkDraftTags");
            if (savedTags != null) for (Tag tag : savedTags) restoreBookmarkTag(tag);
            Ranges savedRanges = savedState.getParcelable("bookmarkDraftRanges");
            if (savedRanges != null) ranges = savedRanges;
        }
        refreshBookmarkRangeLabels();
        findViewById(R.id.save_bookmark).setOnClickListener(v -> saveBookmark());
        searchView.setQueryHint(getString(R.string.bookmark_keywords));
        searchView.setSubmitButtonEnabled(false);
        bookmarkName.requestFocus();
    }

    private Ranges.TimeUnit findTimeUnit(String unit) {
        for (Ranges.TimeUnit candidate : Ranges.TimeUnit.values())
            if (unit.equals(String.valueOf(candidate.getVal()))) return candidate;
        return Ranges.UNDEFINED_DATE;
    }

    private void restoreBookmarkTag(Tag tag) {
        for (ChipTag chip : tags) {
            if (chip.getTag().getType().equals(tag.getType()) && chip.getTag().getName().equals(tag.getName())) {
                chip.changeStatus(tag.getStatus());
                return;
            }
        }
        Chip add = addChip[tag.getType().getId()];
        ChipGroup group = getGroup(tag.getType());
        if (add != null) group.removeView(add);
        addChipTag(tag, true, true);
        if (add != null) group.addView(add);
    }

    private void refreshBookmarkRangeLabels() {
        View page = findViewById(R.id.page_range), date = findViewById(R.id.upload_range);
        ((Button) page.findViewById(R.id.fromButton)).setText(ranges.getFromPage() == Ranges.UNDEFINED ? "" : String.valueOf(ranges.getFromPage()));
        ((Button) page.findViewById(R.id.toButton)).setText(ranges.getToPage() == Ranges.UNDEFINED ? "" : String.valueOf(ranges.getToPage()));
        ((Button) date.findViewById(R.id.fromButton)).setText(formatAge(ranges.getFromDate(), ranges.getFromDateUnit()));
        ((Button) date.findViewById(R.id.toButton)).setText(formatAge(ranges.getToDate(), ranges.getToDateUnit()));
    }

    private String formatAge(int value, Ranges.TimeUnit unit) {
        return value == Ranges.UNDEFINED || unit == null ? "" : value + " " + getString(unit.getString());
    }

    private ArrayList<Tag> getSelectedTags() {
        ArrayList<Tag> selected = new ArrayList<>();
        for (ChipTag chip : tags)
            if (chip.getTag().getStatus() != TagStatus.DEFAULT) selected.add(chip.getTag());
        return selected;
    }

    private String getBookmarkEditorQuery() {
        String keywords = searchView.getQuery().toString().trim();
        StringBuilder filters = new StringBuilder();
        List<String> selected = new ArrayList<>();
        for (Tag tag : getSelectedTags()) selected.add(tag.toQueryTag());
        // Suggested-tag ordering may change after metadata refresh or view recreation.
        Collections.sort(selected);
        for (String condition : selected) filters.append(condition).append(' ');
        filters.append(ranges.toQuery());
        String additions = filters.toString().trim();
        if (additions.isEmpty()) return keywords;
        if (keywords.isEmpty()) return additions;
        if (!BookmarkQuery.canEditAsTags(keywords)) keywords = "(" + keywords + ")";
        return keywords + " " + additions;
    }

    private void saveBookmark() {
        String name = bookmarkName.getText() == null ? "" : bookmarkName.getText().toString().trim();
        TextInputLayout input = findViewById(R.id.bookmark_name_layout);
        if (name.isEmpty()) {
            input.setError(getString(R.string.bookmark_name_required));
            bookmarkName.requestFocus();
            return;
        }
        input.setError(null);
        String query = getBookmarkEditorQuery();
        Bookmark updated;
        if (!creatingBookmark && query.equals(initialBookmarkQuery)) {
            updated = originalBookmark.withName(name);
        } else {
            if (!creatingBookmark && originalBookmark.getQuery() == null && query.isEmpty()) {
                Toast.makeText(this, R.string.bookmark_unavailable, Toast.LENGTH_LONG).show();
                return;
            }
            boolean favorite = !creatingBookmark && originalBookmark.isFavorite();
            InspectorV3 inspector = InspectorV3.bookmarkInspector(this, query, 1, SortType.RECENT_ALL_TIME, favorite, null);
            if (!favorite && !inspector.canAddBookmark()) {
                Toast.makeText(this, R.string.bookmark_requires_filters, Toast.LENGTH_LONG).show();
                return;
            }
            updated = Bookmark.fromInspector(inspector, name);
        }
        if (!Queries.BookmarkTable.saveBookmark(creatingBookmark ? null : originalBookmark.url, updated)) {
            Toast.makeText(this, R.string.bookmark_save_failed, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, R.string.bookmark_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        if (bookmarkMode && bookmarkName != null) {
            state.putString("bookmarkDraftName", bookmarkName.getText() == null ? "" : bookmarkName.getText().toString());
            state.putString("bookmarkDraftKeywords", searchView.getQuery().toString());
            state.putParcelableArrayList("bookmarkDraftTags", getSelectedTags());
            state.putParcelable("bookmarkDraftRanges", ranges);
        }
        super.onSaveInstanceState(state);
    }

    private void createPageBuilder(int title, int min, int max, int actual, DefaultDialogs.DialogResults results) {
        min = Math.max(1, min);
        actual = Math.max(actual, min);
        DefaultDialogs.pageChangerDialog(new DefaultDialogs.Builder(this)
            .setTitle(title)
            .setMax(max)
            .setMin(min)
            .setDrawable(R.drawable.ic_search)
            .setActual(actual)
            .setYesbtn(R.string.ok)
            .setNobtn(R.string.cancel)
            .setMaybebtn(R.string.reset)
            .setDialogs(results));
    }

    private void initRanges() {
        LinearLayout pageRangeLayout = findViewById(R.id.page_range);
        LinearLayout uploadRangeLayout = findViewById(R.id.upload_range);
        if (bookmarkMode) pageRangeLayout.setVisibility(View.GONE);
        ((TextView) pageRangeLayout.findViewById(R.id.title)).setText(R.string.page_range);
        ((TextView) uploadRangeLayout.findViewById(R.id.title)).setText(R.string.upload_time);
        Button fromPage = pageRangeLayout.findViewById(R.id.fromButton);
        Button toPage = pageRangeLayout.findViewById(R.id.toButton);
        Button fromDate = uploadRangeLayout.findViewById(R.id.fromButton);
        Button toDate = uploadRangeLayout.findViewById(R.id.toButton);
        fromPage.setOnClickListener(v -> createPageBuilder(R.string.from_page, 0, 2000, ranges.getFromPage(), new DefaultDialogs.CustomDialogResults() {
            @Override
            public void positive(int actual) {
                ranges.setFromPage(actual);
                fromPage.setText(String.format(Locale.US, "%d", actual));
                if (ranges.getFromPage() > ranges.getToPage()) {
                    ranges.setToPage(Ranges.UNDEFINED);
                    toPage.setText("");
                }
                advanced = true;
            }

            @Override
            public void neutral() {
                ranges.setFromPage(Ranges.UNDEFINED);
                fromPage.setText("");
            }
        }));
        toPage.setOnClickListener(v -> createPageBuilder(R.string.to_page, ranges.getFromPage(), 2000, ranges.getToPage(), new DefaultDialogs.CustomDialogResults() {
            @Override
            public void positive(int actual) {
                ranges.setToPage(actual);
                toPage.setText(String.format(Locale.US, "%d", actual));
                advanced = true;
            }

            @Override
            public void neutral() {
                ranges.setToPage(Ranges.UNDEFINED);
                toPage.setText("");
            }
        }));
        fromDate.setOnClickListener(v -> showUnitDialog(fromDate, true));
        toDate.setOnClickListener(v -> showUnitDialog(toDate, false));
    }

    private void showUnitDialog(Button button, boolean from) {
        int i = 0;
        String[] strings = new String[Ranges.TimeUnit.values().length];
        for (Ranges.TimeUnit unit : Ranges.TimeUnit.values())
            strings[i++] = getString(unit.getString());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.choose_unit);
        builder.setIcon(R.drawable.ic_search);
        builder.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, strings), (dialog, which) -> {
            temporaryUnit = Ranges.TimeUnit.values()[which];
            createPageBuilder(from ? R.string.from_time : R.string.to_time, 1, temporaryUnit == Ranges.TimeUnit.YEAR ? 10 : 100, 1, new DefaultDialogs.CustomDialogResults() {
                @Override
                public void positive(int actual) {
                    if (from) {
                        ranges.setFromDateUnit(temporaryUnit);
                        ranges.setFromDate(actual);
                    } else {
                        ranges.setToDateUnit(temporaryUnit);
                        ranges.setToDate(actual);
                    }
                    button.setText(formatAge(actual, temporaryUnit));
                    advanced = true;
                }

                @Override
                public void neutral() {
                    if (from) {
                        ranges.setFromDateUnit(Ranges.UNDEFINED_DATE);
                        ranges.setFromDate(Ranges.UNDEFINED);
                    } else {
                        ranges.setToDateUnit(Ranges.UNDEFINED_DATE);
                        ranges.setToDate(Ranges.UNDEFINED);
                    }
                    button.setText("");
                }
            });
        });
        builder.setNeutralButton(R.string.reset, (dialog, which) -> {
            if (from) {
                ranges.setFromDateUnit(Ranges.UNDEFINED_DATE);
                ranges.setFromDate(Ranges.UNDEFINED);
            } else {
                ranges.setToDateUnit(Ranges.UNDEFINED_DATE);
                ranges.setToDate(Ranges.UNDEFINED);
            }
            button.setText("");
        });
        builder.show();
    }

    private void populateGroup() {
        // Entity fields start with actual selections only; content-tag suggestions are unchanged.
        for (Tag t : Queries.TagTable.getTopTags(TagType.TAG, Global.getFavoriteLimit(this))) {
            if (bookmarkMode) t.setStatus(TagStatus.DEFAULT);
            addChipTag(t, true, true);
        }
        //add already filtered tags
        if (!bookmarkMode) for (Tag t : Queries.TagTable.getAllFiltered()) {
            // Fixed choices below already include their saved states and must not become removable duplicates.
            if (!TagType.CATEGORY.equals(t.getType()) && !TagType.LANGUAGE.equals(t.getType()))
                addChipTag(t, true, true);
        }
        //add categories
        for (Tag t : Queries.TagTable.getTrueAllType(TagType.CATEGORY)) {
            if (bookmarkMode) t.setStatus(TagStatus.DEFAULT);
            addChipTag(t, false, bookmarkMode);
        }
        //add languages
        for (Tag t : Queries.TagTable.getTrueAllType(TagType.LANGUAGE)) {
            if (bookmarkMode) t.setStatus(TagStatus.DEFAULT);
            else if (t.getId() == SpecialTagIds.LANGUAGE_ENGLISH && Global.getOnlyLanguage() == Language.ENGLISH)
                t.setStatus(TagStatus.ACCEPTED);
            else if (t.getId() == SpecialTagIds.LANGUAGE_JAPANESE && Global.getOnlyLanguage() == Language.JAPANESE)
                t.setStatus(TagStatus.ACCEPTED);
            else if (t.getId() == SpecialTagIds.LANGUAGE_CHINESE && Global.getOnlyLanguage() == Language.CHINESE)
                t.setStatus(TagStatus.ACCEPTED);
            addChipTag(t, false, bookmarkMode);
        }
        //add online tags
        if (!bookmarkMode && Login.useAccountTag()) for (Tag t : Queries.TagTable.getAllOnlineBlacklisted())
            if (!tagAlreadyExist(t))
                addChipTag(t, true, true);
        //add + button
        for (TagType type : TagType.values) {
            //ignore these tags
            if (type == TagType.UNKNOWN || type == TagType.LANGUAGE || type == TagType.CATEGORY) {
                addChip[type.getId()] = null;
                continue;
            }
            ChipGroup cg = getGroup(type);
            Chip add = createAddChip(type, cg);
            addChip[type.getId()] = add;
            cg.addView(add);
        }
    }

    private Chip createAddChip(TagType type, ChipGroup group) {
        Chip c = (Chip) getLayoutInflater().inflate(R.layout.chip_layout, group, false);
        c.setCloseIconVisible(false);
        c.setChipIconResource(R.drawable.ic_add);
        c.setText(getString(R.string.add));
        c.setOnClickListener(v -> loadTag(type));
        Global.setTint(this, c.getChipIcon());
        return c;
    }

    private boolean tagAlreadyExist(Tag tag) {
        for (ChipTag t : tags) {
            if (t.getTag().getName().equals(tag.getName()) && t.getTag().getType().equals(tag.getType())) return true;
        }
        return false;
    }

    private void addChipTag(Tag t, boolean close, boolean canBeAvoided) {
        for (ChipTag existing : tags) {
            Tag original = existing.getTag();
            if (original.getType().equals(t.getType()) && original.getName().equals(t.getName())) {
                // Different IDs can represent the same query value; retain an existing active preference.
                if (original.getStatus() == TagStatus.DEFAULT && t.getStatus() != TagStatus.DEFAULT)
                    existing.changeStatus(t.getStatus());
                return;
            }
        }
        ChipGroup cg = getGroup(t.getType());
        ChipTag c = (ChipTag) getLayoutInflater().inflate(R.layout.chip_layout_entry, cg, false);
        c.init(t, close, canBeAvoided);
        c.setOnCloseIconClickListener(v -> {
            cg.removeView(c);
            tags.remove(c);
            advanced = true;
        });
        c.setOnClickListener(v -> {
            c.updateStatus();
            advanced = true;
        });
        cg.addView(c);
        tags.add(c);
    }

    private void loadDropdown(TagType type) {
        List<Tag> allTags = Queries.TagTable.getAllTagOfType(type);
        if (TagType.TAG.equals(type)) {
            loadContentTagDropdown(allTags);
            loadedTag = type;
            return;
        }
        String[] tagNames = new String[allTags.size()];
        int i = 0;
        for (Tag t : allTags) tagNames[i++] = t.getName();
        autoComplete.setAdapter(new ArrayAdapter<>(SearchActivity.this, android.R.layout.simple_dropdown_item_1line, tagNames));
        loadedTag = type;
    }

    private void loadContentTagDropdown(List<Tag> allTags) {
        List<TagSuggestion> suggestions = new ArrayList<>(allTags.size());
        for (Tag tag : allTags) suggestions.add(new TagSuggestion(this, tag));
        autoComplete.setAdapter(createSuggestionAdapter(suggestions));
    }

    private ArrayAdapter<TagSuggestion> createSuggestionAdapter(List<TagSuggestion> suggestions) {
        return new ArrayAdapter<TagSuggestion>(this,
            android.R.layout.simple_dropdown_item_1line, new ArrayList<>(suggestions)) {
            private final Filter suggestionFilter = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    String query = constraint == null ? "" : constraint.toString().trim().toLowerCase(Locale.ROOT);
                    List<TagSuggestion> matches = new ArrayList<>();
                    for (TagSuggestion suggestion : suggestions) {
                        if (suggestion.matches(query)) matches.add(suggestion);
                    }
                    FilterResults results = new FilterResults();
                    results.values = matches.toArray(new TagSuggestion[0]);
                    results.count = matches.size();
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    setNotifyOnChange(false);
                    clear();
                    for (TagSuggestion suggestion : (TagSuggestion[]) results.values) add(suggestion);
                    notifyDataSetChanged();
                }
            };

            @NonNull
            @Override
            public Filter getFilter() {
                return suggestionFilter;
            }
        };
    }

    private static final class TagSuggestion {
        private final Tag tag;
        private final String displayName;
        private final String searchName, searchDisplay;

        private TagSuggestion(Context context, Tag tag) {
            this.tag = tag;
            displayName = tag.getDisplayName(context);
            searchName = tag.getName().toLowerCase(Locale.ROOT);
            searchDisplay = displayName.toLowerCase(Locale.ROOT);
        }

        private boolean matches(String query) {
            return searchName.contains(query) || searchDisplay.contains(query);
        }

        @NonNull
        @Override
        public String toString() {
            return displayName;
        }
    }

    private void loadTag(TagType type) {
        if (isEntityField(type)) {
            showEntityPicker(type);
            return;
        }
        if (type != loadedTag) loadDropdown(type);
        addDialog();
        autoComplete.requestFocus();
        inputMethodManager.showSoftInput(autoComplete, InputMethodManager.SHOW_IMPLICIT);
    }

    private static boolean isEntityField(TagType type) {
        return TagType.PARODY.equals(type) || TagType.CHARACTER.equals(type)
            || TagType.ARTIST.equals(type) || TagType.GROUP.equals(type);
    }

    private List<TagSuggestion> loadEntityChoices(TagType type) {
        List<TagSuggestion> choices = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Tag tag : Queries.TagTable.getTrueAllType(type)) {
            if (type.equals(tag.getType()) && !tag.getName().trim().isEmpty() && names.add(tag.getName()))
                choices.add(new TagSuggestion(this, tag));
        }
        choices.sort((left, right) -> {
            int display = left.displayName.compareToIgnoreCase(right.displayName);
            return display != 0 ? display : left.tag.getName().compareTo(right.tag.getName());
        });
        return choices;
    }

    private void showEntityPicker(TagType type) {
        View view = getLayoutInflater().inflate(R.layout.tag_choice_dialog, null);
        TextInputLayout field = view.findViewById(R.id.tag_choice_layout);
        MaterialAutoCompleteTextView input = view.findViewById(R.id.tag_choice);
        int title = TagType.PARODY.equals(type) ? R.string.parodies : TagType.CHARACTER.equals(type)
            ? R.string.characters : TagType.ARTIST.equals(type) ? R.string.artists : R.string.groups;
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.select_existing_tag, getString(title)))
            .setView(view).setPositiveButton(R.string.add, null)
            .setNegativeButton(R.string.cancel, null).create();
        TagSuggestion[] selected = new TagSuggestion[1];
        field.setEnabled(false);
        field.setHelperText(getString(R.string.tag_choices_loading));
        Runnable addSelection = () -> {
            TagSuggestion choice = selected[0];
            if (choice == null || !type.equals(choice.tag.getType())
                || !choice.toString().contentEquals(input.getText()) || !addExistingEntityTag(choice.tag, type)) {
                field.setError(getString(R.string.select_existing_tag_error));
                return;
            }
            inputMethodManager.hideSoftInputFromWindow(input.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            dialog.dismiss();
        };
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                selected[0] = null;
                field.setError(null);
                if (dialog.isShowing()) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            }
            @Override public void afterTextChanged(Editable text) {
                if (text.length() == 0 && input.getAdapter() != null)
                    ((ArrayAdapter<?>) input.getAdapter()).getFilter().filter("", count -> {
                        if (dialog.isShowing() && input.hasFocus()) input.showDropDown();
                    });
            }
        });
        input.setOnItemClickListener((parent, itemView, position, id) -> {
            Object item = parent.getItemAtPosition(position);
            selected[0] = item instanceof TagSuggestion ? (TagSuggestion) item : null;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(selected[0] != null);
        });
        input.setOnEditorActionListener((textView, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_SEND) {
                addSelection.run();
                return true;
            }
            return false;
        });
        dialog.setOnShowListener(ignored -> {
            Button add = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            add.setEnabled(false);
            add.setOnClickListener(button -> addSelection.run());
        });
        dialog.show();
        CompletableFuture<List<TagSuggestion>> loading = CompletableFuture.supplyAsync(() -> loadEntityChoices(type));
        dialog.setOnDismissListener(ignored -> loading.cancel(true));
        loading.whenComplete((choices, error) -> runOnUiThread(() -> {
            if (!dialog.isShowing() || isFinishing() || isDestroyed()) return;
            if (error != null) {
                LogUtility.w("Unable to load existing tags", error);
                field.setHelperText(getString(R.string.tag_choices_error));
                return;
            }
            input.setAdapter(createSuggestionAdapter(choices));
            field.setEnabled(true);
            field.setHelperText(getString(choices.isEmpty() ? R.string.tag_choices_empty : R.string.select_existing_tag_hint));
            input.requestFocus();
            input.post(() -> {
                if (dialog.isShowing()) input.showDropDown();
            });
        }));
    }

    private boolean addExistingEntityTag(Tag candidate, TagType type) {
        if (!isEntityField(type) || candidate == null || !type.equals(candidate.getType())) return false;
        Tag existing = Queries.TagTable.getTagById(candidate.getId());
        if (existing == null || !type.equals(existing.getType()) || !existing.getName().equals(candidate.getName())
            || existing.getName().trim().isEmpty() || existing.getName().contains("\"") || existing.getName().contains("\\")) return false;
        Tag tag = new Tag(existing.getName(), existing.getCount(), existing.getId(), type, TagStatus.ACCEPTED);
        ChipGroup group = getGroup(type);
        Chip add = addChip[type.getId()];
        if (add != null) group.removeView(add);
        addChipTag(tag, true, true);
        if (add != null) group.addView(add);
        advanced = true;
        return true;
    }

    private ChipGroup getGroup(TagType type) {
        return groups[type.getId()];
    }

    private void addDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setView(autoComplete);
        autoComplete.setText("");
        autoComplete.setError(null);
        builder.setPositiveButton(R.string.ok, null);
        builder.setCancelable(true).setNegativeButton(R.string.cancel, null);
        builder.setTitle(R.string.insert_tag_name);
        try {
            alertDialog = builder.show();
        } catch (IllegalStateException e) {//the autoComplete is still attached to another View
            ((ViewGroup) autoComplete.getParent()).removeView(autoComplete);
            alertDialog = builder.show();
        }
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (createChip()) alertDialog.dismiss();
        });
    }

    private boolean createChip() {
        if (isEntityField(loadedTag)) {
            autoComplete.setError(getString(R.string.select_existing_tag_error));
            return false;
        }
        String name = autoComplete.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (name.isEmpty() || name.contains("\"") || name.contains("\\")) {
            autoComplete.setError(getString(R.string.invalid_tag_name));
            return false;
        }
        Tag tag = selectedTagSuggestion != null && TagType.TAG.equals(loadedTag)
            && selectedTagSuggestion.toString().contentEquals(autoComplete.getText())
            ? selectedTagSuggestion.tag : Queries.TagTable.searchTag(name, loadedTag);
        if (tag == null) tag = new Tag(name, 0, customId++, loadedTag, TagStatus.ACCEPTED);
        if (tag.getName().contains("\"") || tag.getName().contains("\\")) {
            autoComplete.setError(getString(R.string.invalid_tag_name));
            return false;
        }
        autoComplete.setError(null);
        if (bookmarkMode) tag.setStatus(TagStatus.ACCEPTED);
        LogUtility.d("CREATED WITH ID: " + tag.getId());
        if (tagAlreadyExist(tag)) {
            if (!bookmarkMode) return true;
            restoreBookmarkTag(tag);
        } else {
            if (getGroup(loadedTag) != null) getGroup(loadedTag).removeView(addChip[loadedTag.getId()]);
            addChipTag(tag, true, true);
            getGroup(loadedTag).addView(addChip[loadedTag.getId()]);
        }

        inputMethodManager.hideSoftInputFromWindow(searchView.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        autoComplete.setText("");
        advanced = true;
        if (autoComplete.getParent() != null)
            ((ViewGroup) autoComplete.getParent()).removeView(autoComplete);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


}
