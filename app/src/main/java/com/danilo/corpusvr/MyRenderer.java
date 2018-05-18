package com.danilo.corpusvr;

import android.content.Context;
import android.graphics.Color;
import android.view.MotionEvent;

import org.rajawali3d.Object3D;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.loader.LoaderOBJ;
import org.rajawali3d.loader.ParsingException;
import org.rajawali3d.materials.Material;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.primitives.Sphere;
import org.rajawali3d.renderer.Renderer;

public class MyRenderer extends Renderer implements CameraProjectionListener
{
	private static final String TAG = "MyRenderer";

	private Context mContext;

	// Tracking information
	private static final long MAX_BAD_TRACK_FRAMES = 10;
	private HandTracking.HandPose mHandPose;
	private HandTracking mHandTracking;
	private long mBadTrackFramesCount;
	private double mAngle;

	// Matrices
	private Matrix4 mModelMatrix;
	private Matrix4 mProjectionMat;

	// Scene objects
	private DirectionalLight mDirectionalLight;
	private Sphere mSphere;
	private Object3D myhand;

	MyRenderer(Context context, HandTracking Status)
	{
		super(context);
		mContext = context;
		mHandTracking = Status;
	}

	@Override
	protected void initScene()
	{
		//  http://www.opengl-tutorial.org/beginners-tutorials/tutorial-3-matrices/#an-introduction-to-matrices
		//  getCurrentCamera().getModelMatrix().identity();
		//  getCurrentCamera().getViewMatrix().identity();
		if (mProjectionMat != null)
			getCurrentCamera().setProjectionMatrix(mProjectionMat);

		mModelMatrix = new Matrix4();

		mDirectionalLight = new DirectionalLight(4, 4, -4);
//		mDirectionalLight = new DirectionalLight(1.0f, 0.2f, -1.0f);
		mDirectionalLight.setColor(1.0f, 1.0f, 1.0f);
		mDirectionalLight.setPower(1.25f);
		getCurrentScene().addLight(mDirectionalLight);

		Material material = new Material();
		material.enableLighting(false);
		//material.setDiffuseMethod(new DiffuseMethod.Lambert());
		material.setColor(Color.RED);

//		Texture earthTexture = new Texture("Earth", R.drawable.earthtruecolor_nasa_big);
//		try
//		{
//			material.addTexture(earthTexture);
//
//		}
//		catch (ATexture.TextureException error)
//		{
//			Log.d(TAG, "TEXTURE ERROR");
//		}

		mSphere = new Sphere(0.02f, 24, 24);
		//mSphere.setColor(0xFF00FF);
		mSphere.setMaterial(material);

		getCurrentScene().addChild(mSphere);

		mSphere.setPosition(0.155925d, 0.5703558d,0);

//		mSphere.setVisible(false);

		LoaderOBJ loaderOBJ = new LoaderOBJ(this, R.raw.myhand_obj);
		try
		{
			loaderOBJ.parse();
		}
		catch (ParsingException e)
		{
			e.printStackTrace();
		}
		myhand = loaderOBJ.getParsedObject();
//		myhand.setMaterial(material); Its possible to remove the loaded material and change for a new one: https://github.com/Rajawali/Rajawali/issues/2015

		getCurrentScene().addChild(myhand);

		myhand.setScale(14.0, 14.0, 14.0);
		myhand.setRotX(-90);

		mBadTrackFramesCount = MAX_BAD_TRACK_FRAMES;
		mAngle = 0;
	}

	@Override
	protected void onRender(long ellapsedRealtime, double deltaTime)
	{
		super.onRender(ellapsedRealtime, deltaTime);

//		mHandPose = mHandTracking.getObjStatus();
//		if (mHandPose.render || mBadTrackFramesCount <= MAX_BAD_TRACK_FRAMES)
//		{
//			if (mHandPose.render)
//				mBadTrackFramesCount = 0;
//			else
//				++mBadTrackFramesCount;
//
//			if (!mSphere.isVisible())
//				mSphere.setVisible(true);
//
//			//mModelMatrix.setAll(mHandPose.mPose);
//			mModelMatrix.scale(0.05d, 0.05d, 0.05d);
//			mModelMatrix.rotate(1, 0, 0, mAngle);
//
//			if (mAngle < 360)
//				mAngle += 2.2d;
//			else
//				mAngle = 0;
//
//			//  Matrix.scaleM(mHandStatus.mPose, 0, mHandStatus.mPose, 0, 0.05f, 0.05f, 0.05f);
//			//  Matrix.rotateM(mHandStatus.mPose, 0, mHandStatus.mPose, 0, mAngle, 1, 0, 0);
//
//			mSphere.calculateModelMatrix(mModelMatrix);
//		}
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

	@Override
	public void onProjectionChanged(float[] projectionMat)
	{
		if (mProjectionMat == null)
			mProjectionMat = new Matrix4(projectionMat);
		else
			mProjectionMat.setAll(projectionMat);

		if (mSceneInitialized)
			getCurrentCamera().setProjectionMatrix(mProjectionMat);
	}
}
