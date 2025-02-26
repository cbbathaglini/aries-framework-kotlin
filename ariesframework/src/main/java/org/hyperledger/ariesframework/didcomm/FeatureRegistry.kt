package org.hyperledger.ariesframework.didcomm

import org.hyperledger.ariesframework.didcomm.models.Feature
import org.hyperledger.ariesframework.didcomm.models.FeatureQuery

class FeatureRegistry {
    private val features: MutableList<Feature> = mutableListOf()

    /**
     * Register a single or set of Features in the registry.
     *
     * @param features vararg of Feature objects or any inherited class
     */
    fun register(vararg features: Feature) {
        for (feature in features) {
            val index = this.features.indexOfFirst { it.type == feature.type && it.id == feature.id }

            if (index >= 0) {
                this.features[index] = this.features[index].combine(feature)
            } else {
                this.features.add(feature)
            }
        }
    }

    /**
     * Perform a set of queries in the registry, supporting wildcards (*) as
     * expressed in Aries RFC 0557.
     *
     * @see https://github.com/hyperledger/aries-rfcs/blob/560ffd23361f16a01e34ccb7dcc908ec28c5ddb1/features/0557-discover-features-v2/README.md
     *
     * @param queries vararg of FeatureQuery objects to query features
     * @return List containing all matching features (can be empty)
     */
    fun query(vararg queries: FeatureQuery): List<Feature> {
        val output = mutableListOf<Feature>()

        for (query in queries) {
            val items = features.filter { it.type == query.featureType }

            when {
                query.match == "*" -> {
                    output.addAll(items)
                }
                query.match.endsWith("*") -> {
                    val matchPrefix = query.match.dropLast(1)
                    output.addAll(items.filter { it.id.startsWith(matchPrefix) })
                }
                else -> {
                    output.addAll(items.filter { it.id == query.match })
                }
            }
        }
        return output
    }
}