package me.min.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.widget.ImageView;

import java.lang.ref.WeakReference;

public class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {
   private final WeakReference<ImageView> imageViewWeakReference;

   BitmapWorkerTask(ImageView imageView) {
      this.imageViewWeakReference = new WeakReference<>(imageView);
   }

   @Override
   protected Bitmap doInBackground(String... strings) {
      String path = strings[0];
      return BitmapFactory.decodeFile(path);
   }

   @Override
   protected void onPostExecute(Bitmap bitmap) {
      if (imageViewWeakReference != null && bitmap != null) {
         final ImageView imageView = imageViewWeakReference.get();
         if (imageView != null) {
            imageView.setImageBitmap(bitmap);
         }
      }
   }
}
