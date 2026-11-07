package com.streambid.creative;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AdCreative(UUID id, UUID advertiserId, String title, CreativeFormat format, int widthPx, int heightPx,
        long byteSize, String sha256, CreativeStatus status, long version, Instant createdAt) {
    public AdCreative {
        Objects.requireNonNull(id, "Creative ID is required");
        Objects.requireNonNull(advertiserId, "Advertiser ID is required");
        Objects.requireNonNull(format, "Image format is required");
        Objects.requireNonNull(status, "Moderation status is required");
        Objects.requireNonNull(createdAt, "Creation time is required");
        if (title == null || title.isBlank() || title.strip().length() > 120
                || title.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Creative title must contain 1 to 120 printable characters");
        }
        title = title.strip();
        if (widthPx < 1 || widthPx > 8192 || heightPx < 1 || heightPx > 8192 || (long) widthPx * heightPx > 40000000
                || byteSize < 1 || byteSize > 10485760 || version < 0 || sha256 == null || !sha256.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Invalid creative dimensions, size, digest or version");
        }
        createdAt = createdAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    }

    public String storageKey() {
        return "creatives/" + id + "." + format.extension();
    }
}
