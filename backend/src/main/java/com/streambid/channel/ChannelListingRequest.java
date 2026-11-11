package com.streambid.channel;

import jakarta.validation.constraints.NotNull;

public record ChannelListingRequest(@NotNull Boolean listed) {
}
