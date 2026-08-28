package app.quarry.tanvir.info.data.filesystem

import java.io.File

class FileScanner {
    fun listFiles(directory: File): List<File> {
        return directory.listFiles()?.toList() ?: emptyList()
    }
}
