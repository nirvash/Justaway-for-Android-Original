package info.justaway.fragment.main.tab;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.Iterator;

import butterknife.Bind;
import butterknife.ButterKnife;
import de.greenrobot.event.EventBus;
import info.justaway.R;
import info.justaway.adapter.TwitterAdapter;
import info.justaway.event.NewRecordEvent;
import info.justaway.event.action.GoToTopEvent;
import info.justaway.event.action.PostAccountChangeEvent;
import info.justaway.event.action.StatusActionEvent;
import info.justaway.event.model.StreamingCreateStatusEvent;
import info.justaway.event.model.StreamingDestroyStatusEvent;
import info.justaway.event.model.StreamingUpdateSelfFavoriteEvent;
import info.justaway.event.model.StreamingUpdateSelfRetweetEvent;
import info.justaway.event.settings.BasicSettingsChangeEvent;
import info.justaway.listener.StatusClickListener;
import info.justaway.listener.StatusLongClickListener;
import info.justaway.model.AccessTokenManager;
import info.justaway.model.Row;
import info.justaway.model.TabManager;
import info.justaway.settings.BasicSettings;
import uk.co.senab.actionbarpulltorefresh.library.ActionBarPullToRefresh;
import uk.co.senab.actionbarpulltorefresh.library.PullToRefreshLayout;
import uk.co.senab.actionbarpulltorefresh.library.listeners.OnRefreshListener;

public abstract class BaseFragment extends Fragment implements OnRefreshListener {

    private static final String TAG = BaseFragment.class.getSimpleName();
    protected TwitterAdapter mAdapter;
    protected boolean mAutoLoader = false;
    protected boolean mReloading = false;
    private boolean mScrolling = false;
    protected long mMaxId = 0L; // 読み込んだ最新のツイートID
    protected long mDirectMessagesMaxId = 0L; // 読み込んだ最新の受信メッセージID
    protected long mSentDirectMessagesMaxId = 0L; // 読み込んだ最新の送信メッセージID
    private ArrayList<Row> mStackRows = new ArrayList<>();

    @Bind(R.id.list_view)
    protected ListView mListView;
    @Bind(R.id.guruguru)
    protected ProgressBar mFooter;
    @Bind(R.id.ptr_layout)
    protected PullToRefreshLayout mPullToRefreshLayout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        View v = inflater.inflate(R.layout.pull_to_refresh_list, container, false);
        if (v == null) {
            return null;
        }
        ButterKnife.bind(this, v);

        /**
         * PullToRefreshの初期化処理
         */
        ActionBarPullToRefresh.from(getActivity())
                .theseChildrenArePullable(mListView)
                .listener(this)
                .setup(mPullToRefreshLayout);

        mListView.setOnItemClickListener(new StatusClickListener(getActivity()));
        mListView.setOnItemLongClickListener(new StatusLongClickListener(getActivity()));
        mListView.setOnScrollListener(mOnScrollListener);
        mFooter.setVisibility(View.GONE);
        mListView.setFastScrollEnabled(BasicSettings.getFastScrollOn());

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

                /**
                 * mMainPagerAdapter.notifyDataSetChanged() された時に
                 * onCreateView と onActivityCreated がインスタンスが生きたまま呼ばれる
                 * 多重に初期化処理を実行しないように変数チェックを行う
                 */
                if (mAdapter == null) {
                    // Status(ツイート)をViewに描写するアダプター
                    mAdapter = new TwitterAdapter(getActivity(), R.layout.row_tweet);
                    mListView.setVisibility(View.GONE);
                    taskExecute();
                }

                mListView.setAdapter(mAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onPause() {
        EventBus.getDefault().unregister(this);
        super.onPause();
    }

    @Override
    public void onRefreshStarted(View view) {
        Log.d(TAG, "onRefreshStarted");
        reload();
    }

    public void reload() {
        mReloading = true;
        mPullToRefreshLayout.setRefreshing(true);
        taskExecute();
    }

    public void clear() {
        mMaxId = 0L;
        mDirectMessagesMaxId = 0L;
        mSentDirectMessagesMaxId = 0L;
        mAdapter.clear();
    }

    protected void additionalReading() {
        if (!mAutoLoader || mReloading) {
            return;
        }
        mFooter.setVisibility(View.VISIBLE);
        mAutoLoader = false;
        taskExecute();
    }

    public boolean goToTop() {
        if (mListView == null) {
            getActivity().finish();
            return false;
        }
        mListView.setSelection(0);
        if (mStackRows.size() > 0) {
            showStack();
            return false;
        } else {
            return true;
        }
    }

    public boolean isTop() {
        return mListView != null && mListView.getFirstVisiblePosition() == 0;
    }

    /**
     * ツイートの表示処理、画面のスクロール位置によって適切な処理を行う、まだバグがある
     */
    private Runnable mRender = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "mRender: run");

