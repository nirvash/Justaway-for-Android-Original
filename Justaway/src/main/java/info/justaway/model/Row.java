package info.justaway.model;

import twitter4j.DirectMessage;
import twitter4j.Status;
import twitter4j.User;

public class Row {

    private final static int TYPE_STATUS = 0;
    private final static int TYPE_FAVORITE = 1;
    private final static int TYPE_DM = 2;

    private final static int FAV_STATUS = 0;
    private final static int FAV_FAVORITE = 1;
    private final static int FAV_UNFAVORITE = 2;

    private final static int RT_STATUS = 0;
    private final static int RT_RETWEETED = 1;
    private final static int RT_NOT_RETWEETED = 2;

    private Status status;
    private DirectMessage message;
    private User source;
    private User target;
    private int type;
    private int favorited = FAV_STATUS;
    private int retweeeted = RT_STATUS;
    private long currentUserRetweetId = -1;

    public Row() {
        super();
    }

    public static Row newStatus(Status status) {
        Row row = new Row();
        row.setStatus(status);
        row.setType(TYPE_STATUS);
        return row;
    }

    public static Row newFavorite(User source, User target, Status status) {
        Row row = new Row();
        row.setStatus(status);
        row.setTarget(target);
        row.setSource(source);
        row.setType(TYPE_FAVORITE);
        return row;
    }

    public static Row newDirectMessage(DirectMessage message) {
        Row row = new Row();
        row.setMessage(message);
        row.setType(TYPE_DM);
        return row;
    }

    public boolean isStatus() {
        return type == TYPE_STATUS;
    }

    public boolean isFavorite() {
        return type == TYPE_FAVORITE;
    }

    public boolean isDirectMessage() {
        return type == TYPE_DM;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public DirectMessage getMessage() {
        return message;
    }

    public void setMessage(DirectMessage message) {
        this.message = message;
    }

    public User getSource() {
        return source;
    }

    public void setSource(User source) {
        this.source = source;
    }

    public User getTarget() {
        return target;
    }

    public void setTarget(User target) {
        this.target = target;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void setFavorite(boolean favorite) {
        this.favorited = favorite ? FAV_FAVORITE : FAV_UNFAVORITE;
    }

    public void setRetweet(boolean retweeted) {
        this.retweeeted = retweeted ? RT_RETWEETED : RT_NOT_RETWEETED;
    }

    public void setCurrentUserRetweetId(long id) {
        this.currentUserRetweetId = id;
    }

    public long getCurrentUserRetweetId() {
        if (this.currentUserRetweetId == -1) {
            if (this.status.getCurrentUserRetweetId() != -1) {
                return this.status.getCurrentUserRetweetId();
            }
            if (this.status.isRetweet() && this.status.getUser().getId() == AccessTokenManager.getUserId()) {
                return this.status.getId();
            }
        }
        return this.currentUserRetweetId;
    }

    public boolean isFavorited() {
        if (this.favorited == FAV_STATUS) {
            if (this.status.isRetweet()) {
                return this.status.getRetweetedStatus().isFavorited();
            } else {
                return this.status.isFavorited();
            }
        } else {
            return this.favorited == FAV_FAVORITE;
        }
    }

    public boolean isRetweeted() {
        if (this.retweeeted == RT_STATUS) {
            if (this.status.isRetweet() && this.status.getUser().getId() == AccessTokenManager.getUserId()) {
                return true;
            }
            return this.status.isRetweeted();
        } else {
            return this.retweeeted == RT_RETWEETED;
        }
    }
}
