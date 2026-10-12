package com.hax.focusdeck

import java.time.LocalDate

/** Stateless display projections shared by real pages and explicitly enabled read-only Developer Mode. */
internal data class HomePresentation(val focus: Long, val sessions: Int, val done: Int,
    val week: Long, val lifetime: Long, val weeklyChange: Double? = null)
internal data class TelemetryPresentation(val summary: String, val strengths: String, val friction: String)
internal data class AnalyticsPresentation(val evidence: DashboardAnalyticsEvidence,
    val focusBars: List<Pair<String, Double?>>? = null, val hourlyBars: List<Pair<String, Double?>>? = null,
    val focusReference: Double? = null, val hourlyReference: Double? = null,
    val telemetry: TelemetryPresentation? = null, val kpis: List<Pair<String, String>>? = null,
    val focusCaption: String? = null, val hourlyCaption: String? = null,
    val projectShares: Map<String, Int> = emptyMap(),
    val kpiTrends: Map<String, Double> = emptyMap(), val scoreTrend: Double? = null, val measureCaptions: Map<String,String> = emptyMap())

/** Fixed simulated date supplied by Developer Mode and isolated scenes; ordinary UI uses its observed date. */
internal val LocalDashboardDate = androidx.compose.runtime.staticCompositionLocalOf<LocalDate?> { null }

/** Shared geometry follows available width, not screenshot pixels or one period's dimensions. */
internal object OmssGeometry {
    fun scoreGauge(width: Float): Float = (width * .255f).coerceIn(82f, 120f)
    const val chartHeight = 76
    const val hourlyHeight = 64
    const val analyticsGutter = 2
}
