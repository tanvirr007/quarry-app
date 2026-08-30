package app.quarry.tanvir.info.domain.model

import java.util.Locale

/**
 * High-performance smart search matcher for filenames, directories, paths, and app names.
 *
 * Handles:
 * - Direct substring matching (case-insensitive)
 * - Separator-agnostic matching (dots, underscores, hyphens, spaces: e.g. "wake up" matches "Wake.Up.Dead.Man")
 * - Multi-token / keyword search (e.g. "wake dead 1080p" matches "Wake.Up.Dead.Man.2025.1080p.mkv")
 * - Compact matching (e.g. "wakeup" or "deadman" matches "Wake.Up.Dead.Man")
 * - Token prefix matching (e.g. "wak dea" matches "Wake Up Dead Man")
 */
object SearchMatcher {

    private val SEPARATORS_REGEX = Regex("[._\\-+~()\\[\\],:;\"'/\\\\]+")

    fun matches(name: String, secondaryText: String = "", query: String): Boolean {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return true

        // 1. Fast direct substring check
        if (name.contains(trimmedQuery, ignoreCase = true) ||
            (secondaryText.isNotEmpty() && secondaryText.contains(trimmedQuery, ignoreCase = true))
        ) {
            return true
        }

        val cleanQuery = normalize(trimmedQuery)
        val cleanName = normalize(name)
        val cleanSecondary = if (secondaryText.isNotEmpty()) normalize(secondaryText) else ""
        val combinedClean = if (cleanSecondary.isNotEmpty()) "$cleanName $cleanSecondary" else cleanName

        // 2. Normalized full phrase match
        if (combinedClean.contains(cleanQuery)) {
            return true
        }

        // 3. Compact match (ignoring all spaces and punctuation)
        val compactQuery = cleanQuery.replace(" ", "")
        val compactName = cleanName.replace(" ", "")
        val compactSecondary = cleanSecondary.replace(" ", "")
        if (compactQuery.isNotEmpty() && (compactName.contains(compactQuery) || compactSecondary.contains(compactQuery))) {
            return true
        }

        // 4. Multi-token keyword & prefix matching
        val queryTokens = cleanQuery.split(" ").filter { it.isNotBlank() }
        if (queryTokens.isEmpty()) return true

        val targetTokens = combinedClean.split(" ").filter { it.isNotBlank() }

        // All query tokens must match either as a substring or prefix in target tokens
        return queryTokens.all { qToken ->
            combinedClean.contains(qToken) || targetTokens.any { targetToken ->
                targetToken.startsWith(qToken) || targetToken.contains(qToken)
            }
        }
    }

    private fun normalize(input: String): String {
        return input
            .replace(SEPARATORS_REGEX, " ")
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
    }
}
