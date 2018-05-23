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
	private static final float REF_FINGER_PTS[][] = new float[][]{{-0.0200068f,  0.0397161f},
																  { 0.0201293f,  0.0293674f},
																  { 0.0299186f,  0.0100685f},
																  { 0.0317366f, -0.0064334f},
																  { 0.0230661f, -0.0267112f}};
	private static final long MAX_BAD_TRACK_FRAMES = 5;
	private HandTracking.HandPose mHandPose;
	private HandTracking mHandTracking;
	private long mBadTrackFramesCount;
	private double mAngle;
	// Matrices
	//private float[] mModelMatTest;

	private float[] mModelMatF;
	private Matrix4 mProjMat;
	private Matrix4  mMVPInvMat;
	//private float[] mViewMatrix = new float[]{1.0f, 0, 0, 0, 0, 1.0f, 0, 0, 0, 0, 1.0f, 0, 0, 0, -4.0f, 1.0f}; // default ViewMatrix from OpenGL Renderer

	// Scene objects
	private DirectionalLight mDirectionalLight;
	private Sphere mSphere;

	//private Object3D mHandObjList[][];
	private Object3D mLeftHandModel;
	private Object3D mRightHandModel;

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

		mModelMatF[12] /= mModelMatF[15];
		mModelMatF[13] /= mModelMatF[15];
		mModelMatF[14] /= mModelMatF[15];
		mModelMatF[15] = 1;
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

		//mModelMatTest = new float[16];
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
		mSphere = new Sphere(0.02f, 24, 24);
		mSphere.setMaterial(material);
		getCurrentScene().addChild(mSphere);
		mSphere.setVisible(true);

		// Load OBJ and MTL files
		LoaderOBJ loaderOBJ = new LoaderOBJ(this, R.raw.lefthand_obj);
		try
		{
			loaderOBJ.parse();
		}
		catch (ParsingException e)
		{
			e.printStackTrace();
		}
		mLeftHandModel = loaderOBJ.getParsedObject();
		getCurrentScene().addChild(mLeftHandModel);
		mLeftHandModel.setVisible(true);

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

//			if (!myhand.isVisible())
//				myhand.setVisible(true);

			// Generate Model Matrix of the hand
			Matrix.setIdentityM(mModelMatF, 0);
			ScreenToWorld(/*mHandPose.start*/new Point(640,360)); // Translation
			Matrix.rotateM(mModelMatF, 0, /*mHandPose.angle*/170, 0, 0, 1); // Rotation
			Matrix.scaleM(mModelMatF, 0, /*mHandPose.scale*/0.3f, /*mHandPose.scale*/0.3f, /*mHandPose.scale*/0.3f); // Scale

			// Generate Test Model Matrix

			float tmp[] = new float[4];
			float tmp1[] = new float[16];
			float transf[] = new float[16];

//			float test[] = new float[16];
//			test[12] = REF_FINGER_PTS[1][0];
//			test[13] = REF_FINGER_PTS[1][1];
//			test[14] = /*0.007f*/0;
//			test[15] = 1;
			//			Matrix.setIdentityM(test, 0);
			//			Matrix.rotateM(mModelMatTest, 0, 10, 0, 0, 1); // Rotation
//			Matrix.multiplyMV(test, 12, mModelMatF, 0, test, 12);
//			System.arraycopy(mModelMatF, 0, test, 0, 12);
//			Matrix.rotateM(test, 0, 20, 0, 0, 1);
			//			Matrix.multiplyMM(test, 0, mModelMatF, 0, mModelMatTest, 0);
//			mSphere.setPosition(test[0] / test[3], test[1] / test[3], test[2] / test[3]);
//			mSphere.setScale(0.25 / test[3]);

//			myhand.setPosition(mModelMatF[12] / mModelMatF[15], mModelMatF[13] / mModelMatF[15], mModelMatF[14] / mModelMatF[15]);
//			myhand.setScale(mHandPose.scale / mModelMatF[15]);
//			myhand.setRotation(0, 0, -1, mHandPose.angle);

			// Palm
			for (int i = 0, j = mLeftHandModel.getNumChildren(); i < j; ++i)
			{
				if (mLeftHandModel.getChildAt(i).getName().equals("ANATOMY---HAND-AND-ARM-BONES.022") || mLeftHandModel.getChildAt(i).getName().equals("ANATOMY---HAND-AND-ARM-BONES.016"))
				{
					// Get current object coordinate
					tmp[0] = REF_FINGER_PTS[0][0];
					tmp[1] = REF_FINGER_PTS[0][1];
					tmp[2] = 0;
					tmp[3] = 1;
					Matrix.multiplyMV(tmp, 0, mModelMatF, 0, tmp, 0);

					// Translate to origin
					Matrix.setIdentityM(transf, 0);
					transf[12] = -tmp[0];
					transf[13] = -tmp[1];
					transf[14] = -tmp[2];
					transf[15] = tmp[3];

					// Transformation on the origin
					Matrix.setIdentityM(tmp1, 0);
					Matrix.setRotateM(tmp1, 0, 40, 0, 0, 1);
					Matrix.multiplyMM(transf, 0, tmp1, 0, transf, 0);

					// Translate back to the original position
					Matrix.setIdentityM(tmp1, 0);
					tmp1[12] = tmp[0];
					tmp1[13] = tmp[1];
					tmp1[14] = tmp[2];
					tmp1[15] = tmp[3];
					Matrix.multiplyMM(transf, 0, tmp1, 0, transf, 0);

					// Apply the parent transformation
					Matrix.multiplyMM(transf, 0, transf, 0, mModelMatF, 0);

					mLeftHandModel.getChildAt(i).getModelMatrix().setAll(transf);

				}
				else
					mLeftHandModel.getChildAt(i).getModelMatrix().setAll(mModelMatF);
			}

		//}
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
