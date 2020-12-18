package me.min.camera;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.MarginPageTransformer;
import androidx.viewpager2.widget.ViewPager2;

import android.os.Bundle;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Gallery extends AppCompatActivity {

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.activity_gallery);

      File cameraDir = new File(Environment.getExternalStorageDirectory() + "/DCIM/Camera/");
      File[] files = cameraDir.listFiles();
      assert files != null;
      Arrays.sort(files, (lhs, rhs) -> -Long.compare(lhs.lastModified(), rhs.lastModified()));
      List<String> imagePaths = new ArrayList<>(files.length);
      for (File file : files) {
         if (file.getName().contains(".jpg")) {
            imagePaths.add(file.getAbsolutePath());
         }
      }

      ViewPager2 viewPager2 = findViewById(R.id.viewPager2);
      ViewPagerAdapter viewPagerAdapter = new ViewPagerAdapter(imagePaths);
      viewPager2.setAdapter(viewPagerAdapter);
      viewPager2.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
      viewPager2.setPageTransformer(new MarginPageTransformer(50));
      viewPager2.setOffscreenPageLimit(3);
   }

   @Override
   public void onBackPressed() {
      super.onBackPressed();
      finish();
   }

   @Override
   protected void onPause() {
      super.onPause();
      finish();
   }
}