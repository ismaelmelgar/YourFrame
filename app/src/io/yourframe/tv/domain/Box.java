package io.yourframe.tv.domain;

/** A rectangle in screen pixels. */
public final class Box {
    public final float left, top, right, bottom;

    public Box(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public float width() { return right - left; }
    public float height() { return bottom - top; }
}
