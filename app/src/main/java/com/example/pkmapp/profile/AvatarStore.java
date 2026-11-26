package com.example.pkmapp.profile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.example.pkmapp.core.database.AccountScope;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public final class AvatarStore {
    private static final String GUEST_FILE_NAME = "forest_avatar.png";

    private AvatarStore() {
    }

    public static String fileNameForOwner(String ownerId) {
        if (ownerId == null || ownerId.trim().isEmpty()
                || AccountScope.GUEST_OWNER_ID.equals(ownerId.trim())) {
            return GUEST_FILE_NAME;
        }
        String safe = ownerId.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        return "forest_avatar_" + safe + ".png";
    }

    public static Bitmap load(Context context) {
        return load(context, AccountScope.currentOwnerId(context));
    }

    public static Bitmap load(Context context, String ownerId) {
        File file = new File(context.getFilesDir(), fileNameForOwner(ownerId));
        return file.isFile() ? BitmapFactory.decodeFile(file.getAbsolutePath()) : null;
    }

    public static boolean save(Context context, Bitmap bitmap) {
        return save(context, AccountScope.currentOwnerId(context), bitmap);
    }

    public static boolean save(Context context, String ownerId, Bitmap bitmap) {
        String name = fileNameForOwner(ownerId);
        try (FileOutputStream output = context.openFileOutput(name, Context.MODE_PRIVATE)) {
            return bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (IOException exception) {
            return false;
        }
    }

    public static void clear(Context context) {
        clear(context, AccountScope.currentOwnerId(context));
    }

    public static void clear(Context context, String ownerId) {
        File file = new File(context.getFilesDir(), fileNameForOwner(ownerId));
        if (file.isFile()) {
            file.delete();
        }
    }
}
