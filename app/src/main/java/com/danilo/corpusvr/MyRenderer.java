package com.danilo.corpusvr;

import android.content.Context;
import android.graphics.Color;
import android.opengl.Matrix;
import android.view.MotionEvent;

import org.opencv.core.Point;
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

	private int mScreenWidth = -1;
	private int mScreenHeight = -1;

	// Tracking information
	private static final long MAX_BAD_TRACK_FRAMES = 10;
	private HandTracking.HandPose mHandPose;
	private HandTracking mHandTracking;
	private long mBadTrackFramesCount;
	private double mAngle;

	// Matrices
	private float[] mModelMatF;
	private Matrix4 mProjMat;
	private Matrix4  mMVPInvMat;
	//private float[] mViewMatrix = new float[]{1.0f, 0, 0, 0, 0, 1.0f, 0, 0, 0, 0, 1.0f, 0, 0, 0, -4.0f, 1.0f}; // default ViewMatrix from OpenGL Renderer

	// Scene objects
	private DirectionalLight mDirectionalLight;
	private Sphere mSphere;
	private Object3D myhand;

	// https://stackoverflow.com/questions/7692988/opengl-math-projecting-screen-space-to-world-space-coords
	// Converts the coordinates from OpenCV space to OpenGL's
	private void ScreenToWorld(Point point)
	{
		if (mScreenWidth == -1 || mScreenHeight == -1)
			return;

		if (mMVPInvMat == null)
		{
			mMVPInvMat = new Matrix4(mProjMat);
			mMVPInvMat.multiply(getCurrentCamera().getViewMatrix()).inverse();

//			mMVPInvMat = new float[16];
//			Matrix.multiplyMM(mMVPInvMat, 0, mProjMat.getFloatValues(), 0, getCurrentCamera().getViewMatrix().getFloatValues(), 0);
//			Matrix.invertM(mMVPInvMat, 0, mMVPInvMat, 0);
		}

		mModelMatF[12] = (2.0f * ((float) (point.x) / (float) (mScreenWidth))) - 1.0f;
		mModelMatF[13] = 1.0f - (2.0f * ((float) (point.y) / (float) (mScreenHeight)));
		mModelMatF[14] = /*2.0f * 0.5f*//*Z*//* - 1.0f*/0;
		mModelMatF[15] = 1.0f;

		Matrix.multiplyMV(mModelMatF, 12, mMVPInvMat.getFloatValues(), 0, mModelMatF, 12);
	}

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

		if (mProjMat != null)
			getCurrentCamera().setProjectionMatrix(mProjMat);

		mModelMatF = new float[16];

		// Scene basic light
		mDirectionalLight = new DirectionalLight(4, 4, -4);
		mDirectionalLight.setColor(1.0f, 1.0f, 1.0f);
		mDirectionalLight.setPower(1.25f);
		getCurrentScene().addLight(mDirectionalLight);

		// Debug object basic material (disable light so it looks 2D)
		Material material = new Material();
		material.enableLighting(false);
		//material.setDiffuseMethod(new DiffuseMethod.Lambert());
		material.setColor(Color.RED);

		// Debug object
		//mSphere = new Sphere(0.02f, 24, 24);
		//mSphere.setMaterial(material);
		//getCurrentScene().addChild(mSphere);
		//mSphere.setVisible(true);

		// Load OBJ and MTL files
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
		getCurrentScene().addChild(myhand);
		myhand.setVisible(true);

		//	myhand.setMaterial(material); Its possible to remove the loaded material and change for a new one: https://github.com/Rajawali/Rajawali/issues/2015

		mBadTrackFramesCount = MAX_BAD_TRACK_FRAMES;
		mAngle = 0;
	}

	@Override
	protected void onRender(long ellapsedRealtime, double deltaTime)
	{
		super.onRender(ellapsedRealtime, deltaTime);

//		mHandPose = mHandTracking.getObjStatus();
//		if (mHandPose.render || mBadTrackFramesCount < MAX_BAD_TRACK_FRAMES)
//		{
//			if (mHandPose.render)
//				mBadTrackFramesCount = 0;
//			else
//				++mBadTrackFramesCount;
//
//			if (!myhand.isVisible())
//				myhand.setVisible(true);

			// Generate Model Matrix of the hand
			Matrix.setIdentityM(mModelMatF, 0);
			//ScreenToWorld(mHandPose.start); // Translation
			ScreenToWorld(new Point(1280, 720)); // Translation
			//Matrix.rotateM(mModelMatF, 0, mHandPose.angle, 1, 0, 0); // Rotation
			//Matrix.scaleM(mModelMatF, 0, 14, 14, 14); // Scale

		myhand.setPosition(mModelMatF[12] / mModelMatF[15], mModelMatF[13] / mModelMatF[15], mModelMatF[14] / mModelMatF[15]);
		myhand.setScale(1 / mModelMatF[15]);
		//myhand.getModelMatrix().setAll(mModelMatF);
		//myhand.setPosition(0.9999999893398694,-1.00000080861669,-1.7418392417312134E-6);

		//myhand.getChildAt(0).getmode
//		for (int i = 0, j = myhand.getNumChildren(); i < j; ++i)
//		{
//			myhand.getChildAt(i).getModelMatrix().setAll(mModelMatF);
//		}
//		myhand.getModelMatrix().setAll(mModelMatF);

//		}
//		else if (myhand.isVisible())
//			myhand.setVisible(false);

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
	public void onProjectionChanged(int width, int height, float[] projectionMat)
	{
		mScreenWidth = width;
		mScreenHeight = height;

		mMVPInvMat = null;

		if (mProjMat == null)
			mProjMat = new Matrix4(projectionMat);
		else
			mProjMat.setAll(projectionMat);

		if (mSceneInitialized)
			getCurrentCamera().setProjectionMatrix(mProjMat);
	}
}