            if (mScrolling) {
                return;
            }
            if (mListView == null || mAdapter == null) {
                return;
            }

            // 表示している要素の位置
            int position = mListView.getFirstVisiblePosition();

            // 縦スクロール位置
            View view = mListView.getChildAt(0);
            int y = view != null ? view.getTop() : 0;

            // 要素を上に追加（ addだと下に追加されてしまう ）
            int count = 0;
            boolean highlight = false;
            for (Row row : mStackRows) {
                Log.d(TAG, "insert: " + row.getStatus().getId());
                mAdapter.insert(row, 0);
                count++;
                if (row.isFavorite()) {
                    // お気に入りしたのが自分じゃない時
                    if (row.getSource().getId() != AccessTokenManager.getUserId()) {
                        highlight = true;
                    }
                } else if (row.isStatus()) {
                    // 投稿主が自分じゃない時
                    if (row.getStatus().getUser().getId() != AccessTokenManager.getUserId()) {
                        highlight = true;
                    }
                } else if (row.isDirectMessage()) {
                    // 投稿主が自分じゃない時
                    if (row.getMessage().getSenderId() != AccessTokenManager.getUserId()) {
                        highlight = true;
                    }
                }
            }
            mStackRows.clear();

            boolean autoScroll = position == 0 && y == 0 && count < 5;

            if (highlight) {
                EventBus.getDefault().post(new NewRecordEvent(getTabId(), getSearchWord(), autoScroll));
            }

            // スクロール位置を復元する
            mListView.setSelectionFromTop(position + count, y);

