package com.danilo.corpusvr;

import com.example.rajawali.math.MathUtil;
import com.example.rajawali.math.Matrix;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
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

	private static final double POSE_SC_COMP    = 25;
	private static final double POSE_X_ROT_COMP = 20.0;
	private static final double POSE_Z_ROT_COMP = 4.0;
	private static final double POSE_Y_TR_COMP  = 0.013;

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
	private static final double[] DEF_FINGER_ANGLES = new double[] {2.302089,
																	1.478468,
																	1.427679,
																	1.326974,
																	1.128181};

	private Semaphore mSemaphore;
	private HandPose mHandPose;
	private HandPose mHandPoseTemp;
	private List<HandDefect> mHandDefectsList;
	private List<Point> mPalmPoints;
	private MatOfPoint2f mPalmPointsMat;
	private MatOfPoint mIntPalmPointsMat;
	private float d[];
	private int mFingerIndices[];
	private MatOfPoint3f mRefPoints;
	private MatOfDouble mDistCoeffs;
	private MatOfDouble mRVec;
	private MatOfDouble mTVec;
	private MatOfDouble mRotation;

	public HandTracking()
	{
		mHandPose = new HandPose();
		mHandPoseTemp = new HandPose();
		mSemaphore = new Semaphore(1);
		mHandDefectsList = new ArrayList<>(MAX_HAND_DEFECTS);
		mPalmPoints = new ArrayList<>(MAX_HAND_DEFECTS + 1);
		d = new float[3];
		mFingerIndices = new int[5];
	}

	public void init()
	{
		mPalmPointsMat = new MatOfPoint2f();
		mIntPalmPointsMat = new MatOfPoint();
		mRefPoints = new MatOfPoint3f();
		mDistCoeffs = new MatOfDouble(0, 0, 0, 0); // Assume no distortion
		mRVec = new MatOfDouble();
		mTVec = new MatOfDouble();
		mRotation = new MatOfDouble();

		// Reference points

		mRefPoints.fromArray(new Point3(-0.7855, -0.0178, 0),
							 new Point3(-0.3355, 0.9745 , 0),
							 new Point3(0.1974 , 0.9206 , 0),
							 new Point3(0.5862 , 0.6976 , 0));

//		mRefPoints.fromArray(new Point3(-0.7855, -0.0178, 0),
//							 new Point3(-0.3355, 0.9745 , 0),
//							 new Point3( 0.1831, 0.9224 , 0),
//							 new Point3(0.5862 , 0.6976 , 0));

//		mRefPoints.fromArray(new Point3(-0.9265, -0.0125, 0),
//							 new Point3(-0.3064, 0.9434 , 0),
//							 new Point3(0.1923 , 0.9327 , 0),
//							 new Point3(0.7427 , 0.6633 , 0));
	}

	public void release()
	{
		mHandDefectsList.clear();
		mPalmPoints.clear();
		mPalmPointsMat.release();
		mIntPalmPointsMat.release();
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
			int index = findClosest3Points();
			if (index != -1)
			{
				int prev_index = index == 0 ? size - 1 : index - 1;
				int next_index = (index + 1) % size;
				int prev_index_2 = prev_index == 0 ? size - 1 : prev_index - 1;
				int next_index_2 = (next_index + 1) % size;

				float dist1 = distanceP2P(mHandDefectsList.get(prev_index).farthestPoint, mHandDefectsList.get(prev_index_2).farthestPoint);
				float dist2 = distanceP2P(mHandDefectsList.get(next_index).farthestPoint, mHandDefectsList.get(next_index_2).farthestPoint);

				// Locate thumb
				boolean right_model;
				if (dist1 > dist2)
				{
					right_model = false;
					mPalmPointsMat.fromArray(mHandDefectsList.get(next_index_2).farthestPoint,
											 mHandDefectsList.get(next_index).farthestPoint,
											 mHandDefectsList.get(index).farthestPoint,
											 mHandDefectsList.get(prev_index).farthestPoint);
					mFingerIndices[0] = next_index_2;
					mFingerIndices[1] = next_index_2;
					mFingerIndices[2] = next_index;
					mFingerIndices[3] = index;
					mFingerIndices[4] = prev_index;
				}
				else
				{
					right_model = true;
					mPalmPointsMat.fromArray(mHandDefectsList.get(prev_index_2).farthestPoint,
											 mHandDefectsList.get(prev_index).farthestPoint,
											 mHandDefectsList.get(index).farthestPoint,
											 mHandDefectsList.get(next_index).farthestPoint);
					mFingerIndices[0] = prev_index_2;
					mFingerIndices[1] = prev_index_2;
					mFingerIndices[2] = prev_index;
					mFingerIndices[3] = index;
					mFingerIndices[4] = next_index;
				}

				mPalmPointsMat.convertTo(mIntPalmPointsMat, CvType.CV_32S);
				if (Imgproc.isContourConvex(mIntPalmPointsMat))
				{
					// Find the hand's Euler angles and coordinates
					Calib3d.solvePnP(mRefPoints, mPalmPointsMat, intrinsicParam, mDistCoeffs, mRVec, mTVec, false, Calib3d.SOLVEPNP_ITERATIVE);

					double[] rVecArray = mRVec.toArray();
					rVecArray[0] *= -1.0; // Inverted X angle
					mRVec.fromArray(rVecArray);

					// Convert the Euler angles to 3x3 matrix
					Calib3d.Rodrigues(mRVec, mRotation);

					double[] tVecArray = mTVec.toArray();

					mHandPoseTemp.render = !right_model; // Only render left hand model for now
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

					// Model compensation
					Matrix.scaleM(mHandPoseTemp.pose, 0, POSE_SC_COMP, POSE_SC_COMP, POSE_SC_COMP);
					Matrix.rotateM(mHandPoseTemp.pose, 0, POSE_X_ROT_COMP, 1, 0, 0);
					//Matrix.rotateM(mHandPoseTemp.pose, 0, POSE_Z_ROT_COMP, 0, 0, 1);
					Matrix.translateM(mHandPoseTemp.pose, 0, 0, POSE_Y_TR_COMP, 0);

					// Calculate finger angles
					// https://stackoverflow.com/questions/15022630/how-to-calculate-the-angle-from-rotation-matrix
					double palmRotationZ = Math.atan2(mHandPoseTemp.pose[4], mHandPoseTemp.pose[0]);

					mHandPoseTemp.fingerAngles[0] = arcTang(mHandDefectsList.get(mFingerIndices[0]).farthestPoint,
															right_model ?
															mHandDefectsList.get(mFingerIndices[0]).startPoint :
															mHandDefectsList.get(mFingerIndices[0]).endPoint);
					mHandPoseTemp.fingerAngles[0] -= palmRotationZ;
					mHandPoseTemp.fingerAngles[0] -= DEF_FINGER_ANGLES[0];
					mHandPoseTemp.fingerAngles[0] *= -MathUtil.PRE_180_DIV_PI;
					for (int i = 1, j = mHandPoseTemp.fingerAngles.length; i < j; ++i)
					{
						mHandPoseTemp.fingerAngles[i] = arcTang(mHandDefectsList.get(mFingerIndices[i]).farthestPoint,
																right_model ?
																mHandDefectsList.get(mFingerIndices[i]).endPoint :
																mHandDefectsList.get(mFingerIndices[i]).startPoint);
						mHandPoseTemp.fingerAngles[i] -= palmRotationZ;
						mHandPoseTemp.fingerAngles[i] -= DEF_FINGER_ANGLES[i];
						mHandPoseTemp.fingerAngles[i] *= -MathUtil.PRE_180_DIV_PI;
					}
				}
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
	private int findClosest3Points()
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
		public double fingerAngles[];

		@Override
		public boolean copyFrom(Duplicable obj)
		{
			if (obj.getClass().equals(this.getClass()))
			{
				HandPose tmp = (HandPose) obj;
				render = tmp.render;
				right_model = tmp.right_model;
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
			fingerAngles = new double[5];
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