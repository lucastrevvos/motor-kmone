package com.lucastrevvos.kmonemotor.radar.dedupe

class OfferFingerprintDedupeStore(
    private val clusterTtlMs: Long = OfferFingerprintDedupeEngine.OFFER_CLUSTER_TTL_MS
) {
    private val clustersById = linkedMapOf<String, OfferCandidateCluster>()
    private var nextClusterIndex = 0L

    @Synchronized
    fun snapshot(): List<OfferCandidateCluster> {
        return clustersById.values.sortedByDescending { it.updatedAtMs }
    }

    @Synchronized
    fun removeExpired(nowMs: Long): List<OfferCandidateCluster> {
        val expired = clustersById.values.filter { nowMs - it.updatedAtMs > clusterTtlMs }
        expired.forEach { clustersById.remove(it.clusterId) }
        return expired
    }

    @Synchronized
    fun upsert(cluster: OfferCandidateCluster) {
        clustersById[cluster.clusterId] = cluster
    }

    @Synchronized
    fun newClusterId(): String {
        nextClusterIndex += 1
        return "cluster_$nextClusterIndex"
    }
}
