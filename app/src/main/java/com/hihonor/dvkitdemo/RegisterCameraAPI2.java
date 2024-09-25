/*
 * Copyright (c) Honor Device Co., Ltd. 2022-2024. All rights reserved.
 */

package com.hihonor.dvkitdemo;

import static com.hihonor.mcs.connect.devicevirtualization.DVManager.VIRTUAL_CAMERA_SERVICE;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;

import com.hihonor.mcs.connect.devicevirtualization.DVManager;
import com.hihonor.mcs.connect.devicevirtualization.ICaptureCallback;
import com.hihonor.mcs.connect.devicevirtualization.VirtualCameraBufferParams;
import com.hihonor.mcs.connect.devicevirtualization.VirtualCameraManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 摄像头预览界面
 *
 * @since 2022-06-30
 */
public class RegisterCameraAPI2 extends Activity {
    private static final String TAG = "DvKit-RegisterCameraAPI2";

    private Size mPreviewSize;

    private HandlerThread mBackgroundThread;

    private Handler mBackgroundHandler;

    private AutoFitTextureView mTextureView;

    private ImageButton mCaptureButton;

    private CameraDevice mCameraDevice;

    private CameraCaptureSession mPreviewSession;

    private CaptureRequest.Builder mPreviewBuilder;

    private Spinner mFpsSpinner;

    private ArrayAdapter<Range<Integer>> mFpsAdapter = null;

    private VirtualCameraManager mVirtualCameraManager;

    private ImageView mImageView;

    private Context mContext;

    private String mSeqNum;

    private String mCaptureSeqNum;

    private byte[] mData;

    private int mPixFormat;

    private int mSize;

    private BitmapFactory.Options mOpts;

    private int mCaptureResult;

    private Map<String, Object> mCameraParas = new HashMap<>();

