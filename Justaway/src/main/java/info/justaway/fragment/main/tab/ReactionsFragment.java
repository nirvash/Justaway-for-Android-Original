package info.justaway.fragment.main.tab;

import android.os.AsyncTask;
import android.view.View;

import info.justaway.event.model.StreamingCreateFavoriteEvent;
import info.justaway.model.AccessTokenManager;
import info.justaway.model.Row;
import info.justaway.model.TabManager;
import info.justaway.model.TwitterManager;
import info.justaway.settings.BasicSettings;
import info.justaway.util.StatusUtil;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;

public class ReactionsFragment extends BaseFragment {
    @Override
    protected boolean isSkip(Row row) {
        if (row.isFavorite()) {
            // 自分の Fav は除外
            return row.getSource().getId() == AccessTokenManager.getUserId();
        }

        if (row.isStatus()) {
            Status status = row.getStatus();
            Status retweet = status.getRetweetedStatus();

            /**
             * 自分のツイートがRTされた時
             */
            if (retweet != null && retweet.getUser().getId() == AccessTokenManager.getUserId()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public long getTabId() {
        return TabManager.REACTIONS_TAB_ID;
    }

    @Override
    protected void taskExecute() {
        // 対応する REST API はない。
        // ストリームで受けとったものしか表示されない
    }

    /**
     * ストリーミングAPIからふぁぼを受け取った時のイベント
     * @param event ふぁぼイベント
     */
    public void onEventMainThread(StreamingCreateFavoriteEvent event) {
        addStack(event.getRow());
    }
}
