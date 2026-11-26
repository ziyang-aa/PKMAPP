package com.example.pkmapp.profile

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import com.example.pkmapp.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.IOException
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AvatarCropSheetController(
    caller: ActivityResultCaller,
    private val contextProvider: () -> Context,
    private val scopeProvider: () -> LifecycleCoroutineScope,
    private val isHostAlive: () -> Boolean,
    private val onAvatarUpdated: (Bitmap) -> Unit
) {
    constructor(
        fragment: Fragment,
        onAvatarUpdated: (Bitmap) -> Unit
    ) : this(
        caller = fragment,
        contextProvider = { fragment.requireContext() },
        scopeProvider = { fragment.viewLifecycleOwner.lifecycleScope },
        isHostAlive = { fragment.isAdded && fragment.view != null },
        onAvatarUpdated = onAvatarUpdated
    )

    constructor(
        activity: AppCompatActivity,
        onAvatarUpdated: (Bitmap) -> Unit
    ) : this(
        caller = activity,
        contextProvider = { activity },
        scopeProvider = { activity.lifecycleScope },
        isHostAlive = { !activity.isFinishing && !activity.isDestroyed },
        onAvatarUpdated = onAvatarUpdated
    )

    private var activeSheet: BottomSheetDialog? = null

    private val avatarPicker: ActivityResultLauncher<String> =
        caller.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            handleAvatarPicked(uri)
        }

    fun showAvatarPreview() {
        val context = contextProvider()
        val dialog = BottomSheetDialog(context)
        val root = LayoutInflater.from(context).inflate(R.layout.dialog_avatar_preview, null)
        val preview = root.findViewById<ImageView>(R.id.profile_avatar_preview_image)
        AvatarStore.load(context)?.let { preview.setImageBitmap(it) }
        root.findViewById<View>(R.id.profile_avatar_preview_close).setOnClickListener {
            dialog.dismiss()
        }
        root.findViewById<View>(R.id.profile_avatar_preview_change).setOnClickListener {
            dialog.dismiss()
            avatarPicker.launch("image/*")
        }
        showPaperSheet(dialog, root)
    }

    fun dismissActiveSheet() {
        activeSheet?.dismiss()
        activeSheet = null
    }

    private fun handleAvatarPicked(uri: Uri?) {
        if (uri == null || !isHostAlive()) return
        val appContext = contextProvider().applicationContext
        scopeProvider().launch {
            var errorMessage: String? = null
            val decoded = withContext(Dispatchers.IO) {
                try {
                    decodeAvatarBitmap(appContext.contentResolver, uri)
                } catch (e: IOException) {
                    errorMessage = e.message
                    null
                } catch (e: RuntimeException) {
                    errorMessage = e.message
                    null
                }
            }
            if (!isHostAlive()) return@launch
            if (decoded == null) {
                val toastText = if (errorMessage == null) "这张图片暂时无法使用" else "图片读取失败"
                Toast.makeText(contextProvider(), toastText, Toast.LENGTH_SHORT).show()
            } else {
                showAvatarCrop(decoded)
            }
        }
    }

    private fun showAvatarCrop(bitmap: Bitmap) {
        val context = contextProvider()
        val dialog = BottomSheetDialog(context)
        val root = LayoutInflater.from(context).inflate(R.layout.dialog_avatar_crop, null)
        val cropStage = root.findViewById<FrameLayout>(R.id.avatar_crop_stage)
        val cropView = AvatarCropView(context)
        cropStage.addView(
            cropView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        cropView.setBitmap(bitmap)
        root.findViewById<View>(R.id.profile_avatar_dialog_close).setOnClickListener {
            dialog.dismiss()
        }
        root.findViewById<View>(R.id.profile_avatar_dialog_cancel).setOnClickListener {
            dialog.dismiss()
        }
        root.findViewById<View>(R.id.profile_avatar_dialog_save).setOnClickListener {
            val cropped = cropView.croppedBitmap
            scopeProvider().launch {
                val saved = withContext(Dispatchers.IO) {
                    AvatarStore.save(context.applicationContext, cropped)
                }
                if (!isHostAlive()) return@launch
                if (saved) {
                    onAvatarUpdated(cropped)
                    dialog.dismiss()
                    Toast.makeText(context, "头像已放进森林档案", Toast.LENGTH_SHORT).show()
                } else {
                    cropped.recycle()
                    Toast.makeText(context, "头像保存失败，请稍后再试", Toast.LENGTH_SHORT).show()
                }
            }
        }
        showPaperSheet(dialog, root)
    }

    private fun showPaperSheet(dialog: BottomSheetDialog, root: View) {
        dismissActiveSheet()
        activeSheet = dialog
        dialog.setOnDismissListener {
            if (activeSheet === dialog) activeSheet = null
        }
        dialog.setContentView(root)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT)
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.skipCollapsed = true
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.show()
    }

    companion object {
        @Throws(IOException::class)
        internal fun decodeAvatarBitmap(resolver: ContentResolver, uri: Uri): Bitmap {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            } ?: throw IOException("无法打开图片")

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw IOException("图片尺寸无效")
            }
            var sample = 1
            while (max(bounds.outWidth / sample, bounds.outHeight / sample) > 1600) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            return resolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: throw IOException("无法解码图片")
        }
    }
}
