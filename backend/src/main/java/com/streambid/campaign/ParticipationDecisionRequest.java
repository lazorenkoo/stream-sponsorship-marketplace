package com.streambid.campaign;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ParticipationDecisionRequest(@NotNull ParticipationAction action,
        @NotNull @PositiveOrZero Long expectedVersion) { }
