package me.piebridge.bible.activity;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import java.util.List;
import java.util.Locale;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.R;
import me.piebridge.bible.component.AnnotationComponent;
import me.piebridge.bible.component.VersionComponent;
import me.piebridge.bible.provider.VersionProvider;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.NumberUtils;

/**
 * Created by thom on 2018/11/6.
 */
public class HighlightActivity extends AbstractAnnotationActivity {

    @Override
    protected int getContentLayout() {
        return R.layout.activity_highlight;
    }

    @Override
    protected Cursor search(String book, String query, String order) {
        BibleApplication application = (BibleApplication) getApplication();
        return application.searchHighlight(book, order);
    }

    @Override
    protected void onHighlightChanged() {
        search();
    }

    @Override
    protected void onNoteChanged() {
        // do nothing
    }

    @Override
    public void onResume() {
        super.onResume();
        setTitle(getString(R.string.menu_highlight));
    }

    @Override
    protected String prepareContent(Cursor cursor) {
        String osis = cursor.getString(cursor.getColumnIndex(AnnotationComponent.COLUMN_OSIS));
        String book = BibleUtils.getBook(osis);
        BibleApplication application = (BibleApplication) getApplication();
        if (TextUtils.isEmpty(application.getHuman(book))) {
            return getString(R.string.annotation_no_book, book, application.getFullname());
        }
        int chapter = NumberUtils.parseInt(BibleUtils.getChapter(osis));
        List<String> chapters = application.getChapters(book);
        if (chapters.isEmpty()) {
            return getString(R.string.annotation_no_book, book, application.getFullname());
        }
        boolean hasIntro = VersionComponent.INTRO.equals(chapters.get(0));
        if (hasIntro) {
            chapter += 1;
        }
        String verses = cursor.getString(cursor.getColumnIndex(AnnotationComponent.COLUMN_VERSES));
        int verseStart = NumberUtils.parseInt(BibleUtils.getVerse(verses));
        int verseEnd = NumberUtils.parseInt(BibleUtils.getLastVerse(verses));
        return queryVerse(book, chapter, verseStart, verseEnd);
    }

    private String queryVerse(String book, int chapter, int verseStart, int verseEnd) {
        BibleApplication application = (BibleApplication) getApplication();
        SQLiteDatabase database = application.acquireDatabase();
        String start = String.format(Locale.US, "%d.%03d", chapter, verseStart);
        String end = String.format(Locale.US, "%d.%03d", chapter, verseEnd);
        try (
                Cursor cursor = database.query(VersionProvider.TABLE_VERSE,
                        null,
                        "book = ? and verse between ? and ?",
                        new String[] {book, start, end}, null, null, "id asc", "1");
        ) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndex(VersionProvider.COLUMN_UNFORMATTED));
            } else {
                return fallback(database, book, start);
            }
        } finally {
            application.releaseDatabase(database);
        }
    }

    private String fallback(SQLiteDatabase database, String book, String start) {
        try (
                Cursor cursor = database.query(VersionProvider.TABLE_VERSE,
                        null,
                        "book = ? and verse < ?",
                        new String[] {book, start}, null, null, "id desc", "1");
        ) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndex(VersionProvider.COLUMN_UNFORMATTED));
            } else {
                return null;
            }
        }
    }

}
