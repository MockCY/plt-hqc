package com.qinglian.fitness.auth;

import com.qinglian.fitness.auth.AuthDtos.LoginResponse;
import com.qinglian.fitness.auth.AuthDtos.UserView;
import com.qinglian.fitness.common.ApiException;
import com.qinglian.fitness.user.User;
import com.qinglian.fitness.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final WechatGateway wechatGateway;
    private final UserRepository userRepository;
    private final SessionService sessionService;

    public AuthService(
        WechatGateway wechatGateway,
        UserRepository userRepository,
        SessionService sessionService
    ) {
        this.wechatGateway = wechatGateway;
        this.userRepository = userRepository;
        this.sessionService = sessionService;
    }

    @Transactional
    public LoginResponse login(String loginCode) {
        WechatIdentity identity = wechatGateway.exchangeLoginCode(loginCode);
        User user = userRepository.findOrCreate(identity.openId(), identity.unionId());
        ensureActive(user);
        return loginResponse(user);
    }

    @Transactional
    public LoginResponse loginWithPhone(String loginCode, String phoneCode) {
        WechatIdentity identity = wechatGateway.exchangeLoginCode(loginCode);
        WechatPhone phone = wechatGateway.exchangePhoneCode(phoneCode);
        User user = userRepository.findOrCreate(identity.openId(), identity.unionId());
        ensureActive(user);
        user = userRepository.bindPhone(user.id(), phone.purePhoneNumber(), phone.countryCode());
        return loginResponse(user);
    }

    private LoginResponse loginResponse(User user) {
        SessionService.CreatedSession session = sessionService.create(user.id());
        return new LoginResponse(session.token(), session.expiresAt(), UserView.from(user));
    }

    private void ensureActive(User user) {
        if (!"ACTIVE".equals(user.status())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_UNAVAILABLE", "账号不可用，请联系管理员");
        }
    }
}
