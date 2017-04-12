package info.justaway.task;

import android.os.AsyncTask;

import de.greenrobot.event.EventBus;
import info.justaway.R;
import info.justaway.event.action.StatusActionEvent;
import info.justaway.model.FavRetweetManager;
import info.justaway.model.TwitterManager;
import info.justaway.util.MessageUtil;
import twitter4j.TwitterException;

public class FavoriteTask extends AsyncTask<Void, Void, TwitterException> {

    private long mStatusId;

    public FavoriteTask(long statusId) {
        mStatusId = statusId;

        EventBus.getDefault().post(new StatusActionEvent());
    }

    @Override
    protected TwitterException doInBackground(Void... params) {
        try {
            TwitterManager.getTwitter().createFavorite(mStatusId);
        } catch (TwitterException e) {
            return e;
        }
        return null;
    }

    @Override
    protected void onPostExecute(TwitterException e) {
        if (e == null) {
            MessageUtil.showToast(R.string.toast_favorite_success);
        } else if (e.getErrorCode() == 139) {
            MessageUtil.showToast(R.string.toast_favorite_already);
        } else {
            EventBus.getDefault().post(new StatusActionEvent());
            MessageUtil.showToast(R.string.toast_favorite_failure);
        }
    }
}
