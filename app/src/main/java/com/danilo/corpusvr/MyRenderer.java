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

	private static final double REF_FINGER_PTS[][] = new double[][]{{-0.042,  0.012},
																	{-0.025, -0.025},
																	{-0.004, -0.032},
																	{ 0.010, -0.030},
																	{ 0.030, -0.019}};
	private static final long MAX_BAD_TRACK_FRAMES = 8;
	private HandTracking.HandPose mHandPose;
	private HandTracking mHandTracking;
	private long mBadTrackFramesCount;
	private double mAngle;
	// Matrices
	//private float[] mModelMatTest;

	private double[] mModelMatF;
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

		Matrix.multiplyMV(mModelMatF, 12, mMVPInvMat.getDoubleValues(), 0, mModelMatF, 12);

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
		//getCurrentCamera().getModelMatrix().identity();
		//getCurrentCamera().getViewMatrix().identity();

		if (mProjMat != null)
			getCurrentCamera().setProjectionMatrix(mProjMat);

		//mModelMatTest = new float[16];
		mModelMatF = new double[16];

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
		for (int i = 0, j = mLeftHandModel.getNumChildren(); i < j; ++i)
			mLeftHandModel.getChildAt(i).setUseCustomModelView(true);
		mLeftHandModel.setVisible(false);

		//	mLeftHandModel.setMaterial(material); Its possible to remove the loaded material and change for a new one: https://github.com/Rajawali/Rajawali/issues/2015

		mBadTrackFramesCount = MAX_BAD_TRACK_FRAMES;
		mAngle = 0;
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

			// Generate Test Model Matrix
			double tmp[] = new double[4];
			double tmp1[] = new double[16];
			double transf[] = new double[16];

			Matrix4 mvMatrix = new Matrix4(mHandPose.pose);
			mvMatrix.scale(25);
			mvMatrix.rotate(1, 0, 0, -20);

			tmp[0] = REF_FINGER_PTS[3][0];
			tmp[1] = REF_FINGER_PTS[3][1];
			tmp[2] = 0;
			tmp[3] = 1;

			Matrix4 model = new Matrix4();

			// Translate to the origin
			Matrix.setIdentityM(tmp1, 0);
			tmp1[12] = - tmp[0];
			tmp1[13] = - tmp[1];
			tmp1[14] = - tmp[2];
			tmp1[15] = tmp[3];
			model.leftMultiply(new Matrix4(tmp1));

			// Rotate
			Matrix.setIdentityM(tmp1, 0);
			Matrix.setRotateM(tmp1, 0, mHandPose.fingerAngles[3], 0, 0, 1);
			model.leftMultiply(new Matrix4(tmp1));

			// Translate back
			Matrix.setIdentityM(tmp1, 0);
			tmp1[12] = tmp[0];
			tmp1[13] = tmp[1];
			tmp1[14] = tmp[2];
			tmp1[15] = tmp[3];
			model.leftMultiply(new Matrix4(tmp1));

			Matrix4 view = new Matrix4();
			view.inverse();
			view.leftMultiply(mvMatrix);

			Matrix4 newMV = new Matrix4(view);
			newMV.multiply(model);

			for (int i = 0, j = mLeftHandModel.getNumChildren(); i < j; ++i)
			{
				if (mLeftHandModel.getChildAt(i).getName().equals("ANATOMY---HAND-AND-ARM-BONES.026") || mLeftHandModel.getChildAt(i).getName().equals("ANATOMY---HAND-AND-ARM-BONES.017") || mLeftHandModel.getChildAt(i).getName().equals("ANATOMY---HAND-AND-ARM-BONES.020"))
				{
					// Get current object coordinate
//					tmp[0] = 0.0001;
//					tmp[1] = 0;
//					tmp[2] = 10;
//					tmp[3] = 1;
//					Matrix.multiplyMV(tmp, 0, tmp2.getDoubleValues(), 0, tmp, 0);

//					Matrix.setIdentityM(tmp1, 0);
					//0.5409334617229711, 1.1049283846825118, -24.130624956012504, 1.0
//					System.arraycopy(tmp2.getDoubleValues(), 0, tmp1, 0, 16);
//					tmp1[12] = tmp[0];
//					tmp1[13] = tmp[1];
//					tmp1[14] = tmp[2];
//					tmp1[15] = tmp[3];


//					mSphere.getModelViewMatrix().setAll(tmp1);

//					Log.d(TAG, "mSphere: (" + tmp[0] + ", " + tmp[1] + ", " + tmp[2] + ", " + tmp[3] + ")\n");

					// Translate to origin
//					Matrix.setIdentityM(transf, 0);
//					transf[12] = -tmp[0];
//					transf[13] = -tmp[1];
//					transf[14] = -tmp[2];
//					transf[15] = tmp[3];

					// Transformation on the origin
//					Matrix.setIdentityM(tmp1, 0);
//					Matrix.setRotateM(tmp1, 0, 40, 0, 0, 1);
//					Matrix.multiplyMM(transf, 0, tmp1, 0, transf, 0);

					// Translate back to the original position
//					Matrix.setIdentityM(tmp1, 0);
//					tmp1[12] = tmp[0];
//					tmp1[13] = tmp[1];
//					tmp1[14] = tmp[2];
//					tmp1[15] = tmp[3];
//					Matrix.multiplyMM(transf, 0, tmp1, 0, transf, 0);

					// Apply the parent transformation
//					Matrix.multiplyMM(transf, 0, transf, 0, mModelMatF, 0);

					mLeftHandModel.getChildAt(i).getModelViewMatrix().setAll(newMV);
				}
				else
					mLeftHandModel.getChildAt(i).getModelViewMatrix().setAll(mvMatrix);
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