    private List<Range<Integer>> mFpsList = new ArrayList<>();

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == 0) {
                Log.i(TAG, "show image");
                showImage();
            }
        }
    };

    private void showImage() {
        if (mSeqNum != null && mSeqNum.equals(mCaptureSeqNum) && mCaptureResult == 0) {
            Log.i(TAG, "set image");
            Optional<Bitmap> captureBitmap = getPicFromBytes(mData, mOpts);
            captureBitmap.ifPresent(bitmap -> mImageView.setImageBitmap(bitmap));
        }
    }

    private ICaptureCallback mCaptureCallback = new ICaptureCallback() {
        @Override
        public void onFrameReceive(VirtualCameraBufferParams virtualCameraBufferParams, int result) {
            Log.i(TAG, "onFrameReceive  result: " + result);
            if (virtualCameraBufferParams == null) {
                Log.e(TAG, "virtualCameraBufferParams is null");
                return;
            }
            mData = virtualCameraBufferParams.getData();
            if (mData == null) {
                Log.i(TAG, "mData is null");
            }
            mPixFormat = virtualCameraBufferParams.getPixFormat();
            mSize = virtualCameraBufferParams.getSize();
            mSeqNum = virtualCameraBufferParams.getSeqNum();
            mCaptureResult = result;
            Log.i(TAG,
                "virtualCameraBufferParams mPixFormat // mSeqNum : " + mPixFormat + " " + mSeqNum + " =?" +
                    mCaptureSeqNum + "mSize: " + mSize);
            Log.i(TAG, "result: " + result);
            Message message = Message.obtain();
            message.what = 0;
            mHandler.sendMessage(message);
        }
    };

    private Optional<Bitmap> getPicFromBytes(byte[] data, BitmapFactory.Options opts) {
        if (data != null) {
            if (opts != null) {
                return Optional.of(BitmapFactory.decodeByteArray(data, 0, data.length, opts));
            } else {
                return Optional.of(BitmapFactory.decodeByteArray(data, 0, data.length));
            }
        }
        return Optional.empty();
    }

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
            mCameraOpenCloseLock.release();
            if (mTextureView != null) {
                Log.i(TAG, "onOpened, width x height = " + mTextureView.getWidth() + " x " + mTextureView.getHeight());
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            finish();
        }
    };

    private CameraCaptureSession.StateCallback mPreviewCaptureCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            mPreviewSession = session;
            updatePreview();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.i(TAG, "onConfigureFailed");
        }
    };

    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            Log.i(TAG, "onSurfaceTextureAvailable: width: " + width + ", height: " + height);
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            Log.i(TAG, "onSurfaceTextureSizeChanged: width: " + width + ", height: " + height);
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        setContentView(R.layout.register_camera_api2);
        initFpsAndResolution();

        mOpts = new BitmapFactory.Options();
        mOpts.inSampleSize = 2;
        mTextureView = findViewById(R.id.texture);
        mCaptureButton = findViewById(R.id.capture);
        mImageView = findViewById(R.id.image_c);
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dealCapture();
            }
        });
    }

    private void initFpsAndResolution() {
        mFpsSpinner = (Spinner)findViewById(R.id.fps);
        mFpsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, mFpsList);
        mFpsSpinner.setAdapter(mFpsAdapter);
        mFpsSpinner.setVisibility(View.VISIBLE);
        mFpsSpinner.setSelection(0);
    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            Log.i(TAG,
                "onResume: mTextureView width: " + mTextureView.getWidth() +
                    ",mTextureView height: " + mTextureView.getHeight());
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closeCamera();
        stopBackgroundThread();
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (mBackgroundThread == null) {
            return;
        }
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("MissingPermission")
    private void openCamera(int width, int height) {
        closeCamera();
        stopBackgroundThread();
        if (isFinishing()) {
            return;
        }
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "Time out waiting to lock camera opening.");
                return;
            }
            CameraManager cameraManager;
            if (getSystemService(Context.CAMERA_SERVICE) instanceof CameraManager) {
                cameraManager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
            } else {
                return;
            }
            String cameraId = chooseCameraId();
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            Range<Integer>[] fps = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            mFpsList.clear();
            for (int i = fps.length - 1; i >= 0; i--) {
                Log.i(TAG, "index: " + i + ", [" + fps[i].getLower() + ", " + fps[i].getUpper() + "]");
                mFpsList.add(fps[i]);
            }
            mFpsAdapter.notifyDataSetChanged();
            mPreviewSize = new Size(1920, 1080);
            Log.i(TAG,
                "openCamera, mPreviewSize width x mPreviewSize height = " + mPreviewSize.getWidth() + " x " +
                    mPreviewSize.getHeight());
            int orientation = getResources().getConfiguration().orientation;
            Log.i(TAG, "orientation is : " + orientation);
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            Log.i(TAG,
                "openCamera, mTextureView width x mTextureView height = " + mTextureView.getWidth() + " x " +
                    mTextureView.getHeight());
            Log.i(TAG, "openCamera, width x height = " + width + " x " + height);
            configureTransform(width, height);
            // step1 1、首先由CameraManager.openCamera方法，打开摄像头，该方法传入三个参数：
            // String CameraId：传入要打开的摄像头的Id
            // CameraDevice.stateCallback：即CameraDevice.stateCallback的实例
            // Handler：为一个句柄，代表执行callback的handler，如果程序希望直接在当前线程中执行callback，则可以将handler参数设为null
            cameraManager.openCamera(cameraId, mStateCallback, null);
        } catch (InterruptedException | CameraAccessException ex) {
            this.finish();
            Toast.makeText(this, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            ex.printStackTrace();
        }
    }

    private String chooseCameraId() {
        return getIntent().getStringExtra("cameraId");
    }

    private String getPropName() {
        return getIntent().getStringExtra("propName");
    }

    private void closeCamera() {
        Log.i(TAG, "closeCamera");
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void startPreview() {
        if (mCameraDevice == null || !mTextureView.isAvailable() || mPreviewSize == null) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Log.i(TAG,
                "startPreview, mPreviewSize width x mPreviewSize height = " + mPreviewSize.getWidth() + " x " +
                    mPreviewSize.getHeight());
            Range<Integer> fpsSel = (Range<Integer>)mFpsSpinner.getSelectedItem();
            Log.i(TAG,
                String.format(
                    Locale.ROOT, "startPreview by fps: left: %d, right: %d", fpsSel.getLower(), fpsSel.getUpper()));
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsSel);
            Surface previewSurface = new Surface(texture);
            mPreviewBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(
                Collections.singletonList(previewSurface), mPreviewCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        Log.i(TAG, "updatePreview");
        if (mCameraDevice == null) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mPreviewBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            Log.i(TAG, "updatePreview:setRepeatingRequest");
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    /**
     * 调整视频流尺寸布局
     *
     * @param viewWidth 布局宽度
     * @param viewHeight 布局高度
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == mTextureView || null == mPreviewSize) {
            return;
        }

        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Log.i(TAG, "rotation is: " + rotation);
        int prewWidth = mPreviewSize.getWidth();
        int prewHeight = mPreviewSize.getHeight();
        float sx = (float)viewWidth / (float)prewWidth;
        float sy = (float)viewHeight / (float)prewHeight;
        Matrix matrix = new Matrix();
        // 第1步:把视频区移动到View区,使两者中心点重合.
        matrix.preTranslate((viewWidth - prewWidth) / 2, (viewHeight - prewHeight) / 2);
        float maxScale = Math.max(sx, sy);
        // 第2步:因为默认视频是fitXY的形式显示的,所以首先要缩放还原回来.
        matrix.preScale(prewWidth / (float)viewWidth, prewHeight / (float)viewHeight);
        // 第3步,等比例放大或缩小,直到视频区的一边超过View一边, 另一边与View的另一边相等.
        // 因为超过的部分超出了View的范围,所以是不会显示的,相当于裁剪了.
        matrix.postScale(maxScale, maxScale, viewWidth / 2, viewHeight / 2);
        mTextureView.setTransform(matrix);
        Log.i(TAG,
            "configureTransform end, width x height = " + mTextureView.getWidth() + " x " + mTextureView.getHeight());
    }

    private void closePreviewSession() {
        Log.i(TAG, "closePreviewSession");
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    /**
     * 拍照
     */
    private void dealCapture() {
        long currentSeqNum = System.currentTimeMillis();
        mCaptureSeqNum = String.valueOf(currentSeqNum);
        if (DVManager.getInstance().getDVService(VIRTUAL_CAMERA_SERVICE) instanceof VirtualCameraManager) {
            mVirtualCameraManager = (VirtualCameraManager)DVManager.getInstance().getDVService(VIRTUAL_CAMERA_SERVICE);
        } else {
            Log.i(TAG, "get VirtualCameraManager error");
            return;
        }
        Resources resources = this.getResources();
        DisplayMetrics dm = resources.getDisplayMetrics();
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;
        Log.i(TAG, "screenWidth: " + screenWidth + " screenHeight: " + screenHeight);
        String comJsonStr = "0_0_" + screenHeight + "_" + screenWidth;
        // x_y_w_h表示从(x,y)的坐标开始拍照长w宽h的区域,南向设备要求w和h必须为16的倍数,开发根据实际使用情况设定
        String jsonStr = "0_0_544_352";
        mCameraParas.put("roi", jsonStr);
        mVirtualCameraManager.capture(mCaptureSeqNum, chooseCameraId(), mCameraParas, mCaptureCallback);
    }
}