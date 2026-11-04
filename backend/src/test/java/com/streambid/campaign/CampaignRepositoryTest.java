package com.streambid.campaign;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.streambid.user.UserRole;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.streambid.user.UserAccount;
import com.streambid.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@AutoConfigureMockMvc
@SpringBootTest
@Testcontainers
@Transactional
class CampaignRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired CampaignRepository campaigns;
    @Autowired UserRepository users;

    @Autowired MockMvc mvc;
    @Test
    void campaignListIsOwnedPagedAndValidatesCursor() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        users.save(new UserAccount(owner, "list-owner@example.com", "hash", "Owner"));
        users.save(new UserAccount(other, "list-other@example.com", "hash", "Other"));
        users.addRole(owner, UserRole.ADVERTISER);
        users.addRole(other, UserRole.STREAMER);
        var now = Instant.parse("2026-11-04T18:20:00Z");
        var first = new Campaign(new UUID(0, 10), owner, "First", "", CampaignStatus.DRAFT, now, now);
        var second = new Campaign(new UUID(0, 11), owner, "Second", "", CampaignStatus.DRAFT, now, now);
        campaigns.insert(first);
        campaigns.insert(second);
        mvc.perform(get("/api/campaigns").with(user("list-owner@example.com")).param("limit", "1")
                .param("advertiserId", other.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(second.id().toString())).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/campaigns").with(user("list-owner@example.com")).param("limit", "1")
                .param("before", now.toString()).param("beforeId", second.id().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(first.id().toString()));
        for (String invalid : new String[] {"0", "101", "text"}) {
            mvc.perform(get("/api/campaigns").with(user("list-owner@example.com")).param("limit", invalid))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/campaigns").with(user("list-owner@example.com")).param("before", now.toString()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/campaigns").with(user("list-owner@example.com")).param("beforeId", "invalid"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/campaigns").with(user("list-other@example.com"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/campaigns")).andExpect(status().isUnauthorized());
        users.addRole(other, UserRole.ADVERTISER);
        mvc.perform(get("/api/campaigns").with(user("list-other@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    @Test
    void rejectsInvalidDraftContentWithoutPersistingCampaigns() throws Exception {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, "invalid-draft@example.com", "hash", "Owner"));
        users.addRole(owner, UserRole.ADVERTISER);
        for (CampaignDraftRequest invalid : new CampaignDraftRequest[] {
                new CampaignDraftRequest(null, ""), new CampaignDraftRequest(" ", ""),
                new CampaignDraftRequest("x".repeat(121), ""), new CampaignDraftRequest("Line\nbreak", ""),
                new CampaignDraftRequest("Title", null), new CampaignDraftRequest("Title", "x".repeat(5001)),
                new CampaignDraftRequest("Title", "null\0character")}) {
            mvc.perform(post("/api/campaigns").with(user("invalid-draft@example.com")).with(csrf())
                    .contentType("application/json").content(json.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }
        assertThat(campaigns.listOwned(owner, null, null, 100)).isEmpty();
    }

    @Test
    void createdLocationCanBeReadOnlyByOwner() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        users.save(new UserAccount(owner, "read-owner@example.com", "hash", "Owner"));
        users.save(new UserAccount(other, "read-other@example.com", "hash", "Other"));
        users.addRole(owner, UserRole.ADVERTISER);
        users.addRole(other, UserRole.ADVERTISER);
        var created = mvc.perform(post("/api/campaigns").with(user("read-owner@example.com")).with(csrf())
                .contentType("application/json").content("{\"title\":\"Draft\",\"description\":\"Brief\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        String location = created.getResponse().getHeader("Location");
        assertThat(location).startsWith("/api/campaigns/");
        mvc.perform(get(location).with(user("read-owner@example.com"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Draft")).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(location).with(user("read-other@example.com"))).andExpect(status().isNotFound());
        mvc.perform(get(location)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/campaigns/" + UUID.randomUUID()).with(user("read-owner@example.com")))
                .andExpect(status().isNotFound());
    }


    @Test
    void advertiserCreatesOwnDraftAndCannotForgeServerFields() throws Exception {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, "create@example.com", "hash", "Owner"));
        users.addRole(owner, UserRole.ADVERTISER);
        users.addRole(owner, UserRole.STREAMER);
        mvc.perform(post("/api/campaigns")
                .with(user("create@example.com"))
                .with(csrf())
                .contentType("application/json").content("""
                        {"title":" New draft ","description":"Brief","advertiserId":"00000000-0000-0000-0000-000000000001","status":"PUBLISHED"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.advertiserId").value(owner.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
        assertThat(campaigns.listOwned(owner, null, null, 100)).hasSize(1);
        assertThat(campaigns.listOwned(owner, null, null, 100).getFirst().title()).isEqualTo("New draft");
    }

    @Test
    void draftCreationRequiresDatabaseAdvertiserRoleAndCsrf() throws Exception {
        UUID streamer = UUID.randomUUID();
        users.save(new UserAccount(streamer, "streamer@example.com", "hash", "Streamer"));
        users.addRole(streamer, UserRole.STREAMER);
        var request = post("/api/campaigns")
                .contentType("application/json").content("{\"title\":\"Draft\",\"description\":\"\"}");
        mvc.perform(request.with(user(
                        "streamer@example.com").roles("ADVERTISER"))
                .with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(campaigns.listOwned(streamer, null, null, 100)).isEmpty();
        mvc.perform(post("/api/campaigns")
                .with(csrf()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/campaigns")
                .with(user("streamer@example.com")))
                .andExpect(status().isForbidden());
    }
    @Test
    void storesOwnedDraftsAndPagesEqualTimestampsWithoutDuplicates() {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, "campaign-owner@example.com", "hash", "Owner"));
        var now = Instant.parse("2026-11-04T16:51:00.123456789Z");
        var first = new Campaign(new UUID(0, 1), owner, "First", "Brief", CampaignStatus.DRAFT, now, now);
        var second = new Campaign(new UUID(0, 2), owner, "Second", "", CampaignStatus.DRAFT, now, now);
        campaigns.insert(first);
        campaigns.insert(second);
        assertThat(campaigns.findOwned(first.id(), owner)).contains(first);
        assertThat(campaigns.findOwned(first.id(), UUID.randomUUID())).isEmpty();
        var page = campaigns.listOwned(owner, null, null, 1);
        assertThat(page).containsExactly(second);
        var next = campaigns.listOwned(owner, page.getFirst().createdAt(), page.getFirst().id(), 1);
        assertThat(next).containsExactly(first);
        assertThat(campaigns.listOwned(owner, first.createdAt(), first.id(), 1)).isEmpty();
        assertThat(campaigns.listOwned(UUID.randomUUID(), null, null, 100)).isEmpty();
    }

    @Test
    void rejectsUnboundedLimitsAndIncompleteCursors() {
        UUID owner = UUID.randomUUID();
        assertThatThrownBy(() -> campaigns.listOwned(owner, null, null, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> campaigns.listOwned(owner, null, null, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> campaigns.listOwned(owner, Instant.now(), null, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> campaigns.listOwned(owner, null, UUID.randomUUID(), 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
