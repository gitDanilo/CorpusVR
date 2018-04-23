package com.danilo.corpusvr;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class Triangle
{
	private final int mProgram;
	private final String vertexShaderCode =
			// This matrix member variable provides a hook to manipulate
			// the coordinates of the objects that use this vertex shader
			"uniform mat4 uMVPMatrix;" +
			"attribute vec4 vPosition;" +
			"void main() {" +
			// the matrix must be included as a modifier of gl_Position
			// Note that the uMVPMatrix factor *must be first* in order
			// for the matrix multiplication product to be correct.
			"  gl_Position = uMVPMatrix * vPosition;" +
			"}";
	private final String fragmentShaderCode =
			"precision mediump float;" +
			"uniform vec4 vColor;" +
			"void main() {" +
			"  gl_FragColor = vColor;" +
			"}";
	private final int vertexCount = fCoords.length / COORDS_PER_VERTEX;
	private final int vertexStride = COORDS_PER_VERTEX * 4;
	private static final int COORDS_PER_VERTEX = 3;
	private static float fCoords[] = {0.0f, 0.622008459f, 0.0f, // top
							  -0.5f, -0.311004243f, 0.0f, // bottom left
							  0.5f, -0.311004243f, 0.0f  // bottom right
	};
	private FloatBuffer vertexBuffer;
	private float color[] = {0.63671875f, 0.76953125f, 0.22265625f, 1.0f};
	private int mPositionHandle;
	private int mColorHandle;
	private int mVertexShader;
	private int mFragmentShader;
	private int mMVPMatrixHandle;

	public Triangle()
	{
		ByteBuffer bBuffer = ByteBuffer.allocateDirect(fCoords.length * 4);
		bBuffer.order(ByteOrder.nativeOrder());
		vertexBuffer = bBuffer.asFloatBuffer();
		vertexBuffer.put(fCoords);
		vertexBuffer.position(0);

		mVertexShader = MyGLRenderer.loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
		mFragmentShader = MyGLRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

		mProgram = GLES20.glCreateProgram(); // GLES20.glDeleteProgram(mProgram);

		GLES20.glAttachShader(mProgram, mVertexShader);
		GLES20.glAttachShader(mProgram, mFragmentShader);

		GLES20.glLinkProgram(mProgram);

		GLES20.glDetachShader(mProgram, mVertexShader);
		GLES20.glDetachShader(mProgram, mFragmentShader);

		GLES20.glDeleteShader(mVertexShader);
		GLES20.glDeleteShader(mFragmentShader);
	}

	public void draw(float[] mvpMatrix)
	{
		GLES20.glUseProgram(mProgram);

		mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
		GLES20.glEnableVertexAttribArray(mPositionHandle);
		GLES20.glVertexAttribPointer(mPositionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, vertexStride, vertexBuffer);

		mColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor");
		GLES20.glUniform4fv(mColorHandle, 1, color, 0);

		// get handle to shape's transformation matrix
		mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
		MyGLRenderer.checkGlError("glGetUniformLocation");

		// Apply the projection and view transformation
		GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0);
		MyGLRenderer.checkGlError("glUniformMatrix4fv");

		GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

		GLES20.glDisableVertexAttribArray(mPositionHandle);
	}
}
