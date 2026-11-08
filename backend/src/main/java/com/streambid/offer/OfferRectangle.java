package com.streambid.offer;

/** Coordinates use a 10000 by 10000 normalized canvas, independent of preview resolution. */
public record OfferRectangle(int x, int y, int width, int height) {
    public static final int CANVAS_SIZE = 10000;

    public OfferRectangle {
        if (x < 0 || y < 0 || x >= CANVAS_SIZE || y >= CANVAS_SIZE
                || width < 1 || height < 1 || width > CANVAS_SIZE - x || height > CANVAS_SIZE - y) {
            throw new IllegalArgumentException("Advertising rectangle must fit within the canvas");
        }
    }

    public boolean overlaps(OfferRectangle other) {
        java.util.Objects.requireNonNull(other, "Other rectangle is required");
        return x < other.x + other.width && other.x < x + width
                && y < other.y + other.height && other.y < y + height;
    }
}
