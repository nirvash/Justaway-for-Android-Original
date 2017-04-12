package info.justaway.task;

import android.os.AsyncTask;

import de.greenrobot.event.EventBus;
import info.justaway.R;
import info.justaway.event.action.StatusActionEvent;
import info.justaway.event.model.StreamingUpdateSelfRetweetEvent;
import info.justaway.model.FavRetweetManager;
import info.justaway.model.TwitterManager;
import info.justaway.util.MessageUtil;
import twitter4j.TwitterException;

public class RetweetTask extends AsyncTask<Void, Void, TwitterException> {

    private long mStatusId;
    private static final int ERROR_CODE_DUPLICATE = 37;

    public RetweetTask(long statusId) {
        mStatusId = statusId;
        FavRetweetManager.setRtId(statusId, (long) 0);
        EventBus.getDefault().post(new StatusActionEvent());
    }

    @Override
    protected TwitterException doInBackground(Void... params) {
        try {
            TwitterManager.getTwitter().retweetStatus(mStatusId);
            return null;
        } catch (TwitterException e) {
            e.printStackTrace();
            return e;
        }
    }

    @Override
    protected void onPostExecute(TwitterException e) {
        if (e == null) {
            MessageUtil.showToast(R.string.toast_retweet_success);
            EventBus.getDefault().post(new StreamingUpdateSelfRetweetEvent(mStatusId, true));
        } else if (e.getErrorCode() == ERROR_CODE_DUPLICATE) {
            MessageUtil.showToast(R.string.toast_retweet_already);
        } else {
            EventBus.getDefault().post(new StatusActionEvent());
            MessageUtil.showToast(R.string.toast_retweet_failure);
        }
    }
}