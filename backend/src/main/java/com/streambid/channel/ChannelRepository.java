package com.streambid.channel;

import java.time.OffsetDateTime;
import java.util.List;
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

    public void saveVerified(ConnectedChannel channel) {
        database.insertInto(DSL.table(DSL.name("connected_channels")))
                .columns(ID, USER_ID, PLATFORM, EXTERNAL_ID, NAME, VERIFIED_AT)
                .values(channel.id(), channel.userId(), channel.platform().name(),
                        channel.externalChannelId(), channel.displayName(), channel.verifiedAt())
                .execute();
    }

    public List<ConnectedChannel> findByUser(UUID userId) {
        return database.select(ID, USER_ID, PLATFORM, EXTERNAL_ID, NAME, VERIFIED_AT)
                .from(DSL.table(DSL.name("connected_channels")))
                .where(USER_ID.eq(userId)).orderBy(PLATFORM, EXTERNAL_ID)
                .fetchInto(ConnectedChannel.class);
    }
}
