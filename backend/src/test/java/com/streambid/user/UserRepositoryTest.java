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
        UUID other = UUID.randomUUID();
        users.save(new UserAccount(other, "creative-http-other@example.com", "hash", "Other"));
        var file = new org.springframework.mock.web.MockMultipartFile("file", "../../fake.jpg", "text/plain", bytes);
        try {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/creatives")
                    .file(file).param("title", "Banner").with(user("creative-http@example.com")).with(csrf()))
                    .andExpect(status().isCreated()).andExpect(jsonPath("$.format").value("WEBP"))
                    .andExpect(jsonPath("$.widthPx").value(2)).andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.storageKey").doesNotExist()).andExpect(jsonPath("$.sha256").doesNotExist());
            assertThat(creatives.listOwned(owner, null, 20)).hasSize(1);
            var saved = creatives.listOwned(owner, null, 20).getFirst();
            String path = "/api/creatives/" + saved.id();
            mvc.perform(get(path).with(user("creative-http@example.com"))).andExpect(status().isOk());
            mvc.perform(get(path).with(user("creative-http-other@example.com"))).andExpect(status().isNotFound());
            mvc.perform(get("/api/creatives").with(user("creative-http-other@example.com")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
            org.mockito.Mockito.when(creativeObjects.read(saved.id(), saved.format())).thenReturn(bytes);
            mvc.perform(get(path + "/content").with(user("creative-http@example.com"))).andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(bytes))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "image/webp"));
            mvc.perform(get(path + "/content").with(user("creative-http-other@example.com"))).andExpect(status().isNotFound());
            org.mockito.Mockito.when(creativeObjects.read(saved.id(), saved.format())).thenReturn(new byte[] {1, 2});
            mvc.perform(get(path + "/content").with(user("creative-http@example.com")))
                    .andExpect(status().isServiceUnavailable());
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
            creativeDatabase.execute("DELETE FROM users WHERE id IN (?, ?)", owner, other);
        }
    }
    @Autowired org.jooq.DSLContext creativeDatabase;
    @Autowired com.streambid.creative.AdCreativeRepository creatives;
    @Autowired com.streambid.creative.CreativeUploadIntentRepository uploadIntents;
    @Test
    void cleanupClaimPreventsLateUploadMetadataCommit() {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, "upload-fence@example.com", "hash", "Advertiser"));
        var now = java.time.Instant.parse("2026-11-08T00:00:00Z");
        var creative = new com.streambid.creative.AdCreative(UUID.randomUUID(), owner, "Banner",
                com.streambid.creative.CreativeFormat.PNG, 320, 180, 100, "a".repeat(64),
                com.streambid.creative.CreativeStatus.PENDING, 0, now);
        uploadIntents.create(creative.id(), creative.format(), now);
        assertThat(uploadIntents.claimExpired(now.plusSeconds(3600), 10)).hasSize(1);
        assertThatThrownBy(() -> creatives.finishUpload(creative)).isInstanceOf(IllegalStateException.class);
        assertThat(creatives.find(creative.id())).isEmpty();
        uploadIntents.complete(creative.id());
        assertThatThrownBy(() -> creatives.finishUpload(creative)).isInstanceOf(IllegalStateException.class);
    }

    @Autowired com.streambid.offer.AdOfferRepository offers;
    @Test
    void offerDraftStoresRectangleAndRejectsUnapprovedCreative() throws Exception {
        UUID advertiser = UUID.randomUUID(), streamer = UUID.randomUUID(), channel = UUID.randomUUID(), placement = UUID.randomUUID();
        users.save(new UserAccount(advertiser, "offer-advertiser@example.com", "hash", "Advertiser"));
        users.save(new UserAccount(streamer, "offer-streamer@example.com", "hash", "Streamer"));
        users.addRole(advertiser, UserRole.ADVERTISER);
        creativeDatabase.execute("""
                INSERT INTO connected_channels(id, user_id, platform, external_channel_id, display_name, verified_at)
                VALUES (?, ?, 'TWITCH', ?, 'Offer streamer', CURRENT_TIMESTAMP)
                """, channel, streamer, channel.toString());
        creativeDatabase.execute("INSERT INTO channel_listings(channel_id, listed) VALUES (?, TRUE)", channel);
        creativeDatabase.execute("""
                INSERT INTO channel_credentials VALUES (?, 'v1.test', 'v1.test', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, channel);
        creativeDatabase.execute("""
                INSERT INTO ad_placements(id, channel_id, title, position, width_px, height_px, duration_seconds,
                    minimum_price, currency, negotiable, capacity, active, created_at, updated_at)
                VALUES (?, ?, 'Banner', 'Custom', 320, 180, 60, 10, 'PLN', TRUE, 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, placement, channel);
        var now = java.time.Instant.now();
        var creative = creatives.insertPending(new com.streambid.creative.AdCreative(UUID.randomUUID(), advertiser, "Banner",
                com.streambid.creative.CreativeFormat.PNG, 320, 180, 100, "a".repeat(64),
                com.streambid.creative.CreativeStatus.PENDING, 0, now));
        var terms = new com.streambid.offer.OfferTerms(creative.id(), new java.math.BigDecimal("350"), "PLN", 5400,
                com.streambid.offer.OfferMode.FLEXIBLE, null, "gaming", new com.streambid.offer.OfferRectangle(1000, 2000, 3000, 4000));
        assertThat(offers.createDraft(UUID.randomUUID(), advertiser, placement, terms, now)).isEmpty();
        users.addRole(streamer, UserRole.ADMIN);
        creatives.review(creative.id(), streamer, com.streambid.creative.CreativeStatus.APPROVED, null, 0, now);
        UUID id = UUID.randomUUID();
        assertThat(offers.createDraft(id, advertiser, placement, terms, now)).contains(id);
        assertThat(offers.existsForParty(id, advertiser)).isTrue();
        assertThat(offers.existsForParty(id, streamer)).isFalse();
        var view = offers.findForParty(id, advertiser).orElseThrow();
        assertThat(view.status()).isEqualTo(com.streambid.offer.OfferStatus.DRAFT);
        assertThat(view.terms()).isEqualTo(terms);
        assertThat(offers.listForParty(advertiser, null, 20)).containsExactly(view);
        assertThat(offers.listForParty(advertiser, id, 20)).isEmpty();
        assertThat(offers.listForParty(streamer, null, 20)).isEmpty();
        assertThatThrownBy(() -> offers.listForParty(advertiser, null, 101)).isInstanceOf(IllegalArgumentException.class);
        creativeDatabase.execute("UPDATE ad_offers SET status = 'PENDING', sent_at = created_at WHERE id = ?", id);
        assertThat(offers.findForParty(id, streamer)).isPresent();
        assertThat(offers.listForParty(streamer, null, 20)).hasSize(1);
        assertThat(offers.revisionsForParty(id, advertiser, null, 20).getFirst().terms()).isEqualTo(terms);
        assertThat(offers.revisionsForParty(id, UUID.randomUUID(), null, 20)).isEmpty();
        assertThat(offers.revisionsForParty(id, streamer, 0L, 20)).isEmpty();
        creativeDatabase.connection(connection -> {
            var savepoint = connection.setSavepoint();
            try (var statement = connection.prepareStatement("UPDATE ad_offer_revisions SET price = 1 WHERE offer_id = ?")) {
                statement.setObject(1, id);
                assertThatThrownBy(statement::executeUpdate).isInstanceOf(java.sql.SQLException.class);
            } finally {
                connection.rollback(savepoint);
                connection.releaseSavepoint(savepoint);
            }
        });
        assertThat(offers.findForParty(id, advertiser).orElseThrow().terms().price()).isEqualByComparingTo("350.00");

        assertThat(offers.existsForParty(id, UUID.randomUUID())).isFalse();
        var revision = creativeDatabase.fetchSingle("SELECT * FROM ad_offer_revisions WHERE offer_id = ?", id);
        assertThat(revision.get("rectangle_x", Integer.class)).isEqualTo(1000);
        assertThat(revision.get("rectangle_width", Integer.class)).isEqualTo(3000);
        assertThat(revision.get("price", java.math.BigDecimal.class)).isEqualByComparingTo("350.00");
        assertThat(offers.createDraft(UUID.randomUUID(), streamer, placement, terms, now)).isEmpty();
        String body = json.writeValueAsString(new com.streambid.offer.CreateOfferRequest(placement, terms));
        var response = mvc.perform(post("/api/offers").with(user("offer-advertiser@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.terms.rectangle.x").value(1000)).andReturn();
        String location = response.getResponse().getHeader("Location");
        mvc.perform(get(location + "/revisions").with(user("offer-advertiser@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].proposedByRole").value("ADVERTISER"));
        mvc.perform(get(location + "/revisions").with(user("offer-streamer@example.com"))).andExpect(status().isNotFound());
        String send = location + "/send";
        mvc.perform(post(send).with(user("offer-advertiser@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}")).andExpect(status().isConflict());
        creativeDatabase.execute("""
                INSERT INTO placement_availability VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '2 hours')
                """, UUID.randomUUID(), placement);
        mvc.perform(post(send).with(user("offer-advertiser@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.version").value(1));
        mvc.perform(post(send).with(user("offer-advertiser@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}")).andExpect(status().isConflict());

        mvc.perform(get(location).with(user("offer-advertiser@example.com"))).andExpect(status().isOk());
        mvc.perform(get(location).with(user("offer-streamer@example.com"))).andExpect(status().isOk());
        UUID sentId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        var counter = new com.streambid.offer.OfferTerms(creative.id(), new java.math.BigDecimal("400"), "PLN", 3600,
                com.streambid.offer.OfferMode.FLEXIBLE, null, "gaming", new com.streambid.offer.OfferRectangle(2000, 1000, 3000, 4000));
        users.addRole(streamer, UserRole.STREAMER);
        assertThat(offers.counter(sentId, advertiser, 1, counter, now)).isFalse();
        assertThat(offers.counter(sentId, streamer, 1, counter, now)).isTrue();
        assertThat(offers.counter(sentId, advertiser, 1, terms, now)).isFalse();
        assertThat(offers.counter(sentId, streamer, 2, terms, now)).isFalse();
        var countered = offers.findForParty(sentId, advertiser).orElseThrow();
        assertThat(countered.status()).isEqualTo(com.streambid.offer.OfferStatus.COUNTERED);
        assertThat(countered.revision()).isEqualTo(1);
        assertThat(countered.terms()).isEqualTo(counter);
        var history = offers.revisionsForParty(sentId, advertiser, null, 20);
        assertThat(history).hasSize(2);
        assertThat(history.get(1).terms()).isEqualTo(terms);

        String counterPath = location + "/counter";
        String counterBody = json.writeValueAsString(new com.streambid.offer.CounterOfferRequest(2L, terms));
        mvc.perform(post(counterPath).with(user("offer-advertiser@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(counterBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.revision").value(2)).andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(post(counterPath).with(user("offer-advertiser@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(counterBody)).andExpect(status().isConflict());
        String replyBody = json.writeValueAsString(new com.streambid.offer.CounterOfferRequest(3L, counter));
        mvc.perform(post(counterPath).with(user("offer-streamer@example.com"))
                .contentType(MediaType.APPLICATION_JSON).content(replyBody)).andExpect(status().isForbidden());
        mvc.perform(post(counterPath).with(user("offer-streamer@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":null,\"terms\":null}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(counterPath).with(user("offer-streamer@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(replyBody))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(3))
                .andExpect(jsonPath("$.terms.rectangle.x").value(2000));
        mvc.perform(get(location + "/revisions").with(user("offer-advertiser@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].proposedByRole").value("STREAMER"));
        String closePath = location + "/close";
        String reject = "{\"action\":\"REJECT\",\"expectedVersion\":4}";
        mvc.perform(post(closePath).with(user("offer-streamer@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(reject)).andExpect(status().isConflict());
        mvc.perform(post(closePath).with(user("offer-advertiser@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(reject))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.version").value(5));
        mvc.perform(post(counterPath).with(user("offer-advertiser@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(replyBody)).andExpect(status().isConflict());
        assertThat(offers.revisionsForParty(sentId, advertiser, null, 20)).hasSize(4);
        var events = creativeDatabase.fetch("SELECT * FROM ad_offer_events WHERE offer_id = ? ORDER BY version", sentId);
        assertThat(events.getValues("version", Long.class)).containsExactly(0L, 1L, 3L, 4L, 5L);
        assertThat(events.getValues("status", String.class)).containsExactly("DRAFT", "PENDING", "PENDING", "COUNTERED", "REJECTED");
        assertThat(events.get(3).get("actor_id", UUID.class)).isEqualTo(streamer);
        assertThat(events.get(4).get("revision", Long.class)).isEqualTo(3);
        String eventPath = location + "/events";
        mvc.perform(get(eventPath).with(user("offer-streamer@example.com")).param("limit", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].actorRole").value("ADVERTISER"))
                .andExpect(jsonPath("$[0].actorId").doesNotExist());
        mvc.perform(get(eventPath).with(user("offer-advertiser@example.com")).param("beforeVersion", "4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].version").value(3));
        mvc.perform(get(eventPath).with(user("offer-advertiser@example.com")).param("beforeVersion", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(eventPath)).andExpect(status().isUnauthorized());
        assertThat(offers.eventsForParty(sentId, UUID.randomUUID(), null, 20)).isEmpty();


        UUID withdrawn = offers.createDraft(UUID.randomUUID(), advertiser, placement, terms, now).orElseThrow();
        mvc.perform(post("/api/offers/" + withdrawn + "/close").with(user("offer-advertiser@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"WITHDRAW\",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WITHDRAWN"));
        mvc.perform(get("/api/offers/" + withdrawn).with(user("offer-streamer@example.com"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/offers/" + withdrawn + "/events").with(user("offer-streamer@example.com")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/offers/" + withdrawn + "/revisions").with(user("offer-streamer@example.com")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/offers/" + UUID.randomUUID() + "/counter").with(user("offer-advertiser@example.com")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(replyBody)).andExpect(status().isNotFound());

        mvc.perform(post("/api/offers").with(user("offer-streamer@example.com").roles("ADVERTISER")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/offers").with(user("offer-advertiser@example.com"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/api/offers").with(user("offer-advertiser@example.com")).param("limit", "101"))
                .andExpect(status().isBadRequest());

    }

    @Autowired org.springframework.transaction.PlatformTransactionManager creativeTransactions;
    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void uploadCompletionAndCleanupSerializeOnIntentRow() throws Exception {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, "upload-race@example.com", "hash", "Advertiser"));
        var now = java.time.Instant.now().minusSeconds(7200);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(creativeTransactions);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            for (boolean cleanupFirst : new boolean[] {false, true}) {
                UUID id = UUID.randomUUID();
                var creative = new com.streambid.creative.AdCreative(id, owner, "Race",
                        com.streambid.creative.CreativeFormat.PNG, 320, 180, 100, "a".repeat(64),
                        com.streambid.creative.CreativeStatus.PENDING, 0, now);
                uploadIntents.create(id, creative.format(), now);
                var locked = new java.util.concurrent.CountDownLatch(1);
                var release = new java.util.concurrent.CountDownLatch(1);
                var contenderStarted = new java.util.concurrent.CountDownLatch(1);
                try {
                    var holder = executor.submit(() -> transaction.executeWithoutResult(status -> {
                        creativeDatabase.fetch("SELECT creative_id FROM creative_upload_intents WHERE creative_id = ? FOR UPDATE", id);
                        if (cleanupFirst) uploadIntents.claimExpired(java.time.Instant.now(), 5);
                        else creatives.finishUpload(creative);
                        locked.countDown();
                        try {
                            if (!release.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Release timed out");
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(interrupted);
                        }
                    }));
                    assertThat(locked.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    var contender = executor.submit(() -> {
                        contenderStarted.countDown();
                        if (cleanupFirst) {
                            assertThatThrownBy(() -> creatives.finishUpload(creative)).isInstanceOf(IllegalStateException.class);
                        } else {
                            assertThat(uploadIntents.claimExpired(java.time.Instant.now(), 5)).isEmpty();
                        }
                    });
                    assertThat(contenderStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    release.countDown();
                    holder.get(10, java.util.concurrent.TimeUnit.SECONDS);
                    contender.get(10, java.util.concurrent.TimeUnit.SECONDS);
                    assertThat(creatives.find(id).isPresent()).isEqualTo(!cleanupFirst);
                } finally {
                    release.countDown();
                    creativeDatabase.execute("DELETE FROM ad_creatives WHERE id = ?", id);
                    uploadIntents.complete(id);
                }
            }
        } finally {
            creativeDatabase.execute("DELETE FROM users WHERE id = ?", owner);
        }
    }

    @Test
    void deletedUploadTombstonesBackOffAndIgnoreStaleLeaseResults() {
        var now = java.time.Instant.parse("2026-11-08T00:00:00Z");
        UUID id = UUID.randomUUID();
        uploadIntents.create(id, com.streambid.creative.CreativeFormat.PNG, now);
        var first = uploadIntents.claimExpired(now.plusSeconds(3600), 5).getFirst();
        var second = uploadIntents.claimExpired(now.plusSeconds(3900), 5).getFirst();
        uploadIntents.retainDeleted(second, now.plusSeconds(3900));
        uploadIntents.retainDeleted(first, now.plusSeconds(90000));
        assertThat(uploadIntents.claimExpired(now.plusSeconds(90299), 5)).isEmpty();
        assertThat(uploadIntents.claimExpired(now.plusSeconds(90300), 5)).hasSize(1);
    }

    @Test
    void unfinishedUploadCleanupClaimsAreBoundedAndLeased() {
        var now = java.time.Instant.parse("2026-11-08T00:00:00Z");
        UUID id = UUID.randomUUID();
        uploadIntents.create(id, com.streambid.creative.CreativeFormat.WEBP, now);
        assertThat(uploadIntents.claimExpired(now.plusSeconds(3599), 10)).isEmpty();
        var claimed = uploadIntents.claimExpired(now.plusSeconds(3600), 10);
        assertThat(claimed).hasSize(1);
        assertThat(claimed.getFirst().creativeId()).isEqualTo(id);
        assertThat(claimed.getFirst().attempts()).isEqualTo(1);
        assertThat(uploadIntents.claimExpired(now.plusSeconds(3601), 10)).isEmpty();
        assertThat(uploadIntents.claimExpired(now.plusSeconds(3900), 10).getFirst().attempts()).isEqualTo(2);
        uploadIntents.complete(id);
        assertThat(uploadIntents.claimExpired(now.plusSeconds(7200), 10)).isEmpty();
        assertThatThrownBy(() -> uploadIntents.claimExpired(now, 101)).isInstanceOf(IllegalArgumentException.class);
    }
    @Autowired com.streambid.creative.CreativeModerationService moderation;
    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void reviewEventFailureRollsBackModerationDecision() {
        UUID owner = UUID.randomUUID(), admin = UUID.randomUUID();
        users.save(new UserAccount(owner, "review-rollback-owner@example.com", "hash", "Owner"));
        users.save(new UserAccount(admin, "review-rollback-admin@example.com", "hash", "Admin"));
        users.addRole(admin, UserRole.ADMIN);
        var creative = creatives.insertPending(new com.streambid.creative.AdCreative(UUID.randomUUID(), owner, "Banner",
                com.streambid.creative.CreativeFormat.PNG, 320, 180, 100, "a".repeat(64),
                com.streambid.creative.CreativeStatus.PENDING, 0, java.time.Instant.now()));
        try {
            creativeDatabase.execute("""
                    INSERT INTO creative_review_events(creative_id, version, reviewer_id, status, occurred_at)
                    VALUES (?, 1, ?, 'APPROVED', CURRENT_TIMESTAMP)
                    """, creative.id(), admin);
            var request = new com.streambid.creative.CreativeReviewRequest(com.streambid.creative.CreativeStatus.APPROVED, null, 0L);
            assertThatThrownBy(() -> moderation.review("review-rollback-admin@example.com", creative.id(), request))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(creatives.findOwned(creative.id(), owner)).contains(creative);
            assertThat(creatives.reviewsOwned(creative.id(), owner, null, 20)).hasSize(1);
            creativeDatabase.execute("DELETE FROM creative_review_events WHERE creative_id = ?", creative.id());
            assertThat(moderation.review("review-rollback-admin@example.com", creative.id(), request).version()).isEqualTo(1);
            assertThat(creatives.reviewsOwned(creative.id(), owner, null, 20)).hasSize(1);
        } finally {
            creativeDatabase.execute("DELETE FROM creative_review_events WHERE creative_id = ?", creative.id());
            creativeDatabase.execute("DELETE FROM ad_creatives WHERE id = ?", creative.id());
            creativeDatabase.execute("DELETE FROM users WHERE id IN (?, ?)", owner, admin);
        }
    }
    @Test
    void moderationHttpRequiresDatabaseAdminAndValidReason() throws Exception {
        UUID owner = UUID.randomUUID(), admin = UUID.randomUUID();
        users.save(new UserAccount(owner, "review-http-owner@example.com", "hash", "Owner"));
        users.save(new UserAccount(admin, "review-http-admin@example.com", "hash", "Admin"));
        users.addRole(owner, UserRole.ADVERTISER);
        users.addRole(admin, UserRole.ADMIN);
        var creative = creatives.insertPending(new com.streambid.creative.AdCreative(UUID.randomUUID(), owner, "Banner",
                com.streambid.creative.CreativeFormat.PNG, 320, 180, 100, "a".repeat(64),
                com.streambid.creative.CreativeStatus.PENDING, 0, java.time.Instant.now()));
        String path = "/api/admin/creatives/" + creative.id() + "/review";
        String decision = "{\"status\":\"REJECTED\",\"reason\":\"Unsupported advertising content\",\"expectedVersion\":0}";
        mvc.perform(post(path).with(user("review-http-owner@example.com").roles("ADMIN")).with(csrf())
                .contentType("application/json").content(decision)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/creatives").with(user("review-http-admin@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post(path).with(user("review-http-admin@example.com")).with(csrf()).contentType("application/json")
                .content("{\"status\":\"REJECTED\",\"expectedVersion\":0}")).andExpect(status().isBadRequest());
        mvc.perform(post(path).with(user("review-http-admin@example.com")).with(csrf())
                .contentType("application/json").content(decision)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED")).andExpect(jsonPath("$.version").value(1));
        mvc.perform(post(path).with(user("review-http-admin@example.com")).with(csrf())
                .contentType("application/json").content(decision)).andExpect(status().isConflict());
    }
    @Test
    void moderatorDecisionRequiresAdminAndRecordsOneVersionedEvent() {
        UUID owner = UUID.randomUUID(), admin = UUID.randomUUID();
        users.save(new UserAccount(owner, "review-owner@example.com", "hash", "Advertiser"));
        users.save(new UserAccount(admin, "review-admin@example.com", "hash", "Admin"));
        users.addRole(owner, UserRole.ADVERTISER);
        users.addRole(admin, UserRole.ADMIN);
        var creative = creatives.insertPending(new com.streambid.creative.AdCreative(UUID.randomUUID(), owner, "Banner",
                com.streambid.creative.CreativeFormat.PNG, 320, 180, 100, "a".repeat(64),
                com.streambid.creative.CreativeStatus.PENDING, 0, java.time.Instant.now()));
        assertThat(creatives.pending(null, 20)).containsExactly(creative);
        assertThat(creatives.review(creative.id(), owner, com.streambid.creative.CreativeStatus.APPROVED, null, 0, java.time.Instant.now())).isEmpty();
        assertThat(creatives.review(creative.id(), admin, com.streambid.creative.CreativeStatus.APPROVED, null, 99, java.time.Instant.now())).isEmpty();
        var reviewed = creatives.review(creative.id(), admin, com.streambid.creative.CreativeStatus.APPROVED, null, 0, java.time.Instant.now()).orElseThrow();
        assertThat(reviewed.version()).isEqualTo(1);
        assertThat(reviewed.status()).isEqualTo(com.streambid.creative.CreativeStatus.APPROVED);
        assertThat(creatives.pending(null, 20)).isEmpty();
        assertThat(creatives.review(creative.id(), admin, com.streambid.creative.CreativeStatus.REJECTED, "Changed mind", 1, java.time.Instant.now())).isEmpty();
        assertThat(creativeDatabase.fetchCount(org.jooq.impl.DSL.table("creative_review_events"))).isEqualTo(1);
    }
    @Test
    void creativeMetadataIsOwnerScopedAndAlwaysStartsPending() {
        UUID owner = UUID.randomUUID();
        users.save(new UserAccount(owner, "creative-owner@example.com", "hash", "Advertiser"));
        users.addRole(owner, UserRole.ADVERTISER);
        var input = new com.streambid.creative.AdCreative(UUID.randomUUID(), owner, "Banner",
                com.streambid.creative.CreativeFormat.PNG, 320, 180, 100, "a".repeat(64),
                com.streambid.creative.CreativeStatus.APPROVED, 99, java.time.Instant.now());
        uploadIntents.create(input.id(), input.format(), input.createdAt());
        var saved = creatives.insertPending(input);
        assertThat(creativeDatabase.fetchCount(org.jooq.impl.DSL.table("creative_upload_intents"))).isZero();
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
