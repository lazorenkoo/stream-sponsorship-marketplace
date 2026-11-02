package com.streambid.user;

import java.util.UUID;
import com.streambid.security.AccountDetailsService;
import com.streambid.auth.RegisterRequest;
import com.streambid.auth.RegistrationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
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

    @Autowired
    RegistrationService registration;

    @Autowired
    PasswordEncoder passwords;

    @Autowired
    AccountDetailsService accounts;

    @Test
    void loadsAccountWithBothRoles() {
        var profile = registration.register(new RegisterRequest(
                "roles@example.com", "strong-password", "Roles", UserRole.STREAMER));
        users.addRole(profile.id(), UserRole.ADVERTISER);

        var account = accounts.loadUserByUsername("ROLES@EXAMPLE.COM");
        assertThat(account.getUsername()).isEqualTo("roles@example.com");
        assertThat(account.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_STREAMER", "ROLE_ADVERTISER");
        assertThat(passwords.matches("strong-password", account.getPassword())).isTrue();
    }
    @Test
    void registersWithNormalizedEmailAndHashedPassword() {
        var profile = registration.register(new RegisterRequest(
                "New@Example.com", "strong-password", " New User ", UserRole.STREAMER));
        UserAccount saved = users.findByEmail("new@example.com").orElseThrow();
        assertThat(saved.id()).isEqualTo(profile.id());
        assertThat(saved.displayName()).isEqualTo("New User");
        assertThat(saved.passwordHash()).isNotEqualTo("strong-password");
        assertThat(passwords.matches("strong-password", saved.passwordHash())).isTrue();
    }

    @Test
    void preventsAdminRegistration() {
        assertThatThrownBy(() -> registration.register(new RegisterRequest(
                "admin@example.com", "strong-password", "Admin", UserRole.ADMIN)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
        assertThat(users.findByEmail("admin@example.com")).isEmpty();
    }

    @Test
    void rejectsPasswordsExceedingBcryptByteLimit() {
        assertThatThrownBy(() -> registration.register(new RegisterRequest(
                "long@example.com", "я".repeat(40), "Long", UserRole.ADVERTISER)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }
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
