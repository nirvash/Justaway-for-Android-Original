package info.justaway.fragment.main.tab;

import android.os.AsyncTask;
import android.view.View;

import info.justaway.model.AccessTokenManager;
import info.justaway.model.Row;
import info.justaway.model.TabManager;
import info.justaway.model.TwitterManager;
import info.justaway.settings.BasicSettings;
import info.justaway.util.StatusUtil;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;

/**
 * Created by 0025110052 on 2017/04/13.
 */

public class FilterFragment extends BaseFragment {

    /**
     * このタブを表す固有のID、ユーザーリストで正数を使うため負数を使う
     */
    public long getTabId() {
        return TabManager.FILTER_TAB_ID;
    }

    /**
     * このタブに表示するツイートの定義
     * @param row ストリーミングAPIから受け取った情報（ツイート＋ふぁぼ）
     *            CreateFavoriteEventをキャッチしている為、ふぁぼイベントを受け取ることが出来る
     * @return trueは表示しない、falseは表示する
     */
    @Override
    protected boolean isSkip(Row row) {
        if (row.isFavorite()) {
            return row.getSource().getId() == AccessTokenManager.getUserId();
        }
        if (row.isStatus()) {
            Status status = row.getStatus();
            Status retweet = status.getRetweetedStatus();

            // フィルター条件を書く
            if (StatusUtil.hasGranblueFantasyId(status.getText())) {
                return false;
            }
            if (retweet != null && StatusUtil.hasGranblueFantasyId(retweet.getText())) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void taskExecute() {
        new FilterTimelineTask().execute();
    }

    private class FilterTimelineTask extends AsyncTask<Void, Void, ResponseList<Status>> {
        @Override
        protected ResponseList<twitter4j.Status> doInBackground(Void... params) {
            try {
                Paging paging = new Paging();
                if (mMaxId > 0 && !mReloading) {
                    paging.setMaxId(mMaxId - 1);
                    paging.setCount(BasicSettings.getPageCount());
                }
                return TwitterManager.getTwitter().getHomeTimeline(paging);
            } catch (OutOfMemoryError e) {
                return null;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(ResponseList<twitter4j.Status> statuses) {
            mFooter.setVisibility(View.GONE);
            if (statuses == null || statuses.size() == 0) {
                mReloading = false;
                mPullToRefreshLayout.setRefreshComplete();
                mListView.setVisibility(View.VISIBLE);
                return;
            }
            if (mReloading) {
                clear();
                for (twitter4j.Status status : statuses) {
                    if (mMaxId <= 0L || mMaxId > status.getId()) {
                        mMaxId = status.getId();
                    }
                    Row row = Row.newStatus(status);
                    if (!isSkip(row)) {
                        mAdapter.add(row);
                    }
                }
                mReloading = false;
                mPullToRefreshLayout.setRefreshComplete();
            } else {
                for (twitter4j.Status status : statuses) {
                    if (mMaxId <= 0L || mMaxId > status.getId()) {
                        mMaxId = status.getId();
                    }
                    Row row = Row.newStatus(status);
                    if (!isSkip(row)) {
                        mAdapter.extensionAdd(row);
                    }
                }
                mAutoLoader = true;
                mListView.setVisibility(View.VISIBLE);
            }
        }
    }
}
