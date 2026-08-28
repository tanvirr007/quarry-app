package app.quarry.tanvir.info.domain.treemap

import app.quarry.tanvir.info.domain.model.StorageCategory

data class TreemapRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class TreemapNode(
    val path: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val category: StorageCategory,
    val rect: TreemapRect = TreemapRect(0f, 0f, 0f, 0f),
    val children: List<TreemapNode> = emptyList()
)
