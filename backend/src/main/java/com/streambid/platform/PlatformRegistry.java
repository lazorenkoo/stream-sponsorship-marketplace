package com.streambid.platform;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PlatformRegistry {

    private final Map<StreamingPlatform, StreamingPlatformProvider> providers = new EnumMap<>(StreamingPlatform.class);

    public PlatformRegistry(List<StreamingPlatformProvider> availableProviders) {
        for (var provider : availableProviders) {
            if (providers.putIfAbsent(provider.platform(), provider) != null) {
                throw new IllegalStateException("Duplicate provider for " + provider.platform());
            }
        }
    }

    public Optional<StreamingPlatformProvider> find(StreamingPlatform platform) {
        return Optional.ofNullable(providers.get(platform));
    }
}
