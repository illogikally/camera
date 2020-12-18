package me.min.camera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.material.imageview.ShapeableImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;

@SuppressLint("SetTextI18n")
public class MainActivity extends AppCompatActivity {
   private Button btnCapture;
   private TextureView textureView;
   private ShapeableImageView sivGallery;

   private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
   static {
      ORIENTATIONS.append(Surface.ROTATION_0, 90);
      ORIENTATIONS.append(Surface.ROTATION_90, 0);
      ORIENTATIONS.append(Surface.ROTATION_180, 270);
      ORIENTATIONS.append(Surface.ROTATION_270, 180);
   }

   private CameraDevice cameraDevice;
   private CaptureRequest.Builder previewRequestBuilder;
   private CameraCaptureSession cameraCaptureSession;
   private Size previewSize;

   private File outputFile;
   private ImageReader imageReader;
   private Handler backgroundHandler;
   private HandlerThread backgroundThread;

   private final String cameraDirPath = Environment.getExternalStorageDirectory() + "/DCIM/Camera/";
   private static final int REQUEST_CAMERA_PERMISSION = 1;
   private enum CameraState {
      PREVIEW,
      WAIT_AF_LOCK,
      WAIT_PRECAPTURE,
      WAIT_PRECAPTURE_DONE,
      PICTURE_TAKEN
   }
   private CameraState cameraState = CameraState.PREVIEW;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_main);

      initSivGallery();
      initBtnCapture();
      initTextureView();
   }

   private void initTextureView() {
      textureView = findViewById(R.id.textureView);
      textureView.setSurfaceTextureListener(surfaceTextureListener);

      Point displaySize = new Point();
      getWindowManager().getDefaultDisplay().getSize(displaySize);
      textureView.getLayoutParams().height = displaySize.x * 4/3 ;
   }

   @SuppressLint("ClickableViewAccessibility")
   private void initBtnCapture() {
      btnCapture = findViewById(R.id.btnCapture);
      btnCapture.setBackgroundResource(R.drawable.btn_capture);
      btnCapture.setOnTouchListener((v, event) -> {
         if(event.getAction() == MotionEvent.ACTION_DOWN) {
            runButtonAnimation(R.drawable.btn_capture);
            takePicture();
         } else if (event.getAction() == MotionEvent.ACTION_UP) {
            runButtonAnimation(R.drawable.btn_capture_released);
         }
         return true;
      });
   }

   private void initSivGallery() {
      sivGallery = findViewById(R.id.sivGallery);
      sivGallery.setOnClickListener(view -> {
         startActivity(new Intent(this, Gallery.class));
         overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_right);
      });
      File cameraDir = new File(cameraDirPath);
      File[] images = cameraDir.listFiles();
      assert images != null;
      Arrays.sort(images, (lhs, rhs) -> -Long.compare(lhs.lastModified(), rhs.lastModified()));
      BitmapWorkerTask task = new BitmapWorkerTask(sivGallery);
      task.execute(images[0].getAbsolutePath());
   }

   private void runButtonAnimation(int id) {
      btnCapture.setBackgroundResource(id);
      AnimationDrawable animationDrawable = (AnimationDrawable) btnCapture.getBackground();
      animationDrawable.start();
   }
   private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
      @Override
      public void onOpened(@NonNull CameraDevice camera) {
         cameraDevice = camera;
         createCameraPreview();
      }

      @Override
      public void onDisconnected(@NonNull CameraDevice camera) {
         camera.close();
         cameraDevice = null;
      }

      @Override
      public void onError(@NonNull CameraDevice camera, int error) {
         camera.close();
         cameraDevice = null;
      }
   };

   private final TextureView.SurfaceTextureListener surfaceTextureListener
           = new TextureView.SurfaceTextureListener() {
      @Override
      public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
         openCamera();
      }

      @Override
      public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

      }

      @Override
      public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
         return false;
      }

      @Override
      public void onSurfaceTextureUpdated(SurfaceTexture surface) {

      }
   };


   private void takePicture() {
      try {
         String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
         outputFile = new File(cameraDirPath + timestamp + ".jpg");

         previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                   CameraMetadata.CONTROL_AF_TRIGGER_START);
         cameraState = CameraState.WAIT_AF_LOCK;
         cameraCaptureSession.capture(previewRequestBuilder.build(),
                                      captureCallback,
                                      backgroundHandler);

      } catch (CameraAccessException e) {
         e.printStackTrace();
      }
   }

   private void captureStillImage() {
      try {
         final CaptureRequest.Builder captureBuilder
                 = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
         captureBuilder.addTarget(imageReader.getSurface());
         captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

         int rotation = getWindowManager().getDefaultDisplay().getRotation();
         captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

         CameraCaptureSession.CaptureCallback captureCallback
                 = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                           @NonNull CaptureRequest request,
                                           @NonNull TotalCaptureResult result) {
               createCameraPreview();
            }
         };
         cameraCaptureSession.stopRepeating();
         cameraCaptureSession.abortCaptures();
         cameraCaptureSession.capture(captureBuilder.build(),
                                      captureCallback,
                                      null);

      } catch (CameraAccessException e) {
         e.printStackTrace();
      }
   }

   private void createCameraPreview() {
      try {
         SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
         surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
         Surface surface = new Surface(textureView.getSurfaceTexture());
         previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
         previewRequestBuilder.addTarget(surface);
         cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()),
                                           sessionStateCallback,
                                           backgroundHandler);
      } catch (CameraAccessException e) {
         e.printStackTrace();
      }
   }

   CameraCaptureSession.StateCallback sessionStateCallback
           = new CameraCaptureSession.StateCallback() {
      @Override
      public void onConfigured(@NonNull CameraCaptureSession session) {
         if (cameraDevice == null)
            return;
         cameraCaptureSession = session;
         previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                   CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
         try {
            cameraCaptureSession.setRepeatingRequest(previewRequestBuilder.build(),
                                                     captureCallback,
                                                     backgroundHandler);
         } catch (CameraAccessException e) {
            e.printStackTrace();
         }
      }

      @Override
      public void onConfigureFailed(@NonNull CameraCaptureSession session) {
         makeToast("Configure capture sesssion failed.");
      }
   };

   private final CameraCaptureSession.CaptureCallback captureCallback
           = new CameraCaptureSession.CaptureCallback() {
      private void process(CaptureResult result) {
         switch (cameraState) {
            case PREVIEW:
               break;
            case WAIT_AF_LOCK: {
               Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
               if (afState == null) {
                  captureStillImage();
               } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState
                          || CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {

                  Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                  if (aeState == null
                          || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                     cameraState = CameraState.PICTURE_TAKEN;
                     captureStillImage();
                  } else {
                     runPrecapture();
                  }
               }
               break;
            }

            case WAIT_PRECAPTURE: {
               Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
               if (aeState == null
                     || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE
                     || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                  cameraState = CameraState.WAIT_PRECAPTURE_DONE;
               }
               break;
            }

            case WAIT_PRECAPTURE_DONE: {
               Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
               if (aeState == null
                     || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                  cameraState = CameraState.PICTURE_TAKEN;
                  captureStillImage();
               }
               break;
            }
         }
      }
      @Override
      public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                      @NonNull CaptureRequest request,
                                      @NonNull CaptureResult partialResult) {
         process(partialResult);
      }

      @Override
      public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                     @NonNull CaptureRequest request,
                                     @NonNull TotalCaptureResult result) {
         process(result);
      }
   };

   private void runPrecapture() {
      try {
         previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                   CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
         cameraState = CameraState.WAIT_PRECAPTURE;
         cameraCaptureSession.capture(previewRequestBuilder.build(),
                                      captureCallback,
                                      backgroundHandler);
      } catch (CameraAccessException e) {
         e.printStackTrace();
      }
   }
   private void openCamera() {
      CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
      try {
         String cameraId = cameraManager.getCameraIdList()[0];
         CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
         StreamConfigurationMap map
                 = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
         previewSize = map.getOutputSizes(SurfaceTexture.class)[0];
         Size[] jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(ImageFormat.JPEG);
         int width = jpegSizes[0].getWidth();
         int height = jpegSizes[0].getHeight();
         imageReader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
         ImageReader.OnImageAvailableListener readerListener = reader -> {
            try (Image image = reader.acquireLatestImage()) {
               ByteBuffer buffer = image.getPlanes()[0].getBuffer();
               byte[] bytes = new byte[buffer.capacity()];
               buffer.get(bytes);
               sivGallery.setImageBitmap(BitmapFactory.decodeByteArray(bytes,
                                                                       0,
                                                                       bytes.length,
                                                                       null));
               try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                  outputStream.write(bytes);
               }
            } catch (IOException e) {
               e.printStackTrace();
            }
         };
         imageReader.setOnImageAvailableListener(readerListener, backgroundHandler);

         if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                 != PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                                              new String[] {Manifest.permission.CAMERA,
                                                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                              REQUEST_CAMERA_PERMISSION);
            return;
         }
         cameraManager.openCamera(cameraId, cameraStateCallback, backgroundHandler);

      } catch (CameraAccessException e) {
         e.printStackTrace();
      }
   }

   @Override
   public void onRequestPermissionsResult(int requestCode,
                                          @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
      if (requestCode == REQUEST_CAMERA_PERMISSION) {
         if(grantResults[0] != PERMISSION_GRANTED) {
            makeToast("Camera permission must be granted to be able to use this app!");
            finish();
         }
      }
   }

   @Override
   protected void onResume() {
      super.onResume();
      startBackgroundThread();
      if (textureView.isAvailable())
         openCamera();
      else
         textureView.setSurfaceTextureListener(surfaceTextureListener);
   }

   @Override
   protected void onPause() {
      stopBackgroundThread();
      closeCamera();
      super.onPause();
   }

   private void closeCamera() {
      if (cameraCaptureSession != null) {
         cameraCaptureSession.close();
         cameraCaptureSession = null;
      }

      if (cameraDevice != null) {
         cameraDevice.close();
         cameraDevice = null;
      }
   }

   private void stopBackgroundThread() {
      backgroundThread.quitSafely();
      try{
         backgroundThread.join();
         backgroundThread = null;
         backgroundHandler = null;
      } catch (InterruptedException e) {
         e.printStackTrace();
      }
   }

   private void startBackgroundThread() {
      backgroundThread = new HandlerThread("me.min.camera");
      backgroundThread.start();
      backgroundHandler = new Handler(backgroundThread.getLooper());
   }

   private void makeToast(String msg) {
      this.runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
   }
}
