package com.streambid.campaign;

import com.streambid.user.UserAccount;
import com.streambid.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CampaignDraftServiceTest {
    @Test
    void usesInjectedClockAndDoesNotRegressExistingUpdateTime() {
        var campaigns = mock(CampaignRepository.class);
        var users = mock(UserRepository.class);
        var owner = new UserAccount(UUID.randomUUID(), "clock@example.com", "hash", "Owner");
        when(users.findByEmail(owner.email())).thenReturn(Optional.of(owner));
        when(users.findRoles(owner.id())).thenReturn(List.of("ADVERTISER"));
        var now = Instant.parse("2026-11-04T19:20:00Z");
        var service = new CampaignDraftService(campaigns, users, mock(CampaignEventRepository.class), Clock.fixed(now, ZoneOffset.UTC));
        var created = service.create(owner.email(), new CampaignDraftRequest("Draft", ""));
        assertThat(created.createdAt()).isEqualTo(now);
        var existing = new Campaign(created.id(), owner.id(), "Draft", "", CampaignStatus.DRAFT,
                now, now.plusSeconds(60));
        when(campaigns.findOwned(created.id(), owner.id())).thenReturn(Optional.of(existing));
        when(campaigns.updateDraft(any(), eq(0L))).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
        service.update(owner.email(), created.id(), new CampaignDraftUpdateRequest("Edited", "", 0L));
        var changed = ArgumentCaptor.forClass(Campaign.class);
        verify(campaigns).updateDraft(changed.capture(), eq(0L));
        assertThat(changed.getValue().updatedAt()).isEqualTo(existing.updatedAt());
        assertThat(changed.getValue().createdAt()).isEqualTo(existing.createdAt());
    }
}
