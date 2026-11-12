package dev.itstiers;

import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/** Thin drawing layer so the whole UI is shared between Minecraft versions. */
public interface Gfx {
    int width();
    int height();
    void fill(int x0, int y0, int x1, int y1, int argb);
    void grad(int x0, int y0, int x1, int y1, int top, int bottom);
    void text(String s, int x, int y, int argb, boolean bold);
    int textW(String s, boolean bold);
    void tex(Identifier id, int x, int y, int w, int h, int argb);
    Matrix3x2fStack pose();
    void clip(int x0, int y0, int x1, int y1);
    void unclip();
}