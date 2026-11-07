package com.streambid.creative;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("creatives.storage")
public record CreativeStorageProperties(@NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]") String bucket,
        @NotBlank String region, URI endpoint) { }
