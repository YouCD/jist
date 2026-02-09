package dev.rcht.jist.engine

import dev.rcht.jist.data.repository.AppRuleRepository

/**
 * Evaluates per-app and per-contact rules to determine summarization behavior
 */
class RuleEngine(private val appRuleRepository: AppRuleRepository) {

    enum class SummarizationMode {
        AUTO,    // Automatically summarize after batch window
        MANUAL,  // Show "Summarize" action button
        DISABLED // Don't process notifications from this app
    }

    data class RuleEvaluation(
        val mode: SummarizationMode,
        val batchWindowMinutes: Int,
        val minMessagesForSummary: Int,
        val customPrompt: String? = null
    )

    /**
     * Evaluate rules for a given app
     */
    suspend fun evaluateRules(packageName: String): RuleEvaluation? {
        val appRule = appRuleRepository.getByPackageName(packageName) ?: return null

        if (!appRule.enabled) {
            return RuleEvaluation(mode = SummarizationMode.DISABLED, 0, 0)
        }

        val mode = when (appRule.mode) {
            "AUTO" -> SummarizationMode.AUTO
            "MANUAL" -> SummarizationMode.MANUAL
            else -> SummarizationMode.DISABLED
        }

        return RuleEvaluation(
            mode = mode,
            batchWindowMinutes = appRule.batchWindowMinutes,
            minMessagesForSummary = appRule.minMessagesForSummary,
            customPrompt = appRule.customPrompt
        )
    }

    /**
     * Check if a conversation should be summarized based on its notification count and age
     */
    suspend fun shouldSummarize(
        packageName: String,
        messageCount: Int,
        timeSinceFirstMessageMs: Long
    ): Boolean {
        val evaluation = evaluateRules(packageName) ?: return false

        if (evaluation.mode == SummarizationMode.DISABLED) {
            return false
        }

        // Check if minimum messages threshold is met
        if (messageCount < evaluation.minMessagesForSummary) {
            return false
        }

        // Check if batch window has passed
        val batchWindowMs = evaluation.batchWindowMinutes * 60 * 1000L
        return timeSinceFirstMessageMs >= batchWindowMs
    }

    /**
     * Get the summarization mode for an app
     */
    suspend fun getMode(packageName: String): SummarizationMode {
        return evaluateRules(packageName)?.mode ?: SummarizationMode.DISABLED
    }
}
