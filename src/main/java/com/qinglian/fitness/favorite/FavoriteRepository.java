package com.qinglian.fitness.favorite;

import com.qinglian.fitness.favorite.FavoriteDtos.FavoriteView;
import com.qinglian.fitness.mapper.FavoriteMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FavoriteRepository {

    private final FavoriteMapper favoriteMapper;

    public FavoriteRepository(FavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    public List<FavoriteView> findAll(long userId, String itemType) {
        return favoriteMapper.findAll(userId, itemType);
    }

    public boolean add(long userId, String itemType, long itemId) {
        if (exists(userId, itemType, itemId)) {
            return true;
        }
        favoriteMapper.add(userId, itemType, itemId);
        return true;
    }

    public boolean remove(long userId, String itemType, long itemId) {
        favoriteMapper.remove(userId, itemType, itemId);
        return false;
    }

    private boolean exists(long userId, String itemType, long itemId) {
        return favoriteMapper.count(userId, itemType, itemId) > 0;
    }
}
