package com.danilo.corpusvr;

import android.content.Context;
import android.graphics.Color;
import android.view.MotionEvent;

import com.example.rajawali.Object3D;
import com.example.rajawali.lights.DirectionalLight;
import com.example.rajawali.loader.LoaderOBJ;
import com.example.rajawali.loader.ParsingException;
import com.example.rajawali.materials.Material;
import com.example.rajawali.materials.methods.DiffuseMethod;
import com.example.rajawali.math.Matrix;
import com.example.rajawali.math.Matrix4;
import com.example.rajawali.primitives.Sphere;
import com.example.rajawali.renderer.Renderer;

import org.opencv.core.Point;

public class MyRenderer extends Renderer implements CameraProjectionListener
{
	private static final String TAG = "MyRenderer";

	private Context mContext;

	private int mScreenWidth = -1;
	private int mScreenHeight = -1;

	// Tracking information
//	private static final double REF_FINGER_PTS[][] = new double[][]{{-0.0200068,  0.0397161},
//																    { 0.0201293,  0.0293674},
//																    { 0.0299186,  0.0100685},
//																    { 0.0317366, -0.0064334},
//																    { 0.0230661, -0.0267112}};

	private int mBoneFingerIndex[];
	private HandTracking.HandPose mHandPose;
	private HandTracking mHandTracking;
	private long mBadTrackFramesCount;
	private static final long MAX_BAD_TRACK_FRAMES = 8;
	private static final double REF_FINGER_PTS[][] = new double[][]{{-0.042,  0.012},
																	{-0.025, -0.025},
																	{-0.004, -0.032},
																	{ 0.010, -0.030},
																	{ 0.030, -0.019}};
	private static final String FINGER_BONE_NAMES[][] = new String[][]{{"ANATOMY---HAND-AND-ARM-BONES.022", "ANATOMY---HAND-AND-ARM-BONES.016", ""                                },
																	   {"ANATOMY---HAND-AND-ARM-BONES.018", "ANATOMY---HAND-AND-ARM-BONES.013", "ANATOMY---HAND-AND-ARM-BONES.015"},
																	   {"ANATOMY---HAND-AND-ARM-BONES.019", "ANATOMY---HAND-AND-ARM-BONES.014", "ANATOMY---HAND-AND-ARM-BONES"    },
																	   {"ANATOMY---HAND-AND-ARM-BONES.020", "ANATOMY---HAND-AND-ARM-BONES.017", "ANATOMY---HAND-AND-ARM-BONES.026"},
																	   {"ANATOMY---HAND-AND-ARM-BONES.009", "ANATOMY---HAND-AND-ARM-BONES.011", "ANATOMY---HAND-AND-ARM-BONES.005"}};

	// Matrices
	private double[] mModelMatF;
	private Matrix4 mProjMat;
	private Matrix4 mMVPInvMat;
	private Matrix4 mTempTransf;
	private Matrix4 mTempViewMat;
	private Matrix4 mTempModelMat;
	private Matrix4 mPalmModeViewMat;
	private Matrix4 mFingerModelViewMat[];

	// Scene objects
	private DirectionalLight mDirectionalLight;
	private Sphere mSphere;
	private Object3D mLeftHandModel;
	private Object3D mRightHandModel;

	// https://stackoverflow.com/questions/7692988/opengl-math-projecting-screen-space-to-world-space-coords
	// Converts the coordinates from OpenCV space to OpenGL's (Used for 2D perspective)
	private void screenToWorld(Point point)
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

		Matrix.multiplyMV(mModelMatF, 12, mMVPInvMat.getDoubleValues(), 0, mModelMatF, 12);

