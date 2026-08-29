package com.qinglian.fitness.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void createsUserWhenOpenIdDoesNotExist() {
        User user = userRepository.findOrCreate("new-openid", "new-unionid");

        assertThat(user.id()).isPositive();
        assertThat(user.openId()).isEqualTo("new-openid");
        assertThat(user.unionId()).isEqualTo("new-unionid");
        assertThat(user.status()).isEqualTo("ACTIVE");
    }

    @Test
    void returnsExistingUserWhenOpenIdAlreadyExists() {
        User firstLogin = userRepository.findOrCreate("same-openid", "same-unionid");
        User secondLogin = userRepository.findOrCreate("same-openid", "same-unionid");

        assertThat(secondLogin.id()).isEqualTo(firstLogin.id());
    }
}
