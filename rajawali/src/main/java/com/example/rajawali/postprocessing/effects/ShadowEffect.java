package com.example.rajawali.postprocessing.effects;

import com.example.rajawali.cameras.Camera;
import com.example.rajawali.lights.DirectionalLight;
import com.example.rajawali.materials.textures.ATexture.FilterType;
import com.example.rajawali.materials.textures.ATexture.WrapType;
import com.example.rajawali.postprocessing.APostProcessingEffect;
import com.example.rajawali.postprocessing.materials.ShadowMapMaterial;
import com.example.rajawali.postprocessing.passes.ShadowPass;
import com.example.rajawali.postprocessing.passes.ShadowPass.ShadowPassType;
import com.example.rajawali.renderer.Renderer;
import com.example.rajawali.renderer.RenderTarget;
import com.example.rajawali.scene.Scene;
import android.graphics.Bitmap.Config;
import android.opengl.GLES20;


public class ShadowEffect extends APostProcessingEffect {
	private Scene             mScene;
	private Camera            mCamera;
	private DirectionalLight  mLight;
	private int               mShadowMapSize;
	private RenderTarget      mShadowRenderTarget;
	private float             mShadowInfluence;
	private ShadowMapMaterial mShadowMapMaterial;

	public ShadowEffect(Scene scene, Camera camera, DirectionalLight light, int shadowMapSize) {
		super();
		mScene = scene;
		mCamera = camera;
		mLight = light;
		mShadowMapSize = shadowMapSize;
	}

	public void setShadowInfluence(float influence) {
		mShadowInfluence = influence;
		if(mShadowMapMaterial != null)
			mShadowMapMaterial.setShadowInfluence(influence);
	}

	@Override
	public void initialize(Renderer renderer) {
		mShadowRenderTarget = new RenderTarget("shadowRT" + hashCode(), mShadowMapSize, mShadowMapSize, 0, 0,
				false, false, GLES20.GL_TEXTURE_2D, Config.ARGB_8888,
				FilterType.LINEAR, WrapType.CLAMP);
		renderer.addRenderTarget(mShadowRenderTarget);

		ShadowPass pass1 = new ShadowPass(ShadowPassType.CREATE_SHADOW_MAP, mScene, mCamera, mLight, mShadowRenderTarget);
		addPass(pass1);
		ShadowPass pass2 = new ShadowPass(ShadowPassType.APPLY_SHADOW_MAP, mScene, mCamera, mLight, mShadowRenderTarget);
		mShadowMapMaterial = pass1.getShadowMapMaterial();
		mShadowMapMaterial.setShadowInfluence(mShadowInfluence);
		pass2.setShadowMapMaterial(pass1.getShadowMapMaterial());
		addPass(pass2);
	}
}
