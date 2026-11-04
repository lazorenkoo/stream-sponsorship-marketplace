package com.streambid.channel;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class ChannelCredentialsRepository {

    private static final Field<UUID> CHANNEL_ID = DSL.field(DSL.name("channel_id"), UUID.class);
    private static final Field<String> ACCESS = DSL.field(DSL.name("access_token_ciphertext"), String.class);
    private static final Field<String> REFRESH = DSL.field(DSL.name("refresh_token_ciphertext"), String.class);
    private static final Field<OffsetDateTime> EXPIRES = DSL.field(DSL.name("expires_at"), OffsetDateTime.class);
    private static final Field<OffsetDateTime> VALIDATED = DSL.field(DSL.name("last_validated_at"), OffsetDateTime.class);
    private final DSLContext database;

    public ChannelCredentialsRepository(DSLContext database) {
        this.database = database;
    }

    public void save(ChannelCredentials credentials) {
        database.insertInto(DSL.table(DSL.name("channel_credentials")))
                .columns(CHANNEL_ID, ACCESS, REFRESH, EXPIRES, VALIDATED)
                .values(credentials.channelId(), credentials.accessTokenCiphertext(), credentials.refreshTokenCiphertext(),
                        credentials.expiresAt(), credentials.lastValidatedAt())
                .onConflict(CHANNEL_ID).doUpdate()
                .set(ACCESS, credentials.accessTokenCiphertext()).set(REFRESH, credentials.refreshTokenCiphertext())
                .set(EXPIRES, credentials.expiresAt()).set(VALIDATED, credentials.lastValidatedAt()).execute();
    }

    public boolean markValidated(ChannelCredentials snapshot, OffsetDateTime checkedAt, OffsetDateTime expiresAt) {
        return database.update(DSL.table(DSL.name("channel_credentials")))
                .set(VALIDATED, checkedAt).set(EXPIRES, expiresAt)
                .where(CHANNEL_ID.eq(snapshot.channelId())).and(ACCESS.eq(snapshot.accessTokenCiphertext()))
                .and(REFRESH.eq(snapshot.refreshTokenCiphertext())).execute() == 1;
    }
    public List<UUID> dueForValidation(OffsetDateTime cutoff, UUID after, int limit) {
        return database.select(CHANNEL_ID).from(DSL.table(DSL.name("channel_credentials")))
                .join(DSL.table(DSL.name("connected_channels")))
                .on(CHANNEL_ID.eq(DSL.field(DSL.name("connected_channels", "id"), UUID.class)))
                .where(DSL.field(DSL.name("connected_channels", "platform"), String.class).eq("TWITCH"))
                .and(VALIDATED.le(cutoff)).and(after == null ? DSL.trueCondition() : CHANNEL_ID.gt(after))
                .orderBy(CHANNEL_ID).limit(limit).fetch(CHANNEL_ID);
    }
    public void deleteIfUnchanged(ChannelCredentials snapshot) {
        database.deleteFrom(DSL.table(DSL.name("channel_credentials")))
                .where(CHANNEL_ID.eq(snapshot.channelId())).and(ACCESS.eq(snapshot.accessTokenCiphertext()))
                .and(REFRESH.eq(snapshot.refreshTokenCiphertext())).execute();
    }
    public Optional<ChannelCredentials> lock(UUID channelId) {
        return database.select(CHANNEL_ID, ACCESS, REFRESH, EXPIRES, VALIDATED)
                .from(DSL.table(DSL.name("channel_credentials"))).where(CHANNEL_ID.eq(channelId))
                .forUpdate().fetchOptionalInto(ChannelCredentials.class);
    }

    public void delete(UUID channelId) {
        database.deleteFrom(DSL.table(DSL.name("channel_credentials"))).where(CHANNEL_ID.eq(channelId)).execute();
    }
    public Optional<ChannelCredentials> find(UUID channelId) {
        return database.select(CHANNEL_ID, ACCESS, REFRESH, EXPIRES, VALIDATED)
                .from(DSL.table(DSL.name("channel_credentials"))).where(CHANNEL_ID.eq(channelId))
                .fetchOptionalInto(ChannelCredentials.class);
    }
}
