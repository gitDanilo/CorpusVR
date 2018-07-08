package org.opencv.android;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import org.opencv.core.Core;

import java.text.DecimalFormat;

public class FpsMeter
{
	private static final String TAG = "FpsMeter";
	private static final int STEP = 10;
	private static final int MAX_FPS = 999999;
	private static final DecimalFormat FPS_FORMAT = new DecimalFormat("0.00");
	Paint mPaint;
	boolean mIsInitialized = false;
	int mWidth = 0;
	int mHeight = 0;
	private long mFramesCounter;
	private double mFrequency;
	private long mPrevFrameTime;
	private String mStrFPS;
	private long mTime;
	private double mFPS;
	private double mAvgFPS;
	private double mMinFPS;
	private double mMaxFPS;

	public void init()
	{
		mFramesCounter = 0;
		mFrequency = Core.getTickFrequency();
		mPrevFrameTime = Core.getTickCount();
		mStrFPS = "";
		mTime = 0;
		mFPS = 0;
		mMinFPS = MAX_FPS;
		mMaxFPS = 0;
		mPaint = new Paint();
		mPaint.setColor(Color.GREEN);
		mPaint.setTextSize(60);
	}

	public void measure()
	{
		if (!mIsInitialized)
		{
			init();
			mIsInitialized = true;
		}
		else
		{
			++mFramesCounter;
			mAvgFPS += (mFPS - mAvgFPS) / mFramesCounter;
			if (mFramesCounter % STEP == 0)
			{
				mTime = Core.getTickCount();
				mFPS = STEP * mFrequency / (mTime - mPrevFrameTime);
				if (mFPS < mMinFPS)
					mMinFPS = mFPS;
				if (mFPS > mMaxFPS)
					mMaxFPS = mFPS;
				mPrevFrameTime = mTime;
				if (mWidth != 0 && mHeight != 0)
				{
					mStrFPS =  Integer.valueOf(mWidth) + "x" + Integer.valueOf(mHeight) + " / " + FPS_FORMAT.format(mFPS) + " FPS / " + FPS_FORMAT.format(mMinFPS) + " MIN. FPS / " + FPS_FORMAT.format(mMaxFPS) + " MAX. FPS";
				}
				else
				{
					mStrFPS = FPS_FORMAT.format(mFPS) + " FPS / " + FPS_FORMAT.format(mAvgFPS) + " AVG. FPS / " + FPS_FORMAT.format(mMinFPS) + " MIN. FPS / " + FPS_FORMAT.format(mMaxFPS) + " MAX. FPS";
				}
			}
		}
	}

	public void setResolution(int width, int height)
	{
		mWidth = width;
		mHeight = height;
	}

	public void draw(Canvas canvas, float offsetx, float offsety)
	{
		Log.d(TAG, mStrFPS);
		canvas.drawText(mStrFPS, offsetx, offsety, mPaint);
	}

}
