package com.streambid.offer;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OfferRectangleTest {
    @Test
    void permitsCanvasEdgesAndRejectsOverflowOrEmptySelection() {
        assertThat(new OfferRectangle(0, 0, 10000, 10000).width()).isEqualTo(10000);
        assertThat(new OfferRectangle(9999, 9999, 1, 1).height()).isEqualTo(1);
        assertThatThrownBy(() -> new OfferRectangle(-1, 0, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OfferRectangle(0, 0, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OfferRectangle(9999, 0, 2, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OfferRectangle(0, 9999, 1, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OfferRectangle(1, 0, Integer.MAX_VALUE, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void touchingEdgesDoNotOverlapButSharedAreaDoes() {
        var selected = new OfferRectangle(1000, 1000, 2000, 2000);
        assertThat(selected.overlaps(new OfferRectangle(3000, 1000, 1000, 1000))).isFalse();
        assertThat(selected.overlaps(new OfferRectangle(1000, 3000, 1000, 1000))).isFalse();
        assertThat(selected.overlaps(new OfferRectangle(2999, 2999, 1000, 1000))).isTrue();
        assertThat(selected.overlaps(new OfferRectangle(1500, 1500, 100, 100))).isTrue();
    }
}
