package com.lucastrevvos.kmonemotor.radar.decisionoverlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionPresentation
import com.lucastrevvos.kmonemotor.radar.presentation.DecisionSemantic
import kotlin.math.roundToInt

interface DecisionOverlayViewHandle {
    val platformView: Any
    fun render(presentation: DecisionPresentation)
}

interface DecisionOverlayViewFactory {
    fun create(): DecisionOverlayViewHandle
}

class AndroidDecisionOverlayViewFactory(
    private val context: Context
) : DecisionOverlayViewFactory {
    override fun create(): DecisionOverlayViewHandle {
        val density = context.resources.displayMetrics.density
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(
                (14 * density).roundToInt(),
                (10 * density).roundToInt(),
                (14 * density).roundToInt(),
                (10 * density).roundToInt()
            )
            background = GradientDrawable().apply {
                cornerRadius = 18f * density
                setColor(Color.argb(235, 16, 18, 24))
                setStroke((1 * density).roundToInt(), Color.argb(110, 255, 255, 255))
            }
        }
        val titleView = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val reasonView = TextView(context).apply {
            setTextColor(Color.argb(230, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val primaryMetricView = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val secondaryMetricView = TextView(context).apply {
            setTextColor(Color.argb(200, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        container.addView(titleView)
        container.addView(reasonView)
        container.addView(primaryMetricView)
        container.addView(secondaryMetricView)
        return AndroidDecisionOverlayViewHandle(
            rootView = container,
            titleView = titleView,
            reasonView = reasonView,
            primaryMetricView = primaryMetricView,
            secondaryMetricView = secondaryMetricView
        )
    }
}

private class AndroidDecisionOverlayViewHandle(
    private val rootView: LinearLayout,
    private val titleView: TextView,
    private val reasonView: TextView,
    private val primaryMetricView: TextView,
    private val secondaryMetricView: TextView
) : DecisionOverlayViewHandle {
    override val platformView: Any = rootView

    override fun render(presentation: DecisionPresentation) {
        titleView.text = presentation.title
        reasonView.text = presentation.shortReason
        primaryMetricView.text = presentation.primaryMetric ?: presentation.priceText ?: ""
        primaryMetricView.visibility = if (presentation.primaryMetric != null || presentation.priceText != null) View.VISIBLE else View.GONE
        secondaryMetricView.text = presentation.secondaryMetric ?: presentation.priceText ?: ""
        secondaryMetricView.visibility = if (presentation.secondaryMetric != null || presentation.priceText != null) View.VISIBLE else View.GONE
        val borderColor = when (presentation.semantic) {
            DecisionSemantic.POSITIVE -> Color.argb(220, 80, 200, 120)
            DecisionSemantic.ATTENTION -> Color.argb(220, 240, 180, 60)
            DecisionSemantic.NEGATIVE -> Color.argb(220, 220, 80, 80)
            DecisionSemantic.NEUTRAL -> Color.argb(220, 140, 160, 180)
            DecisionSemantic.BLOCKED -> Color.argb(220, 150, 150, 150)
        }
        (rootView.background as? GradientDrawable)?.setStroke((1 * rootView.resources.displayMetrics.density).roundToInt(), borderColor)
    }
}
