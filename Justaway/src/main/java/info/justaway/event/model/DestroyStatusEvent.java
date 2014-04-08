package info.justaway.event.model;

public class DestroyStatusEvent {

    private final Long mStatusId;

    public DestroyStatusEvent(final Long statusId) {
        mStatusId = statusId;
    }

    public Long getStatusId() {
        return mStatusId;
    }
}