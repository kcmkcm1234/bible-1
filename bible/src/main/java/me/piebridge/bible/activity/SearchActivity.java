package me.piebridge.bible.activity;

import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.SearchRecentSuggestions;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.widget.SearchView;
import androidx.drawerlayout.widget.DrawerLayout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Locale;

import me.piebridge.bible.BibleApplication;
import me.piebridge.bible.OsisItem;
import me.piebridge.bible.R;
import me.piebridge.bible.fragment.SearchFragment;
import me.piebridge.bible.provider.SearchProvider;
import me.piebridge.bible.utils.BibleUtils;
import me.piebridge.bible.utils.LogUtils;

/**
 * Created by thom on 2018/10/19.
 */
public class SearchActivity extends ToolbarActivity implements SearchView.OnQueryTextListener {

    private static final int SEARCH = 1000;
    private static final int CLEAR = 1001;
    private static final int UPDATE_VERSION = 1002;

    public static final String OSIS_FROM = "osisFrom";

    public static final String OSIS_TO = "osisTo";

    public static final String URL = "url";

    public static final String CROSS = "cross";

    private SearchView mSearchView;

    private SearchFragment mSearchFragment;

    private SearchRecentSuggestions mSuggestions;

    private Handler workHandler;

    private Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LogUtils.d("SearchActivity, onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        showBack(true);

        mSuggestions = new SearchRecentSuggestions(this,
                SearchProvider.AUTHORITY, SearchProvider.MODE);

        mSearchView = findViewById(R.id.searchView);
        mSearchView.setOnQueryTextListener(this);
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        if (searchManager != null) {
            SearchableInfo searchableInfo = searchManager.getSearchableInfo(getComponentName());
            mSearchView.setSearchableInfo(searchableInfo);
        }

        mSearchFragment = new SearchFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, mSearchFragment)
                .commit();

        workHandler = new WorkHandler(this);
        mainHandler = new MainHandler(this);

