package com.example.rajawali.debug;

import android.graphics.Color;

import com.example.rajawali.primitives.Line3D;
import com.example.rajawali.renderer.Renderer;

/**
 * @author dennis.ippel
 */
public class DebugObject3D extends Line3D {
    protected Renderer mRenderer;

    public DebugObject3D() {
        this(Color.YELLOW, 1);
    }

    public DebugObject3D(int color, int lineThickness) {
        setColor(color);
        mLineThickness = lineThickness;
    }

    public void setRenderer(Renderer renderer) {
        mRenderer = renderer;
    }
}
