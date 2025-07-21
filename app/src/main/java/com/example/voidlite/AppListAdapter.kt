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
import android.graphics.Point
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import java.lang.ref.WeakReference

class AppListAdapter(
    private val context: Context,
    private var apps: MutableList<ApplicationInfo>,
    private val pm: PackageManager,
    val refreshList: () -> Unit,
    val hideApp: (ApplicationInfo) -> Unit,
    var onAppDragStarted: ((ApplicationInfo) -> Unit)? = null
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    private var newAppPackages: Set<String> = emptySet()
    private var selectedLetter: Char? = null // Track selected letter for dimming

    private val normalizedNameCache = mutableMapOf<String, String>()  // Cache for normalized app names to avoid repeated computation
    private val firstLetterCache = mutableMapOf<String, Char>() // Cache for first letters to avoid repeated computation

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ShapeableImageView = itemView.findViewById(R.id.appIcon)
        val name: TextView = itemView.findViewById(R.id.appName)
        val newAppName: TextView? = itemView.findViewById(R.id.newText)

        private var isDimmed = false    // Store current dimming state to avoid unnecessary updates

        private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val app = apps[adapterPosition]

                val intent = pm.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    context.startActivity(intent)
                }

                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                showAppOptionsDialog(context, apps[adapterPosition])
                return true
            }
        })

        init {
            itemView.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                false
            }
        }

        // Efficient method to update dimming state only when needed
        fun updateDimmingState(shouldDim: Boolean) {
            if (isDimmed != shouldDim) {
                isDimmed = shouldDim
                val alpha = if (shouldDim) 0.2f else 1.0f

                icon.alpha = alpha
                name.alpha = alpha
                newAppName?.alpha = alpha
            }
        }
    }

    // Ultra-fast method that updates only affected items
    fun setSelectedLetter(letter: Char?) {
        val previousLetter = selectedLetter
        selectedLetter = letter

        // Only update if the selection actually changed
        if (previousLetter != letter) {
            // Direct ViewHolder updates for maximum speed
            updateVisibleItemsDirectly()
        }
    }

    // Ultra-fast clear selection
    fun clearSelection() {
        if (selectedLetter != null) {
            selectedLetter = null
            // Direct update for maximum speed
            updateVisibleItemsDirectly()
        }
    }

    // Direct update method that bypasses RecyclerView's update mechanism for speed
    private fun updateVisibleItemsDirectly() {
        // Get reference to RecyclerView from context if needed
        // This assumes the adapter is attached to a RecyclerView
        val recyclerView = try {
            // Try to get RecyclerView reference from the first ViewHolder
            if (::recyclerViewRef.isInitialized) {
                recyclerViewRef.get()
            } else null
        } catch (e: Exception) {
            null
        }

        recyclerView?.let { rv ->
            // Update only visible items for maximum performance
            for (i in 0 until rv.childCount) {
                val child = rv.getChildAt(i)
                val viewHolder = rv.getChildViewHolder(child) as? ViewHolder
                val position = viewHolder?.adapterPosition ?: continue

                if (position >= 0 && position < apps.size) {
                    val shouldDim = shouldDimApp(apps[position])
                    viewHolder.updateDimmingState(shouldDim)
                }
            }
        }
    }

    // WeakReference to RecyclerView for direct updates
    private lateinit var recyclerViewRef: WeakReference<RecyclerView>

    // Method to set RecyclerView reference
    fun setRecyclerView(recyclerView: RecyclerView) {
        recyclerViewRef = WeakReference(recyclerView)
    }

    // Optimized helper method with caching
    private fun shouldDimApp(app: ApplicationInfo, letter: Char? = selectedLetter): Boolean {
        if (letter == null) return false

        val firstLetter = getFirstLetterCached(app)
        return firstLetter != letter
    }

    // Cached method to get first letter
    private fun getFirstLetterCached(app: ApplicationInfo): Char {
        return firstLetterCache.getOrPut(app.packageName) {
            val normalizedName = getNormalizedNameCached(app)
            normalizedName.first().uppercaseChar()
        }
    }

    // Cached method to get normalized name
    private fun getNormalizedNameCached(app: ApplicationInfo): String {
        return normalizedNameCache.getOrPut(app.packageName) {
            MainActivity().normalizeAppName(app.loadLabel(pm).toString())
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (SharedPreferencesManager.isAppDrawerEnabled(context)) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_GRID) R.layout.item_app_grid else R.layout.item_app
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = apps.size

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
                app.loadIcon(pm)    // Fallback to regular icon
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
            ContextCompat.getDrawable(context, R.drawable.themed_icon_background_rounded)
        } else {
            ContextCompat.getDrawable(context, R.drawable.squricle_512_271)
        }
        backgroundDrawable?.setTint(ContextCompat.getColor(context, R.color.themed_icon_background))

        // Tint the monochrome icon with your desired color
        val tintedIcon = monochromeIcon.mutate()
        tintedIcon.setTint(ContextCompat.getColor(context, R.color.themed_icon_foreground))

        // Create layered drawable with background and scaled foreground
        val layerDrawable = LayerDrawable(arrayOf(backgroundDrawable, tintedIcon))

        // Use negative padding to make the icon extend beyond the background bounds
        val negativePadding = (-18 * context.resources.displayMetrics.density).toInt() // -18dp
        layerDrawable.setLayerInset(1, negativePadding, negativePadding, negativePadding, negativePadding)

        return layerDrawable
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val app = apps[position]

        // Handle partial updates for dimming
        if (payloads.isNotEmpty() && payloads.contains("DIMMING_UPDATE")) {
            // Only update dimming state, skip expensive operations
            holder.updateDimmingState(shouldDimApp(app))
            return
        }

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

        // Apply initial dimming state
        holder.updateDimmingState(shouldDimApp(app))

        if (SharedPreferencesManager.isAppDrawerEnabled(context)) {
            holder.icon.visibility = View.VISIBLE
            if (SharedPreferencesManager.isHideAppNameEnabled(context)) {
                holder.name.text = ""
            }
        } else {
            if (SharedPreferencesManager.isShowAppIconEnabled(context)) {
                holder.icon.visibility = View.VISIBLE
            } else {
                holder.icon.visibility = View.GONE
            }
        }

        if (newAppPackages.contains(app.packageName)) {
            holder.newAppName?.visibility = View.VISIBLE
        } else {
            holder.newAppName?.visibility = View.GONE
        }

        holder.itemView.setOnLongClickListener {
            val clipData = ClipData.newPlainText("packageName", app.packageName)
            val shadow = AppIconDragShadowBuilder(context, app, pm)
            it.startDragAndDrop(clipData, shadow, app, 0)
            onAppDragStarted?.invoke(app)
            true
        }
    }

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
            if (SharedPreferencesManager.getAppIconShape(context) == "round") {
                iconImageView.setImageDrawable(loadRegularIcon(appInfo))
            } else {
                iconImageView.setImageDrawable(appInfo.loadIcon(pm))
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogView.findViewById<TextView>(R.id.uninstallBtn).setOnClickListener {
            val packageUri = Uri.parse("package:${appInfo.packageName}") // Replace with target package
            val intent = Intent(Intent.ACTION_DELETE, packageUri)
            context.startActivity(intent)
            refreshList()
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.hideAppBtn).text = context.getString(R.string.hide_app)

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
        // Clear caches when data changes
        normalizedNameCache.clear()
        firstLetterCache.clear()

        this.apps = newApps
        notifyDataSetChanged()
    }

    fun setApps(newApps: Set<String>) {
        this.newAppPackages = newApps
        notifyDataSetChanged()
    }

    fun addApp(app: ApplicationInfo) {
        if (apps.none { it.packageName == app.packageName }) {
            apps.add(app)
            apps.sortBy { getNormalizedNameCached(it).lowercase() }
            notifyDataSetChanged()
        }
    }

    fun removeApp(app: ApplicationInfo) {
        // Remove from caches
        normalizedNameCache.remove(app.packageName)
        firstLetterCache.remove(app.packageName)

        apps.removeAll { it.packageName == app.packageName }
        notifyDataSetChanged()
    }

    fun getApps(): List<ApplicationInfo> {
        apps.sortBy { getNormalizedNameCached(it).lowercase() }
        return apps.toList()
    }

    // Method to clear caches if memory is needed
    fun clearCaches() {
        normalizedNameCache.clear()
        firstLetterCache.clear()
    }

}

