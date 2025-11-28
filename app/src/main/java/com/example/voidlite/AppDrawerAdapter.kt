package com.example.voidlite

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat.getColor
import androidx.core.content.ContextCompat.getDrawable
import androidx.core.content.ContextCompat.getString
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView

class AppDrawerAdapter(
    private val context: Context,
    private val pm: PackageManager,
    private var appList: MutableList<ApplicationInfo>,
    private val onSave: (List<ApplicationInfo>) -> Unit,
    val refreshList: () -> Unit,
    val hideApp: (ApplicationInfo) -> Unit,
    var onAppDragStarted: ((ApplicationInfo) -> Unit)? = null
) : RecyclerView.Adapter<AppDrawerAdapter.ViewHolder>() {

    private val drawerAppSize: Int = SharedPreferencesManager.getMiniAppDrawerCount(context)
    private var parent: RecyclerView? = null
    private var spacingDecoration: CenterSpacingDecoration? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        parent = recyclerView

        // Find the spacing decoration
        for (i in 0 until recyclerView.itemDecorationCount) {
            val decoration = recyclerView.getItemDecorationAt(i)
            if (decoration is CenterSpacingDecoration) {
                spacingDecoration = decoration
                break
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        parent = null
        spacingDecoration = null
    }

    companion object {
        const val DROP_INDICATOR_PACKAGE = "__DROP_INDICATOR__"
        private var dropIndicatorItem: ApplicationInfo? = null

        fun getDropIndicatorItem(): ApplicationInfo {
            if (dropIndicatorItem == null) {
                dropIndicatorItem = ApplicationInfo().apply {
                    packageName = DROP_INDICATOR_PACKAGE
                }
            }
            return dropIndicatorItem!!
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val miniDrawerLayout: LinearLayout = view.findViewById(R.id.miniDrawerLayout)
        val icon: ShapeableImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)

        private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val position = adapterPosition
                if (position == RecyclerView.NO_POSITION || position >= appList.size) return false

                val app = appList[position]
                if (app.packageName == DROP_INDICATOR_PACKAGE) return false

                val intent = pm.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    context.startActivity(intent)
                }

                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val position = adapterPosition
                Log.d("onDoubleTap", "position = $position")
                if (position == RecyclerView.NO_POSITION || position >= appList.size) return false

                val app = appList[position]
                if (app.packageName == DROP_INDICATOR_PACKAGE) return false
                showAppOptionsDialog(context, app)

                return true
            }
        })

        init {
            view.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_icon_only, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = appList.size

    override fun getItemViewType(position: Int): Int {
        return if (position < appList.size && appList[position].packageName == DROP_INDICATOR_PACKAGE) 1 else 0
    }

    private fun loadRegularIcon(app: ApplicationInfo): Drawable {
        return try {
            val regularIcon = app.loadIcon(pm)
            applyShapedIconStyling(regularIcon)
        } catch (e: Exception) {
            app.loadIcon(pm)
        }
    }

    private fun applyShapedIconStyling(regularIcon: Drawable): Drawable {
        // Create a bitmap from the regular icon
        val iconBitmap = drawableToBitmap(regularIcon)

        // Create a shaped bitmap
        val shapedBitmap = createShapedBitmap(iconBitmap)

        return BitmapDrawable(context.resources, shapedBitmap)
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }

        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.takeIf { it > 0 } ?: 512,
            drawable.intrinsicHeight.takeIf { it > 0 } ?: 512,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun createShapedBitmap(originalBitmap: Bitmap): Bitmap {
        val size = maxOf(originalBitmap.width, originalBitmap.height)
        println(size)
        val outputBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Create perfect circular path
        val path = Path()
        val radius = size / 2.1f
        path.addCircle(size / 2f, size / 2f, radius, Path.Direction.CW)

        // Clip canvas to the circular shape
        canvas.clipPath(path)

        // Scale and center the original bitmap
        val scale = size.toFloat() / maxOf(originalBitmap.width, originalBitmap.height)
        val scaledWidth = originalBitmap.width * scale
        val scaledHeight = originalBitmap.height * scale
        val left = (size - scaledWidth) / 2f
        val top = (size - scaledHeight) / 2f

        val matrix = Matrix()
        matrix.setScale(scale, scale)
        matrix.postTranslate(left, top)

        canvas.drawBitmap(originalBitmap, matrix, paint)

        return outputBitmap
    }

    private fun loadThemedIcon(app: ApplicationInfo): Drawable {
        return try {
            val monochromeIcon = getMonochromeIcon(app)
            if (monochromeIcon != null) {
                applyThemedIconStyling(monochromeIcon)
            } else {
                // Fallback to regular icon
                app.loadIcon(pm)
            }
        } catch (e: Exception) {
            app.loadIcon(pm)
        }
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun getMonochromeIcon(app: ApplicationInfo): Drawable? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val icon = pm.getApplicationIcon(app)
                if (icon is AdaptiveIconDrawable) {
                    icon.monochrome
                } else null
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun applyThemedIconStyling(monochromeIcon: Drawable): Drawable {
        // Create a themed background
        val backgroundDrawable = if (SharedPreferencesManager.getAppIconShape(context) == "round") {
            getDrawable(context, R.drawable.themed_icon_background_rounded)
        } else {
            getDrawable(context, R.drawable.squricle_512_271)
        }
        backgroundDrawable?.setTint(getColor(context, R.color.themed_icon_background))

        // Tint the monochrome icon with your desired color
        val tintedIcon = monochromeIcon.mutate()
        tintedIcon.setTint(getColor(context, R.color.themed_icon_foreground))

        // Create layered drawable with background and scaled foreground
        val layerDrawable = LayerDrawable(arrayOf(backgroundDrawable, tintedIcon))

        // Use negative padding to make the icon extend beyond the background bounds
        val negativePadding = (-18 * context.resources.displayMetrics.density).toInt() // -18dp
        layerDrawable.setLayerInset(1, negativePadding, negativePadding, negativePadding, negativePadding)

        return layerDrawable
    }



    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position >= appList.size) return

        val app = appList[position]

        if (app.packageName == DROP_INDICATOR_PACKAGE) {
            holder.icon.setImageResource(0)
            if (SharedPreferencesManager.getAppIconShape(context) == "round") {
                holder.icon.setBackgroundResource(R.drawable.drop_indicator_round)
            } else {
                holder.icon.setBackgroundResource(R.drawable.drop_indicator)
            }
            holder.name.text = ""

            if (SharedPreferencesManager.isLandscapeMode(context)) {
                if (SharedPreferencesManager.isShowMiniAppNameEnabled(context)) {
                    holder.name.visibility = View.VISIBLE
                } else {
                    holder.name.visibility = View.GONE
                }
            } else {
                if (SharedPreferencesManager.isShowMiniAppNameEnabled(context)) {
                    holder.name.visibility = View.VISIBLE
                    holder.miniDrawerLayout.setPadding(0, 6.dp, 0, 6.dp)
                } else {
                    holder.name.visibility = View.GONE
                    holder.miniDrawerLayout.setPadding(0, 18.dp, 0, 0)
                }
            }

            holder.itemView.setOnLongClickListener(null)
        } else {
            holder.name.text = MainActivity().normalizeAppName(app.loadLabel(pm).toString())
            holder.icon.setBackgroundResource(0)

            if (SharedPreferencesManager.isThemedIconsEnabled(context)) {
                holder.icon.setImageDrawable(loadThemedIcon(app))
            } else {
                if (SharedPreferencesManager.getAppIconShape(context) == "round") {
                    holder.icon.setImageDrawable(loadRegularIcon(app))
                } else {
                    holder.icon.setImageDrawable(app.loadIcon(pm))
                }
            }

            if (SharedPreferencesManager.isLandscapeMode(context)) {
                if (SharedPreferencesManager.isShowMiniAppNameEnabled(context)) {
                    holder.name.visibility = View.VISIBLE
                } else {
                    holder.name.visibility = View.GONE
                }
            } else {
                if (SharedPreferencesManager.isShowMiniAppNameEnabled(context)) {
                    holder.name.visibility = View.VISIBLE
                    holder.miniDrawerLayout.setPadding(0, 6.dp, 0, 6.dp)
                } else {
                    holder.name.visibility = View.GONE
                    holder.miniDrawerLayout.setPadding(0, 18.dp, 0, 18.dp)
                }
            }

            holder.itemView.setOnLongClickListener {
                val clipData = ClipData.newPlainText("packageName", app.packageName)
                val shadow = View.DragShadowBuilder(it)
                it.startDragAndDrop(clipData, shadow, app, 0)
                onAppDragStarted?.invoke(app)
                true
            }
        }
    }

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()

    private fun showAppOptionsDialog(context: Context, appInfo: ApplicationInfo) {
        val packageManager = context.packageManager
        val appName = MainActivity().normalizeAppName(appInfo.loadLabel(packageManager).toString())

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_app_options, null)
        val nameTextView = dialogView.findViewById<TextView>(R.id.appNameText)
        val iconImageView = dialogView.findViewById<ImageView>(R.id.appIconImage)

        nameTextView.text = appName
        if (SharedPreferencesManager.isThemedIconsEnabled(context)) {
            iconImageView.setImageDrawable(loadThemedIcon(appInfo))
        } else {
            iconImageView.setImageDrawable(appInfo.loadIcon(pm))
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogView.findViewById<TextView>(R.id.uninstallBtn).setOnClickListener {
            val packageUri = Uri.parse("package:${appInfo.packageName}")
            val intent = Intent(Intent.ACTION_DELETE, packageUri)
            context.startActivity(intent)
            refreshList()
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.hideAppBtn).text = getString(context, R.string.hide_app)

        dialogView.findViewById<TextView>(R.id.hideAppBtn).setOnClickListener {
            hideApp(appInfo)
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.appInfoBtn).setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${appInfo.packageName}")
            context.startActivity(intent)
            dialog.dismiss()
        }

        dialog.show()
    }

    fun updateData(newApps: MutableList<ApplicationInfo>) {
        this.appList = newApps
        notifyDataSetChanged()

        // Force spacing recalculation
        parent?.post {
            spacingDecoration?.invalidateSpacing()
            parent?.invalidateItemDecorations()
        }
    }

    fun hasDropIndicator(): Boolean {
        return appList.any { it.packageName == DROP_INDICATOR_PACKAGE }
    }

    fun insertDropIndicator(position: Int) {
        if (hasDropIndicator()) return

        val safePosition = position.coerceIn(0, appList.size)
        appList.add(safePosition, getDropIndicatorItem())
        notifyItemInserted(safePosition)

        // Force spacing recalculation
        parent?.post {
            spacingDecoration?.invalidateSpacing()
            parent?.invalidateItemDecorations()
        }
    }

    fun moveDropIndicator(toPosition: Int) {
        val dropIndex = appList.indexOfFirst { it.packageName == DROP_INDICATOR_PACKAGE }
        if (dropIndex == -1) return

        val safePosition = toPosition.coerceIn(0, appList.size - 1)
        if (dropIndex == safePosition) return

        // Use notifyItemMoved for smooth animation
        appList.removeAt(dropIndex)
        appList.add(safePosition, getDropIndicatorItem())
        notifyItemMoved(dropIndex, safePosition)

        // Force spacing recalculation after animation
        parent?.postDelayed({
            spacingDecoration?.invalidateSpacing()
            parent?.invalidateItemDecorations()
        }, 250) // Wait for animation to complete
    }

    fun removeDropIndicator() {
        val index = appList.indexOfFirst { it.packageName == DROP_INDICATOR_PACKAGE }
        if (index == -1) return

        appList.removeAt(index)
        notifyItemRemoved(index)

        // Force spacing recalculation after removal
        parent?.post {
            spacingDecoration?.invalidateSpacing()
            parent?.invalidateItemDecorations()
        }
    }

    fun addAppAtPosition(app: ApplicationInfo, position: Int) {
        if (appList.any { it.packageName == app.packageName } ||
            appList.count { it.packageName != DROP_INDICATOR_PACKAGE } >= drawerAppSize) {
            return
        }

        val safePosition = position.coerceIn(0, appList.size)
        appList.add(safePosition, app)
        notifyItemInserted(safePosition)
        onSave(appList.filter { it.packageName != DROP_INDICATOR_PACKAGE })

        // Force spacing recalculation
        parent?.post {
            spacingDecoration?.invalidateSpacing()
            parent?.invalidateItemDecorations()
        }
    }

    fun removeApp(app: ApplicationInfo) {
        val indices = appList.mapIndexedNotNull { index, appInfo ->
            if (appInfo.packageName == app.packageName) index else null
        }

        indices.reversed().forEach { index ->
            appList.removeAt(index)
            notifyItemRemoved(index)
        }

        // Force spacing recalculation
        parent?.post {
            spacingDecoration?.invalidateSpacing()
            parent?.invalidateItemDecorations()
        }
    }

    fun getApps(): List<ApplicationInfo> = appList.toList()
}