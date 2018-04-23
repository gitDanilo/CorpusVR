package com.danilo.corpusvr;

import java.util.concurrent.Semaphore;

/**
 * Created by Danilo on 3/2/2018.
 */

public class HandTracking
{
	private Semaphore mSemaphore;
	private HandStatus mHandStatus;

	public static class HandStatus
	{
		public boolean mRender;
		public float[] mPose;

		public HandStatus()
		{
			mRender = false;
			mPose = new float[16];
		}
	}

	public HandTracking()
	{
		mHandStatus = new HandStatus();
		mSemaphore = new Semaphore(1);
	}


	public HandStatus getObjStatus()
	{
		HandStatus result = null;
		try
		{
			mSemaphore.acquire();
			result = mHandStatus;
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

	public void setObjStatus(HandStatus Status)
	{
		try
		{
			mSemaphore.acquire();
			mHandStatus = Status;
			mSemaphore.release();
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}
}