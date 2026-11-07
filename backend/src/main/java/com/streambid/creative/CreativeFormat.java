package com.streambid.creative;

public enum CreativeFormat {
    PNG("image/png", "png"), JPEG("image/jpeg", "jpg"), WEBP("image/webp", "webp");
    private final String contentType;
    private final String extension;

    CreativeFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() { return contentType; }
    public String extension() { return extension; }
}
