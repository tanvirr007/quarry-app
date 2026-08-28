package app.quarry.tanvir.info.domain.treemap

import app.quarry.tanvir.info.data.database.FileEntity
import app.quarry.tanvir.info.domain.model.StorageCategory
import kotlin.math.max
import kotlin.math.min

object TreemapEngine {

    /**
     * Builds a TreemapNode hierarchy from a list of FileEntity objects.
     * If rootPath is provided, only items under rootPath will be included.
     */
    fun buildTree(
        files: List<FileEntity>,
        rootPath: String = ""
    ): TreemapNode {
        val filtered = if (rootPath.isEmpty()) {
            files
        } else {
            files.filter { it.path.startsWith(rootPath) && it.path != rootPath }
        }

        if (filtered.isEmpty()) {
            return TreemapNode(
                path = rootPath.ifEmpty { "/" },
                name = if (rootPath.isEmpty()) "Storage" else rootPath.substringAfterLast('/'),
                size = 0L,
                isDirectory = true,
                category = StorageCategory.OTHER
            )
        }

        // Aggregate immediate children of rootPath
        val directChildren = if (rootPath.isEmpty()) {
            filtered.filter { it.parentPath == null || it.parentPath.isEmpty() || it.parentPath == "/" }
        } else {
            filtered.filter { it.parentPath == rootPath }
        }

        val childNodes = if (directChildren.isNotEmpty()) {
            directChildren.map { entity ->
                createNodeFromEntity(entity, filtered)
            }.sortedByDescending { it.size }
        } else {
            // If flat file list or deep items, group by immediate child subpath
            val grouped = filtered.groupBy { entity ->
                val relative = if (rootPath.isEmpty()) entity.path.trimStart('/') else entity.path.removePrefix(rootPath).trimStart('/')
                val firstSegment = relative.substringBefore('/')
                if (rootPath.isEmpty()) "/$firstSegment" else "$rootPath/$firstSegment"
            }

            grouped.map { (childPath, groupFiles) ->
                val isDir = groupFiles.size > 1 || groupFiles.any { it.path != childPath } || groupFiles.first().isDirectory
                val totalSize = groupFiles.sumOf { if (!it.isDirectory) it.size else 0L }
                val category = if (isDir) StorageCategory.OTHER else StorageCategory.fromExtension(childPath.substringAfterLast('.'))
                TreemapNode(
                    path = childPath,
                    name = childPath.substringAfterLast('/'),
                    size = totalSize,
                    isDirectory = isDir,
                    category = category
                )
            }.sortedByDescending { it.size }
        }

        val totalSize = childNodes.sumOf { it.size }

        return TreemapNode(
            path = rootPath.ifEmpty { "/" },
            name = if (rootPath.isEmpty()) "Storage" else rootPath.substringAfterLast('/'),
            size = totalSize,
            isDirectory = true,
            category = StorageCategory.OTHER,
            children = childNodes
        )
    }

    private fun createNodeFromEntity(entity: FileEntity, allFiles: List<FileEntity>): TreemapNode {
        val category = StorageCategory.fromExtension(entity.extension)
        return if (entity.isDirectory) {
            val descendants = allFiles.filter { it.path.startsWith(entity.path) && it.path != entity.path && !it.isDirectory }
            val dirSize = if (entity.size > 0) entity.size else descendants.sumOf { it.size }
            TreemapNode(
                path = entity.path,
                name = entity.name,
                size = dirSize,
                isDirectory = true,
                category = StorageCategory.OTHER
            )
        } else {
            TreemapNode(
                path = entity.path,
                name = entity.name,
                size = entity.size,
                isDirectory = false,
                category = category
            )
        }
    }

    /**
     * Calculates squarified treemap layout for a list of items within target bounds [0, 0, width, height].
     * Uses the Squarified Treemap Algorithm (Bruls et al.) to ensure aspect ratios close to 1.
     */
    fun layoutSquarified(
        items: List<TreemapNode>,
        bounds: TreemapRect
    ): List<TreemapNode> {
        if (items.isEmpty() || bounds.width <= 0 || bounds.height <= 0) return emptyList()

        val validItems = items.filter { it.size > 0 }.sortedByDescending { it.size }
        if (validItems.isEmpty()) return emptyList()

        val totalSize = validItems.sumOf { it.size }
        if (totalSize == 0L) return emptyList()

        val totalArea = bounds.width * bounds.height
        val normalizedAreas = validItems.map { (it.size.toDouble() / totalSize.toDouble()) * totalArea }

        val result = mutableListOf<TreemapNode>()
        squarify(validItems, normalizedAreas, bounds, result)
        return result
    }

