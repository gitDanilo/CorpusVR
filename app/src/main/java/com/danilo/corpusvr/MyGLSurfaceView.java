package com.danilo.corpusvr;

import android.content.Context;
import android.opengl.GLSurfaceView;

/**
 * Created by Danilo on 3/1/2018.
 */

public class MyGLSurfaceView extends GLSurfaceView
{
	private static final String TAG = "MyGLSurfaceView";
	private final MyGLRenderer mRenderer;

	public MyGLSurfaceView(Context context)
	{
		super(context);
		setEGLContextClientVersion(2);
		setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Maybe depthSize should be 0
		setZOrderMediaOverlay(true);
		mRenderer = new MyGLRenderer();
		setRenderer(mRenderer);
	}
}
