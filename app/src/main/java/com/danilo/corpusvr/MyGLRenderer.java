package com.danilo.corpusvr;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.content.ContentValues.TAG;

public class MyGLRenderer implements GLSurfaceView.Renderer
{
	// mMVPMatrix is an abbreviation for "Model View Projection Matrix"
	private final float[] mMVPMatrix = new float[16];
	private final float[] mProjectionMatrix = new float[16];
	private final float[] mViewMatrix = new float[16];
	private final float[] mRotationMatrix = new float[16];
	private float mAngle;

	private Triangle mTriangle;

	public static int loadShader(int type, String shaderCode)
	{
		int shader = GLES20.glCreateShader(type);
		GLES20.glShaderSource(shader, shaderCode);
		GLES20.glCompileShader(shader);

		return shader;
	}

	/**
	 * Utility method for debugging OpenGL calls. Provide the name of the call
	 * just after making it:
	 * <p>
	 * <pre>
	 * mColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor");
	 * MyGLRenderer.checkGlError("glGetUniformLocation");</pre>
	 * <p>
	 * If the operation is not successful, the check throws an error.
	 *
	 * @param glOperation - Name of the OpenGL call to check.
	 */
	public static void checkGlError(String glOperation)
	{
		int error;
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR)
		{
			Log.e(TAG, glOperation + ": glError " + error);
			throw new RuntimeException(glOperation + ": glError " + error);
		}
	}

	@Override
	public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig)
	{
		mTriangle = new Triangle();
		GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
	}

	@Override
	public void onSurfaceChanged(GL10 gl10, int width, int height)
	{
		// Adjust the viewport based on geometry changes,
		// such as screen rotation
		GLES20.glViewport(0, 0, width, height);

		float ratio = (float) width / height;

		// this projection matrix is applied to object coordinates
		// in the onDrawFrame() method
		Matrix.frustumM(mProjectionMatrix, 0, -ratio, ratio, -1, 1, 3, 7);
	}

	@Override
	public void onDrawFrame(GL10 gl10)
	{
		float[] scratch = new float[16];

		// Draw background color
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

		// Set the camera position (View matrix)
		Matrix.setLookAtM(mViewMatrix, 0, 0, 0, -3, 0f, 0f, 0f, 0f, 1.0f, 0.0f);

		// Calculate the projection and view transformation
		Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);

		// Create a rotation for the triangle

		// Use the following code to generate constant rotation.
		// Leave this code out when using TouchEvents.
		long time = SystemClock.uptimeMillis() % 4000L;
		mAngle = 0.090f * ((int) time);

		Matrix.setRotateM(mRotationMatrix, 0, mAngle, 0, 0, 1.0f);

		// Combine the rotation matrix with the projection and camera view
		// Note that the mMVPMatrix factor *must be first* in order
		// for the matrix multiplication product to be correct.
		Matrix.multiplyMM(scratch, 0, mMVPMatrix, 0, mRotationMatrix, 0);

		mTriangle.draw(scratch);

		FPSCounter.getFPS();
	}

	/**
	 * Returns the rotation angle of the triangle shape (mTriangle).
	 *
	 * @return - A float representing the rotation angle.
	 */
	public float getAngle()
	{
		return mAngle;
	}

	/**
	 * Sets the rotation angle of the triangle shape (mTriangle).
	 */
	public void setAngle(float angle)
	{
		mAngle = angle;
	}

}
