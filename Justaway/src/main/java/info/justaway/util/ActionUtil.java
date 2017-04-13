package info.justaway.util;

import android.content.Context;
import android.content.Intent;

import de.greenrobot.event.EventBus;
import info.justaway.MainActivity;
import info.justaway.PostActivity;
import info.justaway.event.action.OpenEditorEvent;
import info.justaway.model.AccessTokenManager;
import info.justaway.task.DestroyDirectMessageTask;
import info.justaway.task.DestroyStatusTask;
import info.justaway.task.FavoriteTask;
import info.justaway.task.RetweetTask;
import info.justaway.task.UnFavoriteTask;
import info.justaway.task.UnRetweetTask;
import twitter4j.DirectMessage;
import twitter4j.UserMentionEntity;

public class ActionUtil {
    public static void doFavorite(Long statusId) {
        doFavorite(statusId, true);
    }
    public static void doFavorite(Long statusId, boolean showToast) {
        new FavoriteTask(statusId, showToast).execute();
    }


    public static void doDestroyFavorite(Long statusId) {
        new UnFavoriteTask(statusId).execute();
    }

    public static void doDestroyStatus(Long statusId) {
        new DestroyStatusTask(statusId).execute();
    }

    public static void doRetweet(Long statusId) {
        new RetweetTask(statusId).execute();
    }

    public static void doDestroyRetweet(twitter4j.Status status, long currentUserRetweetId) {
        long targetId = status.isRetweet() ? status.getRetweetedStatus().getId() : status.getId();
        new UnRetweetTask(targetId, currentUserRetweetId).execute();
    }

    public static void doReply(twitter4j.Status status, Context context) {
        Long userId = AccessTokenManager.getUserId();
        UserMentionEntity[] mentions = status.getUserMentionEntities();
        String text;
        if (status.getUser().getId() == userId && mentions.length == 1) {
            text = "@" + mentions[0].getScreenName() + " ";
        } else {
            text = "@" + status.getUser().getScreenName() + " ";
        }
        if (context instanceof MainActivity) {
            EventBus.getDefault().post(new OpenEditorEvent(text, status, text.length(), null));
        } else {
            Intent intent = new Intent(context, PostActivity.class);
            intent.putExtra("status", text);
            intent.putExtra("selection", text.length());
            intent.putExtra("inReplyToStatus", status);
            context.startActivity(intent);
        }
    }

    public static void doReplyAll(twitter4j.Status status, Context context) {
        long userId = AccessTokenManager.getUserId();
        UserMentionEntity[] mentions = status.getUserMentionEntities();
        String text = "";
        int selection_start = 0;
        if (status.getUser().getId() != userId) {
            text = "@" + status.getUser().getScreenName() + " ";
            selection_start = text.length();
        }
        for (UserMentionEntity mention : mentions) {
            if (status.getUser().getId() == mention.getId()) {
                continue;
            }
            if (userId == mention.getId()) {
                continue;
            }
            text = text.concat("@" + mention.getScreenName() + " ");
            if (selection_start == 0) {
                selection_start = text.length();
            }
        }
        if (context instanceof MainActivity) {
            EventBus.getDefault().post(new OpenEditorEvent(text, status, selection_start, text.length()));
        } else {
            Intent intent = new Intent(context, PostActivity.class);
            intent.putExtra("status", text);
            intent.putExtra("selection", selection_start);
            intent.putExtra("selection_stop", text.length());
            intent.putExtra("inReplyToStatus", status);
            context.startActivity(intent);
        }
    }

    public static void doReplyDirectMessage(DirectMessage directMessage, Context context) {
        String text;
        if (AccessTokenManager.getUserId() == directMessage.getSender().getId()) {
            text = "D " + directMessage.getRecipient().getScreenName() + " ";
        } else {
            text = "D " + directMessage.getSender().getScreenName() + " ";
        }
        if (context instanceof MainActivity) {
            EventBus.getDefault().post(new OpenEditorEvent(text, null, text.length(), null));
        } else {
            Intent intent = new Intent(context, PostActivity.class);
            intent.putExtra("status", text);
            intent.putExtra("selection", text.length());
            context.startActivity(intent);
        }
    }

    public static void doDestroyDirectMessage(long id) {
        new DestroyDirectMessageTask().execute(id);
    }

    public static void doQuote(twitter4j.Status status, Context context) {
        String text = " https://twitter.com/"
                + status.getUser().getScreenName()
                + "/status/" + String.valueOf(status.getId());
        if (context instanceof MainActivity) {
            EventBus.getDefault().post(new OpenEditorEvent(text, status, null, null));
        } else {
            Intent intent = new Intent(context, PostActivity.class);
            intent.putExtra("status", text);
            intent.putExtra("inReplyToStatus", status);
            context.startActivity(intent);
        }
    }
}
