package com.dream.park;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.dream.park.utils.DeepAssetUtil;
import com.dream.park.utils.PlateRecognition;
import com.dream.park.utils.ViewFinderView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class LPRactivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    public long handle;
    private SurfaceView mSurfaceView;
    private Camera camera;
    private SurfaceHolder surfaceHolder;
    private ViewFinderView viewFinderView;
    private PalmTask mFaceTask;
    private boolean isStarus = true;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lpr);

        mSurfaceView = findViewById(R.id.camera_ui);
        viewFinderView =  findViewById(R.id.finder_view);
        surfaceHolder = mSurfaceView.getHolder();
        surfaceHolder.addCallback(this);

    }

    //传key值
    private void finishValue(String card) {
        Intent intent = new Intent();
        intent.putExtra("card", card);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }


    //开启相机
    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        camera = Camera.open(0);
        camera.setDisplayOrientation(90);
        //相机参数
        try{
            Camera.Parameters parameters = camera.getParameters();
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
            parameters.setFlashMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            //参数设置赋给Camera对象
            camera.setParameters(parameters);
        }catch (Exception e){
            e.printStackTrace();
        }


        try {
            camera.setPreviewDisplay(surfaceHolder);
        } catch (IOException e) {
            e.printStackTrace();
        }
        camera.setPreviewCallback(this);
        camera.startPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    //释放camera
    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.release();
            camera = null;
        }
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        if (null != mFaceTask) {
            switch (mFaceTask.getStatus()) {
                case RUNNING:
                    return;
                case PENDING:
                    mFaceTask.cancel(false);
                    break;
            }
        }
        if (!isStarus) {
            return;
        }
        mFaceTask = new PalmTask(bytes);
        mFaceTask.execute();
    }


    //加载opencv
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @SuppressLint("StaticFieldLeak")
        @Override
        public void onManagerConnected(int status) {
            //在加载openCV 成功后, 开始加载 vcd so 文件
            if (status == LoaderCallbackInterface.SUCCESS) {
                System.loadLibrary("lpr");
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... voids) {
                        handle = DeepAssetUtil.initRecognizer(LPRactivity.this);
                        return null;
                    }
                }.execute();
            } else {
                super.onManagerConnected(status);
            }
        }
    };

    /**
     * 自定义的FaceTask类，开启一个线程分析数据
     */
    @SuppressLint("StaticFieldLeak")
    private class PalmTask extends AsyncTask<Void, Void, String> {
        private byte[] mData;

        //构造函数
        PalmTask(byte[] data) {
            this.mData = data;
        }

        @Override
        protected String doInBackground(Void... params) {
            Camera.Size size = camera.getParameters().getPreviewSize();
            try {
                //将YUV数据压缩成Jpeg数据的方法
                YuvImage image = new YuvImage(mData, ImageFormat.NV21, size.width, size.height, null);
                //字节数组输出流在内存中创建一个字节数组缓冲区，所有发送到输出流的数据保存在该字节数组缓冲区中。
                ByteArrayOutputStream stream = new ByteArrayOutputStream(mData.length);
                //image.compressToJpeg(new Rect(0, 0, size.width, size.height), 80, stream);
                if (!image.compressToJpeg(new Rect(0, 0, size.width, size.height), 100, stream)) {
                    return null;
                }
                //对字节数组排序
                Bitmap bmp = BitmapFactory.decodeByteArray(stream.toByteArray(), 0, stream.size());
                //旋转图片90°
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                //mBmp = Bitmap.createBitmap(bmp, viewFinderView.center.top + actionHeight, viewFinderView.center.left,viewFinderView.centerHeight, viewFinderView.centerWidth, matrix, true);
                stream.close();
                Bitmap bitmap = Bitmap.createBitmap(bmp, viewFinderView.center.top, viewFinderView.center.left,
                        viewFinderView.centerHeight, viewFinderView.centerWidth, matrix, true);

                int width = bitmap.getWidth();
                int height = bitmap.getHeight();
                Mat m = new Mat(width, height, CvType.CV_8UC4);
//                    Mat m = new Mat(width, height, CvType.CV_8UC2);
                Utils.bitmapToMat(bitmap, m);
                return PlateRecognition.SimpleRecognization(m.getNativeObjAddr(), handle);

            } catch (Exception ex) {
                Log.e("Sys", "Error:" + ex.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(String str) {
            super.onPostExecute(str);
            if (!"".equals(str)) {
                isStarus = false;
                String[] list = str.split(",");
                if (list.length > 1) {
                    new AlertDialog.Builder(LPRactivity.this)
                            .setTitle("请点击选择")
                            .setItems(list, (dialog, which) -> finishValue(list[which]))
                            .setNegativeButton("重新识别", (dialog, which) -> isStarus = true)
                            .show();
                } else
                    new AlertDialog.Builder(LPRactivity.this)
                            .setTitle("识别结果")
                            .setMessage(str)
                            .setPositiveButton("确定", (dialog, which) -> finishValue(str))
                            .setNegativeButton("重新识别", (dialog, which) -> isStarus = true)
                            .show();
            }

        }
    }
}
