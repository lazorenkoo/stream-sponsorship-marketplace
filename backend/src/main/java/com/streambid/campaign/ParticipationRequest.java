package com.streambid.campaign;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ParticipationRequest(@NotNull UUID channelId) { }
