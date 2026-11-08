package com.streambid.offer;

import static org.assertj.core.api.Assertions.*;
import com.streambid.user.*;
import com.streambid.creative.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class OfferConcurrencyTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired org.jooq.DSLContext database;
    @Autowired UserRepository users;
    @Autowired AdCreativeRepository creatives;
    @Autowired AdOfferRepository offers;
    @Autowired AdOfferService service;

    @Test
    void auditFailureRollsBackRevisionAndConcurrentRepliesHaveOneWinner() throws Exception {
        UUID advertiser = UUID.randomUUID(), streamer = UUID.randomUUID(), channel = UUID.randomUUID(), placement = UUID.randomUUID();
        users.save(new UserAccount(advertiser, "race-advertiser@example.com", "hash", "Advertiser"));
        users.save(new UserAccount(streamer, "race-streamer@example.com", "hash", "Streamer"));
        users.addRole(advertiser, UserRole.ADVERTISER);
        users.addRole(streamer, UserRole.STREAMER);
        users.addRole(streamer, UserRole.ADMIN);
        database.execute("""
                INSERT INTO connected_channels(id,user_id,platform,external_channel_id,display_name,verified_at)
                VALUES (?,?,'TWITCH',?,'Race channel',CURRENT_TIMESTAMP)
                """, channel, streamer, channel.toString());
        database.execute("INSERT INTO channel_listings(channel_id,listed) VALUES (?,TRUE)", channel);
        database.execute("INSERT INTO channel_credentials VALUES (?,'v1.test','v1.test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", channel);
        database.execute("""
                INSERT INTO ad_placements(id,channel_id,title,position,width_px,height_px,duration_seconds,
                    minimum_price,currency,negotiable,capacity,active,created_at,updated_at)
                VALUES (?,?,'Banner','Custom',320,180,60,10,'PLN',TRUE,1,TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, placement, channel);
        database.execute("INSERT INTO placement_availability VALUES (?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '2 hours')",
                UUID.randomUUID(), placement);
        var now = Instant.now();
        var creative = creatives.insertPending(new AdCreative(UUID.randomUUID(), advertiser, "Banner", CreativeFormat.PNG,
                320, 180, 100, "a".repeat(64), CreativeStatus.PENDING, 0, now));
        creatives.review(creative.id(), streamer, CreativeStatus.APPROVED, null, 0, now);
        var terms = new OfferTerms(creative.id(), new BigDecimal("100"), "PLN", 60, OfferMode.FLEXIBLE, null,
                "gaming", new OfferRectangle(0, 0, 2000, 1000));
        var draft = service.create("race-advertiser@example.com", new CreateOfferRequest(placement, terms));
        service.send("race-advertiser@example.com", draft.id(), new OfferVersionRequest(0L));
        var request = new CounterOfferRequest(1L, new OfferTerms(creative.id(), new BigDecimal("150"), "PLN", 90,
                OfferMode.FLEXIBLE, null, "gaming", new OfferRectangle(1000, 1000, 2000, 1000)));
        database.execute("""
                INSERT INTO ad_offer_events VALUES (?,2,0,?,'COUNTERED',CURRENT_TIMESTAMP)
                """, draft.id(), streamer);
        assertThatThrownBy(() -> service.counter("race-streamer@example.com", draft.id(), request))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var unchanged = service.find("race-advertiser@example.com", draft.id());
        assertThat(unchanged.version()).isEqualTo(1);
        assertThat(unchanged.terms()).isEqualTo(terms);
        assertThat(offers.revisionsForParty(draft.id(), advertiser, null, 20)).hasSize(1);
        database.execute("DELETE FROM ad_offer_events WHERE offer_id = ? AND version = 2", draft.id());
        var barrier = new CyclicBarrier(2);
        Callable<Integer> reply = () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try {
                service.counter("race-streamer@example.com", draft.id(), request);
                return 200;
            } catch (ResponseStatusException conflict) {
                return conflict.getStatusCode().value();
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(reply);
            var second = executor.submit(reply);
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(service.find("race-advertiser@example.com", draft.id()).version()).isEqualTo(2);
        assertThat(offers.revisionsForParty(draft.id(), advertiser, null, 20)).hasSize(2);
        assertThat(offers.eventsForParty(draft.id(), advertiser, null, 20)).hasSize(3);
    }
}
