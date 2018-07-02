package com.danilo.corpusvr;

import android.content.Context;
import android.view.MotionEvent;

import com.example.rajawali.Object3D;
import com.example.rajawali.lights.DirectionalLight;
import com.example.rajawali.loader.LoaderOBJ;
import com.example.rajawali.loader.ParsingException;
import com.example.rajawali.math.Matrix4;
import com.example.rajawali.renderer.Renderer;

public class MyRenderer extends Renderer implements CameraProjectionListener
{
	private static final String TAG = "MyRenderer";

	private Context mContext;

	private int mScreenWidth = -1;
	private int mScreenHeight = -1;

	// Tracking information
	private int mBoneFingerIndex[];
	private HandTracking.HandPose mHandPose;
	private HandTracking mHandTracking;
	private long mBadFrames;
	private static final long MAX_BAD_FRAMES = 8;
	private static final double REF_FINGER_PTS[][] = new double[][]{{-0.042,  0.012},
																	{-0.025, -0.025},
																	{-0.004, -0.032},
																	{ 0.010, -0.030},
																	{ 0.030, -0.019}};
	private static final String FINGER_BONE_NAMES[][] = new String[][]{{"ANATOMY---HAND-AND-ARM-BONES.022", "ANATOMY---HAND-AND-ARM-BONES.016", ""                                },
																	   {"ANATOMY---HAND-AND-ARM-BONES.018", "ANATOMY---HAND-AND-ARM-BONES.013", "ANATOMY---HAND-AND-ARM-BONES.015"},
																	   {"ANATOMY---HAND-AND-ARM-BONES.019", "ANATOMY---HAND-AND-ARM-BONES.014", "ANATOMY---HAND-AND-ARM-BONES"    },
																	   {"ANATOMY---HAND-AND-ARM-BONES.020", "ANATOMY---HAND-AND-ARM-BONES.017", "ANATOMY---HAND-AND-ARM-BONES.026"},
																	   {"ANATOMY---HAND-AND-ARM-BONES.009", "ANATOMY---HAND-AND-ARM-BONES.011", "ANATOMY---HAND-AND-ARM-BONES.005"}};

	// Matrices
	//	private double[] mModelMatF;
	//	private Matrix4 mMVPInvMat;
	private Matrix4 mTempTransf;
	private Matrix4 mProjMat;
	private Matrix4 mTempViewMat;
	private Matrix4 mTempModelMat;
	private Matrix4 mPalmModeViewMat;
	private Matrix4 mFingerModelViewMat[];

	// Scene objects
	private DirectionalLight mDirectionalLight;
//	private Sphere mSphere;
	private Object3D mLeftHandModel;
//	private Object3D mRightHandModel;

	// https://stackoverflow.com/questions/7692988/opengl-math-projecting-screen-space-to-world-space-coords
	// Converts the coordinates from OpenCV space to OpenGL's (Used for 2D perspective)
//	private void screenToWorld(Point point)
//	{
//		if (mScreenWidth == -1 || mScreenHeight == -1)
//			return;
//
//		if (mMVPInvMat == null)
//		{
//			mMVPInvMat = new Matrix4(mProjMat);
//			mMVPInvMat.multiply(getCurrentCamera().getViewMatrix()).inverse();
//
////			mMVPInvMat = new float[16];
////			Matrix.multiplyMM(mMVPInvMat, 0, mProjMat.getFloatValues(), 0, getCurrentCamera().getViewMatrix().getFloatValues(), 0);
////			Matrix.invertM(mMVPInvMat, 0, mMVPInvMat, 0);
//		}
//
//		mModelMatF[12] = (2.0 * (point.x / mScreenWidth)) - 1.0;
//		mModelMatF[13] = 1.0 - (2.0 * (point.y / mScreenHeight));
//		mModelMatF[14] = /*2.0 * 0.5*//*Z*//* - 1.0*/0;
//		mModelMatF[15] = 1.0;
//
//		Matrix.multiplyMV(mModelMatF, 12, mMVPInvMat.getDoubleValues(), 0, mModelMatF, 12);
//
//		mModelMatF[12] /= mModelMatF[15];
//		mModelMatF[13] /= mModelMatF[15];
//		mModelMatF[14] /= mModelMatF[15];
//		mModelMatF[15] = 1;
//	}

	private void calculateFingerTransf(Matrix4 palmPose)
	{
		for (int i = 0; i < 5; ++i)
		{
			// Carrega identidade
			mTempModelMat.identity();

			// Translada para a origem
			mTempTransf.identity().setTranslation(- REF_FINGER_PTS[i][0], - REF_FINGER_PTS[i][1], 0);
			mTempModelMat.leftMultiply(mTempTransf);

			// Rotaciona no eixo Z
			mTempTransf.identity().setRotate(0,0,1, mHandPose.fingerAngles[i]);
			mTempModelMat.leftMultiply(mTempTransf);

			// Translada de volta
			mTempTransf.identity().setTranslation(REF_FINGER_PTS[i][0], REF_FINGER_PTS[i][1], 0);
			mTempModelMat.leftMultiply(mTempTransf);

			// Aplica as transformações da palma
			mTempViewMat.identity().inverse();
			mTempViewMat.leftMultiply(palmPose);

			// Gera a ModelView do dedo(i)
			mFingerModelViewMat[i].setAll(mTempViewMat).multiply(mTempModelMat);
		}
	}

	private int getFingerIndex(String modelName)
	{
		int i, j;
		for (i = 0; i < FINGER_BONE_NAMES.length; ++i)
		{
			for (j = 0; j < FINGER_BONE_NAMES[0].length; ++j)
			{
				if (modelName.equals(FINGER_BONE_NAMES[i][j]))
				{
					return i;
				}
			}
		}
		return -1;
	}

