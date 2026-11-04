package com.streambid.campaign;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import com.streambid.user.UserRole;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    void historyApiIsOwnedPagedAndSkipsFailedEdits() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        users.save(new UserAccount(owner, "history-owner@example.com", "hash", "Owner"));
        users.save(new UserAccount(other, "history-other@example.com", "hash", "Other"));
        users.addRole(owner, UserRole.ADVERTISER);
        users.addRole(other, UserRole.ADVERTISER);
        var campaign = drafts.create("history-owner@example.com", new CampaignDraftRequest("Draft", ""));
        drafts.update("history-owner@example.com", campaign.id(), new CampaignDraftUpdateRequest("Edited", "", 0L));
        String path = "/api/campaigns/" + campaign.id();
        mvc.perform(put(path).with(user("history-owner@example.com")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(new CampaignDraftUpdateRequest("Stale", "", 0L))))
                .andExpect(status().isConflict());
        mvc.perform(get(path + "/events").with(user("history-owner@example.com")).param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].campaignVersion").value(1)).andExpect(jsonPath("$[0].type").value("EDITED"))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(path + "/events").with(user("history-owner@example.com")).param("beforeVersion", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].type").value("CREATED"));
        mvc.perform(get(path + "/events").with(user("history-other@example.com"))).andExpect(status().isNotFound());
        mvc.perform(get(path + "/events")).andExpect(status().isUnauthorized());
        for (String invalid : new String[] {"0", "101", "text"}) {
            mvc.perform(get(path + "/events").with(user("history-owner@example.com")).param("limit", invalid))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get(path + "/events").with(user("history-owner@example.com")).param("beforeVersion", "-1"))
                .andExpect(status().isBadRequest());
        assertThat(events.findOwned(campaign.id(), owner, null, 100)).hasSize(2);
    }

    @Test
    void serviceRecordsCompleteCampaignLifecycle() {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, "lifecycle@example.com", "hash", "Owner"));
        users.addRole(owner, UserRole.ADVERTISER);
        var created = drafts.create("lifecycle@example.com", new CampaignDraftRequest("Draft", ""));
        var edited = drafts.update("lifecycle@example.com", created.id(), new CampaignDraftUpdateRequest("Edited", "", 0L));
        var archived = drafts.archive("lifecycle@example.com", created.id(), new CampaignArchiveRequest(1L));
        var history = events.findOwned(created.id(), owner, null, 100);
        assertThat(history).extracting(CampaignEvent::type)
                .containsExactly(CampaignEventType.ARCHIVED, CampaignEventType.EDITED, CampaignEventType.CREATED);
        assertThat(history).extracting(CampaignEvent::campaignVersion).containsExactly(2L, 1L, 0L);
        assertThat(history).allSatisfy(event -> assertThat(event.actorId()).isEqualTo(owner));
        assertThat(history.getFirst().occurredAt()).isEqualTo(archived.updatedAt());
        assertThat(history.get(1).occurredAt()).isEqualTo(edited.updatedAt());
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void eventInsertFailureRollsBackCampaignEdit() {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, "event-rollback@example.com", "hash", "Owner"));
        users.addRole(owner, UserRole.ADVERTISER);
        var created = drafts.create("event-rollback@example.com", new CampaignDraftRequest("Original", ""));
        try {
            events.append(new CampaignEvent(created.id(), 1, owner, CampaignEventType.EDITED, created.updatedAt()));
            assertThatThrownBy(() -> drafts.update("event-rollback@example.com", created.id(),
                    new CampaignDraftUpdateRequest("Must roll back", "", 0L)))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(campaigns.findOwned(created.id(), owner)).contains(created);
            assertThat(events.findOwned(created.id(), owner, null, 100)).hasSize(2);
        } finally {
            database.execute("DELETE FROM campaign_events WHERE campaign_id = ?", created.id());
            database.execute("DELETE FROM campaigns WHERE id = ?", created.id());
            database.execute("DELETE FROM users WHERE id = ?", owner);
        }
    }

    @Autowired CampaignEventRepository events;

    @Test
    void campaignEventsAreOwnedAndPagedByVersion() {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, "events-owner@example.com", "hash", "Owner"));
        var now = Instant.parse("2026-11-04T20:30:00.123456789Z");
        var campaign = new Campaign(UUID.randomUUID(), owner, "Draft", "", CampaignStatus.DRAFT, now, now);
        campaigns.insert(campaign);
        var created = new CampaignEvent(campaign.id(), 0, owner, CampaignEventType.CREATED, now);
        var edited = new CampaignEvent(campaign.id(), 1, owner, CampaignEventType.EDITED, now.plusSeconds(1));
        events.append(created);
        events.append(edited);
        assertThat(events.findOwned(campaign.id(), owner, null, 1)).containsExactly(edited);
        assertThat(events.findOwned(campaign.id(), owner, 1L, 100)).containsExactly(created);
        assertThat(events.findOwned(campaign.id(), owner, 0L, 100)).isEmpty();
        assertThat(events.findOwned(campaign.id(), UUID.randomUUID(), null, 100)).isEmpty();
        assertThatThrownBy(() -> events.findOwned(campaign.id(), owner, null, 101)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> events.findOwned(campaign.id(), owner, -1L, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void archiveApiPreservesReadableCampaignAndFiltersLists() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        users.save(new UserAccount(owner, "archive-api@example.com", "hash", "Owner"));
        users.save(new UserAccount(other, "archive-foreign@example.com", "hash", "Other"));
        users.addRole(owner, UserRole.ADVERTISER);
        users.addRole(other, UserRole.ADVERTISER);
        var now = Instant.now();
        var campaign = new Campaign(UUID.randomUUID(), owner, "Draft", "Brief", CampaignStatus.DRAFT, now, now);
        campaigns.insert(campaign);
        String path = "/api/campaigns/" + campaign.id();
        String archive = path + "/archive";
        String body = "{\"expectedVersion\":0}";
        mvc.perform(post(archive).with(user("archive-foreign@example.com")).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isNotFound());
        mvc.perform(post(archive).with(user("archive-api@example.com"))
                .contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(archive).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized());
        for (String invalid : new String[] {"{}", "{\"expectedVersion\":-1}"}) {
            mvc.perform(post(archive).with(user("archive-api@example.com")).with(csrf())
                    .contentType("application/json").content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(archive).with(user("archive-api@example.com")).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED")).andExpect(jsonPath("$.version").value(1))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(path).with(user("archive-api@example.com"))).andExpect(status().isOk());
        mvc.perform(get("/api/campaigns").with(user("archive-api@example.com")).param("status", "DRAFT"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/campaigns").with(user("archive-api@example.com")).param("status", "ARCHIVED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/campaigns").with(user("archive-api@example.com")).param("status", "INVALID"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(archive).with(user("archive-api@example.com")).with(csrf())
                .contentType("application/json").content("{\"expectedVersion\":1}")).andExpect(status().isConflict());
        mvc.perform(put(path).with(user("archive-api@example.com")).with(csrf()).contentType("application/json")
                .content(json.writeValueAsString(new CampaignDraftUpdateRequest("Changed", "", 1L))))
                .andExpect(status().isConflict());
    }

    @Test
    void archivingPreservesCampaignAndPreventsFurtherDraftEdits() {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, "archive-owner@example.com", "hash", "Owner"));
        var now = Instant.parse("2026-11-04T19:52:00Z");
        var draft = new Campaign(UUID.randomUUID(), owner, "Draft", "Brief", CampaignStatus.DRAFT, now, now);
        campaigns.insert(draft);
        assertThat(campaigns.archiveOwned(draft.id(), UUID.randomUUID(), 0, now)).isEmpty();
        assertThat(campaigns.archiveOwned(draft.id(), owner, 1, now)).isEmpty();
        var archived = campaigns.archiveOwned(draft.id(), owner, 0, now.plusSeconds(10)).orElseThrow();
        assertThat(archived.id()).isEqualTo(draft.id());
        assertThat(archived.status()).isEqualTo(CampaignStatus.ARCHIVED);
        assertThat(archived.version()).isEqualTo(1);
        assertThat(archived.createdAt()).isEqualTo(draft.createdAt());
        assertThat(campaigns.findOwned(draft.id(), owner)).contains(archived);
        assertThat(campaigns.updateDraft(draft, 1)).isEmpty();
        assertThat(campaigns.archiveOwned(draft.id(), owner, 1, now)).isEmpty();
        assertThat(campaigns.listOwned(owner, null, null, 100, CampaignStatus.DRAFT)).isEmpty();
        assertThat(campaigns.listOwned(owner, null, null, 100, CampaignStatus.ARCHIVED)).containsExactly(archived);
        assertThat(campaigns.listOwned(owner, null, null, 100)).containsExactly(archived);
    }

    @Autowired CampaignDraftService drafts;
    @Autowired org.jooq.DSLContext database;

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentEditsHaveExactlyOneWinner() throws Exception {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, "concurrent-edit@example.com", "hash", "Owner"));
        users.addRole(owner, UserRole.ADVERTISER);
        var now = Instant.now();
        var campaign = new Campaign(UUID.randomUUID(), owner, "Original", "", CampaignStatus.DRAFT, now, now);
        campaigns.insert(campaign);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> edit = () -> {
                ready.countDown();
                if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new AssertionError("Concurrent edit test timed out");
                }
                try {
                    drafts.update("concurrent-edit@example.com", campaign.id(), new CampaignDraftUpdateRequest("Edited", "", 0L));
                    return 200;
                } catch (org.springframework.web.server.ResponseStatusException exception) {
                    return exception.getStatusCode().value();
                }
            };
            var first = executor.submit(edit);
            var second = executor.submit(edit);
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(5, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(5, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
            assertThat(campaigns.findOwned(campaign.id(), owner).orElseThrow().version()).isEqualTo(1);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            database.execute("DELETE FROM campaign_events WHERE campaign_id = ?", campaign.id());
            database.execute("DELETE FROM campaigns WHERE id = ?", campaign.id());
            database.execute("DELETE FROM users WHERE id = ?", owner);
        }
    }

    @Test
    void updateRejectsMissingAndNegativeVersions() throws Exception {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, "invalid-version@example.com", "hash", "Owner"));
        users.addRole(owner, UserRole.ADVERTISER);
        var now = Instant.now();
        var campaign = new Campaign(UUID.randomUUID(), owner, "Original", "", CampaignStatus.DRAFT, now, now);
        campaigns.insert(campaign);
        for (Long version : new Long[] {null, -1L}) {
            mvc.perform(put("/api/campaigns/" + campaign.id()).with(user("invalid-version@example.com")).with(csrf())
                    .contentType("application/json").content(json.writeValueAsString(
                            new CampaignDraftUpdateRequest("Edited", "", version))))
                    .andExpect(status().isBadRequest());
        }
        assertThat(campaigns.findOwned(campaign.id(), owner)).contains(campaign);
    }

    @Test
    void updateApiRequiresCurrentVersionOwnerAndCsrf() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        users.save(new UserAccount(owner, "edit-owner@example.com", "hash", "Owner"));
        users.save(new UserAccount(other, "edit-other@example.com", "hash", "Other"));
        users.addRole(owner, UserRole.ADVERTISER);
        users.addRole(other, UserRole.ADVERTISER);
        var now = Instant.parse("2026-11-04T18:54:00Z");
        var original = new Campaign(UUID.randomUUID(), owner, "Original", "Brief", CampaignStatus.DRAFT, now, now);
        campaigns.insert(original);
        String path = "/api/campaigns/" + original.id();
        String body = json.writeValueAsString(new CampaignDraftUpdateRequest("Changed", "New brief", 0L));
        mvc.perform(put(path).with(user("edit-owner@example.com")).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.title").value("Changed")).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(put(path).with(user("edit-owner@example.com")).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isConflict());
        mvc.perform(put(path).with(user("edit-other@example.com")).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isNotFound());
        mvc.perform(put(path).with(user("edit-owner@example.com")).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        String invalid = json.writeValueAsString(new CampaignDraftUpdateRequest("", "", 1L));
        mvc.perform(put(path).with(user("edit-owner@example.com")).with(csrf()).contentType("application/json").content(invalid))
                .andExpect(status().isBadRequest());
        assertThat(campaigns.findOwned(original.id(), owner).orElseThrow().version()).isEqualTo(1);
        assertThat(campaigns.findOwned(original.id(), owner).orElseThrow().title()).isEqualTo("Changed");
    }

    @Test
    void draftUpdatesRequireOwnerAndCurrentVersion() {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, "version-owner@example.com", "hash", "Owner"));
        var now = Instant.parse("2026-11-04T18:39:00Z");
        var original = new Campaign(UUID.randomUUID(), owner, "Original", "Brief", CampaignStatus.DRAFT, now, now);
        campaigns.insert(original);
        var changed = new Campaign(original.id(), owner, "Changed", "New brief", CampaignStatus.DRAFT,
                now, now.plusSeconds(1));
        var saved = campaigns.updateDraft(changed, 0).orElseThrow();
        assertThat(saved.version()).isEqualTo(1);
        assertThat(saved.title()).isEqualTo("Changed");
        assertThat(saved.createdAt()).isEqualTo(now);
        assertThat(saved.updatedAt()).isEqualTo(now.plusSeconds(1));
        assertThat(campaigns.updateDraft(original, 0)).isEmpty();
        var foreign = new Campaign(original.id(), UUID.randomUUID(), "Stolen", "", CampaignStatus.DRAFT, now, now);
        assertThat(campaigns.updateDraft(foreign, 1)).isEmpty();
        assertThat(campaigns.findOwned(original.id(), owner)).contains(saved);
        var clockBehind = campaigns.updateDraft(original, 1).orElseThrow();
        assertThat(clockBehind.version()).isEqualTo(2);
        assertThat(clockBehind.updatedAt()).isEqualTo(saved.updatedAt());
    }

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
