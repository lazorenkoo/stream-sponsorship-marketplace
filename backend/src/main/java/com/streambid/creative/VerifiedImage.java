package com.streambid.creative;
public record VerifiedImage(CreativeFormat format, int widthPx, int heightPx, long byteSize, String sha256) { }
