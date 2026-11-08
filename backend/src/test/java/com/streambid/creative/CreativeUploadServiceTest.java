package com.streambid.creative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import com.streambid.user.UserAccount;
import com.streambid.user.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.server.ResponseStatusException;

class CreativeUploadServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AdCreativeRepository creatives = mock(AdCreativeRepository.class);
    private final CreativeUploadIntentRepository intents = mock(CreativeUploadIntentRepository.class);
    private final S3CreativeStorage objects = mock(S3CreativeStorage.class);
    private final ObjectProvider<S3CreativeStorage> provider = mock(ObjectProvider.class);
    private final CreativeUploadService service = new CreativeUploadService(users, creatives, new CreativeImageValidator(), provider, intents);
    private final UUID owner = UUID.randomUUID();
    private final byte[] image = java.util.Base64.getDecoder().decode("UklGRh4AAABXRUJQVlA4TBEAAAAvAUAAAAdQslKUtv+BiOh/AAA=");

    @BeforeEach
    void ownerAndStorage() {
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(new UserAccount(owner, "owner@example.com", "hash", "Owner")));
        when(users.findRoles(owner)).thenReturn(List.of("ADVERTISER"));
        when(provider.getIfAvailable()).thenReturn(objects);
    }

    @Test
    void metadataComesFromValidatedImageAndStartsPending() {
        when(creatives.finishUpload(any())).thenAnswer(call -> call.getArgument(0));
        var saved = service.upload("owner@example.com", "Banner", image);
        assertThat(saved.advertiserId()).isEqualTo(owner);
        assertThat(saved.status()).isEqualTo(CreativeStatus.PENDING);
        assertThat(saved.widthPx()).isEqualTo(2);
        var order = inOrder(intents, objects, creatives);
        order.verify(intents).create(saved.id(), CreativeFormat.WEBP, saved.createdAt());
        order.verify(objects).put(saved.id(), CreativeFormat.WEBP, image);
        order.verify(creatives).finishUpload(any());
        verify(objects, never()).delete(any(), any());
    }

    @Test
    void uncertainMetadataCommitNeverDeletesUploadedObject() {
        var failure = new IllegalStateException("Persistence failed");
        when(creatives.finishUpload(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.upload("owner@example.com", "Banner", image)).isSameAs(failure);
        var saved = org.mockito.ArgumentCaptor.forClass(AdCreative.class);
        verify(creatives).finishUpload(saved.capture());
        verify(objects, never()).delete(any(), any());
        verify(intents, never()).complete(any());
    }

    @Test
    void invalidContentNeverReachesStorage() {
        assertThatThrownBy(() -> service.upload("owner@example.com", "Banner", new byte[0])).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(objects, creatives, intents);
    }
    @Test
    void unavailableIntentDatabasePreventsObjectUpload() {
        doThrow(new IllegalStateException("Database unavailable")).when(intents).create(any(), any(), any());
        assertThatThrownBy(() -> service.upload("owner@example.com", "Banner", image))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(objects, creatives);
    }

    @Test
    void failedUploadRetainsIntentForRecoveryWhenDeleteAlsoFails() {
        doThrow(new IllegalStateException("Upload interrupted")).when(objects).put(any(), any(), any());
        doThrow(new IllegalStateException("Delete unavailable")).when(objects).delete(any(), any());
        assertThatThrownBy(() -> service.upload("owner@example.com", "Banner", image))
                .isInstanceOf(ResponseStatusException.class);
        verify(intents).create(any(), eq(CreativeFormat.WEBP), any());
        verify(intents, never()).complete(any());
        verifyNoInteractions(creatives);
    }
}
