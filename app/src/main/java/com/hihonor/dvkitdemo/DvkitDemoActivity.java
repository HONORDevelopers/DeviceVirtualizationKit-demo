/*
 * Copyright (c) Honor Device Co., Ltd. 2022-2024. All rights reserved.
 */

package com.hihonor.dvkitdemo;

import static com.hihonor.mcs.connect.devicevirtualization.DVCapability.CAMERA;
import static com.hihonor.mcs.connect.devicevirtualization.DVManager.DEVICE_MANAGER_SERVICE;
import static com.hihonor.mcs.connect.devicevirtualization.DVManager.VIRTUAL_CAMERA_SERVICE;
import static com.hihonor.mcs.connect.devicevirtualization.ObserverEventType.VIRTUALDEVICE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;

import com.hihonor.dmsdpsdk.DMSDPConfig;
import com.hihonor.mcs.connect.devicevirtualization.DVCapability;
import com.hihonor.mcs.connect.devicevirtualization.DVManager;
import com.hihonor.mcs.connect.devicevirtualization.DVVersionUtils;
import com.hihonor.mcs.connect.devicevirtualization.DeviceManager;
import com.hihonor.mcs.connect.devicevirtualization.DeviceType;
import com.hihonor.mcs.connect.devicevirtualization.EventType;
import com.hihonor.mcs.connect.devicevirtualization.ICaptureCallback;
import com.hihonor.mcs.connect.devicevirtualization.IDVInitCallback;
import com.hihonor.mcs.connect.devicevirtualization.IDeviceSubscribeCallback;
import com.hihonor.mcs.connect.devicevirtualization.IDiscoveryCallback;
import com.hihonor.mcs.connect.devicevirtualization.ReturnCode;
import com.hihonor.mcs.connect.devicevirtualization.VirtualCamera;
import com.hihonor.mcs.connect.devicevirtualization.VirtualCameraBufferParams;
import com.hihonor.mcs.connect.devicevirtualization.VirtualCameraManager;
import com.hihonor.mcs.connect.devicevirtualization.VirtualDevice;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Demo基本功能操作界面
 *
 * @since 2022-06-30
 */
public class DvkitDemoActivity extends Activity {
    private static final String TAG = "DvKit-DvKitDemo";

    private static final int MSG_SHOW_IMAGE = 0;

    private static final int MSG_SHOW_TEXT = 1;

    private DeviceManager mVirtualDeviceManager;

    private VirtualCameraManager mVirtualCameraManager;

    private IDVInitCallback mDvInitCallback;

    private String mDeviceId;

    private String mDeviceName;

    private Button mDiscoveryButton;

    private Button mRefreshButton;

    private Button mDeInitButton;

    private Button mEnableDeviceButton;

    private Button mDisableDeviceButton;

    private Button mCaptureButton;

    private Button mTestCameraButton;

    private Button mTestParaErrorButton;

    private Button mTestDeviceError;

    private ImageView mImageView;

    private TextView mTextView;

    private Context mContext;

    private BitmapFactory.Options mOpts;

    private Map<String, Object> mCameraParas = new HashMap<>();

    private String mCaptureSeqNum;

    private String mReceiveCapSeqNum;

    private byte[] mReceiveCapData;

    private int mReceiveCapPixFormat;

    private int mReceiveCapsize;

