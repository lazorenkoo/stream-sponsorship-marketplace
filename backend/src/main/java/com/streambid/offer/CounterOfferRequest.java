package com.streambid.offer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CounterOfferRequest(@NotNull @Min(0) Long expectedVersion, @NotNull @Valid OfferTerms terms) { }
