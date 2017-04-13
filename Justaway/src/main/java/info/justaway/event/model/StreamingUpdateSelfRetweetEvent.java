package info.justaway.event.model;

public class StreamingUpdateSelfRetweetEvent {
    private final long statusId;
    private final long rtId;
    private final boolean isRetweeted;

    public StreamingUpdateSelfRetweetEvent(long statusId, long rtId, boolean isRetweeted) {
        this.statusId = statusId;
        this.rtId = rtId;
        this.isRetweeted = isRetweeted;
    }

    public long getId() {
        return statusId;
    }

    public long getRtId() { return rtId; }

    public boolean isRetweeted() {
        return isRetweeted;
    }
}
