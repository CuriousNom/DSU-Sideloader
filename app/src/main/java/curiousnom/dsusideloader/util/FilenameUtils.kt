package curiousnom.dsusideloader.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile

class FilenameUtils {

    companion object {

        /**
         * Append text to the end of all digits containing in a string
         * @param input String containing digits
         * @param textToAppend Text that will be appended
         * @return Formatted string, if there is no digits in "input", a empty string will be returned.
         */
        fun appendToDigitsToString(input: String, textToAppend: String): String {
            var newText = input.filter { it.isDigit() } + textToAppend
            if (newText == textToAppend) {
                newText = ""
            }
            return newText
        }

        /**
         * Tries to convert DocumentFile uri to real path
         * isn't guaranteed that will work with all kinds of path
         */
        fun getFilePath(uri: Uri, addQuotes: Boolean = false): String {
            val input = uri.path.toString()
            val safStorage = input.split("/document/")[1].replace("/tree/", "")
            val path = safStorage.split(":")[1]
            if (path.contains("/storage/emulated")) {
                return if (addQuotes) "'file://'$path" else "file://$path"
            }
            return if (safStorage.contains("primary")) {
                val storagePath = "file:///storage/emulated/0/"
                val finalPath = "$storagePath$path"
                return if (addQuotes) "'$finalPath'" else finalPath
            } else {
                val storagePath = "file:///storage/"
                val finalPath = storagePath + safStorage.replace(":", "/")
                if (addQuotes) "'$finalPath'" else finalPath
            }
        }

        fun queryName(resolver: ContentResolver, uri: Uri): String {
            try {
                resolver.query(uri, null, null, null, null)?.use { returnCursor ->
                    val nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && returnCursor.moveToFirst()) {
                        val name = returnCursor.getString(nameIndex)
                        if (!name.isNullOrEmpty()) return name
                    }
                }
            } catch (_: Exception) {}
            return uri.lastPathSegment ?: "unknown.img"
        }

        fun getDigits(input: String): String {
            return appendToDigitsToString(input, "")
        }

        fun getLengthFromFile(context: Context, uri: Uri): Long {
            // 1. Try DocumentFile
            try {
                val doc = DocumentFile.fromSingleUri(context, uri)
                val len = doc?.length() ?: 0L
                if (len > 0L) return len
            } catch (_: Exception) {}

            // 2. Try ContentResolver openFileDescriptor statSize
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    val stat = pfd.statSize
                    if (stat > 0L) return stat
                }
            } catch (_: Exception) {}

            // 3. Try OpenableColumns.SIZE query
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (idx != -1 && !cursor.isNull(idx)) {
                            val size = cursor.getLong(idx)
                            if (size > 0L) return size
                        }
                    }
                }
            } catch (_: Exception) {}

            // 4. Try resolving real file path via PayloadManager / direct file
            try {
                val realPath = curiousnom.dsusideloader.core.PayloadManager.getRealPath(context, uri)
                if (realPath != null) {
                    val file = java.io.File(realPath)
                    if (file.exists() && file.length() > 0L) {
                        return file.length()
                    }
                }
            } catch (_: Exception) {}

            return 0L
        }
    }
}
