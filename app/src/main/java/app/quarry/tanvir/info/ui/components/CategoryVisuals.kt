package app.quarry.tanvir.info.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.quarry.tanvir.info.domain.model.StorageCategory
import app.quarry.tanvir.info.ui.theme.CategoryApk
import app.quarry.tanvir.info.ui.theme.CategoryApp
import app.quarry.tanvir.info.ui.theme.CategoryArchive
import app.quarry.tanvir.info.ui.theme.CategoryAudio
import app.quarry.tanvir.info.ui.theme.CategoryDocument
import app.quarry.tanvir.info.ui.theme.CategoryImage
import app.quarry.tanvir.info.ui.theme.CategoryOther
import app.quarry.tanvir.info.ui.theme.CategoryVideo

fun StorageCategory.getColor(): Color = when (this) {
    StorageCategory.VIDEOS -> CategoryVideo
    StorageCategory.IMAGES -> CategoryImage
    StorageCategory.DOCUMENTS -> CategoryDocument
    StorageCategory.AUDIO -> CategoryAudio
    StorageCategory.ARCHIVES -> CategoryArchive
    StorageCategory.APKS -> CategoryApk
    StorageCategory.APPS -> CategoryApp
    StorageCategory.OTHER -> CategoryOther
}

fun StorageCategory.getIcon(): ImageVector = when (this) {
    StorageCategory.VIDEOS -> Icons.Rounded.Movie
    StorageCategory.IMAGES -> Icons.Rounded.Image
    StorageCategory.DOCUMENTS -> Icons.Rounded.Description
    StorageCategory.AUDIO -> Icons.Rounded.Headphones
    StorageCategory.ARCHIVES -> Icons.Rounded.FolderZip
    StorageCategory.APKS -> Icons.Rounded.Android
    StorageCategory.APPS -> Icons.Rounded.Apps
    StorageCategory.OTHER -> Icons.Rounded.Folder
}
