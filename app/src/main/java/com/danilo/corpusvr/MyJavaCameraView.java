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
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Danilo on 3/6/2018.
 */

public class MyJavaCameraView extends JavaCameraView implements CameraBridgeViewBase.CvCameraViewListener2
{
	// Used to load the 'native-lib' library on application startup.
	//	static
	//	{
	//		System.loadLibrary("native-lib");
	//	}
	private static final String TAG = "MyJavaCameraView";

	// Colors
	private static final Scalar COLOR_RED = new Scalar(255, 0, 0);
	private static final Scalar COLOR_GREEN = new Scalar(0, 255, 0);
	private static final Scalar COLOR_BLUE = new Scalar(0, 0, 255);
	private static final Scalar COLOR_YELLOW = new Scalar(255, 255, 0);

	private static final int BLUR_SIZE = 5;
	private static final int ELEMENT_SIZE = 4;
	private static final double PI = 3.1415926535897932384626433832795d;

	// Hand detection Parameters
	private static final double MIN_ANGLE = 0;
	private static final double MAX_ANGLE = 179;
	private static final double MIN_INNER_ANGLE = 20;
	private static final double MAX_INNER_ANGLE = 130;
	private static final double MIN_LENGTH = 10;
	private static final double MAX_LENGTH = 80;

	private static final float NEAR = 0.1f;
	private static final float FAR  = 100f;

	private static final int MAX_BAD_TRACKING_FRAMES = 5;

	private HandTracking mHandTracking;
	private int mScreenWidth = -1;
	private int mScreenHeight = -1;
	private float mFOVX = 0;
	private float mFOVY = 0;
	private MatOfDouble mProjectionCV;
	private float[] mProjectionGL;
	private float[] mProjectionGLInv;
	private float[] mWorldPoint;
	private float[] mViewMatrix = new float[]{1.0f, 0, 0, 0, 0, 1.0f, 0, 0, 0, 0, 1.0f, 0, 0, 0, -4.0f, 1.0f};
	private Mat mRGBA;
	private Mat mHSV;
	private Mat mBinMat;
	//	private Mat mMask = null;
	//	private Mat mMask1 = null;
	//	private Mat mMask2 = null;
	private Mat mHierarchy;
	private Scalar mMinHSV1;
	private Scalar mMaxHSV1;
	//	private Scalar mMinHSV2;
	//	private Scalar mMaxHSV2;
	private List<MatOfPoint> mListOfContours;
	//	private Moments mMoments;
	private MatOfPoint mIntSceneCorners;
	private Mat mSceneCorners;
	private MatOfPoint2f mSceneCorners2D;
	private MatOfPoint3f mReferenceCorners3D;
	private MatOfDouble mDistCoeffs; // Distortion
	private MatOfDouble mRVec;
	private MatOfDouble mTVec;
	private MatOfDouble mRotation;
	private Mat mElementMat;
	private int mIndex;
	private int mLargestContour;
	private MatOfInt mHull;
	private MatOfInt4 mDefects;
	private Rect mObjBB;
	private Point mObjBBCenter;
	private Point mStartPt;
	private Point mEndPt;
	private Point mFarthestPt;
	private HandTracking.HandStatus mHandStatus;
	private float mAngle;
	private int mBadTrackingFrames;

	private double innerAngle(double ax, double ay, double bx, double by, double cx, double cy)
	{
		double CAx = cx - ax;
		double CAy = cy - ay;
		double CBx = cx - bx;
		double CBy = cy - by;

		// https://www.mathsisfun.com/algebra/trig-cosine-law.html (The Law of Cosines)
		double A = Math.acos((CBx * CAx + CBy * CAy) / (Math.sqrt(CBx * CBx + CBy * CBy) * Math.sqrt(CAx * CAx + CAy * CAy)));	// (a² + b² − c²) / 2
																																// ( (sqrt( (Ax - Bx)*(Ax - Bx) + (Ay - By)*(Ay - By) ))² + (sqrt( (Ax - Cx)*(Ax - Cx) + (Ay - Cy)*(Ay - Cy) ))² − (sqrt( (Bx - Cx)*(Bx - Cx) + (By - Cy)*(By - Cy) ))²) / 2
		return (A * 180 / PI);
	}

