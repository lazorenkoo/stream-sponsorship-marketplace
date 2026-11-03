package com.streambid.channel;

import com.streambid.platform.StreamingPlatform;
import com.streambid.user.UserAccount;
import com.streambid.user.UserRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
class ChannelRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    ChannelRepository channels;

    @Autowired
    UserRepository users;

    @Test
    void readsOnlyOwnersChannelsAndRejectsDuplicateOwnership() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        users.save(new UserAccount(owner, "owner@example.com", "hash", "Owner"));
        users.save(new UserAccount(other, "other@example.com", "hash", "Other"));
        var channel = new ConnectedChannel(UUID.randomUUID(), owner, StreamingPlatform.TWITCH,
                "opaque-id", "Streamer", OffsetDateTime.now(ZoneOffset.UTC).withNano(0));
        channels.saveVerified(channel);
        assertThat(channels.findByUser(owner)).containsExactly(channel);
        assertThat(channels.findByUser(other)).isEmpty();
        var duplicate = new ConnectedChannel(UUID.randomUUID(), other, StreamingPlatform.TWITCH,
                "opaque-id", "Streamer", channel.verifiedAt());
        assertThatThrownBy(() -> channels.saveVerified(duplicate)).isInstanceOf(DuplicateKeyException.class);
    }
}
