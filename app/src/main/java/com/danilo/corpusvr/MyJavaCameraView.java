package com.danilo.corpusvr;

import android.content.Context;
import android.opengl.Matrix;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Danilo on 3/6/2018.
 */

public class MyJavaCameraView extends JavaCameraView implements CameraBridgeViewBase.CvCameraViewListener2
{
	private static final String TAG = "MyJavaCameraView";

	// Colors
	private static final Scalar COLOR_RED = new Scalar(255, 0, 0);
	private static final Scalar COLOR_GREEN = new Scalar(0, 255, 0);
	private static final Scalar COLOR_BLUE = new Scalar(0, 0, 255);
	private static final Scalar COLOR_YELLOW = new Scalar(255, 255, 0);

	// Image filtering parameters
	private static final int BLUR_SIZE = 5;
	private static final int ELEMENT_SIZE = 4;
	private static final double PI = 3.1415926535897932384626433832795d;

	// Hand defect thresholds
	private static final float MIN_FINGER_LENGTH = 60.0f; // min finger defect length
	private static final float MAX_FINGER_LENGTH = 500.0f; // max finger defect length
	private static final float MIN_INNER_ANGLE = 15.0f;
	private static final float MAX_INNER_ANGLE = 115.0f;

	// Parameters to create the camera perspective matrix
	private static final float NEAR = 0.1f;
	private static final float FAR = 100f;
	private CameraProjectionListener mProjectionListener;
	private HandTracking mHandTracking;
	// Camera parameters
	private int mScreenWidth = -1;
	private int mScreenHeight = -1;
	private float mFOVX = 0;
	private float mFOVY = 0;
	// Camera matrices
	private MatOfDouble mProjectionCV;
	private float[] mProjectionGL;
	private float[] mProjectionGLInv;
	private float[] mWorldPoint;
	private float[] mModelViewMatrix = new float[]{1.0f, 0, 0, 0, 0, 1.0f, 0, 0, 0, 0, 1.0f, 0, 0, 0, -4.0f, 1.0f}; // default ModelViewMatrix from OpenGL Renderer

	// Processed images
	private Mat mRGBA;
	private Mat mHSV;
	private Mat mBinMat;

	// Color filter
	//	private Mat mMask = null;
	//	private Mat mMask1 = null;
	//	private Mat mMask2 = null;
	private Scalar mMinHSV1;
	private Scalar mMaxHSV1;
	//	private Scalar mMinHSV2;
	//	private Scalar mMaxHSV2;

	// solvePNP parameters
	//  private MatOfPoint mIntSceneCorners;
	//  private Mat mSceneCorners;
	//  private MatOfPoint2f mSceneCorners2D;
	//  private MatOfPoint3f mReferenceCorners3D;
	//  private MatOfDouble mDistCoeffs; // Distortion
	//  private MatOfDouble mRVec;
	//  private MatOfDouble mTVec;
	//  private MatOfDouble mRotation;

	// Detected object information
	private List<MatOfPoint> mListOfContours;
	private int mIndex;
	private int mLargestContour;
	private Mat mHierarchy;
	private MatOfInt mHull;
	private MatOfInt4 mDefects;
	private HandTracking.HandDefect mHandDefect;

	public MyJavaCameraView(Context context, int cameraId, HandTracking handTracking, CameraProjectionListener projectionListener)
	{
		super(context, cameraId);
		setCvCameraViewListener(this);
		enableFpsMeter();
		mHandTracking = handTracking;
		mProjectionListener = projectionListener;
	}

	private double innerAngle(Point a, Point b, Point c)
	{
		double CAx = c.x - a.x;
		double CAy = c.y - a.y;
		double CBx = c.x - b.x;
		double CBy = c.y - b.y;

		// https://www.mathsisfun.com/algebra/trig-cosine-law.html (The Law of Cosines)
		double A = Math.acos((CBx * CAx + CBy * CAy) / (Math.sqrt(CBx * CBx + CBy * CBy) * Math.sqrt(CAx * CAx + CAy * CAy)));    // (a² + b² − c²) / 2
		return (A * 180) / PI;
	}