	MyRenderer(Context context, HandTracking Status)
	{
		super(context);
		mContext = context;
		mHandTracking = Status;
	}

	@Override
	protected void initScene()
	{
		//  http://www.opengl-tutorial.org/beginners-tutorials/tutorial-3-matrices/#an-introduction-to-matrices
		//getCurrentCamera().getModelMatrix().identity();
		//getCurrentCamera().getViewMatrix().identity();

		//		mModelMatF = new double[16];

		// Inicializa a matriz de projeção da câmera
		if (mProjMat != null)
			getCurrentCamera().setProjectionMatrix(mProjMat);

		// Inicializa as matrizes temporárias
		mTempTransf = new Matrix4();
		mTempViewMat = new Matrix4();
		mTempModelMat = new Matrix4();

		// Inicializa as matrizes ModelView de cada dedo
		mFingerModelViewMat = new Matrix4[5];
		mFingerModelViewMat[0] = new Matrix4();
		mFingerModelViewMat[1] = new Matrix4();
		mFingerModelViewMat[2] = new Matrix4();
		mFingerModelViewMat[3] = new Matrix4();
		mFingerModelViewMat[4] = new Matrix4();

		// Inicializa a matriz ModelView da palma
		mPalmModeViewMat = new Matrix4();

		// Inicializa a luz da cena
		mDirectionalLight = new DirectionalLight(4, 4, 4);
		mDirectionalLight.setColor(1.0f, 1.0f, 1.0f);
		mDirectionalLight.setPower(1.25f);
		getCurrentScene().addLight(mDirectionalLight);

		// Debug object basic material (disable light so it looks 2D)
//		Material material = new Material();
//		material.enableLighting(true);
//		material.setDiffuseMethod(new DiffuseMethod.Lambert());
//		material.setColor(Color.RED);

		// Debug object
//		mSphere = new Sphere(0.002f, 24, 24);
//		mSphere.setMaterial(material);
//		getCurrentScene().addChild(mSphere);
//		mSphere.setUseCustomModelView(true);
//		mSphere.setVisible(true);

		// Carrega o arquivo .OBJ e .MTL
		LoaderOBJ loaderOBJ = new LoaderOBJ(this, R.raw.lefthand_obj);
		try
		{
			loaderOBJ.parse();
		}
		catch (ParsingException e)
		{
			e.printStackTrace();
		}

		// Obtém a instância do objeto carregado
		mLeftHandModel = loaderOBJ.getParsedObject();

		// Adiciona o objeto na cena
		getCurrentScene().addChild(mLeftHandModel);

		int i, j = mLeftHandModel.getNumChildren();
		mBoneFingerIndex = new int[j];
		for (i = 0; i < j; ++i)
		{
			// Permite que seja utilizada uma matriz customizada
			mLeftHandModel.getChildAt(i).setUseCustomModelView(true);

			// Obtêm o índice do dedo
			mBoneFingerIndex[i] = getFingerIndex(mLeftHandModel.getChildAt(i).getName());
		}

		// Define o modelo como invisível na cena
		mLeftHandModel.setVisible(false);

		// Inicializa o contador
		mBadFrames = MAX_BAD_FRAMES;

		//	mLeftHandModel.setMaterial(material); Its possible to remove the loaded material and change for a new one: https://github.com/Rajawali/Rajawali/issues/2015
	}

	@Override
	protected void onRender(long ellapsedRealtime, double deltaTime)
	{
		super.onRender(ellapsedRealtime, deltaTime);

		// Lê as informações do objeto compartilhado
		mHandPose = mHandTracking.getObjStatus();

		if (mHandPose.render || mBadFrames < MAX_BAD_FRAMES)
		{
			// Atualiza o valor do contador
			if (mHandPose.render)
				mBadFrames = 0;
			else
				++mBadFrames;

			// Muda a visibilidade do objeto
			if (!mLeftHandModel.isVisible())
				mLeftHandModel.setVisible(true);

			// Atualiza a ModelView da palma com a nova pose
			mPalmModeViewMat.setAll(mHandPose.pose);

			// Calcula as ModelView's dos dedos
			calculateFingerTransf(mPalmModeViewMat);

			// Atualiza todos os objetos do modelo 3D com a nova ModelView
			for (int i = 0, j = mLeftHandModel.getNumChildren(), fingerIndex; i < j; ++i)
			{
				fingerIndex = mBoneFingerIndex[i];
				if (fingerIndex != -1) // Objeto de algum dedo
				{
					mLeftHandModel.getChildAt(i).getModelViewMatrix().setAll(mFingerModelViewMat[fingerIndex]);
				}
				else // Objeto da palma
				{
					mLeftHandModel.getChildAt(i).getModelViewMatrix().setAll(mPalmModeViewMat);
				}
			}
		}
		else
		{
			if (mLeftHandModel.isVisible())
				mLeftHandModel.setVisible(false);
		}
	}

	@Override
	public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset)
	{

	}

	@Override
	public void onTouchEvent(MotionEvent event)
	{

	}

	@Override
	public void onProjectionChanged(int width, int height, float[] projectionMat)
	{
		mScreenWidth = width;
		mScreenHeight = height;

//		mMVPInvMat = null;

		// Converte de float[] para Matrix4
		if (mProjMat == null)
			mProjMat = new Matrix4(projectionMat);
		else
			mProjMat.setAll(projectionMat);

		// Caso a cena já tenha sido inicializada, atualiza a matriz
		if (mSceneInitialized)
			getCurrentCamera().setProjectionMatrix(mProjMat);
	}
}