package com.streambid.creative;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreativeUploadCleanup {
    private static final Logger log = LoggerFactory.getLogger(CreativeUploadCleanup.class);
    private final CreativeUploadIntentRepository intents;
    private final AdCreativeRepository creatives;
    private final ObjectProvider<S3CreativeStorage> storage;
    private final Clock clock = Clock.systemUTC();

    public CreativeUploadCleanup(CreativeUploadIntentRepository intents, AdCreativeRepository creatives,
            ObjectProvider<S3CreativeStorage> storage) {
        this.intents = intents;
        this.creatives = creatives;
        this.storage = storage;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void runBatch() {
        var objects = storage.getIfAvailable();
        if (objects == null) return;
        // Five bounded storage requests fit within the five-minute retry lease.
        for (var intent : intents.claimExpired(clock.instant(), 5)) {
            try {
                if (creatives.find(intent.creativeId()).isEmpty()) {
                    objects.delete(intent.creativeId(), intent.format());
                }
                intents.complete(intent.creativeId());
            } catch (RuntimeException failure) {
                // Keep the durable intent for a later lease; never log storage credentials.
                log.warn("Creative upload cleanup will retry {} after attempt {}",
                        intent.creativeId(), intent.attempts());
            }
        }
    }
}
