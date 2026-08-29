package app.quarry.tanvir.info.domain.scanner

import java.io.File
import java.util.Locale

object ExclusionMatcher {

    /**
     * Checks if a given path matches any of the excluded path patterns.
     * Supports:
     * - Absolute / canonical paths (e.g. "/storage/emulated/0/Download/Telegram")
     * - Relative paths with or without leading slash (e.g. "/Android/data", "Android/data", "DCIM/.thumbnails")
     * - Standalone folder names (e.g. ".thumbnails", ".git", ".cache", "Telegram")
     * - Case-insensitive matching across Android filesystem structures
     */
    fun isExcluded(path: String, excluded: Set<String>): Boolean {
        if (excluded.isEmpty() || path.isBlank()) return false

        val normalizedPath = path.trim().replace('\\', '/').trimEnd('/')
        val lowerPath = normalizedPath.lowercase(Locale.ROOT)

        for (raw in excluded) {
            val normalizedRaw = raw.trim().replace('\\', '/').trimEnd('/')
            if (normalizedRaw.isBlank()) continue
            val lowerRaw = normalizedRaw.lowercase(Locale.ROOT)

            // 1. Exact match
            if (lowerPath == lowerRaw) return true

            // 2. Prefix match (child of excluded directory)
            if (lowerPath.startsWith("$lowerRaw/")) return true

            val stripped = lowerRaw.removePrefix("/")
            if (stripped.isBlank()) continue

            // 3. Match without leading slash
            if (lowerPath == stripped || lowerPath == "/$stripped") return true

            // 4. Suffix match (e.g. /storage/emulated/0/Android/data matches Android/data or /Android/data)
            if (lowerPath.endsWith("/$stripped")) return true

            // 5. Infix/subpath match (e.g. /storage/emulated/0/Android/data/com.example matches Android/data)
            if (lowerPath.contains("/$stripped/")) return true

            // 6. Basename / direct folder name match if single segment (e.g. ".git", ".thumbnails", "cache")
            if (!stripped.contains('/')) {
                val fileName = File(normalizedPath).name.lowercase(Locale.ROOT)
                if (fileName == stripped) return true
            }
        }

        return false
    }

    /**
     * Returns true if any parent directory of the path is in the excluded set.
     */
    fun isAnyParentExcluded(path: String, excluded: Set<String>): Boolean {
        if (isExcluded(path, excluded)) return true
        val parent = File(path).parent ?: return false
        return isExcluded(parent, excluded)
    }
}
