package com.qinglian.fitness.mapper;

import com.qinglian.fitness.favorite.FavoriteDtos.FavoriteView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FavoriteMapper {

    List<FavoriteView> findAll(@Param("userId") long userId, @Param("itemType") String itemType);

    int add(@Param("userId") long userId, @Param("itemType") String itemType, @Param("itemId") long itemId);

    int remove(@Param("userId") long userId, @Param("itemType") String itemType, @Param("itemId") long itemId);

    int count(@Param("userId") long userId, @Param("itemType") String itemType, @Param("itemId") long itemId);
}
