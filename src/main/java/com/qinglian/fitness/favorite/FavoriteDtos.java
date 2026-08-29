package com.qinglian.fitness.favorite;

import java.time.Instant;

public final class FavoriteDtos {

    private FavoriteDtos() {
    }

    public record FavoriteView(String itemType, long itemId, Instant createdAt) {
    }

    public record FavoriteState(boolean favorite) {
    }
}
