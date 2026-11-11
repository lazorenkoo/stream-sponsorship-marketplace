package com.streambid.creative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class CreativeImageValidatorTest {
    private final CreativeImageValidator validator = new CreativeImageValidator();
    @Test
    void decodesStaticWebpAndRejectsAnimationAndTruncation() {
        var bytes = java.util.Base64.getDecoder().decode("UklGRh4AAABXRUJQVlA4TBEAAAAvAUAAAAdQslKUtv+BiOh/AAA=");
        var verified = validator.validate(bytes);
        assertThat(verified.format()).isEqualTo(CreativeFormat.WEBP);
        assertThat(verified.widthPx()).isEqualTo(2);
        assertThat(verified.heightPx()).isEqualTo(2);
        assertThatThrownBy(() -> validator.validate(java.util.Arrays.copyOf(bytes, bytes.length - 4)))
                .isInstanceOf(IllegalArgumentException.class);
        var animation = java.util.Base64.getDecoder().decode(
                "UklGRoQAAABXRUJQVlA4WAoAAAACAAAAAQAAAQAAQU5JTQYAAAAAAAAAAABBTk1GKAAAAAAAAAAAAAEAAAEAAGQAAAJWUDhMDwAAAC8BQAAABxD9j/4HIqL/AQBBTk1GKAAAAAAAAAAAAAEAAAEAAGQAAABWUDhMDwAAAC8BQAAABxDR//4HIqL/AQA=");
        assertThatThrownBy(() -> validator.validate(animation)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTruncatedPngAndJpegAndExcessivePngDimensions() throws Exception {
        for (String format : new String[] {"png", "jpeg"}) {
            var output = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output);
            byte[] bytes = output.toByteArray();
            assertThatThrownBy(() -> validator.validate(java.util.Arrays.copyOf(bytes, bytes.length - 8)))
                    .isInstanceOf(IllegalArgumentException.class);
            if (format.equals("png")) {
                java.nio.ByteBuffer.wrap(bytes, 16, 4).putInt(8193);
                var crc = new java.util.zip.CRC32();
                crc.update(bytes, 12, 17);
                java.nio.ByteBuffer.wrap(bytes, 29, 4).putInt((int) crc.getValue());
                assertThatThrownBy(() -> validator.validate(bytes)).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("dimensions");
            }
        }
    }

    @Test
    void decodesImagesAndDerivesMetadataFromContent() throws Exception {
        for (String format : new String[] {"png", "jpeg"}) {
            var output = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(320, 180, BufferedImage.TYPE_INT_RGB), format, output);
            var verified = validator.validate(output.toByteArray());
            assertThat(verified.widthPx()).isEqualTo(320);
            assertThat(verified.heightPx()).isEqualTo(180);
            assertThat(verified.sha256()).hasSize(64);
            assertThat(verified.format()).isEqualTo(format.equals("png") ? CreativeFormat.PNG : CreativeFormat.JPEG);
        }
    }

    @Test
    void rejectsUnsupportedEmptyAndOversizedContent() {
        for (byte[] bytes : new byte[][] {new byte[0], "not an image".getBytes(), new byte[10485761]}) {
            assertThatThrownBy(() -> validator.validate(bytes)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
