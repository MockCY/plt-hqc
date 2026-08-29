package com.qinglian.fitness.favorite;

import com.qinglian.fitness.auth.CurrentUser;
import com.qinglian.fitness.common.ApiException;
import com.qinglian.fitness.favorite.FavoriteDtos.FavoriteState;
import com.qinglian.fitness.favorite.FavoriteDtos.FavoriteView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteRepository repository;

    public FavoriteController(FavoriteRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<FavoriteView> favorites(HttpServletRequest request, @RequestParam String type) {
        return repository.findAll(CurrentUser.id(request), normalizeType(type));
    }

    @PutMapping("/{type}/{itemId}")
    public FavoriteState add(HttpServletRequest request, @PathVariable String type, @PathVariable long itemId) {
        return new FavoriteState(repository.add(CurrentUser.id(request), normalizeType(type), itemId));
    }

    @DeleteMapping("/{type}/{itemId}")
    public FavoriteState remove(HttpServletRequest request, @PathVariable String type, @PathVariable long itemId) {
        return new FavoriteState(repository.remove(CurrentUser.id(request), normalizeType(type), itemId));
    }

    private String normalizeType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!"COURSE".equals(type) && !"EXERCISE".equals(type)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FAVORITE_TYPE", "收藏类型仅支持课程或动作");
        }
        return type;
    }
}
