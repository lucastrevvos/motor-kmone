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
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(
                (14 * density).roundToInt(),
                (14 * density).roundToInt(),
                (14 * density).roundToInt(),
                (14 * density).roundToInt()
            )
            background = GradientDrawable().apply {
                cornerRadius = 28f * density
                setColor(Color.argb(232, 7, 17, 28))
                setStroke((1 * density).roundToInt(), Color.argb(110, 91, 255, 154))
            }
        }

        val brandRail = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                (10 * density).roundToInt(),
                (12 * density).roundToInt(),
                (10 * density).roundToInt(),
                (12 * density).roundToInt()
            )
            background = GradientDrawable().apply {
                cornerRadius = 20f * density
                setColor(Color.argb(50, 91, 255, 154))
            }
        }
        val brandMonogram = TextView(context).apply {
            text = "KM"
            setTextColor(Color.argb(255, 91, 255, 154))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        val brandLabel = TextView(context).apply {
            text = "ONE"
            setTextColor(Color.argb(220, 160, 178, 198))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        brandRail.addView(brandMonogram)
        brandRail.addView(brandLabel)

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding((12 * density).roundToInt(), 0, 0, 0)
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val decisionBadge = chipView(density, bold = true)
        val productChip = chipView(density, bold = false)
        topRow.addView(decisionBadge)
        topRow.addView(productChip)

        val primaryMetricView = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val reasonView = TextView(context).apply {
            setTextColor(Color.argb(232, 244, 248, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val summaryView = TextView(context).apply {
            setTextColor(Color.argb(215, 160, 178, 198))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        val detailView = TextView(context).apply {
            setTextColor(Color.argb(220, 244, 248, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        val footerView = TextView(context).apply {
            setTextColor(Color.argb(200, 122, 144, 166))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            text = "Meta minima: R$ 1,50/km"
        }

        content.addView(topRow)
        content.addView(spaceView(density, 10))
        content.addView(primaryMetricView)
        content.addView(spaceView(density, 4))
        content.addView(reasonView)
        content.addView(spaceView(density, 6))
        content.addView(summaryView)
        content.addView(spaceView(density, 4))
        content.addView(detailView)
        content.addView(spaceView(density, 8))
        content.addView(footerView)

        root.addView(brandRail)
        root.addView(content, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        return AndroidDecisionOverlayViewHandle(
            rootView = root,
            brandRail = brandRail,
            decisionBadge = decisionBadge,
            productChip = productChip,
            primaryMetricView = primaryMetricView,
            reasonView = reasonView,
            summaryView = summaryView,
            detailView = detailView,
            footerView = footerView
        )
    }

    private fun chipView(density: Float, bold: Boolean): TextView {
        return TextView(context).apply {
            setPadding(
                (10 * density).roundToInt(),
                (6 * density).roundToInt(),
                (10 * density).roundToInt(),
                (6 * density).roundToInt()
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            background = GradientDrawable().apply {
                cornerRadius = 18f * density
            }
            visibility = View.GONE
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = (8 * density).roundToInt()
            layoutParams = params
        }
    }

    private fun spaceView(density: Float, heightDp: Int): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (heightDp * density).roundToInt()
            )
        }
    }
}

private class AndroidDecisionOverlayViewHandle(
    private val rootView: LinearLayout,
    private val brandRail: LinearLayout,
    private val decisionBadge: TextView,
    private val productChip: TextView,
    private val primaryMetricView: TextView,
    private val reasonView: TextView,
    private val summaryView: TextView,
    private val detailView: TextView,
    private val footerView: TextView
) : DecisionOverlayViewHandle {
    override val platformView: Any = rootView

    override fun render(presentation: DecisionPresentation) {
        val theme = overlayTheme(presentation.semantic)

        updateChip(
            view = decisionBadge,
            text = when (presentation.semantic) {
                DecisionSemantic.POSITIVE -> "PEGAR"
                DecisionSemantic.ATTENTION -> "ANALISAR"
                DecisionSemantic.NEGATIVE -> "NAO PEGAR"
                DecisionSemantic.NEUTRAL -> "SEM DECISAO"
                DecisionSemantic.BLOCKED -> "LEITURA INCERTA"
            },
            background = theme.badgeBackground,
            textColor = theme.badgeText
        )
        updateChip(
            view = productChip,
            text = presentation.productText ?: presentation.platformText,
            background = Color.argb(48, 90, 168, 255),
            textColor = Color.argb(255, 90, 168, 255)
        )

        primaryMetricView.text = presentation.primaryMetric ?: presentation.priceText ?: presentation.title
        reasonView.text = presentation.shortReason
        summaryView.text = buildSummaryLine(presentation)
        detailView.text = buildDetailLine(presentation)
        footerView.text = if (presentation.priceText != null) {
            "${presentation.priceText} • KM One"
        } else {
            "KM One"
        }

        (rootView.background as? GradientDrawable)?.apply {
            setStroke((1 * rootView.resources.displayMetrics.density).roundToInt(), theme.stroke)
        }
        (brandRail.background as? GradientDrawable)?.setColor(theme.railBackground)
        primaryMetricView.setTextColor(theme.primaryMetric)
    }

    private fun buildSummaryLine(presentation: DecisionPresentation): String {
        val parts = listOfNotNull(
            presentation.secondaryMetric,
            presentation.priceText
        )
        return if (parts.isNotEmpty()) parts.joinToString(" • ") else presentation.title
    }

    private fun buildDetailLine(presentation: DecisionPresentation): String {
        return when {
            presentation.details.isNotEmpty() -> presentation.details.joinToString(" • ")
            presentation.shortReason.isNotBlank() -> presentation.shortReason
            else -> "KM One"
        }
    }

    private fun updateChip(view: TextView, text: String?, background: Int, textColor: Int) {
        if (text.isNullOrBlank()) {
            view.visibility = View.GONE
            return
        }
        view.visibility = View.VISIBLE
        view.text = text
        view.setTextColor(textColor)
        (view.background as? GradientDrawable)?.apply {
            setColor(background)
            setStroke((1 * view.resources.displayMetrics.density).roundToInt(), textColor)
        }
    }

    private fun overlayTheme(semantic: DecisionSemantic): OverlayTheme {
        return when (semantic) {
            DecisionSemantic.POSITIVE -> OverlayTheme(
                stroke = Color.argb(190, 82, 214, 122),
                railBackground = Color.argb(48, 82, 214, 122),
                primaryMetric = Color.argb(255, 82, 214, 122),
                badgeBackground = Color.argb(54, 82, 214, 122),
                badgeText = Color.argb(255, 82, 214, 122)
            )
            DecisionSemantic.ATTENTION -> OverlayTheme(
                stroke = Color.argb(190, 255, 200, 87),
                railBackground = Color.argb(48, 255, 200, 87),
                primaryMetric = Color.argb(255, 255, 200, 87),
                badgeBackground = Color.argb(54, 255, 200, 87),
                badgeText = Color.argb(255, 255, 200, 87)
            )
            DecisionSemantic.NEGATIVE -> OverlayTheme(
                stroke = Color.argb(200, 255, 111, 125),
                railBackground = Color.argb(48, 255, 111, 125),
                primaryMetric = Color.argb(255, 255, 111, 125),
                badgeBackground = Color.argb(54, 255, 111, 125),
                badgeText = Color.argb(255, 255, 111, 125)
            )
            DecisionSemantic.NEUTRAL -> OverlayTheme(
                stroke = Color.argb(190, 125, 147, 168),
                railBackground = Color.argb(48, 125, 147, 168),
                primaryMetric = Color.argb(255, 244, 248, 255),
                badgeBackground = Color.argb(40, 125, 147, 168),
                badgeText = Color.argb(255, 125, 147, 168)
            )
            DecisionSemantic.BLOCKED -> OverlayTheme(
                stroke = Color.argb(190, 160, 178, 198),
                railBackground = Color.argb(48, 160, 178, 198),
                primaryMetric = Color.argb(255, 244, 248, 255),
                badgeBackground = Color.argb(40, 160, 178, 198),
                badgeText = Color.argb(255, 160, 178, 198)
            )
        }
    }

    private data class OverlayTheme(
        val stroke: Int,
        val railBackground: Int,
        val primaryMetric: Int,
        val badgeBackground: Int,
        val badgeText: Int
    )
}
