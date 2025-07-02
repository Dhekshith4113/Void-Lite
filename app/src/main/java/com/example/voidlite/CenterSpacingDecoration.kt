package com.example.voidlite

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class CenterSpacingDecoration : RecyclerView.ItemDecoration() {
    private var itemWidth = 0
    private var calculatedSpacing = 8 // Default spacing

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val adapter = parent.adapter ?: return
        val itemCount = adapter.itemCount
        if (itemCount == 0) return

        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val parentWidth = parent.width
        if (parentWidth <= 0) return

        // Force measure the view if not measured
        if (view.measuredWidth <= 0) {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.measure(widthSpec, heightSpec)
        }

        val currentItemWidth = view.measuredWidth

        // Use cached width if available and consistent
        if (itemWidth <= 0 || abs(currentItemWidth - itemWidth) > 10) {
            itemWidth = currentItemWidth
        }

        if (itemWidth <= 0) {
            // Fallback to default spacing
            outRect.left = 8
            outRect.right = if (position == itemCount - 1) 8 else 0
            return
        }

        val totalItemsWidth = itemWidth * itemCount
        val remainingSpace = parentWidth - totalItemsWidth

        if (remainingSpace > 0 && itemCount >= 1) {
            // Calculate even spacing
            calculatedSpacing = remainingSpace / (itemCount + 1)
            outRect.left = calculatedSpacing
            if (position == itemCount - 1) {
                outRect.right = calculatedSpacing
            }
        } else {
            // Fallback to default spacing when items don't fit
            outRect.left = 8
            outRect.right = if (position == itemCount - 1) 8 else 0
        }
    }

    // Method to force recalculation
    fun invalidateSpacing() {
        itemWidth = 0
        calculatedSpacing = 8
    }
}