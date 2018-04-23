package com.danilo.corpusvr;

import android.util.Log;

/**
 * Created by Danilo on 3/1/2018.
 */

public class FPSCounter
{
	private static final String TAG = "FPSCounter";
	private static long mStartTime = System.nanoTime();
	private static int mFrames = 0;

	public static void getFPS()
	{
		++mFrames;
		if(System.nanoTime() - mStartTime >= 1000000000)
		{
			Log.d(TAG, "fps: " + mFrames);
			mFrames = 0;
			mStartTime = System.nanoTime();
		}
	}
}