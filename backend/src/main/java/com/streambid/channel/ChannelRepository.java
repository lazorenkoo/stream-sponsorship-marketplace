package com.streambid.channel;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class ChannelRepository {

    private static final Field<UUID> ID = DSL.field(DSL.name("id"), UUID.class);
    private static final Field<UUID> USER_ID = DSL.field(DSL.name("user_id"), UUID.class);
    private static final Field<String> PLATFORM = DSL.field(DSL.name("platform"), String.class);
    private static final Field<String> EXTERNAL_ID = DSL.field(DSL.name("external_channel_id"), String.class);
    private static final Field<String> NAME = DSL.field(DSL.name("display_name"), String.class);
    private static final Field<OffsetDateTime> VERIFIED_AT = DSL.field(DSL.name("verified_at"), OffsetDateTime.class);
    private final DSLContext database;

    public ChannelRepository(DSLContext database) {
        this.database = database;
    }

    public Optional<ConnectedChannel> connectVerified(ConnectedChannel channel) {
        return database.insertInto(DSL.table(DSL.name("connected_channels")))
                .columns(ID, USER_ID, PLATFORM, EXTERNAL_ID, NAME, VERIFIED_AT)
                .values(channel.id(), channel.userId(), channel.platform().name(),
                        channel.externalChannelId(), channel.displayName(), channel.verifiedAt())
                .onConflict(PLATFORM, EXTERNAL_ID).doUpdate()
                .set(NAME, channel.displayName()).set(VERIFIED_AT, channel.verifiedAt())
                .where(DSL.field(DSL.name("connected_channels", "user_id"), UUID.class).eq(channel.userId()))
                .returning(ID, USER_ID, PLATFORM, EXTERNAL_ID, NAME, VERIFIED_AT)
                .fetchOptionalInto(ConnectedChannel.class);
    }
    public void saveVerified(ConnectedChannel channel) {
        database.insertInto(DSL.table(DSL.name("connected_channels")))
                .columns(ID, USER_ID, PLATFORM, EXTERNAL_ID, NAME, VERIFIED_AT)
                .values(channel.id(), channel.userId(), channel.platform().name(),
                        channel.externalChannelId(), channel.displayName(), channel.verifiedAt())
                .execute();
    }

    public Optional<ConnectedChannel> lockOwned(UUID channelId, UUID owner) {
        return database.select(ID, USER_ID, PLATFORM, EXTERNAL_ID, NAME, VERIFIED_AT)
                .from(DSL.table(DSL.name("connected_channels")))
                .where(ID.eq(channelId)).and(USER_ID.eq(owner)).forUpdate()
                .fetchOptionalInto(ConnectedChannel.class);
    }
    public Optional<ConnectedChannel> find(UUID channelId) {
        return database.select(ID, USER_ID, PLATFORM, EXTERNAL_ID, NAME, VERIFIED_AT)
                .from(DSL.table(DSL.name("connected_channels"))).where(ID.eq(channelId))
                .fetchOptionalInto(ConnectedChannel.class);
    }
    public List<ChannelSummary> summariesByUser(UUID userId) {
        var credentialId = DSL.field(DSL.name("channel_credentials", "channel_id"), UUID.class);
        var validatedAt = DSL.field(DSL.name("channel_credentials", "last_validated_at"), OffsetDateTime.class);
        return database.select(ID, PLATFORM, EXTERNAL_ID, NAME, VERIFIED_AT, validatedAt,
                        credentialId.isNull().as("authorization_required"))
                .from(DSL.table(DSL.name("connected_channels")))
                .leftJoin(DSL.table(DSL.name("channel_credentials"))).on(ID.eq(credentialId))
                .where(USER_ID.eq(userId)).orderBy(PLATFORM, EXTERNAL_ID).fetchInto(ChannelSummary.class);
    }
    public List<ConnectedChannel> findByUser(UUID userId) {
        return database.select(ID, USER_ID, PLATFORM, EXTERNAL_ID, NAME, VERIFIED_AT)
                .from(DSL.table(DSL.name("connected_channels")))
                .where(USER_ID.eq(userId)).orderBy(PLATFORM, EXTERNAL_ID)
                .fetchInto(ConnectedChannel.class);
    }
}
