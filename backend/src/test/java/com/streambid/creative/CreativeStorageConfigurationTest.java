package com.streambid.creative;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

class CreativeStorageConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(CreativeStorageConfiguration.class);

    @Test
    void storageIsDisabledUntilExplicitlyConfigured() {
        context.run(application -> assertThat(application).doesNotHaveBean(S3CreativeStorage.class));
        context.withPropertyValues("creatives.storage.enabled=true", "creatives.storage.bucket=streambid-creatives",
                "creatives.storage.region=us-east-1", "creatives.storage.endpoint=http://localhost:9000")
                .run(application -> assertThat(application).hasSingleBean(S3Client.class).hasSingleBean(S3CreativeStorage.class));
    }

    @Test
    void credentialsInEndpointAndMissingBucketPreventStartup() {
        context.withPropertyValues("creatives.storage.enabled=true", "creatives.storage.region=us-east-1")
                .run(application -> assertThat(application).hasFailed());
        context.withPropertyValues("creatives.storage.enabled=true", "creatives.storage.bucket=streambid-creatives",
                "creatives.storage.region=us-east-1", "creatives.storage.endpoint=http://user:secret@localhost:9000")
                .run(application -> assertThat(application).hasFailed());
    }
}
