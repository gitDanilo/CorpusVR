package com.danilo.corpusvr;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Created by Danilo on 3/2/2018.
 */

public class HandTracking
{
	private static final String TAG = "HandTracking";
	private static final float MAX_DISTANCE = 99999.0f;
	private static final float DELTA_LENGTH = 0.7f;
	private static final float MIN_FINGER_WIDTH = /*25.0f*/40.0f;
	private static final float MAX_FINGER_WIDTH = /*60.0f*/200.0f;
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
	private float d[];
	private Point mPalmPoint;
	private Point mMidPoint;
	private float mPalmRadius;
	private Point mPalmCenter;

	public HandTracking()
	{
		mHandPose = new HandPose();
		mHandPoseTemp = new HandPose();
		mSemaphore = new Semaphore(1);
		mHandDefectsList = new ArrayList<>(MAX_HAND_DEFECTS);
		d = new float[3];
		mPalmPoint = new Point();
		mMidPoint = new Point();
		mPalmCenter = new Point();
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

//	public void setObjStatus(HandPose Status)
//	{
//		try
//		{
//			mSemaphore.acquire();
//			mHandPose = Status;
//			mSemaphore.release();
//		}
//		catch (InterruptedException e)
//		{
//			e.printStackTrace();
//		}
//	}

	public boolean addHandDefect(HandDefect defect)
	{
		if (mHandDefectsList.size() == MAX_HAND_DEFECTS)
		{
			mHandDefectsList.clear();
			return false;
		}
		mHandDefectsList.add(defect);
		return true;
	}

	public void calculateHandPose(Mat rgba)
	{
		int size = mHandDefectsList.size();
		if (size >= MIN_HAND_DEFECTS && size <= MAX_HAND_DEFECTS)
		{
			int index = find3ClosestPoints();
			if (index != -1)
			{
				int prev_index = index == 0 ? size - 1 : index - 1;
				int next_index = (index + 1) % size;

				mPalmPoint = mHandDefectsList.get(next_index).farthestPoint;

				mMidPoint.x = (mHandDefectsList.get(prev_index).farthestPoint.x + mHandDefectsList.get(next_index).farthestPoint.x) / 2.0d;
				mMidPoint.y = (mHandDefectsList.get(prev_index).farthestPoint.y + mHandDefectsList.get(next_index).farthestPoint.y) / 2.0d;

				double angle = arcTang(mMidPoint, mPalmPoint);
				mPalmRadius = mHandDefectsList.get(index).length * DELTA_LENGTH;

				// Rotate mPalmCenter point by angle
				mPalmCenter.x = -Math.sin(angle) * mPalmRadius;
				mPalmCenter.y = Math.cos(angle) * mPalmRadius;

				// Translate mPalmCenter to midPoint
				mPalmCenter.x += mMidPoint.x;
				mPalmCenter.y -= mMidPoint.y; // Inverted y on OpenCV
				//mPalmCenter.y = mPalmCenter.y - midPoint.y;
				mPalmCenter.y *= -1; // Inverted y on OpenCV

				Imgproc.circle(rgba, mPalmCenter, (int) mPalmRadius, COLOR_BLUE, 1);
				Imgproc.circle(rgba, mPalmCenter, 4, COLOR_BLUE, 2);
				for (int i = 0; i < mHandDefectsList.size(); ++i)
				{
					Imgproc.circle(rgba, mHandDefectsList.get(i).startPoint, 4, COLOR_BLUE, 2);
					Imgproc.circle(rgba, mHandDefectsList.get(i).endPoint, 4, COLOR_GREEN, 2);
					Imgproc.circle(rgba, mHandDefectsList.get(i).farthestPoint, 4, COLOR_RED, 2);
					Imgproc.line(rgba, mHandDefectsList.get(i).startPoint, mHandDefectsList.get(i).endPoint, COLOR_PINK, 1);
					Imgproc.line(rgba, mHandDefectsList.get(i).farthestPoint, mHandDefectsList.get(i).startPoint, COLOR_PINK, 1);
					Imgproc.line(rgba, mHandDefectsList.get(i).farthestPoint, mHandDefectsList.get(i).endPoint, COLOR_PINK, 1);
				}

				// Do shit
//				mHandPoseTemp.render = false;
//				mHandPoseTemp.angle = 0;
//				mHandPoseTemp.scale = 0;
//				mHandPoseTemp.start.x = 0;
//				mHandPoseTemp.start.y = 0;
//				for (int i = 0; i < 5; ++i)
//				{
//					mHandPoseTemp.fingerPoses[i].start.x = 0;
//					mHandPoseTemp.fingerPoses[i].start.y = 0;
//					mHandPoseTemp.fingerPoses[i].angle = 0;
//				}

				// Change the real HandPose object
//				try
//				{
//					mSemaphore.acquire();
//					mHandPose = mHandPoseTemp;
//					mSemaphore.release();
//				}
//				catch (InterruptedException e)
//				{
//					e.printStackTrace();
//				}
			}
		}
		mHandDefectsList.clear();
	}

	double arcTang(Point a, Point b)
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

	public static class FingerPose
	{
		public Point start;
		public float angle;

		public FingerPose()
		{
			start = new Point(0, 0);
			angle = 0;
		}

		public FingerPose(Point start, float angle)
		{
			this.start = start;
			this.angle = angle;
		}
	}

	public static class HandPose
	{
		public boolean render;
		public Point start;
		public float angle;
		public float scale;
		public FingerPose[] fingerPoses;

		public HandPose()
		{
			render = false;
			start = new Point(0, 0);
			angle = 0;
			scale = 0;
			fingerPoses = new FingerPose[5];
		}
	}

	public static class HandDefect
	{
		public Point startPoint;
		public Point endPoint;
		public Point farthestPoint;
		public float length;

		public HandDefect()
		{
			startPoint = new Point(0, 0);
			endPoint = new Point(0, 0);
			farthestPoint = new Point(0, 0);
			length = 0;
		}
	}
}