	// https://stackoverflow.com/questions/7692988/opengl-math-projecting-screen-space-to-world-space-coords
	private float[] ScreenToWorld(Point screenPoint)
	{
		if (mWorldPoint == null) mWorldPoint = new float[4];

		if (mProjectionGLInv == null)
		{
			mProjectionGLInv = new float[16];
			Matrix.multiplyMM(mProjectionGLInv, 0, mProjectionGL, 0, mModelViewMatrix, 0);
			Matrix.invertM(mProjectionGLInv, 0, mProjectionGLInv, 0);
		}

		mWorldPoint[0] = (2.0f * ((float) (screenPoint.x) / (float) (mScreenWidth))) - 1.0f;
		mWorldPoint[1] = 1.0f - (2.0f * ((float) (screenPoint.y) / (float) (mScreenHeight)));
		mWorldPoint[2] = 2.0f * 0.5f/*Z*/ - 1.0f;
		mWorldPoint[3] = 1.0f;

		Matrix.multiplyMV(mWorldPoint, 0, mProjectionGLInv, 0, mWorldPoint, 0);

		return mWorldPoint;
	}

	public MatOfDouble getIntrinsicParam()
	{
		if (mProjectionCV == null)
		{
			mProjectionCV = new MatOfDouble();
			mProjectionCV.create(3, 3, CvType.CV_64FC1);

			final float fovAspectRatio = mFOVX / mFOVY;
			double diagonalPx = Math.sqrt((Math.pow(mScreenWidth, 2.0) + Math.pow(mScreenWidth / fovAspectRatio, 2.0)));
			double diagonalFOV = Math.sqrt((Math.pow(mFOVX, 2.0) + Math.pow(mFOVY, 2.0)));
			double focalLengthPx = diagonalPx / (2.0 * Math.tan(0.5 * diagonalFOV * Math.PI / 180f));

			// https://www.mathworks.com/help/vision/ug/camera-calibration.html
			// Intrinsic Parameters
			mProjectionCV.put(0, 0, focalLengthPx);
			mProjectionCV.put(0, 1, 0.0);
			mProjectionCV.put(0, 2, 0.5 * mScreenWidth);
			mProjectionCV.put(1, 0, 0.0);
			mProjectionCV.put(1, 1, focalLengthPx);
			mProjectionCV.put(1, 2, 0.5 * mScreenHeight);
			mProjectionCV.put(2, 0, 0.0); // Optical center x
			mProjectionCV.put(2, 1, 0.0); // Optical center y
			mProjectionCV.put(2, 2, 1.0);
		}
		return mProjectionCV;
	}

	public float[] getProjectionMat()
	{
		if (mProjectionGL == null)
		{
			mProjectionGL = new float[16];

			float aspectR = (float) mScreenWidth / (float) mScreenHeight;
			float right = (float) Math.tan(0.5f * mFOVX * Math.PI / 180.0f) * NEAR;
			float top = right / aspectR;
			Matrix.frustumM(mProjectionGL, 0, -right, right, -top, top, NEAR, FAR);
		}
		return mProjectionGL;
	}

	@Override
	public void onCameraViewStarted(int width, int height)
	{
		mScreenWidth = width;
		mScreenHeight = height;
		mHSV = new Mat(height, width, CvType.CV_8UC3);
		mBinMat = new Mat(height, width, CvType.CV_8UC1);
		mFOVX = mCamera.getParameters().getHorizontalViewAngle();
		mFOVY = mCamera.getParameters().getVerticalViewAngle();
		//  mMask  = new Mat(height, width, CvType.CV_8UC4);
		//  mMask1 = new Mat(height, width, CvType.CV_8UC4);
		//  mMask2 = new Mat(height, width, CvType.CV_8UC4);
		mHierarchy = new Mat();
		//  Detection For Red Color
		//  mMinHSV1 = new Scalar(0, 70, 50);
		//  mMaxHSV1 = new Scalar(10, 255, 255);
		//  mMinHSV2 = new Scalar(170, 70, 50);
		//  mMaxHSV2 = new Scalar(180, 255, 255);
		mMinHSV1 = new Scalar(0, 30, 60);
		mMaxHSV1 = new Scalar(20, 150, 255);
		//  mIntSceneCorners = new MatOfPoint();
		//  mSceneCorners = new Mat(4, 1, CvType.CV_32FC2);
		mListOfContours = new ArrayList<>();
		mHull = new MatOfInt();
		mDefects = new MatOfInt4();
//		mFitLineMat = new Mat(4, 1, CvType.CV_32FC1);
//		mFitLine = new float[4];
//		mObjBBCenter = new Point();
//		mStartPt = new Point();
//		mEndPt = new Point();
//		mFarthestPt = new Point();
//		mStartFitLine = new Point();
//		mEndFitLine = new Point();
		//  mSceneCorners2D = new MatOfPoint2f();
		//  mReferenceCorners3D = new MatOfPoint3f();
		//  mDistCoeffs = new MatOfDouble(0.0, 0.0, 0.0, 0.0); // Assume no distortion
		//  mRVec = new MatOfDouble();
		//  mTVec = new MatOfDouble();
		//  mRotation = new MatOfDouble();
		//  mReferenceCorners3D.fromArray(
		//  		new Point3(-5, -5, 0.0),
		//  		new Point3( 5, -5, 0.0),
		//  		new Point3( 5,  5, 0.0),
		//  		new Point3(-5,  5, 0.0));

		//  Size elementSize = new Size(2 * ELEMENT_SIZE + 1, 2 * ELEMENT_SIZE + 1);
		//  Point elementPoint = new Point(ELEMENT_SIZE, ELEMENT_SIZE);
		//  mElementMat = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, elementSize, elementPoint);
		mProjectionGL = null;
		mProjectionGLInv = null;
		mProjectionCV = null;
		mProjectionListener.onProjectionChanged(getProjectionMat());
		getIntrinsicParam();
	}

