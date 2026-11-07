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
