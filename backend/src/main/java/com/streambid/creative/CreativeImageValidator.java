package com.streambid.creative;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;

@Component
public class CreativeImageValidator {
    public VerifiedImage validate(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > 10485760) {
            throw new IllegalArgumentException("Image must contain between one byte and ten MiB");
        }
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Unsupported image format");
            var reader = readers.next();
            try {
                reader.setInput(input);
                var format = CreativeFormat.valueOf(reader.getFormatName().toUpperCase(Locale.ROOT));
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 8192 || height > 8192 || (long) width * height > 40000000) {
                    throw new IllegalArgumentException("Image dimensions exceed the allowed bounds");
                }
                requireStatic(bytes, format);
                if (reader.getNumImages(true) != 1) throw new IllegalArgumentException("Only static images are supported");
                reader.addIIOReadWarningListener((source, warning) -> { throw new IllegalArgumentException("Damaged image"); });
                var image = reader.read(0);
                if (image == null) throw new IllegalArgumentException("Image could not be decoded");
                image.flush();
                var digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                return new VerifiedImage(format, width, height, bytes.length, digest);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid image data", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void requireStatic(byte[] bytes, CreativeFormat format) {
        if (format == CreativeFormat.PNG) {
            for (int offset = 8; offset <= bytes.length - 12;) {
                int size = ByteBuffer.wrap(bytes, offset, 4).getInt();
                if (size < 0 || size > bytes.length - offset - 12) throw new IllegalArgumentException("Invalid PNG chunk");
                String chunk = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
                if (chunk.equals("acTL")) throw new IllegalArgumentException("Animated PNG is unsupported");
                offset += size + 12;
            }
        }
        if (format == CreativeFormat.WEBP) {
            long size = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
            if (size != bytes.length - 8L) throw new IllegalArgumentException("Invalid WebP length");
            if (bytes.length >= 21 && new String(bytes, 12, 4, StandardCharsets.US_ASCII).equals("VP8X")
                    && (bytes[20] & 2) != 0) throw new IllegalArgumentException("Animated WebP is unsupported");
        }
    }
}
