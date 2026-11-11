package com.streambid.creative;
import java.util.UUID;
public record CreativeUploadIntent(UUID creativeId, CreativeFormat format, int attempts) { }
