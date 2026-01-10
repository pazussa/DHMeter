package com.dhmeter.domain.usecase

import com.dhmeter.domain.model.*
import com.dhmeter.domain.repository.PreferencesRepository
import com.dhmeter.domain.repository.RunRepository
import javax.inject.Inject
import kotlin.math.abs

/**
 * Compares two runs on the same track and generates a comparison result with verdict.
 */
class CompareRunsUseCase @Inject constructor(
    private val runRepository: RunRepository,
    private val preferencesRepository: PreferencesRepository
) {
    suspend operator fun invoke(
        trackId: String,
        runAId: String,
        runBId: String
    ): Result<ComparisonResult> {
        return try {
            val runA = runRepository.getRunById(runAId)
                ?: return Result.failure(Exception("Run A not found"))
            val runB = runRepository.getRunById(runBId)
                ?: return Result.failure(Exception("Run B not found"))

            if (runA.trackId != trackId || runB.trackId != trackId) {
                return Result.failure(Exception("Runs must be from the same track"))
            }

            // Check validity unless user enabled "include invalid runs"
            val includeInvalid = preferencesRepository.getIncludeInvalidRuns()
            if (!includeInvalid && (!runA.isValid || !runB.isValid)) {
                return Result.failure(Exception("Both runs must be valid for comparison. Enable 'Include invalid runs' in settings to compare anyway."))
            }

            val impactComparison = compareMetric(runA.impactScore, runB.impactScore)
            val harshnessComparison = compareMetric(runA.harshnessAvg, runB.harshnessAvg)
            val stabilityComparison = compareMetric(runA.stabilityScore, runB.stabilityScore)
            val landingComparison = compareMetric(runA.landingQualityScore, runB.landingQualityScore)
            val durationComparison = compareMetric(
                runA.durationMs.toFloat() / 1000f,
                runB.durationMs.toFloat() / 1000f
            )

            // Generate section insights
            val sectionInsights = generateSectionInsights(runAId, runBId)

            // Determine verdict
            val verdict = determineVerdict(
                impactComparison,
                harshnessComparison,
                stabilityComparison,
                landingComparison,
                durationComparison
            )

            Result.success(
                ComparisonResult(
                    trackId = trackId,
                    runA = runA,
                    runB = runB,
                    impactComparison = impactComparison,
                    harshnessComparison = harshnessComparison,
                    stabilityComparison = stabilityComparison,
                    landingComparison = landingComparison,
                    durationComparison = durationComparison,
                    verdict = verdict,
                    sectionInsights = sectionInsights
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun compareMetric(valueA: Float?, valueB: Float?): MetricComparison? {
        if (valueA == null || valueB == null || valueA == 0f) return null
        
        val deltaPercent = ((valueB - valueA) / valueA) * 100f
        return MetricComparison(
            valueA = valueA,
            valueB = valueB,
            deltaPercent = deltaPercent
        )
    }

    private suspend fun generateSectionInsights(
        runAId: String,
        runBId: String
    ): List<String> {
        val insights = mutableListOf<String>()
        
        try {
            val impactA = runRepository.getSeries(runAId, SeriesType.IMPACT_DENSITY)
            val impactB = runRepository.getSeries(runBId, SeriesType.IMPACT_DENSITY)
            
            if (impactA != null && impactB != null) {
                // Analyze by sections (10 bins: 0-10%, 10-20%, etc.)
                val numBins = 10
                val binSize = impactA.pointCount / numBins
                
                for (bin in 0 until numBins) {
                    val startIdx = bin * binSize
                    val endIdx = minOf((bin + 1) * binSize, impactA.pointCount)
                    
                    val avgA = (startIdx until endIdx).map { 
                        impactA.getPoint(it).second 
                    }.average().toFloat()
                    
                    val avgB = (startIdx until endIdx).map { 
                        impactB.getPoint(it).second 
                    }.average().toFloat()
                    
                    if (avgA > 0 && avgB > 0) {
                        val change = ((avgB - avgA) / avgA) * 100f
                        val sectionStart = bin * 10
                        val sectionEnd = (bin + 1) * 10
                        
                        when {
                            change < -20 -> {
                                insights.add("Section $sectionStart-$sectionEnd%: Impact reduced by ${abs(change).toInt()}%")
                            }
                            change > 20 -> {
                                insights.add("Section $sectionStart-$sectionEnd%: Impact increased by ${change.toInt()}%")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore errors in insight generation
        }
        
        return insights.take(5) // Limit to 5 most significant insights
    }

    private fun determineVerdict(
        impact: MetricComparison?,
        harshness: MetricComparison?,
        stability: MetricComparison?,
        landing: MetricComparison?,
        duration: MetricComparison?
    ): Verdict {
        // For all metrics, lower is better (less impact, less harshness, less instability)
        val impactBetter = impact?.let { it.deltaPercent < -5 } ?: false
        val impactWorse = impact?.let { it.deltaPercent > 5 } ?: false
        val harshnessBetter = harshness?.let { it.deltaPercent < -5 } ?: false
        val harshnessWorse = harshness?.let { it.deltaPercent > 5 } ?: false
        val stabilityBetter = stability?.let { it.deltaPercent < -5 } ?: false
        val stabilityWorse = stability?.let { it.deltaPercent > 5 } ?: false
        val fasterTime = duration?.let { it.deltaPercent < -3 } ?: false
        val slowerTime = duration?.let { it.deltaPercent > 3 } ?: false
        
        val betterCount = listOf(impactBetter, harshnessBetter, stabilityBetter).count { it }
        val worseCount = listOf(impactWorse, harshnessWorse, stabilityWorse).count { it }
        
        return when {
            // Clear improvement
            betterCount >= 2 && worseCount == 0 -> {
                val highlights = buildString {
                    if (impactBetter) append("Lower impact. ")
                    if (harshnessBetter) append("Smoother ride. ")
                    if (stabilityBetter) append("More stable. ")
                    if (fasterTime) append("Faster time.")
                }
                Verdict.createBetter(highlights.trim())
            }
            
            // Clear regression
            worseCount >= 2 && betterCount == 0 -> {
                val highlights = buildString {
                    if (impactWorse) append("Higher impact. ")
                    if (harshnessWorse) append("Rougher ride. ")
                    if (stabilityWorse) append("Less stable. ")
                    if (slowerTime) append("Slower time.")
                }
                Verdict.createWorse(highlights.trim())
            }
            
            // Mixed results
            betterCount > 0 && worseCount > 0 -> {
                val highlights = buildString {
                    if (impactBetter) append("Impact improved. ")
                    else if (impactWorse) append("Impact worsened. ")
                    if (harshnessBetter) append("Harshness reduced. ")
                    else if (harshnessWorse) append("More vibration. ")
                    if (fasterTime && impactWorse) append("Faster but more punishing.")
                    else if (slowerTime && impactBetter) append("Slower but smoother.")
                }
                Verdict.createMixed(highlights.trim())
            }
            
            // Similar
            else -> Verdict.createSimilar()
        }
    }
}
