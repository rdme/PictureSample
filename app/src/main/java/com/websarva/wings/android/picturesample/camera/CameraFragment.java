package com.websarva.wings.android.picturesample.camera;


import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.os.EnvironmentCompat;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.websarva.wings.android.picturesample.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 */
public class CameraFragment extends Fragment {

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    /// Member
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    private Activity mParentActivity;
    private TextureView mTextureView;
    private CameraDevice mCameraDevice;
    private Size mPreviewSize;
    private CaptureRequest.Builder mCaptureRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private ImageReader mImageReader;
    private Handler mBackgroundHandler;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
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
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onClick(View view) {
            takePicture();
        }
    };

    /**
     * 画像保存のリスナ
     */
    private ImageReader.OnImageAvailableListener mReaderListener
            = new ImageReader.OnImageAvailableListener() {
        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            Log.d("DEBUG","画像保存の準備ができた");
            Image image = imageReader.acquireNextImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            OutputStream output = null;
            final String saveDirectory = Environment.getExternalStorageDirectory().toString();
            final String saveFileName = "pic_" + System.currentTimeMillis() + ".jpg";
            try {
                File file = new File(saveDirectory,saveFileName);
                output = new FileOutputStream(file);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (image != null) {
                    image.close();
                }
                if (output != null) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            String[] paths = {saveDirectory + "/" + saveFileName};
            String[] mimeTypes = {"image/jpeg"};
            MediaScannerConnection.scanFile(mParentActivity.getApplicationContext(),
                    paths,
                    mimeTypes,
                    new MediaScannerConnection.OnScanCompletedListener() {
                        @Override
                        public void onScanCompleted(String s, Uri uri) {
                            mParentActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(mParentActivity,"保存完了:"+saveDirectory+"/"+saveFileName,Toast.LENGTH_LONG).show();
                                    createCameraCaptureSession();
                                }
                            });
                        }
                    }
            );
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
    public void onResume() {
        super.onResume();
        // 別スレッドで実行
        HandlerThread thread = new HandlerThread("CameraPicture");
        thread.start();
        mBackgroundHandler = new Handler(thread.getLooper());
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mParentActivity = (Activity) context;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////
    /// Private Methods
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

                    // ストリーム設定からJPEGの出力サイズを取得する
                    Size jpegSize = getJPEGSize(characteristics);

                    // 画像を取得するためのImageReader
                    mImageReader = ImageReader.newInstance(jpegSize.getWidth(),
                            jpegSize.getHeight(),
                            ImageFormat.JPEG, 1);
                    mImageReader.setOnImageAvailableListener(mReaderListener, mBackgroundHandler);

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

        // 出力するためのSurfaceView
        List outputSurfaces = new ArrayList(2);
        outputSurfaces.add(mImageReader.getSurface());
        outputSurfaces.add(new Surface(mTextureView.getSurfaceTexture()));

        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        mCaptureRequestBuilder.addTarget(surface);

        try {
            mCameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
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

        Log.d("DEBUG","リクエストスタート");
        try {
            mCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(),null,mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 撮影系のもの
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void takePicture() {
        Log.d("DEBUG","撮影ボタンタップ");
        if(null == mCameraDevice) {
            return;
        }

        try {
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            // 画像の調整
            int rotation = mParentActivity.getWindowManager().getDefaultDisplay().getRotation();
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            // キャプチャセッションの生成
            mCaptureSession.capture(mCaptureRequestBuilder.build(),
                    new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                       @NonNull CaptureRequest request,
                                                       @NonNull TotalCaptureResult result) {
                            super.onCaptureCompleted(session, request, result);
                        }
                    },
                    mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * ストリーム設定からJPEGの出力サイズを取得する
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private Size getJPEGSize(CameraCharacteristics characteristics) {
        int width = 640;
        int height = 480;
        if (characteristics == null) {
            return new Size(width,height);
        }

        StreamConfigurationMap map = characteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size largest = Collections.max(
                Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                new CompareSizesByArea());

        width = largest.getWidth();
        height = largest.getHeight();
        return new Size(width,height);
    }

    /**
     * ファイル保存先を生成する
     */
    private File getSaveFile() {
        String saveDirectory = Environment.getExternalStorageDirectory().toString();
        String saveFileName = "pic_" + System.currentTimeMillis() + ".jpg";
        return new File(saveDirectory,saveFileName);
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }
}
