package com.danilo.corpusvr;

import android.content.Context;
import android.opengl.Matrix;
import android.util.Log;
import android.view.MotionEvent;

import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.primitives.Sphere;
import org.rajawali3d.renderer.Renderer;

public class MyRenderer extends Renderer
{
	private static final String TAG = "MyRenderer";
	private static final float NEAR = 0.1f;
	private static final float FAR  = 100f;
	private MyJavaCameraView mJavaCameraView;
	private Matrix4 mPose = null;
	private Matrix4 mProjectionGL = null;
	private Context mContext;
	private HandTracking mHandTracking;
	private HandTracking.HandStatus mHandStatus = null;
	private DirectionalLight mDirectionalLight = null;
	private Sphere mSphere = null;

	MyRenderer(Context context, MyJavaCameraView JavaCameraView, HandTracking Status)
	{
		super(context);
		mContext = context;
		mJavaCameraView = JavaCameraView;
		mHandTracking = Status;
	}

	public float getAspectRatio()
	{
		return (float) getDefaultViewportWidth() / (float) getDefaultViewportHeight();
	}

	public Matrix4 getProjectionGL()
	{
		if (mProjectionGL == null)
		{
			float[] tmp = new float[16];
			float fovX = mJavaCameraView.getFOVX();
			float aspectR = getAspectRatio();
			float right = (float) Math.tan(0.5f * fovX * Math.PI / 180.0f) * NEAR;
			float top = right / aspectR;
			Matrix.frustumM(tmp, 0, -right, right, -top, top, NEAR, FAR);
			mProjectionGL = new Matrix4(tmp);
		}
		return mProjectionGL;
	}

	@Override
	protected void initScene()
	{
		setFrameRate(30);

		getCurrentCamera().setProjectionMatrix(getProjectionGL());

		mPose = new Matrix4();

		mDirectionalLight = new DirectionalLight(1.0f, 0.2f, -1.0f);
		mDirectionalLight.setColor(1.0f, 1.0f, 1.0f);
		mDirectionalLight.setPower(2.0f);
		getCurrentScene().addLight(mDirectionalLight);

		Material material = new Material();
		material.enableLighting(true);
		material.setDiffuseMethod(new DiffuseMethod.Lambert());
		material.setColor(0);

		Texture earthTexture = new Texture("Earth", R.drawable.earthtruecolor_nasa_big);
		try
		{
			material.addTexture(earthTexture);

		}
		catch (ATexture.TextureException error)
		{
			Log.d(TAG, "TEXTURE ERROR");
		}

		mSphere = new Sphere(1, 24, 24);
		mSphere.setMaterial(material);

		getCurrentScene().addChild(mSphere);

		mSphere.setVisible(false);
	}

	@Override
	protected void onRender(long ellapsedRealtime, double deltaTime)
	{
		super.onRender(ellapsedRealtime, deltaTime);

		mHandStatus = mHandTracking.getObjStatus();
		if (mHandStatus.mRender)
		{
			if (!mSphere.isVisible())
				mSphere.setVisible(true);

			mPose.setAll(mHandStatus.mPose);
			mSphere.calculateModelMatrix(mPose);
		}
//		else if (mSphere.isVisible())
//			mSphere.setVisible(false);

	}

	@Override
	public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset)
	{

	}

	@Override
	public void onTouchEvent(MotionEvent event)
	{

	}
}
