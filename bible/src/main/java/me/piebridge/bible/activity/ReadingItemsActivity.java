package me.piebridge.bible.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.OsisItem;
import me.piebridge.bible.R;
import me.piebridge.bible.adapter.HiddenArrayAdapter;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.LogUtils;

/**
 * Created by thom on 16/7/1.
 */
public class ReadingItemsActivity extends AbstractReadingActivity implements AdapterView.OnItemSelectedListener {

    public static final String ITEMS = "items";
    public static final String SEARCH = "search";

    private String search;
    private List<OsisItem> items;

    private Spinner spinner;
    private TextView itemsView;

    private boolean mNoteChanged;
    private boolean mHighlightChanged;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        initItems();
        super.onCreate(savedInstanceState);
        showBack(true);
    }

    protected void initItems() {
        Intent intent = getIntent();
        setSearch(intent.getStringExtra(SEARCH));
        setItems(intent.getParcelableArrayListExtra(ITEMS));
    }

    protected void setSearch(String search) {
        this.search = search;
    }

    protected void setItems(List<OsisItem> items) {
        this.items = items;
    }

    @Override
    protected String getInitialOsis() {
        return getOsis(0);
    }

    @Override
    protected int getInitialPosition() {
        return 0;
    }

    @Override
    protected int retrieveOsisCount() {
        return items.size();
    }

    @Override
    public Bundle retrieveOsis(int position, String x) {
        OsisItem item = items.get(position);
        Bundle bundle = super.retrieveOsis(position, item.toOsis());
        LogUtils.d("retrieveOsis, position: " + position + ", osis: " + item.toOsis());
        bundle.putString(VERSE_START, item.verseStart);
        bundle.putString(VERSE_END, item.verseEnd);
        bundle.putString(PREV, getOsis(position - 1));
        bundle.putString(NEXT, getOsis(position + 1));
        bundle.putString(SEARCH, search);
        bundle.putString(VERSES, item.verses);
        return bundle;
    }

    private String getOsis(int index) {
        if (index >= 0 && index < items.size()) {
            return items.get(index).toOsis();
        } else {
            return "";
        }
    }

    @Override
    protected void initializeHeader(View header) {
        itemsView = header.findViewById(R.id.items);
        spinner = header.findViewById(R.id.spinner);
        ArrayAdapter adapter = new HiddenArrayAdapter(this, R.layout.view_spinner_item, convertItems(items));
        adapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(0);
        spinner.setOnItemSelectedListener(this);
        if (items.size() <= 1) {
            header.findViewById(R.id.dropdown).setVisibility(View.GONE);
        } else {
            header.findViewById(R.id.items_button).setOnClickListener(this);
        }

        initializeVersion(header);
    }

    @Override
    protected int getContentLayout() {
        return R.layout.activity_reading_items;
    }

    private CharSequence[] convertItems(List<OsisItem> items) {
        int size = items.size();
        CharSequence[] values = new CharSequence[size];
        for (int i = 0; i < size; ++i) {
            OsisItem item = items.get(i);
            values[i] = getTitle(item);
        }
        return values;
    }

    @Override
    protected void updateHeader(Bundle bundle, String osis) {
        String title = getTitle(bundle, osis);
        itemsView.setText(title);
        updateTaskDescription(title);
    }

    @Override
    protected int getToolbarLayout() {
        return R.id.toolbar_reading_items;
    }

    protected String getTitle(Bundle bundle, String osis) {
        String book = BibleUtils.getBook(osis);
        BibleApplication application = (BibleApplication) getApplication();
        String bookName = application.getHuman(book);
        String verses = bundle.getString(VERSES);
        if (TextUtils.isEmpty(verses)) {
            String chapterVerse = BibleUtils.getChapterVerse(this, bundle);
            return BibleUtils.getBookChapterVerse(bookName, chapterVerse);
        } else {
            return bookName + " " + BibleUtils.getChapter(osis, this) + ":" + verses;
        }
    }

    protected String getTitle(OsisItem item) {
        String book = BibleUtils.getBook(item.toOsis());
        BibleApplication application = (BibleApplication) getApplication();
        String bookName = application.getHuman(book);
        String verses = item.verses;
        if (TextUtils.isEmpty(verses)) {
            String chapterVerse = BibleUtils.getChapterVerse(this, item.toBundle());
            return BibleUtils.getBookChapterVerse(bookName, chapterVerse);
        } else {
            return bookName + " " + BibleUtils.getChapter(item.toOsis(), this) + ":" + verses;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Bundle bundle = retrieveOsis(position, null);
        mAdapter.setData(position, bundle);
        prepare(position);
        mPager.setCurrentItem(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // do nothing
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.items_button) {
            spinner.performClick();
        } else {
            super.onClick(v);
        }
    }

    @Override
    public void saveNotes(long id, String verses, String content) {
        super.saveNotes(id, verses, content);
        mNoteChanged = true;
    }

    @Override
    public void deleteNote(long id, String verses) {
        super.deleteNote(id, verses);
        mNoteChanged = true;
    }

    @Override
    public void saveHighlight(String verses) {
        super.saveHighlight(verses);
        mHighlightChanged = true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                setResult();
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void finish() {
        setResult();
        super.finish();
    }

    private void setResult() {
        Intent data = new Intent();
        data.putExtra(NOTE_CHANGED, mNoteChanged);
        data.putExtra(HIGHLIGHT_CHANGED, mHighlightChanged);
        LogUtils.d("set result, data: " + data.getExtras());
        setResult(RESULT_OK, data);
    }

}