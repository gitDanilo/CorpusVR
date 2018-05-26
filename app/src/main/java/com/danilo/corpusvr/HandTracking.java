package com.danilo.corpusvr;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Created by Danilo on 3/2/2018.
 */

public class HandTracking
{
	private static final String TAG = "HandTracking";

	private static final float DELTA_SCALE = 0.016f;

	private static final float MAX_DISTANCE = 99999.0f;
	private static final float DELTA_LENGTH = 0.8f; // 0.7f
	private static final float MIN_FINGER_WIDTH = /*25.0f*/30.0f;
	private static final float MAX_FINGER_WIDTH = /*60.0f*/130.0f;
	private static final float EPSILON = 120.0f;
	public static final int MIN_HAND_DEFECTS = 4; // min 4 hand defects, one between each finger
	public static final int MAX_HAND_DEFECTS = 5; // max number of palm points (create one exception)

	// Colors
	private static final Scalar COLOR_RED = new Scalar(255, 0, 0);
	private static final Scalar COLOR_GREEN = new Scalar(0, 255, 0);
	private static final Scalar COLOR_BLUE = new Scalar(0, 0, 255);
	private static final Scalar COLOR_YELLOW = new Scalar(255, 255, 0);
	private static final Scalar COLOR_PINK = new Scalar(255, 0, 255);

	private Semaphore mSemaphore;
	private HandPose mHandPose;
	private HandPose mHandPoseTemp;
	private List<HandDefect> mHandDefectsList;
	private List<Point> mPalmPoints;
	private MatOfPoint2f mPalmPointsMat;
	private float d[];
	private Point mPalmPoint;
	private Point mMidPoint;
	private float mPalmRadius;
	private Point mPalmCenter;
	private MatOfPoint3f mRefPoints;
	private MatOfDouble mDistCoeffs;
	private MatOfDouble mRVec;
	private MatOfDouble mTVec;
	private MatOfDouble mRotation;
	//private List<Integer> mRedPoints;

	public HandTracking()
	{
		mHandPose = new HandPose();
		mHandPoseTemp = new HandPose();
		mSemaphore = new Semaphore(1);
		mHandDefectsList = new ArrayList<>(MAX_HAND_DEFECTS);
		mPalmPoints = new ArrayList<>(MAX_HAND_DEFECTS + 1);
		d = new float[3];
		mPalmPoint = new Point();
		mMidPoint = new Point();
		mPalmCenter = new Point();
	}

	public void init()
	{
		mPalmPointsMat = new MatOfPoint2f();
		mRefPoints = new MatOfPoint3f();
		mDistCoeffs = new MatOfDouble(0, 0, 0, 0); // Assume no distortion
		mRVec = new MatOfDouble();
		mTVec = new MatOfDouble();
		mRotation = new MatOfDouble();

		// Reference points
//		mRefPoints.fromArray(new Point3(-1.47686,2.59216,0),
//							 new Point3(0.218609,3.08954,0),
//							 new Point3(2.19977,2.62338,0),
//							 new Point3(2.57853,-5.29157,0),
//							 new Point3(-2.06362,-4.79628,0));

//		mRefPoints.fromArray(new Point3(-1.464284,0.208872,0), // X = -146.4284     Y = 20.8872      Z = 0.0000
//							 new Point3(-0.514284,1.438872,0), // X = -51.4284      Y = 143.8872     Z = 0.0000
//							 new Point3(0.145716,1.378872,0),  // X = 14.5716       Y = 137.8872     Z = 0.0000
//							 new Point3(0.795716,1.018872,0)); // X = 79.5716       Y = 101.8872     Z = 0.0000

//		mRefPoints.fromArray(new Point3(-1.104284,0.068872,0.012255),
//							 new Point3(-0.514284,1.358872,0),
//							 new Point3(0.125716,1.378872,0),
//							 new Point3(0.695716,1.218872,0));

		mRefPoints.fromArray(new Point3(-1.104, 0.069, 0.012),
							 new Point3(-0.514, 1.359, 0),
							 new Point3(0.126, 1.379, 0),
							 new Point3(0.696, 1.219, 0));
	}

	public void release()
	{
		mHandDefectsList.clear();
		mPalmPoints.clear();
		mPalmPointsMat.release();
		mRefPoints.release();
		mDistCoeffs.release();
		mRVec.release();
		mTVec.release();
		mRotation.release();
	}

