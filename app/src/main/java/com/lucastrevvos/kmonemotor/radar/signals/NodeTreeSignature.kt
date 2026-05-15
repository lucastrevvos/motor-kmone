package com.lucastrevvos.kmonemotor.radar.signals

data class NodeTreeSignature(
    val packageName: String?,
    val nodeCount: Int,
    val visibleTextNodeCount: Int,
    val clickableNodeCount: Int,
    val maxDepth: Int,
    val bottomHalfTextNodeCount: Int,
    val numericTextNodeCount: Int,
    val buttonLikeNodeCount: Int,
    val knownStateTexts: List<String>
) {
    fun roughlyMatches(other: NodeTreeSignature?): Boolean {
        if (other == null) return false
        if (packageName != other.packageName) return false
        return kotlin.math.abs(nodeCount - other.nodeCount) <= 8 &&
            kotlin.math.abs(visibleTextNodeCount - other.visibleTextNodeCount) <= 4 &&
            kotlin.math.abs(clickableNodeCount - other.clickableNodeCount) <= 3
    }

    companion object {
        fun empty() = NodeTreeSignature(
            packageName = null,
            nodeCount = 0,
            visibleTextNodeCount = 0,
            clickableNodeCount = 0,
            maxDepth = 0,
            bottomHalfTextNodeCount = 0,
            numericTextNodeCount = 0,
            buttonLikeNodeCount = 0,
            knownStateTexts = emptyList()
        )
    }
}
