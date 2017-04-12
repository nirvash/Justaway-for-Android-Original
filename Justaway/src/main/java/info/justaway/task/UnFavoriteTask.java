package info.justaway.task;

import android.os.AsyncTask;

import de.greenrobot.event.EventBus;
import info.justaway.R;
import info.justaway.event.action.StatusActionEvent;
import info.justaway.model.FavRetweetManager;
import info.justaway.model.TwitterManager;
import info.justaway.util.MessageUtil;
import twitter4j.TwitterException;

public class UnFavoriteTask extends AsyncTask<Void, Void, TwitterException> {

    private static final int ERROR_CODE_DUPLICATE = 34;

    private long mStatusId;

    public UnFavoriteTask(long statusId) {
        mStatusId = statusId;
        EventBus.getDefault().post(new StatusActionEvent());
    }

    @Override
    protected TwitterException doInBackground(Void... params) {
        try {
            TwitterManager.getTwitter().destroyFavorite(mStatusId);
        } catch (TwitterException e) {
            return e;
        }
        return null;
    }

    @Override
    protected void onPostExecute(TwitterException e) {
        if (e == null) {
            MessageUtil.showToast(R.string.toast_destroy_favorite_success);
        } else if (e.getErrorCode() == ERROR_CODE_DUPLICATE) {
            MessageUtil.showToast(R.string.toast_destroy_favorite_already);
        } else {
            FavRetweetManager.setFav(mStatusId);
            EventBus.getDefault().post(new StatusActionEvent());
            MessageUtil.showToast(R.string.toast_destroy_favorite_failure);
        }
    }
}
