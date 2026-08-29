package com.qinglian.fitness.user;

import com.qinglian.fitness.auth.AuthDtos.UserView;
import com.qinglian.fitness.auth.CurrentUser;
import com.qinglian.fitness.common.ApiException;
import com.qinglian.fitness.user.ProfileDtos.ProfileView;
import com.qinglian.fitness.user.ProfileDtos.UpdateProfileRequest;
import com.qinglian.fitness.user.ProfileDtos.UpdateSettingsRequest;
import com.qinglian.fitness.user.ProfileDtos.UserSettings;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class ProfileController {

    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;

    public ProfileController(UserRepository userRepository, ProfileRepository profileRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

    @GetMapping
    public ProfileView me(HttpServletRequest request) {
        long userId = CurrentUser.id(request);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        return new ProfileView(UserView.from(user), profileRepository.settings(userId));
    }

    @PutMapping
    public UserView updateProfile(
        HttpServletRequest request,
        @Valid @RequestBody UpdateProfileRequest updateRequest
    ) {
        return UserView.from(profileRepository.updateProfile(CurrentUser.id(request), updateRequest));
    }

    @PutMapping("/settings")
    public UserSettings updateSettings(
        HttpServletRequest request,
        @RequestBody UpdateSettingsRequest updateRequest
    ) {
        return profileRepository.updateSettings(CurrentUser.id(request), updateRequest);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(HttpServletRequest request) {
        userRepository.deleteById(CurrentUser.id(request));
    }
}
