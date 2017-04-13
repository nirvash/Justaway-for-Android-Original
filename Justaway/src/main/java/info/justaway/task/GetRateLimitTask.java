package info.justaway.task;

import android.app.DialogFragment;
import android.os.AsyncTask;
import android.os.Bundle;

import de.greenrobot.event.EventBus;
import info.justaway.event.AlertDialogEvent;
import info.justaway.fragment.dialog.RateLimitFragment;
import info.justaway.model.TwitterManager;
import twitter4j.RateLimitStatus;
import twitter4j.TwitterException;

public class GetRateLimitTask extends AsyncTask<Void, Void, String> {
    @Override
    protected String doInBackground(Void... voids) {
        String text = "";
        try {
            RateLimitStatus status = TwitterManager.getTwitter().getHomeTimeline().getRateLimitStatus();
            text = String.format("Limit %d/%d\nReset %dsec", status.getRemaining(), status.getLimit(), status.getSecondsUntilReset());
        } catch (TwitterException e) {
            e.printStackTrace();
        }

        return text;
    }

    @Override
    protected void onPostExecute(String text) {
        RateLimitFragment dialog = new RateLimitFragment();
        Bundle args = new Bundle(1);
        args.putSerializable("text", text);
        dialog.setArguments(args);
        EventBus.getDefault().post(new AlertDialogEvent(dialog));
    }
}