	public HandPose getObjStatus()
	{
		HandPose result = null;
		try
		{
			mSemaphore.acquire();
			result = mHandPose;
			mSemaphore.release();
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		finally
		{
			return result;
		}
	}

	public void updateHandPose()
	{
		// Change the real HandPose object
		try
		{
			mSemaphore.acquire();
			mHandPose.copyFrom(mHandPoseTemp);
			mSemaphore.release();
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
		mHandPoseTemp.render = false;
	}

	public boolean addHandDefect(HandDefect defect)
	{
		if (mHandDefectsList.size() == MAX_HAND_DEFECTS)
		{
			mHandDefectsList.clear();
			mPalmPoints.clear();
			return false;
		}
		HandDefect tmp = new HandDefect();
		tmp.copyFrom(defect);
		mHandDefectsList.add(tmp);
		return true;
	}

	public boolean addPalmPoint(Point point)
	{
		if (mPalmPoints.size() == MAX_HAND_DEFECTS)
		{
			mHandDefectsList.clear();
			mPalmPoints.clear();
			return false;
		}
		Point tmp = new Point(point.x, point.y);
		mPalmPoints.add(tmp);
		return true;
	}

	public void calculateHandPose(MatOfDouble intrinsicParam, Mat rgba)
	{
		int size = mHandDefectsList.size();
		if (size >= MIN_HAND_DEFECTS && size <= MAX_HAND_DEFECTS)
		{
			int index = find3ClosestPoints();
			if (index != -1)
			{
				int prev_index = index == 0 ? size - 1 : index - 1;
				int next_index = (index + 1) % size;
				int prev_index_2 = prev_index == 0 ? size - 1 : prev_index - 1;
				int next_index_2 = (next_index + 1) % size;

				float distList[] = new float[3];
				distList[0] = distanceP2P(mHandDefectsList.get(prev_index).farthestPoint, mHandDefectsList.get(prev_index_2).farthestPoint);
				distList[1] = distanceP2P(mHandDefectsList.get(next_index).farthestPoint, mHandDefectsList.get(next_index_2).farthestPoint);

				boolean right_model;
				// Locate thumb
				if (distList[0] > distList[1])
				{
					right_model = false;
				}
				else
				{
					right_model = true;
				}

				// Draw
//				int i, j = mPalmPoints.size();
//				for (i = 0; i < j; ++i)
//				{
//					Imgproc.circle(rgba, mPalmPoints.get(i), 4, COLOR_RED, 2);
//				}

//				Imgproc.isContourConvex();

				if (right_model)
				{
					mPalmPointsMat.fromArray(mHandDefectsList.get(prev_index_2).farthestPoint,
											 mHandDefectsList.get(prev_index).farthestPoint,
											 mHandDefectsList.get(index).farthestPoint,
											 mHandDefectsList.get(next_index).farthestPoint);

//					Log.d(TAG, "thumb : " + mHandDefectsList.get(prev_index_2).farthestPoint);
//					Log.d(TAG, "index : " + mHandDefectsList.get(prev_index).farthestPoint);
//					Log.d(TAG, "middle: " + mHandDefectsList.get(index).farthestPoint);
//					Log.d(TAG, "ring  : " + mHandDefectsList.get(next_index).farthestPoint);
				}
				else
				{
					mPalmPointsMat.fromArray(mHandDefectsList.get(next_index_2).farthestPoint,
											 mHandDefectsList.get(next_index).farthestPoint,
											 mHandDefectsList.get(index).farthestPoint,
											 mHandDefectsList.get(prev_index).farthestPoint);
				}

				// Find the hand's Euler angles and coordinates
				Calib3d.solvePnP(mRefPoints, mPalmPointsMat, intrinsicParam, mDistCoeffs, mRVec, mTVec);

				double[] rVecArray = mRVec.toArray();
				rVecArray[0] *= -1.0; // Inverted X angle
				mRVec.fromArray(rVecArray);

				// Convert the Euler angles to 3x3 matrix
				Calib3d.Rodrigues(mRVec, mRotation);

				double[] tVecArray = mTVec.toArray();

				mHandPoseTemp.render = true;
				mHandPoseTemp.right_model = right_model;

				mHandPoseTemp.pose[0] = mRotation.get(0,0)[0];
				mHandPoseTemp.pose[1] = mRotation.get(0,1)[0];
				mHandPoseTemp.pose[2] = mRotation.get(0,2)[0];
				mHandPoseTemp.pose[3] = 0;
				mHandPoseTemp.pose[4] = mRotation.get(1,0)[0];
				mHandPoseTemp.pose[5] = mRotation.get(1,1)[0];
				mHandPoseTemp.pose[6] = mRotation.get(1,2)[0];
				mHandPoseTemp.pose[7] = 0;
				mHandPoseTemp.pose[8] = mRotation.get(2,0)[0];
				mHandPoseTemp.pose[9] = mRotation.get(2,1)[0];
				mHandPoseTemp.pose[10] = mRotation.get(2,2)[0];
				mHandPoseTemp.pose[11] = 0;
				mHandPoseTemp.pose[12] = tVecArray[0];
				mHandPoseTemp.pose[13] = - tVecArray[1];
				mHandPoseTemp.pose[14] = - tVecArray[2];
				mHandPoseTemp.pose[15] = 1;
			}
		}
		mHandDefectsList.clear();
		mPalmPoints.clear();
	}

	private double arcTang(Point a, Point b)
	{
		return Math.atan2(a.y - b.y, b.x - a.x);
	}

	// Returns the closest 3 points that respect the min and max threshold distances
	private int find3ClosestPoints()
	{
		int defectsSize = mHandDefectsList.size();

		if (defectsSize < MIN_HAND_DEFECTS)
			return -1;

		d[0] = d[1] = d[2] = 0;

		int i = defectsSize;
		float shortestDist = MAX_DISTANCE;
		float oldDist;
		int result = -1;

		for (; i > 0; --i)
		{
			oldDist = d[0];
			d[0] = distanceP2P(mHandDefectsList.get(i % defectsSize).farthestPoint, mHandDefectsList.get((i - 1) % defectsSize).farthestPoint);
			if (d[0] < MIN_FINGER_WIDTH || d[0] > MAX_FINGER_WIDTH)
			{
				// skip next point because he will be invalid
				d[0] = 0;
				--i;
				continue;
			}
			d[1] = oldDist > 0 ? oldDist : distanceP2P(mHandDefectsList.get(i % defectsSize).farthestPoint, mHandDefectsList.get((i + 1) % defectsSize).farthestPoint);
			if (d[1] < MIN_FINGER_WIDTH || d[1] > MAX_FINGER_WIDTH)
				continue;

			d[2] = d[0] + d[1];

			if ((d[2]) < shortestDist)
			{
				shortestDist = d[2];
				result = i % defectsSize;
			}
		}

		return result;
	}

	private float distanceP2P(Point a, Point b)
	{
		return (float) (Math.sqrt(Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2)));
	}

	public static class HandPose implements Duplicable
	{
		public boolean render;
		public boolean right_model;
		public double pose[];
		public Point start;
		public float angle;
		public float scale;
		public float fingerAngles[];

		@Override
		public boolean copyFrom(Duplicable obj)
		{
			if (obj.getClass().equals(this.getClass()))
			{
				HandPose tmp = (HandPose) obj;
				render = tmp.render;
				right_model = tmp.right_model;
				start.x = tmp.start.x;
				start.y = tmp.start.y;
				angle = tmp.angle;
				scale = tmp.scale;
				System.arraycopy(tmp.fingerAngles, 0, fingerAngles, 0, 5);
				System.arraycopy(tmp.pose, 0, pose, 0, 16);
				return true;
			}
			return false;
		}

		public HandPose()
		{
			render = false;
			right_model = false;
			start = new Point();
			angle = 0;
			scale = 0;
			fingerAngles = new float[5];
			pose = new double[16];
		}
	}

	public static class HandDefect implements Duplicable
	{
		public Point startPoint;
		public Point endPoint;
		public Point farthestPoint;
		public float length;

		@Override
		public boolean copyFrom(Duplicable obj)
		{
			if (obj.getClass().equals(this.getClass()))
			{
				HandDefect tmp = (HandDefect) obj;
				startPoint.x = tmp.startPoint.x;
				startPoint.y = tmp.startPoint.y;
				endPoint.x = tmp.endPoint.x;
				endPoint.y = tmp.endPoint.y;
				farthestPoint.x = tmp.farthestPoint.x;
				farthestPoint.y = tmp.farthestPoint.y;
				length = tmp.length;
				return true;
			}
			return false;
		}

		public HandDefect()
		{
			startPoint = new Point(0, 0);
			endPoint = new Point(0, 0);
			farthestPoint = new Point(0, 0);
			length = 0;
		}
	}
}