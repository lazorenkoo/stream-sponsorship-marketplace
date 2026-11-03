package com.streambid.platform;

public interface StreamingPlatformProvider {

    StreamingPlatform platform();

    ChannelInfo getChannel(String channelId);

    StreamStatus getStreamStatus(String channelId);
}
