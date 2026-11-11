package com.streambid.creative;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@ConditionalOnProperty(name = "creatives.storage.enabled", havingValue = "true")
@EnableConfigurationProperties(CreativeStorageProperties.class)
public class CreativeStorageConfiguration {
    @Bean(destroyMethod = "close")
    public S3Client creativeStorageClient(CreativeStorageProperties properties) {
        var client = S3Client.builder().region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .httpClientBuilder(UrlConnectionHttpClient.builder().connectionTimeout(Duration.ofSeconds(3))
                        .socketTimeout(Duration.ofSeconds(10)))
                .overrideConfiguration(config -> config.apiCallAttemptTimeout(Duration.ofSeconds(15))
                        .apiCallTimeout(Duration.ofSeconds(30)));
        var endpoint = properties.endpoint();
        if (endpoint != null) {
            if (!("http".equals(endpoint.getScheme()) || "https".equals(endpoint.getScheme()))
                    || endpoint.getHost() == null || endpoint.getUserInfo() != null
                    || endpoint.getQuery() != null || endpoint.getFragment() != null) {
                throw new IllegalArgumentException("Invalid creative storage endpoint");
            }
            client.endpointOverride(endpoint).forcePathStyle(true);
        }
        return client.build();
    }

    @Bean
    public S3CreativeStorage creativeStorage(S3Client creativeStorageClient, CreativeStorageProperties properties) {
        return new S3CreativeStorage(creativeStorageClient, properties.bucket());
    }
}
