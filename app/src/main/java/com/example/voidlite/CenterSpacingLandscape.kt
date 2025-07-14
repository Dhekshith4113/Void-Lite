package com.example.voidlite

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class CenterSpacingLandscape : RecyclerView.ItemDecoration() {
    private var itemHeight = 0
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

        val parentHeight = parent.height
        if (parentHeight <= 0) return

        // Force measure the view if not measured
        if (view.measuredHeight <= 0) {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.measure(widthSpec, heightSpec)
        }

        val currentItemHeight = view.measuredHeight

        // Use cached width if available and consistent
        if (itemHeight <= 0 || abs(currentItemHeight - itemHeight) > 10) {
            itemHeight = currentItemHeight
        }

        if (itemHeight <= 0) {
            // Fallback to default spacing
            outRect.top = 8
            outRect.bottom = if (position == itemCount - 1) 8 else 0
            return
        }

        val totalItemsHeight = itemHeight * itemCount
        val remainingSpace = parentHeight - totalItemsHeight

        if (remainingSpace > 0 && itemCount >= 1) {
            // Calculate even spacing
            calculatedSpacing = remainingSpace / (itemCount + 1)
            outRect.top = calculatedSpacing
            if (position == itemCount - 1) {
                outRect.bottom = calculatedSpacing
            }
        } else {
            // Fallback to default spacing when items don't fit
            outRect.top = 8
            outRect.bottom = if (position == itemCount - 1) 8 else 0
        }
    }

    // Method to force recalculation
    fun invalidateSpacing() {
        itemHeight = 0
        calculatedSpacing = 8
    }
}