package com.example.pkmapp.profile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class AvatarStore {
    private static final String FILE_NAME = "forest_avatar.png";

    private AvatarStore() {
    }

    public static Bitmap load(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        return file.isFile() ? BitmapFactory.decodeFile(file.getAbsolutePath()) : null;
    }

    public static boolean save(Context context, Bitmap bitmap) {
        try (FileOutputStream output = context.openFileOutput(FILE_NAME, Context.MODE_PRIVATE)) {
            return bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (IOException exception) {
            return false;
        }
    }

    public static void clear(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        if (file.isFile()) {
            file.delete();
        }
    }
}