	private float[] ScreenToWorld(Point screenPoint)
	{
		if (mWorldPoint == null)
			mWorldPoint = new float[4];

		if (mProjectionGLInv == null)
		{
			mProjectionGLInv = new float[16];
			Matrix.multiplyMM(mProjectionGLInv, 0, mProjectionGL, 0, mViewMatrix , 0);
			Matrix.invertM(mProjectionGLInv, 0, mProjectionGLInv, 0);
		}

		mWorldPoint[0] = (2.0f * ((float) (screenPoint.x) / (float) (mScreenWidth))) - 1.0f;
		mWorldPoint[1] = 1.0f - (2.0f * ((float) (screenPoint.y) / (float) (mScreenHeight)));
		mWorldPoint[2] = 2.0f * 0.5f/*Z*/ - 1.0f;
		mWorldPoint[3] = 1.0f;

		Matrix.multiplyMV(mWorldPoint, 0, mProjectionGLInv, 0, mWorldPoint, 0);

//		mWorldPoint[3] = 1.0f / mWorldPoint[3];
//
//		mWorldPoint[0] *= mWorldPoint[3];
//		mWorldPoint[1] *= mWorldPoint[3];
//		mWorldPoint[2] *= mWorldPoint[3];


		return mWorldPoint;
	}

	public MyJavaCameraView(Context context, int cameraId, HandTracking handTracking)
	{
		super(context, cameraId);
		setCvCameraViewListener(this);
		enableFpsMeter();
		mHandTracking = handTracking;
	}

	public float getFOVX()
	{
		return mCamera.getParameters().getHorizontalViewAngle();
	}

	public float getFOVY()
	{
		return mCamera.getParameters().getVerticalViewAngle();
	}

	public MatOfDouble getProjectionCV()
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

	private float[] getProjectionGL()
	{
		if (mProjectionGL == null)
		{
			mProjectionGL = new float[16];

			float fovX = getFOVX();
			float aspectR = (float) mScreenWidth / (float) mScreenHeight;
			float right = (float) Math.tan(0.5f * fovX * Math.PI / 180.0f) * NEAR;
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
		//mRGBA = new Mat(height, width, CvType.CV_8UC4);
		mHSV = new Mat(height, width, CvType.CV_8UC3);
		mBinMat = new Mat(height, width, CvType.CV_8UC1);
		mFOVX = getFOVX();
		mFOVY = getFOVY();
		//		mMask      = new Mat(height, width, CvType.CV_8UC4);
		//		mMask1     = new Mat(height, width, CvType.CV_8UC4);
		//		mMask2     = new Mat(height, width, CvType.CV_8UC4);
		mHierarchy = new Mat();
		// 		Detection For Red Color
		//		mMinHSV1 = new Scalar(0, 70, 50);
		//		mMaxHSV1 = new Scalar(10, 255, 255);
		//		mMinHSV2 = new Scalar(170, 70, 50);
		//		mMaxHSV2 = new Scalar(180, 255, 255);
		mMinHSV1 = new Scalar(0, 30, 60);
		mMaxHSV1 = new Scalar(20, 150, 255);
		mHandStatus = new HandTracking.HandStatus();
		mIntSceneCorners = new MatOfPoint();
		mSceneCorners = new Mat(4, 1, CvType.CV_32FC2);
		mListOfContours = new ArrayList<>();
		mHull = new MatOfInt();
		mDefects = new MatOfInt4();
		mObjBBCenter = new Point();
		mStartPt = new Point();
		mEndPt = new Point();
		mFarthestPt = new Point();
		mSceneCorners2D = new MatOfPoint2f();
		mReferenceCorners3D = new MatOfPoint3f();
		mDistCoeffs = new MatOfDouble(0.0, 0.0, 0.0, 0.0); // Assume no distortion
		mRVec = new MatOfDouble();
		mTVec = new MatOfDouble();
		mRotation = new MatOfDouble();
		mReferenceCorners3D.fromArray(
				new Point3(-5, -5, 0.0),
				new Point3( 5, -5, 0.0),
				new Point3( 5,  5, 0.0),
				new Point3(-5,  5, 0.0));

		Size elementSize = new Size(2 * ELEMENT_SIZE + 1, 2 * ELEMENT_SIZE + 1);
		Point elementPoint = new Point(ELEMENT_SIZE, ELEMENT_SIZE);
		mElementMat = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, elementSize, elementPoint);
		mProjectionGL = null;
		mProjectionGLInv = null;
		mProjectionCV = null;
		getProjectionGL();
		getProjectionCV();
		mAngle = 0;
		mBadTrackingFrames = 0;
	}

	@Override
	public void onCameraViewStopped()
	{
		mHSV.release();
		mBinMat.release();
		//		mMask.release();
		//		mMask1.release();
		//		mMask2.release();
		mHierarchy.release();
		mSceneCorners.release();
		mListOfContours.clear();
		mHull.release();
		mDefects.release();
		//mProjectionCV.release();
	}

