package me.min.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.ref.WeakReference;
import java.util.List;

public class ViewPagerAdapter extends RecyclerView.Adapter<ViewPagerAdapter.ViewHolder> {
   private final List<String> imagePaths;

   public ViewPagerAdapter(List<String> imagePaths) {
      this.imagePaths = imagePaths;
   }

   @NonNull
   @Override
   public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_view_pager,
                                                                   parent,
                                                                   false);
      return new ViewHolder(view);
   }

   @Override
   public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
      holder.setImage(imagePaths.get(position));
   }

   @Override
   public int getItemCount() {
      return imagePaths.size();
   }

   static class ViewHolder extends RecyclerView.ViewHolder {
      private final ImageView imageView;

      public ViewHolder(@NonNull View itemView) {
         super(itemView);
         imageView = itemView.findViewById(R.id.imageView);
      }

      void setImage(String path) {
         BitmapWorkerTask task = new BitmapWorkerTask(imageView);
         task.execute(path);
      }
   }
}
