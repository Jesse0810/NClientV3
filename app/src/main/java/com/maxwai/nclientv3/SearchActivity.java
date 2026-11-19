package com.maxwai.nclientv3;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
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
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private HistoryAdapter adapter;
    private boolean advanced = false;
    private Ranges.TimeUnit temporaryUnit;
    private InputMethodManager inputMethodManager;
    private boolean bookmarkMode;
    private boolean creatingBookmark;
    private Bookmark originalBookmark;
    private TextInputEditText bookmarkName;
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
            if (!restoreDraft(savedInstanceState)) searchView.requestFocus();
        }
    }

    private void submitSearch(String query) {
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
        refreshRangeLabels();
        return true;
    }

    private void refreshRangeLabels() {
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
        if (bookmarkName != null)
            state.putString("bookmarkDraftName", bookmarkName.getText() == null ? "" : bookmarkName.getText().toString());
        if (searchView != null) {
            state.putString("filterDraftKeywords", searchView.getQuery().toString());
            state.putParcelableArrayList("filterDraftTags", getSelectedTags());
            state.putParcelable("filterDraftRanges", ranges);
            state.putBoolean("filterDraftAdvanced", advanced);
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
        for (TagType type : TagType.values) {
            if (TagType.UNKNOWN.equals(type)) continue;
            ChipGroup group = getGroup(type);
            Chip choose = (Chip) getLayoutInflater().inflate(R.layout.chip_layout, group, false);
            choose.setCloseIconVisible(false);
            choose.setChipIconResource(R.drawable.ic_add);
            choose.setText(R.string.choose_filter_values);
            choose.setOnClickListener(view -> openFilterPicker(type));
            Global.setTint(this, choose.getChipIcon());
            addChip[type.getId()] = choose;
            group.addView(choose);
        }
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
                group.removeView(chip);
                tags.remove(index);
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
        if (tag.getStatus() == TagStatus.DEFAULT || TagType.UNKNOWN.equals(tag.getType())) return;
        ChipTag existing = findFilterTag(tag);
        if (existing != null) {
            existing.changeStatus(tag.getStatus());
            return;
        }
        TagType type = tag.getType();
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
        chip.init(tag, true, true);
        chip.setOnCloseIconClickListener(view -> removeFilterChip(group, chip));
        chip.setOnClickListener(view -> {
            chip.updateStatus();
            if (chip.getTag().getStatus() == TagStatus.DEFAULT) removeFilterChip(group, chip);
            advanced = true;
        });
        Chip choose = addChip[type.getId()];
        int chooseIndex = choose == null ? -1 : group.indexOfChild(choose);
        group.addView(chip, chooseIndex);
        tags.add(chip);
    }

    private void removeFilterChip(ChipGroup group, ChipTag chip) {
        group.removeView(chip);
        tags.remove(chip);
        advanced = true;
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