            if (autoScroll) {
                // リストの先頭を表示していた場合は追加された項目をスクロールしてみせる
                mListView.smoothScrollToPositionFromTop(position, 0, 400);
            } else {
                // 沢山追加されたときは未読の新規ツイートをチラ見せ
                if (position == 0 && y == 0) {
                    mListView.smoothScrollToPositionFromTop(position + count, 120);
                }
            }
        }
    };

    /**
     * 新しいツイートを表示して欲しいというリクエストを一旦待たせ、
     * 250ms以内に同じリクエストが来なかったら表示する。
     * 250ms以内に同じリクエストが来た場合は、更に250ms待つ。
     * 表示を連続で行うと処理が重くなる為この制御を入れている。
     */
    private void showStack() {
        if (mListView == null) {
            return;
        }
        mListView.removeCallbacks(mRender);
        if (mStackRows.size() > 0) {
            mListView.postDelayed(mRender, 250);
        }
    }

    private void scrollFinish() {
        if (mListView == null) {
            return;
        }
        mListView.removeCallbacks(mScrollFinish);
        mListView.postDelayed(mScrollFinish, 250);
    }

    private Runnable mScrollFinish = new Runnable() {
        @Override
        public void run() {
            mScrolling = false;
            if (mStackRows.size() > 0) {
                showStack();
            } else if (isTop()) {
                EventBus.getDefault().post(new GoToTopEvent());
            }
        }
    };

    /**
     * ストリーミングAPIからツイートやメッセージを受信した時の処理
     * 1. 表示スべき内容かチェックし、不適切な場合はスルーする
     * 2. すぐ表示すると流速が早い時にガクガクするので溜めておく
     *
     * @param row ツイート情報
     */
    public void addStack(Row row) {
        if (isSkip(row)) {
            return;
        }
        mStackRows.add(row);
        if (!mScrolling && isTop()) {
            showStack();
        } else {
            EventBus.getDefault().post(new NewRecordEvent(getTabId(), getSearchWord(), false));
        }
    }

    /**
     * 1. スクロールが終わった瞬間にストリーミングAPIから受信し溜めておいたツイートがあればそれを表示する
     * 2. スクロールが終わった瞬間に表示位置がトップだったらボタンのハイライトを消すためにイベント発行
     * 3. スクロール中はスクロール中のフラグを立てる
     */
    private AbsListView.OnScrollListener mOnScrollListener = new AbsListView.OnScrollListener() {

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            switch (scrollState) {
                case AbsListView.OnScrollListener.SCROLL_STATE_IDLE:
                    scrollFinish();
                    break;
                case AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL:
                case AbsListView.OnScrollListener.SCROLL_STATE_FLING:
                    mListView.removeCallbacks(mScrollFinish);
                    mScrolling = true;
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
    };

    /**
     * そのツイート（またはメッセージ）を表示するかどうかのチェック
     */
    protected abstract boolean isSkip(Row row);

    /**
     * タブ固有のID、ユーザーリストではリストのIDを、その他はマイナスの固定値を返す
     */
    public abstract long getTabId();

    public String getSearchWord() {
        return "";
    }

    ;

    /**
     * 読み込み用のAsyncTaskを実行する
     */
    protected abstract void taskExecute();

    /**
     *
     * !!! EventBus !!!
     *
     */


    /**
     * 高速スクロールの設定が変わったら切り替える
     */
    @SuppressWarnings("UnusedDeclaration")
    public void onEventMainThread(BasicSettingsChangeEvent event) {
        mListView.setFastScrollEnabled(BasicSettings.getFastScrollOn());
    }

    @SuppressWarnings("UnusedDeclaration")
    public void onEventMainThread(StatusActionEvent event) {
        Log.d(TAG, "onEvent: StatusActionEvent");
        mAdapter.notifyDataSetChanged();
    }

    /**
     * ストリーミングAPIからツイ消しイベントを受信
     *
     * @param event ツイート
     */
    public void onEventMainThread(StreamingDestroyStatusEvent event) {
        Log.d(TAG, "onEvent: DestroyStatus");
        Iterator<Row> itr = mStackRows.iterator();
        while (itr.hasNext()) {
            Row row = itr.next();
            if (row.getStatus().getId() == event.getStatusId()) {
                if (row.getStatus().getUser().getId() == AccessTokenManager.getUserId()) {
                    itr.remove();
                } else {
                    row.setDeleted(true);
                }
            }
        }

        ArrayList<Integer> removePositions = mAdapter.removeStatus2(event.getStatusId());
        for (Integer removePosition : removePositions) {
            if (removePosition >= 0) {
                int visiblePosition = mListView.getFirstVisiblePosition();
                if (visiblePosition > removePosition) {
                    View view = mListView.getChildAt(0);
                    int y = view != null ? view.getTop() : 0;
                    mListView.setSelectionFromTop(visiblePosition - 1, y);
                    break;
                }
            }
        }
    }

    /**
     * ストリーミングAPIからツイートイベントを受信
     *
     * @param event ツイート
     */
    public void onEventMainThread(StreamingCreateStatusEvent event) {
        Log.d(TAG, "onEvent: CreateStatus: " + event.getRow().getStatus().getId());
        addStack(event.getRow());
    }

    /**
     * アカウント変更通知を受け、表示中のタブはリロード、表示されていたいタブはクリアを行う
     *
     * @param event アプリが表示しているタブのID
     */
    public void onEventMainThread(PostAccountChangeEvent event) {
        if (event.getTabId() == getTabId()) {
            reload();
        } else {
            clear();
        }
    }


    /**
     * ストリーミングAPIからふぁぼを受け取った時のイベント
     * @param event ふぁぼイベント
     */
    public void onEventMainThread(StreamingUpdateSelfFavoriteEvent event) {
        Log.d(TAG, "onEvent: SelfFavorite");
        try {
            for (Row row : mStackRows) {
                if (row.isRetweeted() && row.getStatus().getRetweetedStatus().getId() == event.getId()) {
                    row.setFavorite(event.isFavorited());
                }
            }
            mAdapter.updateFavorite(event.getId(), event.isFavorited());

            if (getTabId() == TabManager.FAVORITES_TAB_ID && event.isFavorited()) {
                // ふぁぼったときにふぁぼタブを青くしたいとき
                // EventBus.getDefault().post(new NewRecordEvent(getTabId(), getSearchWord(), false));
            }
        } catch (Exception e) {
            // NOP
        }
    }

    /**
     * 自分で RT したときのイベント
     * @param event イベント
     */
    public void onEventMainThread(StreamingUpdateSelfRetweetEvent event) {
        Log.d(TAG, "onEvent: SelfRetweet");
        try {
            mAdapter.updateRetweet(event.getId(), event.getRtId(), event.isRetweeted());
        } catch (Exception e) {
            // NOP
        }
    }
}