		mModelMatF[12] /= mModelMatF[15];
		mModelMatF[13] /= mModelMatF[15];
		mModelMatF[14] /= mModelMatF[15];
		mModelMatF[15] = 1;
	}

	private void calculateFingerTransf(Matrix4 palmPose)
	{
		for (int i = 0; i < 5; ++i)
		{
			mTempModelMat.identity();

			// Translate to the origin
			mTempTransf.identity().setTranslation(- REF_FINGER_PTS[i][0], - REF_FINGER_PTS[i][1], 0);
			mTempModelMat.leftMultiply(mTempTransf);

			// Rotate fingers on Z axis
			mTempTransf.identity().setRotate(0,0,1, mHandPose.fingerAngles[i]);
			mTempModelMat.leftMultiply(mTempTransf);

			// Translate back
			mTempTransf.identity().setTranslation(REF_FINGER_PTS[i][0], REF_FINGER_PTS[i][1], 0);
			mTempModelMat.leftMultiply(mTempTransf);

			// Apply palm pose transformations
			mTempViewMat.identity().inverse();
			mTempViewMat.leftMultiply(palmPose);

			// Generate the finger ModelView Matrix
			mFingerModelViewMat[i].setAll(mTempViewMat).multiply(mTempModelMat);
		}
	}

	private int getFingerIndex(String modelName)
	{
		int i, j;
		for (i = 0; i < FINGER_BONE_NAMES.length; ++i)
		{
			for (j = 0; j < FINGER_BONE_NAMES[0].length; ++j)
			{
				if (modelName.equals(FINGER_BONE_NAMES[i][j]))
				{
					return i;
				}
			}
		}
		return -1;
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
		//getCurrentCamera().getModelMatrix().identity();
		//getCurrentCamera().getViewMatrix().identity();

		if (mProjMat != null)
			getCurrentCamera().setProjectionMatrix(mProjMat);

		mModelMatF = new double[16];

		mTempTransf = new Matrix4();
		mFingerModelViewMat = new Matrix4[5];
		mFingerModelViewMat[0] = new Matrix4();
		mFingerModelViewMat[1] = new Matrix4();
		mFingerModelViewMat[2] = new Matrix4();
		mFingerModelViewMat[3] = new Matrix4();
		mFingerModelViewMat[4] = new Matrix4();

		mTempViewMat = new Matrix4();
		mTempModelMat = new Matrix4();
		mPalmModeViewMat = new Matrix4();

		// Scene basic light
		mDirectionalLight = new DirectionalLight(4, 4, 4);
		mDirectionalLight.setColor(1.0f, 1.0f, 1.0f);
		mDirectionalLight.setPower(1.25f);
		getCurrentScene().addLight(mDirectionalLight);

		// Debug object basic material (disable light so it looks 2D)
		Material material = new Material();
		material.enableLighting(true);
		material.setDiffuseMethod(new DiffuseMethod.Lambert());
		material.setColor(Color.RED);

		// Debug object
		mSphere = new Sphere(0.002f, 24, 24);
		mSphere.setMaterial(material);
		getCurrentScene().addChild(mSphere);
		mSphere.setUseCustomModelView(true);
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

		int i, j = mLeftHandModel.getNumChildren();
		mBoneFingerIndex = new int[j];
		for (i = 0; i < j; ++i)
		{
			mLeftHandModel.getChildAt(i).setUseCustomModelView(true);
			mBoneFingerIndex[i] = getFingerIndex(mLeftHandModel.getChildAt(i).getName());
		}
		mLeftHandModel.setVisible(false);

		//	mLeftHandModel.setMaterial(material); Its possible to remove the loaded material and change for a new one: https://github.com/Rajawali/Rajawali/issues/2015

		mBadTrackFramesCount = MAX_BAD_TRACK_FRAMES;
	}

	@Override
	protected void onRender(long ellapsedRealtime, double deltaTime)
	{
		super.onRender(ellapsedRealtime, deltaTime);

		mHandPose = mHandTracking.getObjStatus();
		if (mHandPose.render || mBadTrackFramesCount < MAX_BAD_TRACK_FRAMES)
		{
			if (mHandPose.render)
				mBadTrackFramesCount = 0;
			else
				++mBadTrackFramesCount;

			if (!mLeftHandModel.isVisible())
				mLeftHandModel.setVisible(true);

			mPalmModeViewMat.setAll(mHandPose.pose);

			calculateFingerTransf(mPalmModeViewMat);

			for (int i = 0, j = mLeftHandModel.getNumChildren(), fingerIndex; i < j; ++i)
			{
				fingerIndex = mBoneFingerIndex[i];
				if (fingerIndex != -1) // Finger bone
				{
					mLeftHandModel.getChildAt(i).getModelViewMatrix().setAll(mFingerModelViewMat[fingerIndex]);
				}
				else // Palm bone
				{
					mLeftHandModel.getChildAt(i).getModelViewMatrix().setAll(mPalmModeViewMat);
				}
			}
		}
		else
		{
			if (mLeftHandModel.isVisible())
				mLeftHandModel.setVisible(false);
		}
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
