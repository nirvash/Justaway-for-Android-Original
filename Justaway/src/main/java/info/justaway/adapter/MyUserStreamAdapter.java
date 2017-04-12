package info.justaway.adapter;

import android.os.AsyncTask;
import android.os.Handler;

import java.util.ArrayList;

import de.greenrobot.event.EventBus;
import info.justaway.event.model.NotificationEvent;
import info.justaway.event.model.StreamingCreateFavoriteEvent;
import info.justaway.event.model.StreamingCreateStatusEvent;
import info.justaway.event.model.StreamingDestroyMessageEvent;
import info.justaway.event.model.StreamingDestroyStatusEvent;
import info.justaway.event.model.StreamingUnFavoriteEvent;
import info.justaway.event.model.StreamingUpdateSelfFavoriteEvent;
import info.justaway.model.AccessTokenManager;
import info.justaway.model.FavRetweetManager;
import info.justaway.model.Relationship;
import info.justaway.model.Row;
import info.justaway.model.TwitterManager;
import info.justaway.settings.MuteSettings;
import twitter4j.DirectMessage;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.User;
import twitter4j.UserStreamAdapter;

public class MyUserStreamAdapter extends UserStreamAdapter {

    private boolean mStopped;
    private boolean mPause;
    private ArrayList<StreamingCreateStatusEvent> mStreamingCreateStatusEvents = new ArrayList<>();
    private ArrayList<StreamingDestroyStatusEvent> mStreamingDestroyStatusEvents = new ArrayList<>();
    private ArrayList<StreamingCreateFavoriteEvent> mStreamingCreateFavoriteEvents = new ArrayList<>();
    private ArrayList<StreamingUnFavoriteEvent> mStreamingUnFavoriteEvents = new ArrayList<>();
    private ArrayList<StreamingDestroyMessageEvent> mStreamingDestroyMessageEvents = new ArrayList<>();

    public void stop() {
        mStopped = true;
    }

    public void start() {
        mStopped = false;
    }

    public void pause() {
        mPause = true;
    }

    public void resume() {
        mPause = false;
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                for (StreamingCreateStatusEvent event : mStreamingCreateStatusEvents) {
                    EventBus.getDefault().post(event);
                }
                for (StreamingDestroyStatusEvent event : mStreamingDestroyStatusEvents) {
                    EventBus.getDefault().post(event);
                }
                for (StreamingCreateFavoriteEvent event : mStreamingCreateFavoriteEvents) {
                    EventBus.getDefault().post(event);
                }
                for (StreamingUnFavoriteEvent event : mStreamingUnFavoriteEvents) {
                    EventBus.getDefault().post(event);
                }
                for (StreamingDestroyMessageEvent event : mStreamingDestroyMessageEvents) {
                    EventBus.getDefault().post(event);
                }
                mStreamingCreateStatusEvents.clear();
                mStreamingDestroyStatusEvents.clear();
                mStreamingCreateFavoriteEvents.clear();
                mStreamingUnFavoriteEvents.clear();
                mStreamingDestroyMessageEvents.clear();
            }
        });
    }

    @Override
    public void onStatus(Status status) {
        if (mStopped) {
            return;
        }
        if (!Relationship.isVisible(status)) {
            return;
        }
        Row row = Row.newStatus(status);
        if (MuteSettings.isMute(row)) {
            return;
        }
        long userId = AccessTokenManager.getUserId();
        Status retweetedStatus = status.getRetweetedStatus();
        if (status.getInReplyToUserId() == userId || (retweetedStatus != null && retweetedStatus.getUser().getId() == userId)) {
            EventBus.getDefault().post(new NotificationEvent(row));
        }
        if (mPause) {
            mStreamingCreateStatusEvents.add(new StreamingCreateStatusEvent(row));
        } else {
            EventBus.getDefault().post(new StreamingCreateStatusEvent(row));
        }
    }

    @Override
    public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
        if (mStopped) {
            return;
        }
        if (mPause) {
            mStreamingDestroyStatusEvents.add(new StreamingDestroyStatusEvent(statusDeletionNotice.getStatusId()));
        } else {
            EventBus.getDefault().post(new StreamingDestroyStatusEvent(statusDeletionNotice.getStatusId()));
        }
    }

    @Override
    public void onFavorite(User source, User target, Status status) {
        if (mStopped) {
            return;
        }
        Row row = Row.newFavorite(source, target, status);
        // 自分の fav を反映
        if (source.getId() == AccessTokenManager.getUserId()) {
            row.setFavorite(true); // 自分で Fav したフラグを立てる (なぜか status.isFavorited は false になっている)
            EventBus.getDefault().post(new StreamingUpdateSelfFavoriteEvent(status.getId(), true));
            EventBus.getDefault().post(new StreamingCreateFavoriteEvent(row));
            return;
        }
        EventBus.getDefault().post(new NotificationEvent(row));
        new AsyncTask<Row, Void, twitter4j.Status>(){
            private Row mRow;
            @Override
            protected twitter4j.Status doInBackground(Row... params) {
                mRow = params[0];
                try {
                    return TwitterManager.getTwitter().showStatus(mRow.getStatus().getId());
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }

            @Override
            protected void onPostExecute(twitter4j.Status status) {
                if (status != null) {
                    mRow.setStatus(status);
                }
                if (mPause) {
                    mStreamingCreateFavoriteEvents.add(new StreamingCreateFavoriteEvent(mRow));
                } else {
                    EventBus.getDefault().post(new StreamingCreateFavoriteEvent(mRow));
                }
            }
        }.execute(row);
    }

    @Override
    public void onUnfavorite(User arg0, User arg1, Status arg2) {
        if (mStopped) {
            return;
        }
        // 自分の unfav を反映
        if (arg0.getId() == AccessTokenManager.getUserId()) {
            EventBus.getDefault().post(new StreamingUpdateSelfFavoriteEvent(arg2.getId(), false));
        }
        if (mPause) {
            mStreamingUnFavoriteEvents.add(new StreamingUnFavoriteEvent(arg0, arg2));
        } else {
            EventBus.getDefault().post(new StreamingUnFavoriteEvent(arg0, arg2));
        }
    }

    @Override
    public void onDirectMessage(DirectMessage directMessage) {
        if (mStopped) {
            return;
        }
        Row row = Row.newDirectMessage(directMessage);
        if (MuteSettings.isMute(row)) {
            return;
        }
        EventBus.getDefault().post(new NotificationEvent(row));
        if (mPause) {
            mStreamingCreateStatusEvents.add(new StreamingCreateStatusEvent(row));
        } else {
            EventBus.getDefault().post(new StreamingCreateStatusEvent(row));
        }
    }

    @Override
    public void onDeletionNotice(long directMessageId, long userId) {
        if (mStopped) {
            return;
        }
        if (mPause) {
            mStreamingDestroyMessageEvents.add(new StreamingDestroyMessageEvent(directMessageId));
        } else {
            EventBus.getDefault().post(new StreamingDestroyMessageEvent(directMessageId));
        }
    }
}
