package com.streambid.user;

import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.streambid.creative.S3CreativeStorage creativeObjects;
    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void advertiserUploadsActualImageWithoutTrustingFilenameOrMimeType() throws Exception {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, "creative-http@example.com", "hash", "Advertiser"));
        users.addRole(owner, UserRole.ADVERTISER);
        byte[] bytes = java.util.Base64.getDecoder().decode("UklGRh4AAABXRUJQVlA4TBEAAAAvAUAAAAdQslKUtv+BiOh/AAA=");
        var file = new org.springframework.mock.web.MockMultipartFile("file", "../../fake.jpg", "text/plain", bytes);
        try {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/creatives")
                    .file(file).param("title", "Banner").with(user("creative-http@example.com")).with(csrf()))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.format").value("WEBP"))
                    .andExpect(jsonPath("$.widthPx").value(2)).andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.storageKey").doesNotExist()).andExpect(jsonPath("$.sha256").doesNotExist());
            assertThat(creatives.listOwned(owner, null, 20)).hasSize(1);
            org.mockito.Mockito.verify(creativeObjects).put(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.eq(com.streambid.creative.CreativeFormat.WEBP), org.mockito.ArgumentMatchers.eq(bytes));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/creatives")
                    .file(file).param("title", "Banner").with(user("creative-http@example.com")))
                    .andExpect(status().isForbidden());
            var invalid = new org.springframework.mock.web.MockMultipartFile("file", "image.png", "image/png", new byte[0]);
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/creatives")
                    .file(invalid).param("title", "Banner").with(user("creative-http@example.com")).with(csrf()))
                    .andExpect(status().isBadRequest());
            assertThat(creatives.listOwned(owner, null, 20)).hasSize(1);
        } finally {
            for (var creative : creatives.listOwned(owner, null, 100)) {
                creativeDatabase.execute("DELETE FROM ad_creatives WHERE id = ?", creative.id());
            }
            creativeDatabase.execute("DELETE FROM users WHERE id = ?", owner);
        }
    }
    @Autowired org.jooq.DSLContext creativeDatabase;
    @Autowired com.streambid.creative.AdCreativeRepository creatives;
    @Test
    void creativeMetadataIsOwnerScopedAndAlwaysStartsPending() {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, "creative-owner@example.com", "hash", "Advertiser"));
        users.addRole(owner, UserRole.ADVERTISER);
        var input = new com.streambid.creative.AdCreative(UUID.randomUUID(), owner, "Banner",
                com.streambid.creative.CreativeFormat.PNG, 320, 180, 100, "a".repeat(64),
                com.streambid.creative.CreativeStatus.APPROVED, 99, java.time.Instant.now());
        var saved = creatives.insertPending(input);
        assertThat(saved.status()).isEqualTo(com.streambid.creative.CreativeStatus.PENDING);
        assertThat(saved.version()).isZero();
        assertThat(saved.storageKey()).isEqualTo(input.storageKey());
        assertThat(creatives.findOwned(saved.id(), owner)).contains(saved);
        assertThat(creatives.findOwned(saved.id(), UUID.randomUUID())).isEmpty();
        assertThat(creatives.listOwned(owner, null, 20)).containsExactly(saved);
        assertThat(creatives.listOwned(owner, saved.id(), 20)).isEmpty();
        assertThat(creatives.listOwned(UUID.randomUUID(), null, 20)).isEmpty();
        assertThatThrownBy(() -> creatives.listOwned(owner, null, 101)).isInstanceOf(IllegalArgumentException.class);
    }

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
    void roleChangeInvalidatesSessionAndAppliesOnNextLogin() throws Exception {
        registration.register(new RegisterRequest(
                "refresh@example.com", "strong-password", "Refresh", UserRole.STREAMER));
        var login = mvc.perform(post("/api/auth/login").with(csrf())
                .param("email", "refresh@example.com").param("password", "strong-password"))
                .andExpect(status().isNoContent()).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(post("/api/users/me/roles").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADVERTISER\"}"))
                .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        var nextLogin = mvc.perform(post("/api/auth/login").with(csrf())
                .param("email", "refresh@example.com").param("password", "strong-password"))
                .andExpect(status().isNoContent()).andReturn();
        var nextSession = (MockHttpSession) nextLogin.getRequest().getSession(false);
        mvc.perform(get("/api/users/me").session(nextSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles.length()").value(2));
        assertThat(accounts.loadUserByUsername("refresh@example.com").getAuthorities())
                .extracting("authority").contains("ROLE_ADVERTISER");
    }

    @Test
    void roleMutationRequiresLoginAndKnownRole() throws Exception {
        mvc.perform(post("/api/users/me/roles").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"STREAMER\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users/me/roles").with(user("any@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test
    void addsSecondPublicRoleButRejectsAdmin() throws Exception {
        var profile = registration.register(new RegisterRequest(
                "second@example.com", "strong-password", "Second", UserRole.STREAMER));
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post("/api/users/me/roles").with(user("second@example.com")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADVERTISER\"}"))
                    .andExpect(status().isNoContent());
        }
        assertThat(users.findRoles(profile.id())).containsExactlyInAnyOrder("STREAMER", "ADVERTISER");
        mvc.perform(post("/api/users/me/roles").with(user("second@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        assertThat(users.findRoles(profile.id())).doesNotContain("ADMIN");
    }
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
