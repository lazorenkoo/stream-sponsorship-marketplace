package com.streambid.creative;
import java.time.Instant;
import java.util.UUID;

public record CreativeView(UUID id, String title, CreativeFormat format, int widthPx, int heightPx,
        long byteSize, CreativeStatus status, long version, Instant createdAt) {
    public static CreativeView from(AdCreative creative) {
        return new CreativeView(creative.id(), creative.title(), creative.format(), creative.widthPx(), creative.heightPx(),
                creative.byteSize(), creative.status(), creative.version(), creative.createdAt());
    }
}
