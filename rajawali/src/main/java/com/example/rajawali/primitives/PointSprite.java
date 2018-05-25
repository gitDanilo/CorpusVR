package com.example.rajawali.primitives;

import com.example.rajawali.cameras.Camera;
import com.example.rajawali.materials.Material;
import com.example.rajawali.math.Matrix4;
import com.example.rajawali.math.vector.Vector3.Axis;


public class PointSprite extends Plane {
	public PointSprite(float width, float height) {
		super(width, height, 1, 1, Axis.Z);
	}

    public PointSprite(float width, float height, boolean createVBOs) {
        super(width, height, 1, 1, Axis.Z, true, false, 1, createVBOs);
    }
	
	@Override
	public void render(Camera camera, final Matrix4 vpMatrix, final Matrix4 projMatrix, final Matrix4 vMatrix,
			final Matrix4 parentMatrix, Material sceneMaterial) {
		setLookAt(camera.getPosition());		
		super.render(camera, vpMatrix, projMatrix, vMatrix, parentMatrix, sceneMaterial);
	}
}
