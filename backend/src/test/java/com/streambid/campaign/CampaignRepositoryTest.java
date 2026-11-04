package com.streambid.campaign;

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
