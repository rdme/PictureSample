package com.websarva.wings.android.picturesample.camera;


import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.websarva.wings.android.picturesample.R;

import java.util.Arrays;

/**
 * A simple {@link Fragment} subclass.
 */
public class CameraFragment extends Fragment {

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    /// Member
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    private Context mParentActivity;
    private TextureView mTextureView;
    private CameraDevice mCameraDevice;
    private Size mPreviewSize;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CameraCaptureSession mCaptureSession;

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    /// Listener
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * カメラ画面のリスナ
     */
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            // Textureが有効化されたとき
            Log.d("DEBUG","Textureが有効化されたとき");

            prepareCameraView();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }
    };

    /**
     * 撮影ボタンタップ
     */
    private View.OnClickListener mButtonOnCickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {

        }
    };


    ////////////////////////////////////////////////////////////////////////////////////////////////////
    /// Fragment
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    public CameraFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_camera, container, false);
        mTextureView = (TextureView) view.findViewById(R.id.texture_view);
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);

        Button button = (Button) view.findViewById(R.id.button_take_picture);
        button.setOnClickListener(mButtonOnCickListener);

        return view;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mParentActivity = context;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    /// Private Method
    ////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * TextureViewが有効化されたらカメラを準備する
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void prepareCameraView() {
        CameraManager cameraManager = (CameraManager) mParentActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            String backCameraId = null;
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (characteristics.get(CameraCharacteristics.LENS_FACING)
                        == CameraCharacteristics.LENS_FACING_BACK) {
                    backCameraId = cameraId;

                    StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    mPreviewSize = map.getOutputSizes(SurfaceTexture.class)[0];

                }
            }

            if (backCameraId == null) {
                return;
            }

            Log.d("DEBUG","checkSelfPermission");
            if (ActivityCompat.checkSelfPermission(mParentActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            Log.d("DEBUG","openCamera");
            cameraManager.openCamera(backCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    Log.d("DEBUG","CameraDeviceが有効化されたとき");
                    mCameraDevice = cameraDevice;
                    createCameraCaptureSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {

                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int i) {

                }
            }, null);

//                this.configureTransform();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * CameraCaptureSessionを有効化する
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void createCameraCaptureSession() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }

        SurfaceTexture texture =  mTextureView.getSurfaceTexture();
        if (null == texture) {
            return;
        }
        texture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
        Surface surface = new Surface(texture);

        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mCaptureRequestBuilder.addTarget(surface);

        try {
            mCameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.d("DEBUG","Sessionが有効化された");
                    mCaptureSession = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(mParentActivity, "onConfigureFailed", Toast.LENGTH_LONG).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * CaptureSessionから画像を繰り返し取得してTextureViewに表示する
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        HandlerThread thread = new HandlerThread("CameraCapture");
        thread.start();
        Handler backgroundHandler = new Handler(thread.getLooper());

        Log.d("DEBUG","リクエストスタート");
        try {
            mCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(),null,backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}
