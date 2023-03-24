package io.emeraldpay.dshackle.config

import io.emeraldpay.dshackle.Chain

data class ChainsConfig(private val chains: Map<Chain, RawChainConfig>, val currentDefault: RawChainConfig?) {
    companion object {
        @JvmStatic
        fun default(): ChainsConfig = ChainsConfig(emptyMap(), RawChainConfig.default())
    }

    class RawChainConfig {
        var syncingLagSize: Int? = null
        var laggingLagSize: Int? = null
        var options: UpstreamsConfig.PartialOptions? = null

        companion object {
            @JvmStatic
            fun default() = RawChainConfig()
                .apply {
                    syncingLagSize = 6
                    laggingLagSize = 1
                }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as RawChainConfig

            if (syncingLagSize != other.syncingLagSize) return false
            if (laggingLagSize != other.laggingLagSize) return false
            if (options != other.options) return false

            return true
        }

        override fun hashCode(): Int {
            var result = syncingLagSize ?: 0
            result = 31 * result + (laggingLagSize ?: 0)
            result = 31 * result + (options?.hashCode() ?: 0)
            return result
        }
    }

    data class ChainConfig(
        val syncingLagSize: Int,
        val laggingLagSize: Int,
        val options: UpstreamsConfig.PartialOptions
    ) {
        companion object {
            @JvmStatic
            fun default() = ChainConfig(6, 1, UpstreamsConfig.PartialOptions())
        }
    }

    fun resolve(chain: Chain): ChainConfig {
        val default = currentDefault ?: panic()
        val raw = chains[chain] ?: default
        val options = default.options?.merge(raw.options) ?: raw.options ?: UpstreamsConfig.PartialOptions()

        return ChainConfig(
            laggingLagSize = raw.laggingLagSize ?: default.laggingLagSize ?: panic(),
            syncingLagSize = raw.syncingLagSize ?: default.syncingLagSize ?: panic(),
            options = options
        )
    }

    fun patch(patch: ChainsConfig) = ChainsConfig(
        merge(this.chains, patch.chains),
        merge(this.currentDefault!!, patch.currentDefault)
    )

    private fun merge(
        current: RawChainConfig,
        patch: RawChainConfig?
    ) = RawChainConfig()
        .apply {
            syncingLagSize = patch?.syncingLagSize ?: current.syncingLagSize
            laggingLagSize = patch?.laggingLagSize ?: current.laggingLagSize
            options = patch?.options ?: current.options
        }

    private fun merge(
        current: Map<Chain, RawChainConfig>,
        patch: Map<Chain, RawChainConfig>
    ): Map<Chain, RawChainConfig> {
        val currentMut = current.toMutableMap()

        for (k in patch) {
            currentMut.merge(k.key, k.value) { v1, v2 -> merge(v1, v2) }
        }

        return currentMut.toMap()
    }

    fun panic(): Nothing = throw IllegalStateException("Chains settings state is illegal - default config is null")
}