	@Override
	public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame)
	{
		mRGBA = inputFrame.rgba();

		mHandStatus.mRender = false;

		//FUCKJNI(mRGBA.getNativeObjAddr());
		Imgproc.cvtColor(mRGBA, mHSV, Imgproc.COLOR_RGB2HSV);
		Core.inRange(mHSV, mMinHSV1, mMaxHSV1, mBinMat);
		Imgproc.medianBlur(mBinMat, mBinMat, BLUR_SIZE);

		//Imgproc.dilate(mHSV, mHSV, mElementMat);
		//Core.inRange(mHSV, mMinHSV1, mMaxHSV1, mMask1);
		//Core.inRange(mHSV, mMinHSV2, mMaxHSV2, mMask2);
		//Core.add(mMask1, mMask2, mMask);

		//Imgproc.circle(mRGBA, new Point(1280, 720), 6, COLOR_RED, 2);

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
				if (mDefects.rows() > 0)
				{
					mObjBB = Imgproc.boundingRect(mListOfContours.get(mLargestContour));
					Imgproc.rectangle(mRGBA, mObjBB.tl(), mObjBB.br(), COLOR_GREEN, 1);
					mObjBBCenter = new Point(mObjBB.x + mObjBB.width / 2, mObjBB.y + mObjBB.height / 2);
					int[] defectsList = mDefects.toArray();
					for (mIndex = 0; mIndex < defectsList.length; mIndex += 2)
					{
						mStartPt.set(mListOfContours.get(mLargestContour).get(defectsList[mIndex++], 0));
						mEndPt.set(mListOfContours.get(mLargestContour).get(defectsList[mIndex++], 0));
						mFarthestPt.set(mListOfContours.get(mLargestContour).get(defectsList[mIndex], 0));
						//double angle = Math.atan2(mObjBBCenter.y - mStartPt.y, mObjBBCenter.x - mStartPt.x) * 180 / PI;
						double inAngle = innerAngle(mStartPt.x, mStartPt.y, mEndPt.x, mEndPt.y, mFarthestPt.x, mFarthestPt.y);
						double length = Math.sqrt(Math.pow(mStartPt.x - mFarthestPt.x, 2) + Math.pow(mStartPt.y - mFarthestPt.y, 2));
						if (/*angle > MIN_ANGLE &&*/
							/*angle < MAX_ANGLE &&*/
							inAngle > MIN_INNER_ANGLE &&
							inAngle < MAX_INNER_ANGLE &&
							length > MIN_LENGTH / 100.0 * mObjBB.height &&
							length < MAX_LENGTH / 100.0 * mObjBB.height)
						{
							Imgproc.drawContours(mRGBA, mListOfContours, mLargestContour, COLOR_GREEN, 1);
							Imgproc.line(mRGBA, mStartPt, mEndPt, COLOR_RED);
							Imgproc.line(mRGBA, mStartPt, mFarthestPt, COLOR_RED);
							Imgproc.line(mRGBA, mEndPt, mFarthestPt, COLOR_RED);
							Imgproc.circle(mRGBA, mStartPt, 5, COLOR_BLUE, 2);
							Imgproc.circle(mRGBA, mEndPt, 5, COLOR_BLUE, 2);
							Imgproc.circle(mRGBA, mFarthestPt, 5, COLOR_BLUE, 2);

							Imgproc.circle(mRGBA, mObjBBCenter, 5, COLOR_YELLOW, 2);

							ScreenToWorld(mObjBBCenter);

							mHandStatus.mPose[0] = 1.0f;
							mHandStatus.mPose[1] = 0;
							mHandStatus.mPose[2] = 0;
							mHandStatus.mPose[3] = 0;
							mHandStatus.mPose[4] = 0;
							mHandStatus.mPose[5] = 1.0f;
							mHandStatus.mPose[6] = 0;
							mHandStatus.mPose[7] = 0;
							mHandStatus.mPose[8] = 0;
							mHandStatus.mPose[9] = 0;
							mHandStatus.mPose[10] = 1.0f;
							mHandStatus.mPose[11] = 0;
							mHandStatus.mPose[12] = mWorldPoint[0];
							mHandStatus.mPose[13] = mWorldPoint[1];
							mHandStatus.mPose[14] = mWorldPoint[2];
							mHandStatus.mPose[15] = mWorldPoint[3];

							Matrix.scaleM(mHandStatus.mPose, 0, mHandStatus.mPose, 0, 0.05f, 0.05f, 0.05f);
							Matrix.rotateM(mHandStatus.mPose, 0, mHandStatus.mPose, 0, mAngle, 1, 0, 0);
							if (mAngle == 360)
								mAngle = 0;
							else
								mAngle += 1.5;

							mHandStatus.mRender = true;
							mBadTrackingFrames = 0;
						}
					}
				}

			}

			mListOfContours.get(mLargestContour).release();
			mListOfContours.clear();
		}

		if (mHandStatus.mRender == false)
		{
			if (mBadTrackingFrames < 15)
			{
				++mBadTrackingFrames;
				mHandStatus.mRender = true;
			}
			else // Lost tracking
			{
				mBadTrackingFrames = 0;
			}
		}

		mHandTracking.setObjStatus(mHandStatus);

		return mRGBA;
	}

	//private native void FUCKJNI(long addrMatRBG);
}