    private fun squarify(
        children: List<TreemapNode>,
        areas: List<Double>,
        rect: TreemapRect,
        result: MutableList<TreemapNode>
    ) {
        if (children.isEmpty() || rect.width <= 0 || rect.height <= 0) return

        var row = mutableListOf<TreemapNode>()
        var rowAreas = mutableListOf<Double>()
        var remainingChildren = children.toMutableList()
        var remainingAreas = areas.toMutableList()
        var currentRect = rect

        while (remainingChildren.isNotEmpty()) {
            val nextChild = remainingChildren.first()
            val nextArea = remainingAreas.first()
            val currentShortSide = min(currentRect.width, currentRect.height).toDouble()

            if (row.isEmpty()) {
                row.add(nextChild)
                rowAreas.add(nextArea)
                remainingChildren.removeAt(0)
                remainingAreas.removeAt(0)
            } else {
                val currentWorst = worstAspectRatio(rowAreas, currentShortSide)
                val newRowAreas = rowAreas + nextArea
                val newWorst = worstAspectRatio(newRowAreas, currentShortSide)

                if (newWorst <= currentWorst) {
                    row.add(nextChild)
                    rowAreas.add(nextArea)
                    remainingChildren.removeAt(0)
                    remainingAreas.removeAt(0)
                } else {
                    // Lay out current row and update remaining rectangle
                    currentRect = layoutRow(row, rowAreas, currentRect, result)
                    row = mutableListOf()
                    rowAreas = mutableListOf()
                }
            }
        }

        if (row.isNotEmpty()) {
            layoutRow(row, rowAreas, currentRect, result)
        }
    }

    private fun worstAspectRatio(rowAreas: List<Double>, length: Double): Double {
        if (rowAreas.isEmpty() || length <= 0.0) return Double.MAX_VALUE
        val totalArea = rowAreas.sum()
        if (totalArea <= 0.0) return Double.MAX_VALUE
        val otherSide = totalArea / length

        var worst = 0.0
        for (area in rowAreas) {
            val itemLength = area / otherSide
            val ratio = max(otherSide / itemLength, itemLength / otherSide)
            if (ratio > worst) worst = ratio
        }
        return worst
    }

    private fun layoutRow(
        row: List<TreemapNode>,
        rowAreas: List<Double>,
        rect: TreemapRect,
        result: MutableList<TreemapNode>
    ): TreemapRect {
        val totalArea = rowAreas.sum()
        val isHorizontal = rect.width < rect.height

        if (isHorizontal) {
            // Horizontal slice (subdividing along height)
            val rowHeight = (totalArea / rect.width).toFloat()
            var currentX = rect.left
            for (i in row.indices) {
                val itemWidth = (rowAreas[i] / rowHeight).toFloat()
                val itemRect = TreemapRect(
                    left = currentX,
                    top = rect.top,
                    right = (currentX + itemWidth).coerceAtMost(rect.right),
                    bottom = (rect.top + rowHeight).coerceAtMost(rect.bottom)
                )
                result.add(row[i].copy(rect = itemRect))
                currentX += itemWidth
            }
            return TreemapRect(
                left = rect.left,
                top = rect.top + rowHeight,
                right = rect.right,
                bottom = rect.bottom
            )
        } else {
            // Vertical slice (subdividing along width)
            val rowWidth = (totalArea / rect.height).toFloat()
            var currentY = rect.top
            for (i in row.indices) {
                val itemHeight = (rowAreas[i] / rowWidth).toFloat()
                val itemRect = TreemapRect(
                    left = rect.left,
                    top = currentY,
                    right = (rect.left + rowWidth).coerceAtMost(rect.right),
                    bottom = (currentY + itemHeight).coerceAtMost(rect.bottom)
                )
                result.add(row[i].copy(rect = itemRect))
                currentY += itemHeight
            }
            return TreemapRect(
                left = rect.left + rowWidth,
                top = rect.top,
                right = rect.right,
                bottom = rect.bottom
            )
        }
    }
}
