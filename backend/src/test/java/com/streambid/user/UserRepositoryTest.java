package com.streambid.user;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@Transactional
class UserRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    UserRepository users;

    @Test
    void savesAndFindsUser() {
        UserAccount user = new UserAccount(UUID.randomUUID(), "user@example.com", "hash", "User");
        users.save(user);

        assertThat(users.findByEmail(user.email())).contains(user);
    }

    @Test
    void returnsEmptyForUnknownEmail() {
        assertThat(users.findByEmail("missing@example.com")).isEmpty();
    }

    @Test
    void rejectsDuplicateEmail() {
        users.save(new UserAccount(UUID.randomUUID(), "same@example.com", "hash", "First"));

        assertThatThrownBy(() -> users.save(new UserAccount(
                UUID.randomUUID(), "same@example.com", "hash", "Second")))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
