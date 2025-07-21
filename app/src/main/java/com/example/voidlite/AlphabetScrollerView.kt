package com.example.voidlite

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.LinearLayoutManager

class AlphabetScrollerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private var usedAlphabets: List<Char> = emptyList()
    private var indexMap: Map<Char, Int> = emptyMap()
    private var lastIndex = -1
    private var bubbleBackground: Drawable? = null
    private var layoutManager: LinearLayoutManager? = null
    private var floatingBubble: TextView? = null
    private var rootOverlay: ViewGroup? = null

    private var gradientUpdateListener: GradientUpdateListener? = null

    private var appListAdapter: AppListAdapter? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
    }

    fun setGradientUpdateListener(listener: GradientUpdateListener) {
        this.gradientUpdateListener = listener
    }

    fun setAppListAdapter(adapter: AppListAdapter) {
        this.appListAdapter = adapter
    }

    fun setup(
        alphabets: List<Char>,
        indexMap: Map<Char, Int>,
        recyclerLayoutManager: LinearLayoutManager,
        bubbleBackground: Drawable? = null
    ) {
        this.usedAlphabets = alphabets
        this.indexMap = indexMap
        this.layoutManager = recyclerLayoutManager
        this.bubbleBackground = bubbleBackground
        refreshLetters()
    }

    fun enableFloatingBubble(bubbleParent: ViewGroup) {
        rootOverlay = bubbleParent
        floatingBubble = TextView(context).apply {
            layoutParams = LayoutParams(32.dp, 32.dp).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            background = AppCompatResources.getDrawable(context, R.drawable.bubble_background)
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Color.WHITE)
            visibility = View.GONE
            elevation = 20f
        }
        rootOverlay?.addView(floatingBubble)
    }

    private fun refreshLetters() {
        removeAllViews()
        usedAlphabets.forEach { letter ->
            val textView = TextView(context).apply {
                text = letter.toString()
                textSize = 14f
                setTextColor(context.getColor(R.color.textColorPrimary))
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(4.dp, 2.dp, 4.dp, 2.dp)
                    gravity = Gravity.CENTER
                }
                setPadding(4.dp, 2.dp, 4.dp, 2.dp)
            }
            addView(textView)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val itemHeight = height / usedAlphabets.size
                val index = (event.y / itemHeight).toInt().coerceIn(0, usedAlphabets.size - 1)
                val selectedChar = usedAlphabets[index]
                if (index != lastIndex) {
                    indexMap[selectedChar]?.let {
                        if (index == 0) {
                            layoutManager?.scrollToPositionWithOffset(it, 0)
                        } else {
                            layoutManager?.scrollToPositionWithOffset(it - 3, 0)
                        }
                        post {
                            gradientUpdateListener?.updateGradients()
                        }
                    }

                    appListAdapter?.setSelectedLetter(selectedChar) // Apply dimming effect to the adapter
                }

                lastIndex = index

                floatingBubble?.let { bubble ->
                    bubble.text = selectedChar.toString()
                    bubble.visibility = View.VISIBLE

                    // Calculate X position so it appears to the *left* of AlphabetScrollerView
                    val location = IntArray(2)
                    getLocationOnScreen(location)
                    val alphabetScrollerX = location[0]
                    val bubbleX = alphabetScrollerX - bubble.width - 24.dp
                    val bubbleY = event.rawY - bubble.height * 1.5f   // Calculate Y position based on raw touch

                    bubble.x = bubbleX.toFloat()
                    bubble.y = bubbleY
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                lastIndex = -1
                floatingBubble?.visibility = View.GONE

                appListAdapter?.clearSelection()    // Clear dimming effect when touch ends
            }
        }
        return true
    }

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()
}