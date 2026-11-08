package com.streambid.offer;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
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
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
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
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AdCreativeRepository creatives;
    @Autowired AdOfferRepository offers;
    @Autowired AdOfferService service;
    @Autowired com.streambid.booking.AdBookingRepository bookings;

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
        assertThat(bookings.accept(draft.id(), advertiser, 2, Instant.now())).isEmpty();
        var scheduled = new OfferTerms(creative.id(), new BigDecimal("100"), "PLN", 60, OfferMode.SCHEDULED,
                Instant.now().plusSeconds(300), "gaming", terms.rectangle());
        var bookable = service.create("race-advertiser@example.com", new CreateOfferRequest(placement, scheduled));
        service.send("race-advertiser@example.com", bookable.id(), new OfferVersionRequest(0L));
        assertThat(bookings.accept(bookable.id(), advertiser, 1, Instant.now())).isEmpty();
        String acceptPath = "/api/offers/" + bookable.id() + "/accept";
        var http = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(acceptPath)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("race-streamer@example.com"))
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType("application/json").content("{\"expectedVersion\":1}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()).andReturn();
        String location = http.getResponse().getHeader("Location");
        UUID booking = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(location)
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("race-advertiser@example.com")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertThat(bookings.findForParty(booking, UUID.randomUUID())).isEmpty();
        assertThatThrownBy(() -> database.execute("UPDATE ad_bookings SET rectangle_width = 3000 WHERE id = ?", booking))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(bookings.findForParty(booking, advertiser).orElseThrow().window().rectangle().width()).isEqualTo(2000);
        assertThatThrownBy(() -> database.execute("""
                INSERT INTO ad_bookings(id,offer_id,revision,placement_id,channel_id,starts_at,ends_at,
                    rectangle_x,rectangle_y,rectangle_width,rectangle_height,created_at,updated_at)
                SELECT ?,offer_id,revision,placement_id,channel_id,starts_at,ends_at,
                    rectangle_x,rectangle_y,rectangle_width + 1,rectangle_height,created_at,updated_at
                FROM ad_bookings WHERE id = ?
                """, UUID.randomUUID(), booking)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .satisfies(failure -> {
                    Throwable cause = failure;
                    while (cause.getCause() != null) cause = cause.getCause();
                    assertThat(((java.sql.SQLException) cause).getSQLState()).isEqualTo("23514");
                });
        users.save(new UserAccount(UUID.randomUUID(), "booking-outsider@example.com", "hash", "Other"));
        mvc.perform(get(location).with(user("booking-outsider@example.com"))).andExpect(status().isNotFound());
        mvc.perform(post(acceptPath).with(user("booking-outsider@example.com")).with(csrf())
                .contentType("application/json").content("{\"expectedVersion\":1}")).andExpect(status().isNotFound());
        mvc.perform(post(acceptPath).with(user("race-streamer@example.com"))
                .contentType("application/json").content("{\"expectedVersion\":1}")).andExpect(status().isForbidden());
        mvc.perform(post(acceptPath).with(user("race-streamer@example.com")).with(csrf())
                .contentType("application/json").content("{\"expectedVersion\":null}")).andExpect(status().isBadRequest());
        mvc.perform(post(acceptPath).with(user("race-streamer@example.com")).with(csrf())
                .contentType("application/json").content("{\"expectedVersion\":1}")).andExpect(status().isConflict());

        assertThat(service.find("race-advertiser@example.com", bookable.id()).status()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(database.fetchSingle("SELECT rectangle_width FROM ad_bookings WHERE id = ?", booking)
                .get("rectangle_width", Integer.class)).isEqualTo(2000);
        assertThat(bookings.accept(bookable.id(), streamer, 1, Instant.now())).isEmpty();
        var disjoint = new OfferTerms(creative.id(), new BigDecimal("100"), "PLN", 60, OfferMode.SCHEDULED,
                scheduled.startsAt(), "gaming", new OfferRectangle(5000, 5000, 1000, 1000));
        UUID capacityBlocked = sentOffer(placement, disjoint);
        assertThat(bookings.accept(capacityBlocked, streamer, 1, Instant.now())).isEmpty();
        assertThat(service.find("race-advertiser@example.com", capacityBlocked).status()).isEqualTo(OfferStatus.PENDING);
        UUID otherPlacement = UUID.randomUUID();
        database.execute("""
                INSERT INTO ad_placements(id,channel_id,title,position,width_px,height_px,duration_seconds,
                    minimum_price,currency,negotiable,capacity,active,created_at,updated_at)
                SELECT ?,channel_id,title,position,width_px,height_px,duration_seconds,
                    minimum_price,currency,negotiable,10,active,created_at,updated_at FROM ad_placements WHERE id = ?
                """, otherPlacement, placement);
        database.execute("INSERT INTO placement_availability VALUES (?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + INTERVAL '2 hours')",
                UUID.randomUUID(), otherPlacement);
        UUID geometryBlocked = sentOffer(otherPlacement, scheduled);
        assertThatThrownBy(() -> bookings.accept(geometryBlocked, streamer, 1, Instant.now()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
                .satisfies(failure -> {
                    Throwable cause = failure;
                    while (cause.getCause() != null) cause = cause.getCause();
                    assertThat(((java.sql.SQLException) cause).getSQLState()).isEqualTo("23P01");
                });
        mvc.perform(post("/api/offers/" + geometryBlocked + "/accept").with(user("race-streamer@example.com")).with(csrf())
                .contentType("application/json").content("{\"expectedVersion\":1}"))
                .andExpect(status().isConflict()).andExpect(header().string("Cache-Control", "no-store"));
        var rolledBack = service.find("race-advertiser@example.com", geometryBlocked);
        assertThat(rolledBack.status()).isEqualTo(OfferStatus.PENDING);
        assertThat(rolledBack.version()).isEqualTo(1);
        assertThat(offers.eventsForParty(geometryBlocked, advertiser, null, 20)).hasSize(2);
        assertThat(database.fetchSingle("SELECT count(*) AS total FROM ad_bookings WHERE offer_id = ?", geometryBlocked)
                .get("total", Long.class)).isZero();
        var adjacentArea = new OfferTerms(creative.id(), new BigDecimal("100"), "PLN", 60, OfferMode.SCHEDULED,
                scheduled.startsAt(), "gaming", new OfferRectangle(2000, 0, 2000, 1000));
        assertThat(bookings.accept(sentOffer(otherPlacement, adjacentArea), streamer, 1, Instant.now())).isPresent();
        var adjacentTime = new OfferTerms(creative.id(), new BigDecimal("100"), "PLN", 60, OfferMode.SCHEDULED,
                scheduled.startsAt().plusSeconds(60), "gaming", scheduled.rectangle());
        assertThat(bookings.accept(sentOffer(placement, adjacentTime), streamer, 1, Instant.now())).isPresent();
        UUID firstOffer = sentOffer(otherPlacement, disjoint), secondOffer = sentOffer(otherPlacement, disjoint);
        var acceptBarrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var firstAccept = executor.submit(() -> acceptAfterBarrier(firstOffer, streamer, acceptBarrier));
            var secondAccept = executor.submit(() -> acceptAfterBarrier(secondOffer, streamer, acceptBarrier));
            assertThat(java.util.List.of(firstAccept.get(10, TimeUnit.SECONDS), secondAccept.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(database.fetchCount(org.jooq.impl.DSL.table("ad_bookings"))).isEqualTo(4);
        var inbox = mvc.perform(get("/api/bookings").with(user("race-advertiser@example.com")).param("limit", "1").param("status", "BOOKED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.bookings.length()").value(1)).andReturn();
        String after = new com.fasterxml.jackson.databind.ObjectMapper().readTree(inbox.getResponse().getContentAsString()).get("nextCursor").asText();
        mvc.perform(get("/api/bookings").with(user("race-streamer@example.com")).param("after", after))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bookings.length()").value(3))
                .andExpect(jsonPath("$.hasMore").value(false));
        mvc.perform(get("/api/bookings").with(user("booking-outsider@example.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bookings.length()").value(0));
        mvc.perform(get("/api/bookings").with(user("race-advertiser@example.com")).param("status", "CANCELLED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bookings.length()").value(0));
        mvc.perform(get("/api/bookings").with(user("race-advertiser@example.com")).param("limit", "101"))
                .andExpect(status().isBadRequest());
        String calendar = "/api/catalog/channels/" + channel + "/reservations";
        String from = scheduled.startsAt().toString(), to = scheduled.startsAt().plusSeconds(120).toString();
        var calendarResult = mvc.perform(get(calendar).with(user("booking-outsider@example.com"))
                .param("from", from).param("to", to).param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.reservations[0].rectangle").exists())
                .andExpect(jsonPath("$.reservations[0].offerId").doesNotExist()).andReturn();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String next = mapper.readTree(calendarResult.getResponse().getContentAsString()).get("nextCursor").asText();
        mvc.perform(get(calendar).with(user("booking-outsider@example.com")).param("from", from).param("to", to).param("cursor", next))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reservations.length()").value(3));
        String cancel = location + "/cancel";
        String cancellation = "{\"expectedVersion\":0,\"reason\":\" Plans changed \"}";
        mvc.perform(post(cancel).with(user("booking-outsider@example.com")).with(csrf())
                .contentType("application/json").content(cancellation)).andExpect(status().isNotFound());
        mvc.perform(post(cancel).with(user("race-advertiser@example.com"))
                .contentType("application/json").content(cancellation)).andExpect(status().isForbidden());
        mvc.perform(post(cancel).with(user("race-advertiser@example.com")).with(csrf())
                .contentType("application/json").content("{\"expectedVersion\":0,\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(cancel).with(user("race-advertiser@example.com")).with(csrf())
                .contentType("application/json").content(cancellation))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(post(cancel).with(user("race-streamer@example.com")).with(csrf())
                .contentType("application/json").content(cancellation)).andExpect(status().isConflict());
        assertThat(service.find("race-advertiser@example.com", bookable.id()).status()).isEqualTo(OfferStatus.ACCEPTED);
        assertThat(database.fetchSingle("SELECT reason FROM ad_booking_cancellations WHERE booking_id = ?", booking)
                .get("reason", String.class)).isEqualTo("Plans changed");
        mvc.perform(get(calendar).with(user("booking-outsider@example.com")).param("from", from).param("to", to))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reservations.length()").value(3));
        assertThat(bookings.accept(geometryBlocked, streamer, 1, Instant.now())).isPresent();
        database.execute("UPDATE channel_listings SET listed = FALSE WHERE channel_id = ?", channel);
        mvc.perform(get(calendar).with(user("booking-outsider@example.com")).param("from", from).param("to", to))
                .andExpect(status().isNotFound());
        mvc.perform(get(calendar).with(user("race-streamer@example.com")).param("from", from).param("to", to))
                .andExpect(status().isOk());



    }

    private UUID sentOffer(UUID placement, OfferTerms terms) {
        var created = service.create("race-advertiser@example.com", new CreateOfferRequest(placement, terms));
        service.send("race-advertiser@example.com", created.id(), new OfferVersionRequest(0L));
        return created.id();
    }

    private int acceptAfterBarrier(UUID offer, UUID recipient, CyclicBarrier barrier) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            return bookings.accept(offer, recipient, 1, Instant.now()).isPresent() ? 200 : 409;
        } catch (org.springframework.dao.DataIntegrityViolationException conflict) {
            Throwable cause = conflict;
            while (cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof java.sql.SQLException sql && "23P01".equals(sql.getSQLState())) return 409;
            throw conflict;
        }
    }
}
