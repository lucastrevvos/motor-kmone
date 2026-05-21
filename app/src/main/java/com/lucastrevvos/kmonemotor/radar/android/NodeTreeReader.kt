package com.lucastrevvos.kmonemotor.radar.android

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.lucastrevvos.kmonemotor.radar.signals.NodeTreeSignature
import java.util.Locale

class NodeTreeReader {
    fun read(root: AccessibilityNodeInfo?): NodeTreeSignature {
        if (root == null) {
            return NodeTreeSignature.empty()
        }

        var nodeCount = 0
        var visibleTextNodeCount = 0
        var clickableNodeCount = 0
        var maxDepth = 0
        var bottomHalfTextNodeCount = 0
        var numericTextNodeCount = 0
        var buttonLikeNodeCount = 0
        val knownTexts = linkedSetOf<String>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to 0)

        while (queue.isNotEmpty()) {
            val (node, depth) = queue.removeFirst()
            nodeCount += 1
            maxDepth = maxOf(maxDepth, depth)

            val text = node.text?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) {
                visibleTextNodeCount += 1
                if (text.any(Char::isDigit)) {
                    numericTextNodeCount += 1
                }
                val lower = text.lowercase(Locale.ROOT)
                if (KNOWN_UBER_STATE_TEXTS.any { lower.contains(it) }) {
                    knownTexts += lower
                }
                val screenRect = Rect()
                node.getBoundsInScreen(screenRect)
                if (screenRect.centerY() > 0) {
                    bottomHalfTextNodeCount += 1
                }
            }

            if (node.isClickable) {
                clickableNodeCount += 1
            }
            if (node.className?.toString()?.contains("Button", ignoreCase = true) == true ||
                node.contentDescription?.toString()?.contains("button", ignoreCase = true) == true
            ) {
                buttonLikeNodeCount += 1
            }

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { child ->
                    queue.add(child to depth + 1)
                }
            }
        }

        return NodeTreeSignature(
            packageName = root.packageName?.toString(),
            nodeCount = nodeCount,
            visibleTextNodeCount = visibleTextNodeCount,
            clickableNodeCount = clickableNodeCount,
            maxDepth = maxDepth,
            bottomHalfTextNodeCount = bottomHalfTextNodeCount,
            numericTextNodeCount = numericTextNodeCount,
            buttonLikeNodeCount = buttonLikeNodeCount,
            knownStateTexts = knownTexts.toList()
        )
    }

    private companion object {
        val KNOWN_UBER_STATE_TEXTS = listOf(
            "offline",
            "online",
            "procurando corridas",
            "procurando viagens",
            "buscando corridas",
            "buscando",
            "página inicial",
            "pagina inicial",
            "ganhos",
            "ficar online",
            "mensagens",
            "menu",
            "conectar",
            "você está offline",
            "você está online",
            "uberx",
            "priority",
            "flash",
            "comfort",
            "black",
            "exclusivo",
            "r$",
            "min",
            "km"
        )
    }
}
