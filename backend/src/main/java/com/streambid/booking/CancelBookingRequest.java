package com.streambid.booking;
import jakarta.validation.constraints.*;
public record CancelBookingRequest(@NotNull @Min(0) Long expectedVersion, @NotBlank @Size(max = 500) String reason) { }
