package info.justaway.fragment.main.tab;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.ProgressBar;

import java.util.ArrayList;

import de.greenrobot.event.EventBus;
import info.justaway.JustawayApplication;
import info.justaway.R;
import info.justaway.adapter.TwitterAdapter;
import info.justaway.event.NewRecordEvent;
import info.justaway.model.Row;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;

/**
 * タイムライン、すべての始まり
 */
public class TimelineFragment extends BaseFragment {

    private Boolean mAutoLoader = false;
    private Boolean mReload = false;
    private Boolean mBusy = false;
    private long mMaxId = 0L;
    private ProgressBar mFooter;

    public long getTabId() {
        return -1L;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        mFooter = (ProgressBar) v.findViewById(R.id.guruguru);
        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        ListView listView = getListView();
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {

            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                switch (scrollState) {
                    case AbsListView.OnScrollListener.SCROLL_STATE_IDLE:
                        mBusy = false;
                        render();
                        break;
                    case AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
                    case AbsListView.OnScrollListener.SCROLL_STATE_FLING:
                        mBusy = true;
                        break;
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                // 最後までスクロールされたかどうかの判定
                if (totalItemCount > 0 && totalItemCount == firstVisibleItem + visibleItemCount) {
                    additionalReading();
                }
            }
        });

        if (mMaxId == 0L) {
            new HomeTimelineTask().execute();
        }
    }

    @Override
    public void reload() {
        mReload = true;
        clear();
        getPullToRefreshLayout().setRefreshing(true);
        new HomeTimelineTask().execute();
    }

    @Override
    public void clear() {
        mMaxId = 0L;
        TwitterAdapter adapter = getListAdapter();
        if (adapter != null) {
            adapter.clear();
        }
    }

    @Override
    public void onRefreshStarted(View view) {
        reload();
    }

    private void additionalReading() {
        if (!mAutoLoader || mReload) {
            return;
        }
        mFooter.setVisibility(View.VISIBLE);
        mAutoLoader = false;
        new HomeTimelineTask().execute();
    }

    private ArrayList<Row> mRows = new ArrayList<Row>();
    private Runnable mRender = new Runnable() {
        @Override
        public void run() {
            if (mBusy) {
                return;
            }
            ListView listView = getListView();

            // 表示している要素の位置
            int position = listView.getFirstVisiblePosition();

            // 縦スクロール位置
            View view = listView.getChildAt(0);
            int y = view != null ? view.getTop() : 0;

            // 要素を上に追加（ addだと下に追加されてしまう ）
            TwitterAdapter adapter = (TwitterAdapter) listView.getAdapter();
            for (Row row : mRows) {
                adapter.insert(row, 0);
            }

            boolean autoScroll = position == 0 && y == 0 && mRows.size() < 5;
            listView.setSelectionFromTop(position + mRows.size(), y);
            mRows.clear();

            EventBus.getDefault().post(new NewRecordEvent(getTabId(), autoScroll));

            // 少しでもスクロールさせている時は画面を動かさない様にスクロー位置を復元する
            if (autoScroll) {
                listView.setSelection(0);
            }
        }
    };

    /**
     * ページ最上部だと自動的に読み込まれ、スクロールしていると動かないという美しい挙動
     */
    public void add(final Row row) {
        Status retweet = row.getStatus().getRetweetedStatus();
        if (retweet != null && retweet.getUser().getId() == JustawayApplication.getApplication().getUserId()) {
            return;
        }
        mRows.add(row);
        render();
    }

    private void render() {
        ListView listView = getListView();
        if (listView == null) {
            return;
        }
        listView.removeCallbacks(mRender);
        listView.postDelayed(mRender, 500);
    }

    private class HomeTimelineTask extends AsyncTask<Void, Void, ResponseList<Status>> {
        @Override
        protected ResponseList<twitter4j.Status> doInBackground(Void... params) {
            try {
                JustawayApplication application = JustawayApplication.getApplication();
                Paging paging = new Paging();
                if (mMaxId > 0) {
                    paging.setMaxId(mMaxId - 1);
                    paging.setCount(application.getPageCount());
                }
                return application.getTwitter().getHomeTimeline(paging);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(ResponseList<twitter4j.Status> statuses) {
            mFooter.setVisibility(View.GONE);
            if (statuses == null || statuses.size() == 0) {
                mReload = false;
                getPullToRefreshLayout().setRefreshComplete();
                getListView().setVisibility(View.VISIBLE);
                return;
            }
            TwitterAdapter adapter = getListAdapter();
            if (mReload) {
                adapter.clear();
                for (twitter4j.Status status : statuses) {
                    if (mMaxId == 0L || mMaxId > status.getId()) {
                        mMaxId = status.getId();
                    }
                    adapter.add(Row.newStatus(status));
                }
                mReload = false;
                getPullToRefreshLayout().setRefreshComplete();
            } else {
                for (twitter4j.Status status : statuses) {
                    if (mMaxId == 0L || mMaxId > status.getId()) {
                        mMaxId = status.getId();
                    }
                    adapter.extensionAdd(Row.newStatus(status));
                }
                mAutoLoader = true;
                getListView().setVisibility(View.VISIBLE);
            }
        }
    }
}