package com.danilo.corpusvr;

import android.content.Context;
import android.opengl.Matrix;

import com.example.rajawali.math.MathUtil;

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
	private static final int ELEMENT_SIZE = 3;
	private static final double PI = 3.1415926535897932384626433832795d;

	// Hand defect thresholds
	private static final float MIN_FINGER_LENGTH = 60.0f; // min finger defect length
	private static final float MAX_FINGER_LENGTH = 450.0f; // max finger defect length
	private static final float MIN_INNER_ANGLE = 15.0f;
	private static final float MAX_INNER_ANGLE = 115.0f;

	// Palm points thresholds
	private static final float MIN_BASE_LENGTH = 40.0f;
	private static final float MIN_PALM_INNER_ANGLE = 50.0f;
	private static final float MAX_PALM_INNER_ANGLE = 179.0f;

	// Parameters to create the camera perspective matrix
	private static final float NEAR = 1f;
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

	// Processed images
	private Mat mRGBA;
	private Mat mHSV;
	private Mat mBinMat;
	//private Mat mROI;
	//private Mat mElementMat;

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

	private double distanceP2P(Point a, Point b)
	{
		return Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2));
	}

	private double innerAngle(Point a, Point b, Point c)
	{
		double CAx = c.x - a.x;
		double CAy = c.y - a.y;
		double CBx = c.x - b.x;
		double CBy = c.y - b.y;

		// https://www.mathsisfun.com/algebra/trig-cosine-law.html (The Law of Cosines)
		double A = Math.acos((CBx * CAx + CBy * CAy) / (Math.sqrt(CBx * CBx + CBy * CBy) * Math.sqrt(CAx * CAx + CAy * CAy)));    // (a² + b² − c²) / 2
		return A * MathUtil.PRE_180_DIV_PI;

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
		mHierarchy = new Mat();
		mMinHSV1 = new Scalar(0, 20, 60); // 0, 20, 60
		mMaxHSV1 = new Scalar(20, 150, 255); // 20, 150, 255
		mListOfContours = new ArrayList<>();
		mHull = new MatOfInt();
		mDefects = new MatOfInt4();
		mHandDefect = new HandTracking.HandDefect();

//		Size elementSize = new Size(2 * ELEMENT_SIZE + 1, 2 * ELEMENT_SIZE + 1);
//		Point elementPoint = new Point(ELEMENT_SIZE, ELEMENT_SIZE);
//		mElementMat = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, elementSize, elementPoint);
		mProjectionGL = null;
		mProjectionCV = null;
		mProjectionListener.onProjectionChanged(width, height, getProjectionMat());
		getIntrinsicParam();
		mHandTracking.init();
	}

	@Override
	public void onCameraViewStopped()
	{
		mHSV.release();
		mBinMat.release();
		mHierarchy.release();
		mListOfContours.clear();
		mHull.release();
		mDefects.release();
		mProjectionCV.release();
		mHandTracking.release();
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
					double angle, length;
					int[] defectsList = mDefects.toArray();
					for (mIndex = 0; mIndex < defectsList.length; ++mIndex)
					{
						mHandDefect.startPoint.set(mListOfContours.get(mLargestContour).get(defectsList[mIndex++], 0));
						mHandDefect.endPoint.set(mListOfContours.get(mLargestContour).get(defectsList[mIndex++], 0));
						mHandDefect.farthestPoint.set(mListOfContours.get(mLargestContour).get(defectsList[mIndex++], 0));
						mHandDefect.length = defectsList[mIndex] / 256.0f;
						angle = innerAngle(mHandDefect.startPoint, mHandDefect.endPoint, mHandDefect.farthestPoint);

//						Imgproc.circle(mRGBA, mHandDefect.farthestPoint, 4, COLOR_GREEN, 2);

						if (mHandDefect.length >= MIN_FINGER_LENGTH &&
							mHandDefect.length <= MAX_FINGER_LENGTH &&
							angle >= MIN_INNER_ANGLE &&
							angle <= MAX_INNER_ANGLE)
						{
							if (!mHandTracking.addHandDefect(mHandDefect))
								break;
						}
						else
						{
							length = distanceP2P(mHandDefect.startPoint, mHandDefect.endPoint);
							if (length >= MIN_BASE_LENGTH &&
								angle >= MIN_PALM_INNER_ANGLE &&
								angle <= MAX_PALM_INNER_ANGLE)
							{
								if (!mHandTracking.addPalmPoint(mHandDefect.farthestPoint))
									break;
							}
						}
					}
					Imgproc.drawContours(mRGBA, mListOfContours, mLargestContour, COLOR_GREEN, 1);
					mHandTracking.calculateHandPose(getIntrinsicParam(), mRGBA);
				}
			}
			mListOfContours.get(mLargestContour).release();
			mListOfContours.clear();
		}

		mHandTracking.updateHandPose();

		return mRGBA;
	}
}
