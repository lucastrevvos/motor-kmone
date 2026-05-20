package com.lucastrevvos.kmonemotor.radar.piu

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

interface PiuOverlayViewHandle {
    val platformView: Any
    val estimatedWidthPx: Int
    fun updateEarningsText(value: String)
    fun updateAnalyzeButton(enabled: Boolean, label: String)
    fun bindAnalyzeClick(listener: () -> Unit)
    fun bindHorizontalDrag(currentXProvider: () -> Int, listener: (Int) -> Unit)
}

interface PiuOverlayViewFactory {
    fun create(initialEarningsText: String): PiuOverlayViewHandle
}

class AndroidPiuOverlayViewFactory(
    private val context: Context
) : PiuOverlayViewFactory {
    override fun create(initialEarningsText: String): PiuOverlayViewHandle {
        val density = context.resources.displayMetrics.density
        val estimatedWidth = (252 * density).roundToInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (12 * density).roundToInt(),
                (10 * density).roundToInt(),
                (12 * density).roundToInt(),
                (10 * density).roundToInt()
            )
            background = GradientDrawable().apply {
                cornerRadius = 24f * density
                setColor(Color.argb(236, 9, 17, 29))
                setStroke((1 * density).roundToInt(), Color.argb(120, 91, 255, 154))
            }
        }
        val infoColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val labelView = TextView(context).apply {
            text = "Total do dia"
            setTextColor(Color.argb(210, 159, 179, 201))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val textView = TextView(context).apply {
            text = initialEarningsText
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
        }
        infoColumn.addView(labelView)
        infoColumn.addView(textView)
        val analyzeButton = Button(context).apply {
            text = "Analisar"
            isAllCaps = false
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setTextColor(Color.argb(255, 7, 17, 29))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(
                (16 * density).roundToInt(),
                (10 * density).roundToInt(),
                (16 * density).roundToInt(),
                (10 * density).roundToInt()
            )
            background = GradientDrawable().apply {
                cornerRadius = 18f * density
                setColor(Color.argb(255, 91, 255, 154))
            }
        }
        container.addView(infoColumn)
        container.addView(analyzeButton)

        return AndroidPiuOverlayViewHandle(
            rootView = container,
            earningsTextView = textView,
            analyzeButton = analyzeButton,
            estimatedWidthPx = estimatedWidth
        )
    }
}

private class AndroidPiuOverlayViewHandle(
    private val rootView: View,
    private val earningsTextView: TextView,
    private val analyzeButton: Button,
    override val estimatedWidthPx: Int
) : PiuOverlayViewHandle {
    override val platformView: Any = rootView

    override fun updateEarningsText(value: String) {
        earningsTextView.text = value
    }

    override fun updateAnalyzeButton(enabled: Boolean, label: String) {
        analyzeButton.isEnabled = enabled
        analyzeButton.text = label
    }

    override fun bindAnalyzeClick(listener: () -> Unit) {
        analyzeButton.setOnClickListener { listener() }
    }

    override fun bindHorizontalDrag(currentXProvider: () -> Int, listener: (Int) -> Unit) {
        rootView.setOnTouchListener(object : View.OnTouchListener {
            private var initialTouchRawX = 0f
            private var initialOverlayX = 0

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                if (isTouchInsideAnalyzeButton(event)) {
                    return false
                }
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        initialTouchRawX = event.rawX
                        initialOverlayX = currentXProvider()
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchRawX).roundToInt()
                        listener(initialOverlayX + deltaX)
                        return true
                    }
                }
                return false
            }

            private fun isTouchInsideAnalyzeButton(event: MotionEvent): Boolean {
                val location = IntArray(2)
                analyzeButton.getLocationOnScreen(location)
                val left = location[0].toFloat()
                val top = location[1].toFloat()
                val right = left + analyzeButton.width
                val bottom = top + analyzeButton.height
                return event.rawX in left..right && event.rawY in top..bottom
            }
        })
    }
}
