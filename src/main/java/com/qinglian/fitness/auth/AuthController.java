package com.qinglian.fitness.auth;

import com.qinglian.fitness.auth.AuthDtos.LoginRequest;
import com.qinglian.fitness.auth.AuthDtos.LoginResponse;
import com.qinglian.fitness.auth.AuthDtos.PhoneLoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionService sessionService;

    public AuthController(AuthService authService, SessionService sessionService) {
        this.authService = authService;
        this.sessionService = sessionService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.code());
    }

    @PostMapping("/wechat-phone")
    public LoginResponse loginWithPhone(@Valid @RequestBody PhoneLoginRequest request) {
        return authService.loginWithPhone(request.loginCode(), request.phoneCode());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        sessionService.revoke(CurrentUser.rawToken(request));
    }
}
