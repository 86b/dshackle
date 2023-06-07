package io.emeraldpay.dshackle.config.spans

import brave.handler.MutableSpan
import brave.handler.SpanHandler
import brave.propagation.TraceContext
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.cloud.sleuth.Span
import java.time.Duration

class ProviderSpanHandler(
    @Qualifier("spanMapper")
    private val spanMapper: ObjectMapper,
    private val spanExportableList: List<SpanExportable>
) : SpanHandler() {

    companion object {
        private val log = LoggerFactory.getLogger(ProviderSpanHandler::class.java)
    }

    private val spans = Caffeine
        .newBuilder()
        .expireAfterWrite(Duration.ofMinutes(5))
        .build<String, MutableList<MutableSpan>>()

    override fun end(context: TraceContext, span: MutableSpan, cause: Cause): Boolean {
        if (span.traceId().length > 20 && span.parentId() != null) {
            val spanList = spans.asMap().computeIfAbsent(span.parentId()) { mutableListOf() }
            spanList.add(span)
            if (span.name().contains("TxMemCache", true)) {
                try {
                    log.info(span.toString())
                } catch (e: Exception) {
                    log.error("error parse")
                }
            }
        }
        return super.end(context, span, cause)
    }

    fun getErrorSpans(spanId: String, currentSpan: Span): String {
        val spansInfo = SpansInfo()

        enrichErrorSpans(spanId, spansInfo)
        currentSpan.end()
        currentSpan.context().parentId()?.let {
            spans.getIfPresent(it)?.let { mutableSpans ->
                if (mutableSpans.isNotEmpty()) {
                    processSpanInfo(mutableSpans[0], spansInfo)
                }
            }
        }

        spansInfo.spans
            .map { it.parentId() }
            .forEach {
                if (it != null) {
                    spans.invalidate(it)
                }
            }

        return if (spansInfo.exportable) {
            spanMapper.writeValueAsString(spansInfo.spans)
        } else {
            ""
        }
    }

    private fun enrichErrorSpans(spanId: String, spansInfo: SpansInfo) {
        val currentSpans: List<MutableSpan>? = spans.getIfPresent(spanId)

        currentSpans?.forEach {
            processSpanInfo(it, spansInfo)
            if (spanId != it.id()) {
                enrichErrorSpans(it.id(), spansInfo)
            }
        }
    }

    private fun processSpanInfo(span: MutableSpan, spansInfo: SpansInfo) {
        spansInfo.spans.add(span)
        if (spanExportableList.any { it.isExportable(span) }) {
            spansInfo.exportable = true
        }
    }

    private data class SpansInfo(
        var exportable: Boolean = false,
        val spans: MutableList<MutableSpan> = mutableListOf()
    )
}
