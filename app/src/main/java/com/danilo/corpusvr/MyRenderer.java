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
	private Matrix4 mModelMatrix;
	private Matrix4 mProjectionMat;
	private float[] mProjectionGLInv;
	private float[] mWorldPoint;
	private float[] mModelViewMatrix = new float[]{1.0f, 0, 0, 0, 0, 1.0f, 0, 0, 0, 0, 1.0f, 0, 0, 0, -4.0f, 1.0f}; // default ModelViewMatrix from OpenGL Renderer

	// Scene objects
	private DirectionalLight mDirectionalLight;
	private Sphere mSphere;
	private Object3D myhand;

	// https://stackoverflow.com/questions/7692988/opengl-math-projecting-screen-space-to-world-space-coords
	private void ScreenToWorld(Point screenPoint)
	{
		if (mWorldPoint == null)
			mWorldPoint = new float[4];

		if (mProjectionGLInv == null)
		{
			mProjectionGLInv = new float[16];
			Matrix.multiplyMM(mProjectionGLInv, 0, mProjectionMat.getFloatValues(), 0, mModelViewMatrix, 0);
			Matrix.invertM(mProjectionGLInv, 0, mProjectionGLInv, 0);
		}

		mWorldPoint[0] = (2.0f * ((float) (screenPoint.x) / (float) (mScreenWidth))) - 1.0f;
		mWorldPoint[1] = 1.0f - (2.0f * ((float) (screenPoint.y) / (float) (mScreenHeight)));
		mWorldPoint[2] = /*2.0f * 0.5f*//*Z*//* - 1.0f*/0;
		mWorldPoint[3] = 1.0f;

		Matrix.multiplyMV(mWorldPoint, 0, mProjectionGLInv, 0, mWorldPoint, 0);
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

		mScreenHeight = 720;
		mScreenWidth = 1280;

		if (mProjectionMat != null)
			getCurrentCamera().setProjectionMatrix(mProjectionMat);

		mModelMatrix = new Matrix4();

		mDirectionalLight = new DirectionalLight(4, 4, -4);
		mDirectionalLight.setColor(1.0f, 1.0f, 1.0f);
		mDirectionalLight.setPower(1.25f);
		getCurrentScene().addLight(mDirectionalLight);

		Material material = new Material();
		material.enableLighting(false);
		//material.setDiffuseMethod(new DiffuseMethod.Lambert());
		material.setColor(Color.RED);

		mSphere = new Sphere(0.02f, 24, 24);
		mSphere.setMaterial(material);

		getCurrentScene().addChild(mSphere);

//		mSphere.setPosition(0.155925d, 0.5703558d,0);
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

		//getCurrentScene().addChild(myhand);

		//myhand.setVisible(false);
		//myhand.setScale(14);
		//myhand.setScale(14.0, 14.0, 14.0);
		//myhand.setRotX(-90);

		mBadTrackFramesCount = MAX_BAD_TRACK_FRAMES;
		mAngle = 0;
	}

	@Override
	protected void onRender(long ellapsedRealtime, double deltaTime)
	{
		super.onRender(ellapsedRealtime, deltaTime);

		ScreenToWorld(new Point(1280, 720));

		mSphere.setPosition(mWorldPoint[0] / mWorldPoint[3], mWorldPoint[1] / mWorldPoint[3], mWorldPoint[2] / mWorldPoint[3]);

//		float mPose[] = new float[16];
//
//		mPose[0] = 1.0f;
//		mPose[1] = 0;
//		mPose[2] = 0;
//		mPose[3] = 0;
//		mPose[4] = 0;
//		mPose[5] = 1.0f;
//		mPose[6] = 0;
//		mPose[7] = 0;
//		mPose[8] = 0;
//		mPose[9] = 0;
//		mPose[10] = 1.0f;
//		mPose[11] = 0;
//		mPose[12] = mWorldPoint[0];
//		mPose[13] = mWorldPoint[1];
//		mPose[14] = mWorldPoint[2];
//		mPose[15] = mWorldPoint[3];
//
//		mModelMatrix.setAll(mPose);
//		mSphere.calculateModelMatrix(mModelMatrix);


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
//
//
//			ScreenToWorld(mHandPose.start);
//
//			float mPose[] = new float[16];
//
//			mPose[0] = 1.0f;
//			mPose[1] = 0;
//			mPose[2] = 0;
//			mPose[3] = 0;
//			mPose[4] = 0;
//			mPose[5] = 1.0f;
//			mPose[6] = 0;
//			mPose[7] = 0;
//			mPose[8] = 0;
//			mPose[9] = 0;
//			mPose[10] = 1.0f;
//			mPose[11] = 0;
//			mPose[12] = mWorldPoint[0];
//			mPose[13] = mWorldPoint[1];
//			mPose[14] = mWorldPoint[2];
//			mPose[15] = mWorldPoint[3];
//
//			mModelMatrix.setAll(mPose);
//
//			myhand.calculateModelMatrix(mModelMatrix);

			//myhand.rotateAround(Vector3.Z, (mHandPose.angle * - 1.0d) + 106.0d, false);
			//myhand.setPosition(mPoint.x, mPoint.y, 0);
			//myhand.setScale(mHandPose.scale);

			//mModelMatrix.setAll(mHandPose.mPose);
			//mModelMatrix.scale(0.05d, 0.05d, 0.05d);
			//mModelMatrix.rotate(1, 0, 0, mAngle);

//			if (mAngle < 360)
//				mAngle += 2.2d;
//			else
//				mAngle = 0;

			//  Matrix.scaleM(mHandStatus.mPose, 0, mHandStatus.mPose, 0, 0.05f, 0.05f, 0.05f);
			//  Matrix.rotateM(mHandStatus.mPose, 0, mHandStatus.mPose, 0, mAngle, 1, 0, 0);

			//myhand.calculateModelMatrix(mModelMatrix);
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