	@Override
	public void onCameraViewStopped()
	{
		mHSV.release();
		mBinMat.release();
		//  mMask.release();
		//  mMask1.release();
		//  mMask2.release();
		mHierarchy.release();
		//  mSceneCorners.release();
		mListOfContours.clear();
		mHull.release();
		mDefects.release();
		mProjectionCV.release();
	}

	@Override
	public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame)
	{
		mRGBA = inputFrame.rgba();

		Imgproc.cvtColor(mRGBA, mHSV, Imgproc.COLOR_RGB2HSV);
		Core.inRange(mHSV, mMinHSV1, mMaxHSV1, mBinMat);
		Imgproc.medianBlur(mBinMat, mBinMat, BLUR_SIZE);

		//Imgproc.dilate(mHSV, mHSV, mElementMat);
		//Core.inRange(mHSV, mMinHSV1, mMaxHSV1, mMask1);
		//Core.inRange(mHSV, mMinHSV2, mMaxHSV2, mMask2);
		//Core.add(mMask1, mMask2, mMask);

		Imgproc.findContours(mBinMat, mListOfContours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
		if (!mListOfContours.isEmpty())
		{
			mLargestContour = 0;
			for (mIndex = 1; mIndex < mListOfContours.size(); ++mIndex)
			{
				if (Imgproc.contourArea(mListOfContours.get(mIndex)) > Imgproc.contourArea(mListOfContours.get(mLargestContour)))
				{
					mListOfContours.get(mLargestContour).release();
					mLargestContour = mIndex;
				}
				else
				{
					mListOfContours.get(mIndex).release();
				}
			}
			Imgproc.convexHull(mListOfContours.get(mLargestContour), mHull, true);
			if (!mHull.empty())
			{
				Imgproc.convexityDefects(mListOfContours.get(mLargestContour), mHull, mDefects);
				if (mDefects.rows() >= HandTracking.MIN_HAND_DEFECTS)
				{
					double angle;
					int[] defectsList = mDefects.toArray();
					for (mIndex = 0; mIndex < defectsList.length; ++mIndex)
					{
						mHandDefect = new HandTracking.HandDefect();
						mHandDefect.startPoint.set(mListOfContours.get(mLargestContour).get(defectsList[mIndex++], 0));
						mHandDefect.endPoint.set(mListOfContours.get(mLargestContour).get(defectsList[mIndex++], 0));
						mHandDefect.farthestPoint.set(mListOfContours.get(mLargestContour).get(defectsList[mIndex++], 0));
						mHandDefect.length = defectsList[mIndex] / 256.0f;
						angle = innerAngle(mHandDefect.startPoint, mHandDefect.endPoint, mHandDefect.farthestPoint);
						if (mHandDefect.length >= MIN_FINGER_LENGTH &&
							mHandDefect.length <= MAX_FINGER_LENGTH &&
							angle >= MIN_INNER_ANGLE &&
							angle <= MAX_INNER_ANGLE)
						{
							if (!mHandTracking.addHandDefect(mHandDefect))
								break;
						}
					}
					Imgproc.drawContours(mRGBA, mListOfContours, mLargestContour, COLOR_GREEN, 1);
					mHandTracking.calculateHandPose(mRGBA);
				}
			}
			mListOfContours.get(mLargestContour).release();
			mListOfContours.clear();
		}

		return mRGBA;
	}
}
