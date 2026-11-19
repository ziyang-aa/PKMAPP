package com.example.pkmapp.forest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

public final class ForestBitmaps {

    private static final LruCache<Integer, Bitmap> CACHE = new LruCache<Integer, Bitmap>(
        24 * 1024
    ) {
        @Override
        protected int sizeOf(Integer key, Bitmap value) {
            return value.getAllocationByteCount() / 1024;
        }
    };

    private ForestBitmaps() {}

    public static synchronized Bitmap get(Context context, int resource) {
        Bitmap bitmap = CACHE.get(resource);
        if (bitmap != null) return bitmap;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(context.getResources(), resource, options);
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / (options.inSampleSize * 2) >= 768)
            options.inSampleSize *= 2;
        options.inJustDecodeBounds = false;
        options.inScaled = false;
        bitmap = BitmapFactory.decodeResource(context.getResources(), resource, options);
        if (bitmap == null) throw new IllegalStateException("森林素材无法读取");
        CACHE.put(resource, bitmap);
        return bitmap;
    }
}
