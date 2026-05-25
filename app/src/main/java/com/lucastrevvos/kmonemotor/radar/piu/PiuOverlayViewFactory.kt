package com.lucastrevvos.kmonemotor.radar.piu

import android.content.res.ColorStateList
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.lucastrevvos.kmonemotor.R
import kotlin.math.max
import kotlin.math.roundToInt

interface PiuOverlayViewHandle {
    val platformView: Any
    val estimatedWidthPx: Int
    val currentWidthPx: Int
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
        val minimumContainerWidth = (156 * density).roundToInt()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumWidth = minimumContainerWidth
            setPadding(
                (10 * density).roundToInt(),
                (8 * density).roundToInt(),
                (10 * density).roundToInt(),
                (8 * density).roundToInt()
            )
            background = GradientDrawable().apply {
                cornerRadius = 26f * density
                setColor(Color.argb(236, 9, 17, 29))
                setStroke((1 * density).roundToInt(), Color.argb(132, 0, 230, 118))
            }
        }
        val statusIndicator = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (8 * density).roundToInt(),
                (8 * density).roundToInt()
            ).apply {
                marginEnd = (8 * density).roundToInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(255, 91, 255, 154))
            }
        }
        val earningsTextView = TextView(context).apply {
            text = initialEarningsText
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (10 * density).roundToInt()
            }
        }
        val analyzeButton = ImageButton(context).apply {
            contentDescription = "Analisar"
            minimumHeight = 0
            minimumWidth = 0
            layoutParams = LinearLayout.LayoutParams(
                (44 * density).roundToInt(),
                (44 * density).roundToInt()
            )
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setImageResource(R.drawable.ic_eye)
            setImageTintList(ColorStateList.valueOf(Color.argb(255, 7, 17, 29)))
            imageAlpha = 255
            setPadding(
                (10 * density).roundToInt(),
                (10 * density).roundToInt(),
                (10 * density).roundToInt(),
                (10 * density).roundToInt()
            )
            background = GradientDrawable().apply {
                cornerRadius = 20f * density
                setColor(Color.argb(255, 91, 255, 154))
            }
        }
        container.contentDescription = "Radar KM One. Total do dia $initialEarningsText"
        container.addView(statusIndicator)
        container.addView(earningsTextView)
        container.addView(analyzeButton)
        container.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val estimatedWidth = max(container.measuredWidth, minimumContainerWidth)

        return AndroidPiuOverlayViewHandle(
            rootView = container,
            statusIndicator = statusIndicator,
            earningsTextView = earningsTextView,
            analyzeButton = analyzeButton,
            estimatedWidthPx = estimatedWidth
        )
    }
}

private class AndroidPiuOverlayViewHandle(
    private val rootView: View,
    private val statusIndicator: View,
    private val earningsTextView: TextView,
    private val analyzeButton: ImageButton,
    override val estimatedWidthPx: Int
) : PiuOverlayViewHandle {
    override val platformView: Any = rootView
    override val currentWidthPx: Int
        get() = rootView.width.takeIf { it > 0 } ?: estimatedWidthPx

    override fun updateEarningsText(value: String) {
        earningsTextView.text = value
        rootView.contentDescription = "Radar KM One. Total do dia $value"
    }

    override fun updateAnalyzeButton(enabled: Boolean, label: String) {
        analyzeButton.isEnabled = enabled
        analyzeButton.contentDescription = label
        analyzeButton.alpha = if (enabled) 1f else 0.72f
        statusIndicator.alpha = if (enabled) 1f else 0.55f
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
