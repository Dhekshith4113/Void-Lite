package com.example.voidlite

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.AlertDialog
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

class HiddenAppListAdapter(
    private val context: Context,
    private var apps: MutableList<ApplicationInfo>,
    private val pm: PackageManager,
    val refreshList: () -> Unit,
    val showApp: (ApplicationInfo) -> Unit,
) : RecyclerView.Adapter<HiddenAppListAdapter.ViewHolder>() {

    @SuppressLint("ClickableViewAccessibility")
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ShapeableImageView = itemView.findViewById(R.id.appIcon)
        val name: TextView = itemView.findViewById(R.id.appName)
        val newText: TextView = itemView.findViewById(R.id.newText)

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
                true
            }
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = apps.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
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

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val app = apps[position]
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

        holder.newText.visibility = View.GONE
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

        dialogView.findViewById<TextView>(R.id.hideAppBtn).text = context.getString(R.string.unhide_app)

        dialogView.findViewById<TextView>(R.id.hideAppBtn).setOnClickListener {
            showApp(appInfo)
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

    fun getApps(): List<ApplicationInfo> {
        apps.sortedBy { it.packageName }
        return apps.toList()
    }

    fun removeApp(app: ApplicationInfo) {
        apps.removeAll { it.packageName == app.packageName }
        notifyDataSetChanged()
    }

}