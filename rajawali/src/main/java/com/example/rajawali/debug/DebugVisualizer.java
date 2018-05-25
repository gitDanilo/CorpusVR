package com.example.rajawali.debug;

import com.example.rajawali.Object3D;
import com.example.rajawali.renderer.Renderer;

/**
 * @author dennis.ippel
 */
public class DebugVisualizer extends Object3D {
    private Renderer mRenderer;

    public DebugVisualizer(Renderer renderer) {
        mRenderer = renderer;
    }

    public void addChild(DebugObject3D child) {
        super.addChild(child);
        child.setRenderer(mRenderer);
    }
}