class AppIconDragShadowBuilder(val context: Context, appInfo: ApplicationInfo, private val pm: PackageManager) : View.DragShadowBuilder() {

    private val icon: Drawable

    init {
        val pm = context.packageManager
        icon = if (SharedPreferencesManager.isThemedIconsEnabled(context)) {
            loadThemedIcon(appInfo)
        } else {
            if (SharedPreferencesManager.getAppIconShape(context) == "round") {
                loadRegularIcon(appInfo)
            } else {
                appInfo.loadIcon(pm)
            }
        }
        icon.setBounds(0, 0, 48.dp, 48.dp)
    }

    override fun onProvideShadowMetrics(size: Point, touch: Point) {
        size.set(48.dp, 48.dp)
        touch.set(size.x / 2, size.y / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        icon.draw(canvas)
    }

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()

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
            ContextCompat.getDrawable(context, R.drawable.themed_icon_background_rounded)
        } else {
            ContextCompat.getDrawable(context, R.drawable.squricle_512_271)
        }
        backgroundDrawable?.setTint(ContextCompat.getColor(context, R.color.themed_icon_background))

        // Tint the monochrome icon with your desired color
        val tintedIcon = monochromeIcon.mutate()
        tintedIcon.setTint(ContextCompat.getColor(context, R.color.themed_icon_foreground))

        // Create layered drawable with background and scaled foreground
        val layerDrawable = LayerDrawable(arrayOf(backgroundDrawable, tintedIcon))

        // Use negative padding to make the icon extend beyond the background bounds
        val negativePadding = (-18 * context.resources.displayMetrics.density).toInt() // -18dp
        layerDrawable.setLayerInset(1, negativePadding, negativePadding, negativePadding, negativePadding)

        return layerDrawable
    }
}