        handleIntent(getIntent());
        updateTaskDescription(getTitle().toString());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        LogUtils.d("SearchActivity, onNewIntent");
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.search, menu);
        return true;
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_clear:
                clearSuggestions();
                return true;
            case android.R.id.home:
                BibleUtils.startLauncher(this, null);
                finish();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void clearSuggestions() {
        workHandler.obtainMessage(CLEAR).sendToTarget();
    }

    public void clearSuggestionsOnWork() {
        mSuggestions.clearHistory();
    }

    @Override
    public void onBackPressed() {
        if (isShortcut()) {
            BibleUtils.startLauncher(this, null);
            finish();
        } else {
            super.onBackPressed();
        }
    }

    private boolean isShortcut() {
        Intent intent = getIntent();
        return intent.getAction() != null || parseQuery(intent) != null;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        if (!TextUtils.isEmpty(query)) {
            if (query.startsWith("http://") || query.startsWith("https://")) {
                Intent intent = new Intent(Intent.ACTION_SEARCH);
                intent.putExtra(SearchManager.QUERY, query);
                handleIntent(intent);
            } else {
                doSearch(query, false, null);
            }
        }
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    private void doSearch(String query, boolean finished, Uri data) {
        mSuggestions.saveRecentQuery(query, null);

        ArrayList<OsisItem> items;
        if (finished) {
            items = new ArrayList<>();
        } else {
            items = OsisItem.parseSearch(query, (BibleApplication) getApplication());
            BibleUtils.fixItems(items);
        }
        if (!items.isEmpty()) {
            showItems(items, false, false);
        } else {
            Intent intent = new Intent(this, ResultsActivity.class);
            intent.setAction(Intent.ACTION_SEARCH);
            intent.putExtra(SearchManager.QUERY, query);

            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            if (!finished) {
                intent.putExtra(OSIS_FROM, sharedPreferences.getString(SearchFragment.KEY_SEARCH_FROM, null));
                intent.putExtra(OSIS_TO, sharedPreferences.getString(SearchFragment.KEY_SEARCH_TO, null));
            }
            intent.putExtra(URL, data);

            LogUtils.d("intent: " + intent + ", extra: " + intent.getExtras());
            super.startActivity(setFinished(intent, finished));
        }
    }

    @Override
    public void startActivity(Intent intent) {
        if ((getComponentName()).equals(intent.getComponent())) {
            String query = parseQuery(intent);
            if (!TextUtils.isEmpty(query)) {
                mSearchView.setQuery(query, true);
            }
        } else {
            super.startActivity(intent);
        }
    }

    private String parseQuery(Intent intent) {
        return intent.getStringExtra(SearchManager.QUERY);
    }

    private String lower(String version) {
        if (version == null) {
            return null;
        } else {
            return version.toLowerCase(Locale.US);
        }
    }

    private void handleIntent(Intent intent) {
        workHandler.obtainMessage(SEARCH, intent).sendToTarget();
    }

    void handleIntentOnWork(Intent intent) {
        BibleApplication application = (BibleApplication) getApplication();
        if (!application.setDefaultVersion()) {
            mainHandler.sendEmptyMessage(UPDATE_VERSION);
        }
        mainHandler.obtainMessage(SEARCH, intent).sendToTarget();
    }

    void handleIntentOnMain(Intent intent) {
        LogUtils.d("intent: " + intent + ", extra: " + intent.getExtras());
        String action = intent.getAction();
        if (action == null) {
            mSearchView.setQuery(parseQuery(intent), false);
            return;
        }
        String query = null;
        Uri data = null;
        switch (intent.getAction()) {
            case Intent.ACTION_SEND:
                query = intent.getStringExtra(Intent.EXTRA_TEXT);
                break;
            case Intent.ACTION_PROCESS_TEXT:
                query = intent.getStringExtra(Intent.EXTRA_PROCESS_TEXT);
                break;
            case Intent.ACTION_VIEW:
                data = intent.getData();
                break;
            case Intent.ACTION_SEARCH:
                query = intent.getStringExtra(SearchManager.QUERY);
                break;
            default:
                break;
        }

        LogUtils.d("query: " + query + ", data: " + data);

        boolean cross = intent.getBooleanExtra(CROSS, false);

        if (!TextUtils.isEmpty(query) && (query.startsWith("http://") || query.startsWith("https://"))) {
            data = Uri.parse(query);
        }

        String version = null;
        if (data != null) {
            version = lower(data.getQueryParameter("version"));
            if (TextUtils.isEmpty(version)) {
                version = lower(data.getQueryParameter("qs_version"));
            }
            if (TextUtils.isEmpty(version)) {
                version = lower(data.getQueryParameter("translation"));
            }
            query = data.getQueryParameter("search");
            if (TextUtils.isEmpty(query)) {
                query = data.getQueryParameter("quicksearch");
            }
            if (TextUtils.isEmpty(query)) {
                query = data.getQueryParameter("q");
            }
            if (TextUtils.isEmpty(query) && data.getPath() != null) {
                query = data.getPath().replaceAll("^/search/([^/]*).*$", "$1");
            }
            LogUtils.d("data: " + data + ", query: " + query);
        }

        BibleApplication application = (BibleApplication) getApplication();
        if (!TextUtils.isEmpty(version) && application.hasVersion(version) && application.setVersion(version)) {
            mSearchFragment.updateVersion(version);
        }

        if (query != null) {
            ArrayList<OsisItem> items = OsisItem.parseSearch(query, (BibleApplication) getApplication());
            BibleUtils.fixItems(items);
            if (!items.isEmpty()) {
                LogUtils.d("items: " + items);
                mSuggestions.saveRecentQuery(query, null);
                showItems(items, cross, true);
            } else {
                doSearch(query, true, data);
            }
            finish();
        }
    }

    private void showItems(ArrayList<OsisItem> items, boolean cross, boolean finished) {
        Intent intent = new Intent(this, cross ? ReadingCrossActivity.class : ReadingItemsActivity.class);
        intent.putParcelableArrayListExtra(ReadingItemsActivity.ITEMS, items);
        intent.putExtra(Intent.EXTRA_REFERRER, getIntent().getAction());
        super.startActivity(setFinished(intent, finished));
    }

    public void hideSoftInput() {
        mSearchView.clearFocus();
    }

    public void updateVersion() {
        if (mSearchFragment != null) {
            mSearchFragment.updateVersion();
        }
    }

    public void updateVersionIfNeeded() {
        BibleApplication application = (BibleApplication) getApplication();
        if (BibleUtils.isDemoVersion(application.getVersion())) {
            BibleUtils.startLauncher(this, null);
        } else {
            updateVersion();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setTitle(getString(R.string.manifest_search));
    }

    private String state(int state) {
        switch (state) {
            case DrawerLayout.STATE_DRAGGING:
                return "dragging";
            case DrawerLayout.STATE_IDLE:
                return "idle";
            case DrawerLayout.STATE_SETTLING:
                return "settling";
            default:
                return "unknown(" + state + ")";
        }
    }

    private static class WorkHandler extends Handler {

        private final WeakReference<SearchActivity> mReference;

        public WorkHandler(SearchActivity activity) {
            super(newLooper());
            this.mReference = new WeakReference<>(activity);
        }

        private static Looper newLooper() {
            HandlerThread thread = new HandlerThread("Search");
            thread.start();
            return thread.getLooper();
        }

        @Override
        public void handleMessage(Message msg) {
            SearchActivity activity = mReference.get();
            if (activity != null) {
                switch (msg.what) {
                    case SEARCH:
                        activity.handleIntentOnWork((Intent) msg.obj);
                        break;
                    case CLEAR:
                        activity.clearSuggestionsOnWork();
                        break;
                    default:
                        break;
                }
            }

        }
    }

    private static class MainHandler extends Handler {

        private final WeakReference<SearchActivity> mReference;

        public MainHandler(SearchActivity activity) {
            this.mReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            SearchActivity activity = mReference.get();
            if (activity != null) {
                switch (msg.what) {
                    case SEARCH:
                        activity.handleIntentOnMain((Intent) msg.obj);
                        break;
                    case UPDATE_VERSION:
                        activity.updateVersionIfNeeded();
                        break;
                    default:
                        break;
                }
            }
        }

    }

}