    private int mReceiveCapResult;

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (msg.what == MSG_SHOW_IMAGE) {
                Log.i(TAG, "show image");
                showImage();
            } else if (msg.what == MSG_SHOW_TEXT) {
                Log.i(TAG, "show text");
                showText(msg);
            } else {
                Log.i(TAG, "not handle");
            }
        }
    };

    /**
     * 图片信息回调，获取返回的图片信息
     */
    private ICaptureCallback mCaptureCallback = new ICaptureCallback() {
        /**
         * 回调的图片信息
         * API: getData getPixFormat getSize getSeqNum
         *
         * @param virtualCameraBufferParams 图像信息
         * @param result 拍照结果
         */
        @Override
        public void onFrameReceive(VirtualCameraBufferParams virtualCameraBufferParams, int result) {
            // 异常时数据为空，注意判空
            if (virtualCameraBufferParams == null) {
                Log.e(TAG, "virtualCameraBufferParams is null");
                sendMessageToHandler(MSG_SHOW_TEXT,
                    "回调照片信息为空! \n"
                        + "mReceiveCapResult: " + result);
                return;
            }
            // 拍照结果值，为0表示拍照成功，非0时请参照错误码查看原因
            mReceiveCapResult = result;
            // 获取图像数据，可进行相应处理
            mReceiveCapData = virtualCameraBufferParams.getData();
            // 图片格式
            mReceiveCapPixFormat = virtualCameraBufferParams.getPixFormat();
            // 图片大小
            mReceiveCapsize = virtualCameraBufferParams.getSize();
            // 图片标识
            mReceiveCapSeqNum = virtualCameraBufferParams.getSeqNum();
            Log.i(TAG,
                "virtualCameraBufferParams mReceiveCapPixFormat // mReceiveCapSeqNum : " + mReceiveCapPixFormat + " " +
                    mReceiveCapSeqNum + " =?" + mCaptureSeqNum + " mReceiveCapsize: " + mReceiveCapsize +
                    " mReceiveCapResult: " + mReceiveCapResult);
            sendMessageToHandler(MSG_SHOW_TEXT,
                "回调照片信息： \n"
                    + " capSeqNum: " + mReceiveCapSeqNum + "\n capPixFormat: " + mReceiveCapPixFormat +
                    "\n capsize: " + mReceiveCapsize + "\n capResult: " + mReceiveCapResult);
            sendMessageToHandler(MSG_SHOW_IMAGE, null);
        }
    };

    /**
     * 设备和设备服务状态监听
     */
    private IDeviceSubscribeCallback mObserver = new IDeviceSubscribeCallback() {
        @Override
        public void onDeviceConnectStateChanged(String deviceId, int state) {
            switch (state) {
                case EventType.EVENT_DEVICE_CONNECT:
                    sendMessageToHandler(MSG_SHOW_TEXT, "连接状态: 设备连接成功  状态值: " + state);
                    break;
                case EventType.EVENT_DEVICE_DISCONNECT:
                    sendMessageToHandler(MSG_SHOW_TEXT, "连接状态: 设备断开  状态值: " + state);
                    break;
                case EventType.EVENT_DEVICE_CONNECT_FALIED:
                    sendMessageToHandler(MSG_SHOW_TEXT, "连接状态: 设备连接失败  状态值: " + state);
                    break;
                case EventType.EVENT_DEVICE_BUSY:
                    sendMessageToHandler(MSG_SHOW_TEXT, "连接状态: 设备忙碌  状态值: " + state);
                    break;
                default:
                    Log.e(TAG, "state is not match the state is " + state);
                    break;
            }
            Log.i(TAG, "device id and connect state : " + deviceId + " " + state);
        }

        @Override
        public void onDeviceCapabilityStateChanged(String deviceId, DVCapability dvCapability, int state) {
            Log.i(TAG, "device id and connect state : " + deviceId + " " + state);
            switch (state) {
                case DMSDPConfig.EVENT_DEVICE_SERVICE_UPDATE:
                    sendMessageToHandler(MSG_SHOW_TEXT, "服务状态: 设备服务状态更新  状态值: " + state);
                    break;
                case EventType.EVENT_DEVICE_CAPABILITY_ENABLE:
                    sendMessageToHandler(MSG_SHOW_TEXT, "服务状态: 设备能力使能成功  状态值: " + state);
                    break;
                case EventType.EVENT_DEVICE_CAPABILITY_DISABLE:
                    sendMessageToHandler(MSG_SHOW_TEXT, "服务状态: 设备能力失能成功  状态值: " + state);
                    break;
                case EventType.EVENT_DEVICE_CAPABILITY_ABNORMAL:
                    sendMessageToHandler(MSG_SHOW_TEXT, "服务状态: 设备能力使能异常  状态值: " + state);
                    break;
                case EventType.EVENT_DEVICE_CAPABILITY_BUSY:
                    sendMessageToHandler(MSG_SHOW_TEXT, "服务状态: 设备能力忙碌  状态值: " + state);
                    break;
                default:
                    Log.e(TAG, "state is not match the state is " + state);
                    break;
            }
        }
    };

    /**
     * 设备发现回调，保存设备信息
     */
    private final IDiscoveryCallback mDiscoveryCallback = new IDiscoveryCallback() {
        @Override
        public void onFound(VirtualDevice virtualDevice) {
            if (virtualDevice == null) {
                return;
            }
            // 设备标识，连接使能对应虚拟化设备
            mDeviceId = virtualDevice.getDeviceId();
            // 设备名称，可用于设备显示
            mDeviceName = virtualDevice.getDeviceName();
            // 设备类型
            String deviceType = virtualDevice.getDeviceType();
            // 筛选所需设备类型
            if (deviceType.equals(DeviceType.DEVICE_TYPE_CAMERA)) {
                sendMessageToHandler(MSG_SHOW_TEXT, "发现了摄像头设备 ");
            } else if (deviceType.equals(DeviceType.DEVICE_TYPE_DESKLAMP)) {
                sendMessageToHandler(MSG_SHOW_TEXT, "发现了台灯设备 ");
            } else {
                Log.i(TAG, "unknown device");
            }
            // 每次回调一个虚拟设备，开发者可以在此设置筛选机制或展示设备列表等，使用户操作到相应的目标虚拟设备
            sendMessageToHandler(MSG_SHOW_TEXT,
                "deviceId: " + mDeviceId + "\n deviceName: " + mDeviceName + "\n deviceType: " + deviceType);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dvkitdemo);
        mContext = this;
        initView();
        initDVKitService();
        mOpts = new BitmapFactory.Options();
        mOpts.inSampleSize = 2;
    }

    /**
     * 初始化硬件服务跨设备共享能力
     * API: init
     */
    private void initDVKitService() {
        mDvInitCallback = new IDVInitCallback() {
            @Override
            public void onInited() {
                Log.i(TAG, "service bind success");
                sendMessageToHandler(MSG_SHOW_TEXT, "service bind success");
                onDvInitSuccess();
            }

            @Override
            public void onInitFailed(int i) {
                sendMessageToHandler(MSG_SHOW_TEXT, "service bind fail");
            }

            @Override
            public void onDeinited() {
                sendMessageToHandler(MSG_SHOW_TEXT, "service unbind success");
            }

            @Override
            public void onDeinitFailed(int i) {
                sendMessageToHandler(MSG_SHOW_TEXT, "service unbind fail");
            }
        };
        int initResult = DVManager.getInstance().init(getApplicationContext(), mDvInitCallback);
        // 判断返回值是否为ReturnCode.SUCCESS(即0)，注意若返回值为ReturnCode.ERROR_INIT_ALREADY_REGISTERT(-3)，
        // 表示已经进行过初始化操作且未调用deInit()销毁服务，无需重复执行初始化，且此时不会回调onInited()
        Log.i(TAG, "init result " + initResult);
        if (initResult != ReturnCode.SUCCESS) {
            // 初始化异常处理
            Log.w(TAG, "init error");
        }
    }

    /**
     * 服务绑定成功后初始化
     * API: getInstance getDVService subscribeDeviceStatus
     */
    private void onDvInitSuccess() {
        // 初始化成功后，可获取SDK版本号和服务端版本号。
        if (!isSystemSupport()) {
            return;
        }
        // 获取设备管理DeviceManager和设备虚拟相机能力管理VirtualCameraManager的对象。
        mVirtualDeviceManager = (DeviceManager)DVManager.getInstance().getDVService(DEVICE_MANAGER_SERVICE);
        mVirtualCameraManager = (VirtualCameraManager)DVManager.getInstance().getDVService(VIRTUAL_CAMERA_SERVICE);
        // 订阅虚拟设备的连接状态和能力状态事件消息。
        mVirtualDeviceManager.subscribeDeviceStatus(EnumSet.of(VIRTUALDEVICE), mObserver);
    }

    /**
     * 初始化界面view
     */
    private void initView() {
        mEnableDeviceButton = (Button)findViewById(R.id.device_enable);
        mDisableDeviceButton = (Button)findViewById(R.id.device_disable);
        mDiscoveryButton = (Button)findViewById(R.id.device_discover);
        mRefreshButton = (Button)findViewById(R.id.device_refresh);
        mDeInitButton = (Button)findViewById(R.id.deInit);
        mCaptureButton = (Button)findViewById(R.id.registerCamera);
        mTestCameraButton = (Button)findViewById(R.id.testCamera);
        mTestParaErrorButton = (Button)findViewById(R.id.testParaError);
        mTestDeviceError = (Button)findViewById(R.id.testDeviceError);
        mImageView = (ImageView)findViewById(R.id.myimage);
        mTextView = findViewById(R.id.textView);
        mTextView.setMovementMethod(ScrollingMovementMethod.getInstance());
        mTestCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessageToHandler(MSG_SHOW_TEXT, "开始拍照测试");
                dealTestCapture();
            }
        });
        mEnableDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessageToHandler(MSG_SHOW_TEXT, "开始使能设备");
                dealEnableDeviceStatusEvent();
            }
        });
        mDisableDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessageToHandler(MSG_SHOW_TEXT, "开始去使能设备");
                dealDisableDeviceStatusEvent();
            }
        });
        mRefreshButton.setEnabled(false);
        mDiscoveryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessageToHandler(MSG_SHOW_TEXT, "开始发现设备");
                mVirtualDeviceManager.startDiscovery(mDiscoveryCallback);
            }
        });
        mDeInitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dealDeInitDeviceEvent();
                Log.i(TAG, "设备服务结束");
                sendMessageToHandler(MSG_SHOW_TEXT, "设备服务结束");
            }
        });
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "打开摄像头拍照");
                sendMessageToHandler(MSG_SHOW_TEXT, "打开摄像头拍照");
                openVirtualCamera();
            }
        });
        mTestParaErrorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG, "拍照参数错误测试");
                sendMessageToHandler(MSG_SHOW_TEXT, "拍照参数错误测试");
                dealTestParaErrorCapture();
            }
        });
        mTestDeviceError.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, "台灯错误测试");
                sendMessageToHandler(MSG_SHOW_TEXT, "台灯错误测试");
                dealTestDeviceError();
            }
        });
    }

    /**
     * 测试拍照接口功能
     * API: getVirtualCameraList capture getCameraId
     */
    private void dealTestCapture() {
        Resources resources = this.getResources();
        DisplayMetrics dm = resources.getDisplayMetrics();
        int screenWidth = dm.widthPixels;
        int screenHeight = dm.heightPixels;
        String comJsonStr = "0_0_" + screenHeight + "_" + screenWidth;
        // x_y_w_h表示从(x,y)的坐标开始拍照宽w高h的区域,当前南向设备要求w和h必须为16的倍数，根据实际情况设置
        String jsonStr = "0_0_544_352";
        startCapture(jsonStr);
    }

    /**
     * 拍照参数错误回调测试
     * API: getVirtualCameraList capture getCameraId
     */
    private void dealTestParaErrorCapture() {
        // 非法roi值
        String jsonStr = "1_1_abcd";
        startCapture(jsonStr);
    }

    /**
     * 台灯错误回调测试
     * API: getVirtualCameraList capture getCameraId
     */
    private void dealTestDeviceError() {
        // 设置非16倍数的w、h
        String jsonStr = "0_0_8888_8888";
        startCapture(jsonStr);
    }

    private void startCapture(String jsonStr) {
        long currentSeqNum = System.currentTimeMillis();
        mCaptureSeqNum = String.valueOf(currentSeqNum);
        mCameraParas.put("roi", jsonStr);
        List<VirtualCamera> virtualCameraList = mVirtualCameraManager.getVirtualCameraList(mDeviceId);
        if (virtualCameraList.size() == 0) {
            sendMessageToHandler(MSG_SHOW_TEXT, "获取到的虚拟camera为空，请重新使能");
            return;
        }
        VirtualCamera virtualCamera = virtualCameraList.get(0);
        String cameraId = virtualCamera.getCameraId();
        Log.i(TAG,
            "mCaptureSeqNum: " + mCaptureSeqNum + " cameras: " + mCameraParas.get("roi") + " cameraId: " + cameraId);
        int captureFlag = mVirtualCameraManager.capture(mCaptureSeqNum, cameraId, mCameraParas, mCaptureCallback);
        sendMessageToHandler(MSG_SHOW_TEXT, "拍照接口调用结果：" + captureFlag + " (0为成功,非0为失败)");
    }

    /**
     * 结束设备虚拟化服务
     * API: getInstance deInit
     */
    private void dealDeInitDeviceEvent() {
        int returnCode = DVManager.getInstance().deInit();
        Log.i(TAG, "deInit:" + returnCode);
    }

    /**
     * 使能设备
     * API: enableVirtualDevice
     */
    private void dealEnableDeviceStatusEvent() {
        if (mVirtualDeviceManager == null) {
            sendMessageToHandler(MSG_SHOW_TEXT, "请重新初始化");
            return;
        }
        int status = mVirtualDeviceManager.enableVirtualDevice(mDeviceId, EnumSet.of(CAMERA), null);
        if (status == 0) {
            sendMessageToHandler(MSG_SHOW_TEXT, "使能camera接口调用成功，status = " + status + "，等待服务端结果...");
        } else if (status == ReturnCode.ERROR_CODE_ALREADY_REGISTER) {
            sendMessageToHandler(MSG_SHOW_TEXT, "请勿重复使能camera设备, status = " + status);
        } else {
            sendMessageToHandler(MSG_SHOW_TEXT, "使能camera设备失败, status = " + status);
        }
    }

    /**
     * 去使能设备
     * API: disableVirtualDevice
     */
    private void dealDisableDeviceStatusEvent() {
        if (mVirtualDeviceManager == null) {
            sendMessageToHandler(MSG_SHOW_TEXT, "请重新初始化");
            return;
        }
        int status = mVirtualDeviceManager.disableVirtualDevice(mDeviceId, EnumSet.of(CAMERA));
        if (status == 0) {
            sendMessageToHandler(MSG_SHOW_TEXT, "去使能camera设备接口调用成功，status = " + status);
        } else {
            sendMessageToHandler(MSG_SHOW_TEXT, "去使能camera设备失败，status = " + status);
        }
    }

    /**
     * 打开摄像头预览界面
     * API: getVirtualCameraList getCameraId
     */
    private void openVirtualCamera() {
        if (mVirtualCameraManager == null) {
            sendMessageToHandler(MSG_SHOW_TEXT, "mVirtualCameraManager is null");
            Log.e(TAG, "mVirtualCameraManager is null");
            return;
        }
        List<VirtualCamera> virtualCameraList = mVirtualCameraManager.getVirtualCameraList(mDeviceId);
        if (virtualCameraList.size() == 0) {
            sendMessageToHandler(MSG_SHOW_TEXT, "获取到的虚拟camera为空，请重新使能");
            return;
        }
        VirtualCamera virtualCamera = virtualCameraList.get(0);
        // open virtual camera
        sendMessageToHandler(MSG_SHOW_TEXT, "cameraId " + virtualCamera.getCameraId());
        Intent intent = new Intent(DvkitDemoActivity.this, RegisterCameraAPI2.class);
        intent.putExtra("cameraId", virtualCamera.getCameraId());
        startActivity(intent);
    }

    /**
     * 版本号获取、设备是否支持虚拟化能力
     * API: getSDKVersion getServiceVersionLevel isSupportDVService
     *
     * @return 是否支持
     */
    private boolean isSystemSupport() {
        boolean isSupport = true;
        String version = "0.0.0";
        int serviceVersion = 0;
        try {
            // Get the running version of the DvKit
            version = DVVersionUtils.getSDKVersion();
            serviceVersion = DVVersionUtils.getServiceVersionLevel();
            isSupport = DVVersionUtils.isSupportDVService();
        } catch (NoClassDefFoundError e) {
            // The current operating environment does not support DvKit
            isSupport = false;
        }
        sendMessageToHandler(MSG_SHOW_TEXT,
            "local DvKit version: " + version + ",serviceVersion: " + serviceVersion + ", isSupport: " + isSupport);
        return isSupport;
    }

    /**
     * 展示图片
     */
    private void showImage() {
        if (mReceiveCapSeqNum != null && mReceiveCapSeqNum.equals(mCaptureSeqNum) && mReceiveCapResult == 0) {
            Log.i(TAG, "set image");
            Optional<Bitmap> captureBitmap = getPicFromBytes(mReceiveCapData, mOpts);
            captureBitmap.ifPresent(bitmap -> mImageView.setImageBitmap(bitmap));
        }
    }

    /**
     * 展示文字
     *
     * @param msg 展示内容信息
     */
    private void showText(Message msg) {
        if (msg != null && msg.obj instanceof String) {
            String message = (String)msg.obj;
            long currentTime = System.currentTimeMillis();
            SimpleDateFormat formatter = new SimpleDateFormat("[yyyy-MM_dd-HH_mm_ss]");
            Date date = new Date(currentTime);
            mTextView.append(formatter.format(date));
            mTextView.append(message + "\n");
            Log.i(TAG, "set text:" + message);
            int offset = mTextView.getLineCount() * mTextView.getLineHeight();
            if (offset > mTextView.getHeight()) {
                mTextView.scrollTo(0, offset - mTextView.getHeight() + mTextView.getLineHeight());
            }
        }
    }

    private void sendMessageToHandler(int what, String msg) {
        Message message = Message.obtain();
        message.what = what;
        message.obj = msg;
        mHandler.sendMessage(message);
    }

    /**
     * 图片转换
     *
     * @param data 图像数据
     * @param opts 生成选项
     * @return bitmap
     */
    private Optional<Bitmap> getPicFromBytes(byte[] data, BitmapFactory.Options opts) {
        if (data != null) {
            if (opts != null) {
                return Optional.of(BitmapFactory.decodeByteArray(data, 0, data.length, opts));
            } else {
                return Optional.of(BitmapFactory.decodeByteArray(data, 0, data.length));
            }
        }
        Log.i(TAG, "getPicFromBytes is null");
        return Optional.empty();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dealDeInitDeviceEvent();
        if (mVirtualDeviceManager != null) {
            // 界面销毁时取消注册设备状态监听
            mVirtualDeviceManager.unsubscribeDeviceStatus(EnumSet.of(VIRTUALDEVICE), mObserver);
            mVirtualDeviceManager = null;
        }
    }
}
