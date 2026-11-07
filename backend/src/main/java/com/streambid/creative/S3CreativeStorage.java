package com.streambid.creative;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

public class S3CreativeStorage {
    private final S3Client client;
    private final String bucket;

    public S3CreativeStorage(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    public void put(UUID id, CreativeFormat format, byte[] bytes) {
        if (bytes == null || bytes.length < 1 || bytes.length > 10485760) {
            throw new IllegalArgumentException("Invalid creative object size");
        }
        client.putObject(request -> request.bucket(bucket).key(key(id, format)).contentType(format.contentType()),
                RequestBody.fromBytes(bytes));
    }

    public byte[] read(UUID id, CreativeFormat format) throws IOException {
        try (var stream = client.getObject(request -> request.bucket(bucket).key(key(id, format)))) {
            byte[] bytes = stream.readNBytes(10485761);
            if (bytes.length == 0 || bytes.length > 10485760) throw new IOException("Invalid stored creative size");
            return bytes;
        }
    }

    public void delete(UUID id, CreativeFormat format) {
        client.deleteObject(request -> request.bucket(bucket).key(key(id, format)));
    }

    private String key(UUID id, CreativeFormat format) {
        return "creatives/" + Objects.requireNonNull(id) + "." + Objects.requireNonNull(format).extension();
    }
}
