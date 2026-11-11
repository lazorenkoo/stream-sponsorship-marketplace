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
import javax.imageio.ImageReader;
import org.w3c.dom.Element;
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
                if (format == CreativeFormat.GIF) {
                    var screen = ByteBuffer.wrap(bytes, 6, 4).order(ByteOrder.LITTLE_ENDIAN);
                    width = Short.toUnsignedInt(screen.getShort());
                    height = Short.toUnsignedInt(screen.getShort());
                }
                if (width < 1 || height < 1 || width > 8192 || height > 8192 || (long) width * height > 40000000) {
                    throw new IllegalArgumentException("Image dimensions exceed the allowed bounds");
                }
                reader.addIIOReadWarningListener((source, warning) -> { throw new IllegalArgumentException("Damaged image"); });
                if (format == CreativeFormat.GIF) {
                    validateGif(bytes, reader, width, height);
                } else {
                    requireStatic(bytes, format);
                    if (reader.getNumImages(true) != 1) throw new IllegalArgumentException("Only static images are supported for this format");
                    decodeFrame(reader, 0);
                }
                var digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                return new VerifiedImage(format, width, height, bytes.length, digest);
            } finally {
                reader.dispose();
            }
        } catch (IOException | IndexOutOfBoundsException exception) {
            throw new IllegalArgumentException("Invalid image data", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void validateGif(byte[] bytes, ImageReader reader, int width, int height) throws IOException {
        if (bytes[bytes.length - 1] != 0x3b) throw new IllegalArgumentException("Missing GIF ending");
        int frames = reader.getNumImages(true);
        if (frames < 1 || frames > 200 || (frames > 1 && (long) width * height * frames > 120000000)) {
            throw new IllegalArgumentException("GIF animation exceeds the allowed bounds");
        }
        for (int index = 0; index < frames; index++) {
            var metadata = (Element) reader.getImageMetadata(index).getAsTree("javax_imageio_gif_image_1.0");
            var descriptor = (Element) metadata.getElementsByTagName("ImageDescriptor").item(0);
            int left = Integer.parseInt(descriptor.getAttribute("imageLeftPosition"));
            int top = Integer.parseInt(descriptor.getAttribute("imageTopPosition"));
            int frameWidth = reader.getWidth(index);
            int frameHeight = reader.getHeight(index);
            if (frameWidth < 1 || frameHeight < 1 || frameWidth > width - left || frameHeight > height - top) {
                throw new IllegalArgumentException("GIF frame exceeds the canvas");
            }
            decodeFrame(reader, index);
        }
    }

    private void decodeFrame(ImageReader reader, int index) throws IOException {
        var image = reader.read(index);
        if (image == null) throw new IllegalArgumentException("Image could not be decoded");
        image.flush();
    }

    private void requireStatic(byte[] bytes, CreativeFormat format) {
        if (format == CreativeFormat.PNG) {
            boolean ended = false;
            for (int offset = 8; offset <= bytes.length - 12;) {
                int size = ByteBuffer.wrap(bytes, offset, 4).getInt();
                if (size < 0 || size > bytes.length - offset - 12) throw new IllegalArgumentException("Invalid PNG chunk");
                String chunk = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
                if (chunk.equals("acTL")) throw new IllegalArgumentException("Animated PNG is unsupported");
                if (chunk.equals("IEND")) {
                    if (size != 0 || offset + 12 != bytes.length) throw new IllegalArgumentException("Invalid PNG ending");
                    ended = true;
                }
                offset += size + 12;
            }
            if (!ended) throw new IllegalArgumentException("Missing PNG ending");
        }
        if (format == CreativeFormat.JPEG && (bytes.length < 2 || bytes[bytes.length - 2] != (byte) 0xff
                || bytes[bytes.length - 1] != (byte) 0xd9)) {
            throw new IllegalArgumentException("Missing JPEG ending");
        }
        if (format == CreativeFormat.WEBP) {
            long size = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
            if (size != bytes.length - 8L) throw new IllegalArgumentException("Invalid WebP length");
            if (bytes.length >= 21 && new String(bytes, 12, 4, StandardCharsets.US_ASCII).equals("VP8X")
                    && (bytes[20] & 2) != 0) throw new IllegalArgumentException("Animated WebP is unsupported");
        }
    }
}
