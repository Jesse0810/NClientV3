package com.maxwai.nclientv3;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Filter;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
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
import com.maxwai.nclientv3.utility.BookmarkQuery;
import com.maxwai.nclientv3.utility.BookmarkFilters;
import com.maxwai.nclientv3.utility.ContentTagAliases;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public class SearchActivity extends GeneralActivity {
    public static final String EXTRA_BOOKMARK_URL = "com.maxwai.nclientv3.extra.EDIT_BOOKMARK_URL";
    public static final String EXTRA_CREATE_BOOKMARK = "com.maxwai.nclientv3.extra.CREATE_BOOKMARK";
    public static final int CUSTOM_ID_START = 100000000;
    private static final int[] FIELD_NAMES = {0, R.string.parodies, R.string.characters, R.string.tags,
        R.string.artists, R.string.groups, R.string.languages, R.string.categories};
    private static final int[] FIELD_PICKERS = {0, R.id.parody_picker, R.id.character_picker, R.id.tag_picker,
        R.id.artist_picker, R.id.group_picker, 0, 0};
    private static final int[] UPLOAD_PRESET_NAMES = {R.string.advanced_filter_any, R.string.advanced_filter_recent_24_hours,
        R.string.advanced_filter_recent_7_days, R.string.advanced_filter_recent_30_days, R.string.advanced_filter_recent_year,
        R.string.advanced_filter_older_year, R.string.advanced_filter_custom};
    private static final int CUSTOM_UPLOAD = UPLOAD_PRESET_NAMES.length - 1;
    private static final int OLDER_YEAR = CUSTOM_UPLOAD - 1;
    private static final int[] RECENT_UPLOAD_LIMITS = {24, 7, 30, 1};
    private static final Ranges.TimeUnit[] RECENT_UPLOAD_UNITS = {Ranges.TimeUnit.HOUR, Ranges.TimeUnit.DAY,
        Ranges.TimeUnit.DAY, Ranges.TimeUnit.YEAR};
    private static int customId = CUSTOM_ID_START;
    private Ranges ranges = new Ranges();
    private final ArrayList<ChipTag> tags = new ArrayList<>();
    private final Chip[] anyChips = new Chip[TagType.values.length];
    private final Chip[] uploadPresets = new Chip[UPLOAD_PRESET_NAMES.length];
    private ChipGroup[] groups;
    private SearchView searchView;
    private HistoryAdapter adapter;
    private boolean advanced = false;
    private Ranges.TimeUnit temporaryUnit;
    private InputMethodManager inputMethodManager;
    private boolean bookmarkMode;
    private boolean creatingBookmark;
    private boolean showCustomUpload, updatingFavorites;
    private Bookmark originalBookmark;
    private TextInputEditText bookmarkName;
    private TextInputEditText minimumFavorites;
    private String initialBookmarkQuery;
    private final ActivityResultLauncher<Intent> filterPicker = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            Intent data = result.getData();
            if (result.getResultCode() != Activity.RESULT_OK || data == null) return;
            TagType type = FilterPickerActivity.getType(data);
            ArrayList<Tag> selected = FilterPickerActivity.getSelection(data);
            if (type == null || TagType.UNKNOWN.equals(type) || selected == null || groups == null) return;
            Map<String, TagStatus> before = fieldStatuses(type, getSelectedTags());
            Map<String, TagStatus> after = fieldStatuses(type, selected);
            replaceFieldTags(type, selected);
            if (!before.equals(after)) advanced = true;
            refreshFilterSummary();
        });

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
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        getSupportActionBar().setTitle(!bookmarkMode ? R.string.search
            : creatingBookmark ? R.string.bookmark_new : R.string.bookmark_edit);

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
                submitSearch(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });

        populateGroup();
        if (bookmarkMode) initializeBookmarkEditor(savedInstanceState);
        else {
            // SearchView's built-in submit button ignores an empty keyword. Submit filters directly.
            findViewById(R.id.submit_search).setOnClickListener(v -> submitSearch(searchView.getQuery().toString()));
            if (!restoreDraft(savedInstanceState)) {
                refreshRangeLabels();
                searchView.requestFocus();
            }
        }
        refreshFilterSummary();
    }

    private void submitSearch(String query) {
        if (!validateMinimumFavorites(true)) return;
        query = query.trim();
        if (!query.isEmpty()) adapter.addHistory(query);
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(getPackageName() + ".SEARCHMODE", true);
        intent.putExtra(getPackageName() + ".QUERY", query);
        intent.putExtra(getPackageName() + ".ADVANCED", advanced);
        if (advanced) {
            intent.putParcelableArrayListExtra(getPackageName() + ".TAGS", getSelectedTags());
            intent.putExtra(getPackageName() + ".RANGES", ranges);
        }
        inputMethodManager.hideSoftInputFromWindow(searchView.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        startActivity(intent);
        finish();
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
        ranges.setMinFavorites(filters.minFavorites);
        for (BookmarkQuery.Token term : filters.tags) {
            TagType type = TagType.typeByName(term.namespace);
            Tag tag = Queries.TagTable.searchTag(term.value, type);
            if (tag == null) tag = new Tag(term.value, -1, customId++, type, TagStatus.DEFAULT);
            tag.setStatus(term.excluded ? TagStatus.AVOIDED : TagStatus.ACCEPTED);
            addFilterTag(tag);
        }
        initialBookmarkQuery = getBookmarkEditorQuery();
        TextView hint = findViewById(R.id.bookmark_complex_hint);
        hint.setVisibility(filters.rawExpression || originalQuery == null ? View.VISIBLE : View.GONE);
        if (originalQuery == null) hint.setText(R.string.bookmark_unavailable);
        boolean restored = restoreDraft(savedState);
        if (!restored) refreshRangeLabels();
        findViewById(R.id.save_bookmark).setOnClickListener(v -> saveBookmark());
        searchView.setQueryHint(getString(R.string.bookmark_keywords));
        searchView.setSubmitButtonEnabled(false);
        if (!restored) bookmarkName.requestFocus();
    }

    private Ranges.TimeUnit findTimeUnit(String unit) {
        for (Ranges.TimeUnit candidate : Ranges.TimeUnit.values())
            if (unit.equals(String.valueOf(candidate.getVal()))) return candidate;
        return Ranges.UNDEFINED_DATE;
    }

    private boolean restoreDraft(Bundle state) {
        if (state == null || !state.containsKey("filterDraftTags")) return false;
        searchView.setQuery(state.getString("filterDraftKeywords", ""), false);
        if (bookmarkName != null) bookmarkName.setText(state.getString("bookmarkDraftName", ""));
        ArrayList<Tag> selected = state.getParcelableArrayList("filterDraftTags");
        if (selected != null) {
            for (TagType type : TagType.values)
                if (!TagType.UNKNOWN.equals(type)) replaceFieldTags(type, selected);
        }
        Ranges savedRanges = state.getParcelable("filterDraftRanges");
        if (savedRanges != null) ranges = savedRanges;
        advanced = state.getBoolean("filterDraftAdvanced");
        showCustomUpload = state.getBoolean("filterDraftCustomUpload");
        refreshRangeLabels();
        String favoriteDraft = state.getString("filterDraftFavoriteText");
        if (favoriteDraft != null) setMinimumFavoritesText(favoriteDraft);
        validateMinimumFavorites(false);
        return true;
    }

    private void refreshRangeLabels() {
        refreshRangeButtons();
        setMinimumFavoritesText(ranges.getMinFavorites() == Ranges.UNDEFINED ? "" : String.valueOf(ranges.getMinFavorites()));
        refreshUploadPresets();
        refreshFilterSummary();
    }

    private void refreshRangeButtons() {
        View page = findViewById(R.id.page_range), date = findViewById(R.id.upload_range);
        setRangeLabel(page, R.id.fromButton, R.string.advanced_filter_minimum,
            ranges.getFromPage() == Ranges.UNDEFINED ? "" : String.valueOf(ranges.getFromPage()));
        setRangeLabel(page, R.id.toButton, R.string.advanced_filter_maximum,
            ranges.getToPage() == Ranges.UNDEFINED ? "" : String.valueOf(ranges.getToPage()));
        setRangeLabel(date, R.id.fromButton, R.string.advanced_filter_minimum, formatAge(ranges.getFromDate(), ranges.getFromDateUnit()));
        setRangeLabel(date, R.id.toButton, R.string.advanced_filter_maximum, formatAge(ranges.getToDate(), ranges.getToDateUnit()));
    }

    private void setRangeLabel(View range, int button, int placeholder, String value) {
        Button view = range.findViewById(button);
        view.setText(value.isEmpty() ? getString(placeholder) : value);
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
        if (!validateMinimumFavorites(true)) return;
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
        if (bookmarkName != null)
            state.putString("bookmarkDraftName", bookmarkName.getText() == null ? "" : bookmarkName.getText().toString());
        if (searchView != null) {
            state.putString("filterDraftKeywords", searchView.getQuery().toString());
            state.putParcelableArrayList("filterDraftTags", getSelectedTags());
            state.putParcelable("filterDraftRanges", ranges);
            state.putBoolean("filterDraftAdvanced", advanced);
            state.putBoolean("filterDraftCustomUpload", showCustomUpload);
            state.putString("filterDraftFavoriteText", minimumFavorites.getText().toString());
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
        uploadRangeLayout.findViewById(R.id.title).setVisibility(View.GONE);
        Button fromPage = pageRangeLayout.findViewById(R.id.fromButton);
        Button toPage = pageRangeLayout.findViewById(R.id.toButton);
        Button fromDate = uploadRangeLayout.findViewById(R.id.fromButton);
        Button toDate = uploadRangeLayout.findViewById(R.id.toButton);
        fromPage.setOnClickListener(v -> createPageBuilder(R.string.from_page, 0, 2000, ranges.getFromPage(), new DefaultDialogs.CustomDialogResults() {
            @Override
            public void positive(int actual) {
                ranges.setFromPage(actual);
                if (ranges.getFromPage() > ranges.getToPage()) ranges.setToPage(Ranges.UNDEFINED);
                onRangeChanged();
            }

            @Override
            public void neutral() {
                ranges.setFromPage(Ranges.UNDEFINED);
                onRangeChanged();
            }
        }));
        toPage.setOnClickListener(v -> createPageBuilder(R.string.to_page, ranges.getFromPage(), 2000, ranges.getToPage(), new DefaultDialogs.CustomDialogResults() {
            @Override
            public void positive(int actual) {
                ranges.setToPage(actual);
                onRangeChanged();
            }

            @Override
            public void neutral() {
                ranges.setToPage(Ranges.UNDEFINED);
                onRangeChanged();
            }
        }));
        fromDate.setOnClickListener(v -> showUnitDialog(true));
        toDate.setOnClickListener(v -> showUnitDialog(false));

        ChipGroup presets = findViewById(R.id.upload_presets);
        for (int index = 0; index < UPLOAD_PRESET_NAMES.length; index++) {
            int preset = index;
            Chip chip = createChoiceChip(presets, UPLOAD_PRESET_NAMES[index]);
            chip.setOnClickListener(view -> selectUploadPreset(preset));
            presets.addView(chip);
            uploadPresets[index] = chip;
        }
        minimumFavorites = findViewById(R.id.filter_min_favorites);
        // The draft saves the exact input, including an invalid value still being edited.
        minimumFavorites.setSaveEnabled(false);
        minimumFavorites.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                if (updatingFavorites) return;
                int previous = ranges.getMinFavorites();
                if (validateMinimumFavorites(false) && ranges.getMinFavorites() != previous) advanced = true;
                refreshFilterSummary();
            }
            @Override public void afterTextChanged(Editable text) {}
        });
    }

    private void showUnitDialog(boolean from) {
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
                    onRangeChanged();
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
                    onRangeChanged();
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
            onRangeChanged();
        });
        builder.show();
    }

    private void onRangeChanged() {
        advanced = true;
        refreshRangeButtons();
        refreshUploadPresets();
        refreshFilterSummary();
    }

    private void setMinimumFavoritesText(String value) {
        updatingFavorites = true;
        minimumFavorites.setText(value);
        updatingFavorites = false;
    }

    private boolean validateMinimumFavorites(boolean focusOnError) {
        TextInputLayout field = findViewById(R.id.filter_min_favorites_layout);
        CharSequence value = minimumFavorites.getText();
        String text = value == null ? "" : value.toString().trim();
        if (text.isEmpty()) {
            field.setError(null);
            ranges.setMinFavorites(Ranges.UNDEFINED);
            return true;
        }
        try {
            int valueAsNumber = Integer.parseInt(text);
            if (valueAsNumber > 0) {
                field.setError(null);
                ranges.setMinFavorites(valueAsNumber);
                return true;
            }
        } catch (NumberFormatException ignored) {
            // Leave the draft intact so an invalid number can be corrected.
        }
        ranges.setMinFavorites(Ranges.UNDEFINED);
        field.setError(getString(R.string.advanced_filter_invalid_number));
        if (focusOnError) minimumFavorites.requestFocus();
        return false;
    }

    private void selectUploadPreset(int preset) {
        if (preset == CUSTOM_UPLOAD) {
            showCustomUpload = true;
            refreshUploadPresets();
            return;
        }
        showCustomUpload = false;
        ranges.setFromDate(Ranges.UNDEFINED);
        ranges.setToDate(Ranges.UNDEFINED);
        ranges.setFromDateUnit(Ranges.UNDEFINED_DATE);
        ranges.setToDateUnit(Ranges.UNDEFINED_DATE);
        if (preset == OLDER_YEAR) {
            ranges.setFromDate(1);
            ranges.setFromDateUnit(Ranges.TimeUnit.YEAR);
        } else if (preset > 0) {
            ranges.setToDate(RECENT_UPLOAD_LIMITS[preset - 1]);
            ranges.setToDateUnit(RECENT_UPLOAD_UNITS[preset - 1]);
        }
        onRangeChanged();
    }

    private int currentUploadPreset() {
        if (showCustomUpload) return CUSTOM_UPLOAD;
        if (ranges.getFromDate() == Ranges.UNDEFINED) {
            if (ranges.getToDate() == Ranges.UNDEFINED) return 0;
            for (int i = 0; i < RECENT_UPLOAD_LIMITS.length; i++)
                if (ranges.getToDate() == RECENT_UPLOAD_LIMITS[i] && ranges.getToDateUnit() == RECENT_UPLOAD_UNITS[i])
                    return i + 1;
        } else if (ranges.getToDate() == Ranges.UNDEFINED && ranges.getFromDate() == 1
            && ranges.getFromDateUnit() == Ranges.TimeUnit.YEAR) return OLDER_YEAR;
        return CUSTOM_UPLOAD;
    }

    private void refreshUploadPresets() {
        int active = currentUploadPreset();
        findViewById(R.id.upload_range).setVisibility(active == CUSTOM_UPLOAD ? View.VISIBLE : View.GONE);
        for (int index = 0; index < uploadPresets.length; index++)
            stylePresetChip(uploadPresets[index], index == active);
    }

    private void populateGroup() {
        for (TagType type : TagType.values) {
            int pickerId = FIELD_PICKERS[type.getId()];
            if (pickerId != 0) findViewById(pickerId).setOnClickListener(view -> openFilterPicker(type));
        }
        populateInlineField(TagType.LANGUAGE);
        populateInlineField(TagType.CATEGORY);
        if (!bookmarkMode) {
            for (Tag tag : Queries.TagTable.mergeContentTags(Queries.TagTable.getAllFiltered()))
                if (tag.getStatus() != TagStatus.AVOIDED || Global.removeAvoidedGalleries()) addFilterTag(tag);
            Tag language = getDefaultSearchLanguage();
            if (language != null) addFilterTag(language);
            if (Login.useAccountTag()) {
                for (Tag tag : Queries.TagTable.mergeContentTags(Queries.TagTable.getAllOnlineBlacklisted())) {
                    if (findFilterTag(tag) == null) {
                        tag.setStatus(TagStatus.AVOIDED);
                        addFilterTag(tag);
                    }
                }
            }
        } else if (creatingBookmark) {
            // Only blank bookmark creation supplies a default. Edits and restored drafts
            // must keep the user's language selections, including an empty language field.
            addFilterTag(includedLanguage(SpecialTagIds.LANGUAGE_CHINESE, "chinese"));
        }
    }

    private void populateInlineField(TagType type) {
        ChipGroup group = getGroup(type);
        Chip any = createChoiceChip(group, R.string.advanced_filter_any);
        any.setOnClickListener(view -> {
            if (fieldStatuses(type, getSelectedTags()).isEmpty()) return;
            replaceFieldTags(type, Collections.emptyList());
            advanced = true;
            refreshFilterSummary();
        });
        anyChips[type.getId()] = any;
        group.addView(any);
        Map<String, Tag> unique = new LinkedHashMap<>();
        for (Tag tag : Queries.TagTable.getTrueAllType(type)) {
            if (!isSafeNewValue(tag.getName())) continue;
            unique.merge(tag.getName(), tag, (first, second) -> new Tag(first.getName(),
                Math.max(first.getCount(), second.getCount()), Math.min(first.getId(), second.getId()), type, TagStatus.DEFAULT));
        }
        List<Tag> choices = new ArrayList<>(unique.values());
        choices.sort(Comparator.comparingInt((Tag tag) -> ChipTag.hasKnownCount(tag) ? tag.getCount() : -1).reversed()
            .thenComparing(tag -> tag.getFilterDisplayName(this), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(Tag::getName));
        for (Tag tag : choices)
            addFilterTag(new Tag(tag.getName(), tag.getCount(), tag.getId(), type, TagStatus.DEFAULT));
    }

    private static boolean isSafeNewValue(String name) {
        return name != null && !name.trim().isEmpty() && name.indexOf('"') < 0 && name.indexOf('\\') < 0;
    }

    private static boolean isInlineField(TagType type) {
        return TagType.LANGUAGE.equals(type) || TagType.CATEGORY.equals(type);
    }

    private Chip createChoiceChip(ChipGroup group, int label) {
        Chip chip = (Chip) getLayoutInflater().inflate(R.layout.chip_layout, group, false);
        chip.setCloseIconVisible(false);
        chip.setChipIconVisible(false);
        chip.setText(label);
        return chip;
    }

    private void stylePresetChip(Chip chip, boolean active) {
        chip.setSelected(active);
        int background = ContextCompat.getColor(this, active ? R.color.bookmark_include_background : R.color.bookmark_neutral_background);
        int foreground = ContextCompat.getColor(this, active ? R.color.bookmark_include_foreground : R.color.bookmark_neutral_foreground);
        chip.setChipBackgroundColor(ColorStateList.valueOf(background));
        chip.setTextColor(foreground);
    }

    private void refreshFilterSummary() {
        int[] counts = new int[TagType.values.length];
        for (ChipTag chip : tags) {
            Tag tag = chip.getTag();
            if (tag.getStatus() != TagStatus.DEFAULT) counts[tag.getType().getId()]++;
        }
        int total = 0;
        for (TagType type : TagType.values) {
            int index = type.getId();
            total += counts[index];
            int pickerId = FIELD_PICKERS[index];
            if (pickerId != 0) {
                MaterialButton picker = findViewById(pickerId);
                String field = getString(FIELD_NAMES[index]);
                picker.setText(counts[index] == 0 ? field : getString(R.string.advanced_filter_field_count, field, counts[index]));
                getGroup(type).setVisibility(counts[index] == 0 ? View.GONE : View.VISIBLE);
            } else if (anyChips[index] != null) stylePresetChip(anyChips[index], counts[index] == 0);
        }
        if (!bookmarkMode && (ranges.getFromPage() != Ranges.UNDEFINED || ranges.getToPage() != Ranges.UNDEFINED)) total++;
        if (ranges.getFromDate() != Ranges.UNDEFINED || ranges.getToDate() != Ranges.UNDEFINED) total++;
        if (ranges.getMinFavorites() != Ranges.UNDEFINED) total++;
        TextView summary = findViewById(R.id.advanced_filter_summary);
        summary.setText(total == 0 ? getString(R.string.advanced_filters) : getString(R.string.advanced_filters_count, total));
    }

    private Tag getDefaultSearchLanguage() {
        Language language = Global.getOnlyLanguage();
        if (Language.ENGLISH == language) return includedLanguage(SpecialTagIds.LANGUAGE_ENGLISH, "english");
        if (Language.JAPANESE == language) return includedLanguage(SpecialTagIds.LANGUAGE_JAPANESE, "japanese");
        if (Language.CHINESE == language) return includedLanguage(SpecialTagIds.LANGUAGE_CHINESE, "chinese");
        return null;
    }

    private static Tag includedLanguage(int id, String name) {
        Tag existing = Queries.TagTable.getTagById(id);
        if (existing != null && TagType.LANGUAGE.equals(existing.getType()) && name.equals(existing.getName()))
            return new Tag(name, existing.getCount(), id, TagType.LANGUAGE, TagStatus.ACCEPTED);
        return new Tag(name, -1, id, TagType.LANGUAGE, TagStatus.ACCEPTED);
    }

    private static Map<String, TagStatus> fieldStatuses(TagType type, List<Tag> selected) {
        Map<String, TagStatus> statuses = new LinkedHashMap<>();
        for (Tag tag : selected)
            if (type.equals(tag.getType()) && tag.getStatus() != TagStatus.DEFAULT)
                statuses.put(tag.getName(), tag.getStatus());
        return statuses;
    }

    private void openFilterPicker(TagType type) {
        ArrayList<Tag> selected = new ArrayList<>();
        for (Tag tag : getSelectedTags())
            if (type.equals(tag.getType())) selected.add(tag);
        inputMethodManager.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
        filterPicker.launch(FilterPickerActivity.createIntent(this, type, selected));
    }

    private void replaceFieldTags(TagType type, List<Tag> selected) {
        ChipGroup group = getGroup(type);
        for (int index = tags.size() - 1; index >= 0; index--) {
            ChipTag chip = tags.get(index);
            if (type.equals(chip.getTag().getType())) {
                if (isInlineField(type)) chip.changeStatus(TagStatus.DEFAULT);
                else {
                    group.removeView(chip);
                    tags.remove(index);
                }
            }
        }
        for (Tag tag : selected)
            if (type.equals(tag.getType())) addFilterTag(tag);
    }

    private ChipTag findFilterTag(Tag tag) {
        for (ChipTag chip : tags) {
            Tag current = chip.getTag();
            if (current.getType().equals(tag.getType()) && current.getName().equals(tag.getName()))
                return chip;
        }
        return null;
    }

    private void addFilterTag(Tag tag) {
        TagType type = tag.getType();
        boolean inline = isInlineField(type);
        if (TagType.UNKNOWN.equals(type) || (!inline && tag.getStatus() == TagStatus.DEFAULT)) return;
        ChipTag existing = findFilterTag(tag);
        if (existing != null) {
            existing.changeStatus(tag.getStatus());
            return;
        }
        if (TagType.TAG.equals(type) && ContentTagAliases.members(tag.getName()).size() > 1) {
            // Management collapses a family using its highest count. A filter displays the
            // count of the exact raw value that will be queried, including old spellings.
            Tag raw = Queries.TagTable.getTagById(tag.getId());
            if (raw != null && raw.getName().equals(tag.getName()) && type.equals(raw.getType())) {
                raw.setStatus(tag.getStatus());
                tag = raw;
            }
        }
        ChipGroup group = getGroup(type);
        ChipTag chip = (ChipTag) getLayoutInflater().inflate(R.layout.chip_layout_entry, group, false);
        chip.init(tag, !inline, true);
        if (!inline) chip.setOnCloseIconClickListener(view -> {
            removeFilterChip(group, chip);
            onFilterChanged();
        });
        chip.setOnClickListener(view -> {
            chip.updateStatus();
            if (!inline && chip.getTag().getStatus() == TagStatus.DEFAULT) removeFilterChip(group, chip);
            onFilterChanged();
        });
        group.addView(chip);
        tags.add(chip);
    }

    private void removeFilterChip(ChipGroup group, ChipTag chip) {
        group.removeView(chip);
        tags.remove(chip);
    }

    private void onFilterChanged() {
        advanced = true;
        refreshFilterSummary();
    }

    private ChipGroup getGroup(TagType type) {
        return groups[type.getId()];
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
