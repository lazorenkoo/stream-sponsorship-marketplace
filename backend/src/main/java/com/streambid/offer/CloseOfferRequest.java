package com.streambid.offer;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
public record CloseOfferRequest(@NotNull OfferCloseAction action, @NotNull @Min(0) Long expectedVersion) { }
