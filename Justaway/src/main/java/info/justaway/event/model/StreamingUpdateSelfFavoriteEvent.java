package info.justaway.event.model;

import com.google.common.base.CharMatcher;

public class StreamingUpdateSelfFavoriteEvent {
    private final long id;
    private final boolean isFavorited;

    public StreamingUpdateSelfFavoriteEvent(long id, boolean isFavorited) {
        this.id = id;
        this.isFavorited = isFavorited;
    }

    public long getId() {
        return id;
    }

    public boolean isFavorited() {
        return isFavorited;
    }
}
