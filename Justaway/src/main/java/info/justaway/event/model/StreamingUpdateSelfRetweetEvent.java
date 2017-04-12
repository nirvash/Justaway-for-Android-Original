package info.justaway.event.model;

public class StreamingUpdateSelfRetweetEvent {
    private final long statusId;
    private final boolean isRetweeted;

    public StreamingUpdateSelfRetweetEvent(long statusId, boolean isRetweeted) {
        this.statusId = statusId;
        this.isRetweeted = isRetweeted;
    }

    public long getId() {
        return statusId;
    }

    public boolean isRetweeted() {
        return isRetweeted;
    }
}
