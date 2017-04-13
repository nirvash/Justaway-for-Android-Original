package info.justaway.task;

import android.os.AsyncTask;
import android.util.Log;

import de.greenrobot.event.EventBus;
import info.justaway.R;
import info.justaway.event.action.StatusActionEvent;
import info.justaway.event.model.StreamingDestroyStatusEvent;
import info.justaway.event.model.StreamingUpdateSelfRetweetEvent;
import info.justaway.model.AccessTokenManager;
import info.justaway.model.FavRetweetManager;
import info.justaway.model.TwitterManager;
import info.justaway.util.MessageUtil;
import twitter4j.ResponseList;
import twitter4j.TwitterException;

public class UnRetweetTask extends AsyncTask<Void, Void, TwitterException> {

    private long mRetweetedStatusId;
    private long mStatusId;
    private static final int ERROR_CODE_DUPLICATE = 34;

    public UnRetweetTask(long retweetedStatusId, long statusId) {
        // 自分自身がリツイートしたツイートの ID を statusId に渡す必要がある
        mRetweetedStatusId = retweetedStatusId;
        mStatusId = statusId;
        Log.d("UnretweetTask", String.format("RtId %d, Id %d", mRetweetedStatusId, mStatusId));
    }

    @Override
    protected TwitterException doInBackground(Void... params) {
        try {
            if (mStatusId == 0) {
                ResponseList<twitter4j.Status> responses = TwitterManager.getTwitter().getHomeTimeline();
                Log.d("UnretweetTask", String.format("response size %d", responses.size()));

                for (twitter4j.Status status : responses) {
                    if (status.getUser().getId() == AccessTokenManager.getUserId() && status.isRetweet() && status.getRetweetedStatus().getId() == mRetweetedStatusId) {
                        mStatusId = status.getId();
                        Log.d("UnretweetTask", String.format("response found %d", mStatusId));
                        break;
                    }
                }
            }
            if (mStatusId == 0) {
                // 取り消す対象のツイートが見つからない場合
                return new TwitterException("target id not found");
            }

            TwitterManager.getTwitter().destroyStatus(mStatusId);
            return null;
        } catch (TwitterException e) {
            e.printStackTrace();
            return e;
        }
    }

    @Override
    protected void onPostExecute(TwitterException e) {
        if (e == null) {
            MessageUtil.showToast(R.string.toast_destroy_retweet_success);
            EventBus.getDefault().post(new StreamingDestroyStatusEvent(mStatusId));
            // RT を取り消した対象を指定してその状態を変更するので実際に削除したツイートの mStatusId ではなく
            // mRetweetedStatusId を渡す
            EventBus.getDefault().post(new StreamingUpdateSelfRetweetEvent(mRetweetedStatusId, -1, false));
        } else if (e.getErrorCode() == ERROR_CODE_DUPLICATE) {
            MessageUtil.showToast(R.string.toast_destroy_retweet_already);
            // 状態を現状にあわせる
            EventBus.getDefault().post(new StreamingUpdateSelfRetweetEvent(mRetweetedStatusId, -1, false));
        } else {
            if (mRetweetedStatusId > 0) {
                EventBus.getDefault().post(new StatusActionEvent());
            }
            MessageUtil.showToast(R.string.toast_destroy_retweet_failure);
        }
    }
}