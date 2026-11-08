package com.streambid.creative;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "creative.storage.cleanup-enabled", havingValue = "true")
public class CreativeUploadCleanupJob {
    private final CreativeUploadCleanup cleanup;

    public CreativeUploadCleanupJob(CreativeUploadCleanup cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(initialDelay = 60000, fixedDelay = 60000)
    public void run() {
        cleanup.runBatch();
    }
}
