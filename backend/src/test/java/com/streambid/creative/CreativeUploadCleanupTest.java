package com.streambid.creative;

import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class CreativeUploadCleanupTest {
    private final CreativeUploadIntentRepository intents = mock(CreativeUploadIntentRepository.class);
    private final AdCreativeRepository creatives = mock(AdCreativeRepository.class);
    private final S3CreativeStorage objects = mock(S3CreativeStorage.class);
    private final ObjectProvider<S3CreativeStorage> provider = mock(ObjectProvider.class);
    private final CreativeUploadCleanup cleanup = new CreativeUploadCleanup(intents, creatives, provider);

    @Test
    void orphanIsDeletedBeforeItsTombstoneIsRetained() {
        var intent = new CreativeUploadIntent(UUID.randomUUID(), CreativeFormat.WEBP, 1);
        when(provider.getIfAvailable()).thenReturn(objects);
        when(intents.claimExpired(any(), eq(5))).thenReturn(List.of(intent));
        when(creatives.find(intent.creativeId())).thenReturn(Optional.empty());
        cleanup.runBatch();
        var order = inOrder(objects, intents);
        order.verify(objects).delete(intent.creativeId(), intent.format());
        order.verify(intents).retainDeleted(eq(intent), any());
        verify(intents, never()).complete(intent.creativeId());
    }

    @Test
    void validFileIsPreservedAndFailedDeleteRetainsIntentWithoutBlockingNextItem() {
        var valid = new CreativeUploadIntent(UUID.randomUUID(), CreativeFormat.PNG, 1);
        var failed = new CreativeUploadIntent(UUID.randomUUID(), CreativeFormat.WEBP, 2);
        when(provider.getIfAvailable()).thenReturn(objects);
        when(intents.claimExpired(any(), eq(5))).thenReturn(List.of(failed, valid));
        when(creatives.find(valid.creativeId())).thenReturn(Optional.of(mock(AdCreative.class)));
        when(creatives.find(failed.creativeId())).thenReturn(Optional.empty());
        doThrow(new IllegalStateException("Unavailable")).when(objects).delete(failed.creativeId(), failed.format());
        cleanup.runBatch();
        verify(intents, never()).complete(failed.creativeId());
        verify(objects, never()).delete(valid.creativeId(), valid.format());
        verify(intents).complete(valid.creativeId());
    }

    @Test
    void disabledStorageDoesNotClaimWork() {
        cleanup.runBatch();
        verifyNoInteractions(intents, creatives, objects);
    }
}
