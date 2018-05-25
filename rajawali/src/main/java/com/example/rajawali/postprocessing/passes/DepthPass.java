package com.example.rajawali.postprocessing.passes;

import android.opengl.GLES20;

import com.example.rajawali.cameras.Camera;
import com.example.rajawali.materials.Material;
import com.example.rajawali.materials.plugins.DepthMaterialPlugin;
import com.example.rajawali.postprocessing.APass;
import com.example.rajawali.primitives.ScreenQuad;
import com.example.rajawali.renderer.Renderer;
import com.example.rajawali.renderer.RenderTarget;
import com.example.rajawali.scene.Scene;


public class DepthPass extends APass {
	protected Scene               mScene;
	protected Camera              mCamera;
	protected Camera              mOldCamera;
	protected DepthMaterialPlugin mDepthPlugin;

	public DepthPass(Scene scene, Camera camera) {
		mPassType = PassType.DEPTH;
		mScene = scene;
		mCamera = camera;

		mEnabled = true;
		mClear = true;
		mNeedsSwap = true;

		Material mat = new Material();
		mDepthPlugin = new DepthMaterialPlugin();
		mat.addPlugin(mDepthPlugin);
		setMaterial(mat);
	}

	@Override
	public void render(Scene scene, Renderer renderer, ScreenQuad screenQuad, RenderTarget writeTarget,
					   RenderTarget readTarget, long ellapsedTime, double deltaTime) {
		GLES20.glClearColor(0, 0, 0, 1);
		mDepthPlugin.setFarPlane((float)mCamera.getFarPlane());
		mOldCamera = mScene.getCamera();
		mScene.switchCamera(mCamera);
		mScene.render(ellapsedTime, deltaTime, writeTarget, mMaterial);
		mScene.switchCamera(mOldCamera);
	}
}
