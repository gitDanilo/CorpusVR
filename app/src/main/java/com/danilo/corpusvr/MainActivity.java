package com.danilo.corpusvr;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;
import org.rajawali3d.view.ISurface;
import org.rajawali3d.view.SurfaceView;

public class MainActivity extends AppCompatActivity
{
	private static final String TAG = "MainActivity";

	// Bundle keys
	private static final String CAMERA_PERMISSION_KEY = "CAMERA_INDEX";
	private static final String OPENCV_LOADED_KEY = "OPENCV_INDEX";

	private static final int REQUEST_CAMERA_PERMISSION = 200;

	// Android layout
	private FrameLayout mFrame;
	private MyJavaCameraView mJavaCameraView;
	private MyRenderer mRenderer;
	private SurfaceView mSurfaceView;

	private HandTracking mHandTracking;

	private boolean mCameraPermission;
	private boolean mOpenCVLoaded;

	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this)
	{
		@Override
		public void onManagerConnected(int status)
		{
			switch (status)
			{
				case BaseLoaderCallback.SUCCESS:
					mOpenCVLoaded = true;
					if (mJavaCameraView != null)
						mJavaCameraView.enableView();
					break;
				default:
					super.onManagerConnected(status);
			}
		}
	};

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		outState.putBoolean(CAMERA_PERMISSION_KEY, mCameraPermission);
		outState.putBoolean(OPENCV_LOADED_KEY, mOpenCVLoaded);
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null)
		{
			mOpenCVLoaded = savedInstanceState.getBoolean(OPENCV_LOADED_KEY, false);
			mCameraPermission = savedInstanceState.getBoolean(CAMERA_PERMISSION_KEY, false);
		}
		else
		{
			mOpenCVLoaded = false;
			mCameraPermission = false;
		}

		// Check For Camera Permission
		if (!mCameraPermission && ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
			ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
		else
			mCameraPermission = true;

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null)
			actionBar.setDisplayHomeAsUpEnabled(false);

		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

		// Create FrameLayout
		mFrame = new FrameLayout(this);
		mFrame.setBackgroundColor(Color.BLACK);
		mFrame.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
		setContentView(mFrame);

		// Create HandTracking Object
		mHandTracking = new HandTracking();

		// Create OpenGL Renderer
		mRenderer = new MyRenderer(this, mHandTracking);

		// Camera View
		mJavaCameraView = new MyJavaCameraView(this, 99, mHandTracking, mRenderer);
		mJavaCameraView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
		mJavaCameraView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
		mFrame.addView(mJavaCameraView);

		// Rajawali Surface View
		mSurfaceView = new SurfaceView(this);
		mSurfaceView.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
		mSurfaceView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

		mSurfaceView.setZOrderMediaOverlay(true);
		mSurfaceView.getHolder().setFormat(PixelFormat.TRANSPARENT);
		mSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);

		mSurfaceView.setTransparent(true);
		mSurfaceView.setRenderMode(ISurface.RENDERMODE_WHEN_DIRTY);
		mSurfaceView.setAntiAliasingMode(ISurface.ANTI_ALIASING_CONFIG.MULTISAMPLING);
		mSurfaceView.setFrameRate(60);
		mFrame.addView(mSurfaceView);

		mSurfaceView.setSurfaceRenderer(mRenderer);
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		View decorView = getWindow().getDecorView();
		decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
										| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
										| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
										| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
										| View.SYSTEM_UI_FLAG_FULLSCREEN
										| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

		if (mCameraPermission && !mOpenCVLoaded)
		{
			if (OpenCVLoader.initDebug())
				mLoaderCallback.onManagerConnected(BaseLoaderCallback.SUCCESS);
			else
				OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
		}
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		if (mJavaCameraView != null)
			mJavaCameraView.disableView();
	}

	@Override
	protected void onDestroy()
	{
		if (mJavaCameraView != null)
			mJavaCameraView.disableView();
		super.onDestroy();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == REQUEST_CAMERA_PERMISSION)
		{
			for (int i = 0; i < permissions.length; ++i)
			{
				if (permissions[i].equals(Manifest.permission.CAMERA))
				{
					if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
					{
						mCameraPermission = true;
						if (OpenCVLoader.initDebug())
							mLoaderCallback.onManagerConnected(BaseLoaderCallback.SUCCESS);
						else
							OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, this, mLoaderCallback);
					}
				}
			}
		}
	}
}