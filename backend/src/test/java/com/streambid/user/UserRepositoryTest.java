package com.streambid.user;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
@AutoConfigureMockMvc
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

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void rotatesSessionAndCsrfTokenAfterLogin() throws Exception {
        registration.register(new RegisterRequest(
                "csrf@example.com", "strong-password", "CSRF", UserRole.STREAMER));
        var before = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        var session = (MockHttpSession) before.getRequest().getSession(false);
        String oldSessionId = session.getId();
        String oldToken = json.readTree(before.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/auth/login").session(session).header("X-CSRF-TOKEN", oldToken)
                .param("email", "csrf@example.com").param("password", "strong-password"))
                .andExpect(status().isNoContent());
        assertThat(session.getId()).isNotEqualTo(oldSessionId);
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", oldToken))
                .andExpect(status().isForbidden());
        var after = mvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn();
        String newToken = json.readTree(after.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-TOKEN", newToken))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
    }
    @Test
    void validationDoesNotExposePassword() throws Exception {
        String body = """
                {"email":"valid@example.com","password":"secret",
                "displayName":"Valid","role":"STREAMER"}
                """;
        var result = mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0]").value("password"))
                .andExpect(jsonPath("$.detail").value("Invalid request data")).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("secret");
    }

    @Test
    void malformedJsonReturnsSafeError() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid JSON request"));
    }
    @Test
    void logsInReadsProfileAndLogsOut() throws Exception {
        registration.register(new RegisterRequest(
                "login@example.com", "strong-password", "Login", UserRole.STREAMER));
        var result = mvc.perform(post("/api/auth/login").with(csrf())
                .param("email", "LOGIN@EXAMPLE.COM").param("password", "strong-password"))
                .andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/api/users/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongLoginAndMissingCsrf() throws Exception {
        mvc.perform(post("/api/auth/login").with(csrf())
                .param("email", "missing@example.com").param("password", "wrong-password"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").param("email", "missing@example.com"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registersThroughApiAndRejectsDuplicate() throws Exception {
        String body = """
                {"email":"api@example.com","password":"strong-password",
                "displayName":"API User","role":"ADVERTISER"}
                """;
        mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("API User"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidRegistrationData() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
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
