package io.github.molishadaze.weijing.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageStorageManager {

    /**
     * Copies the image from the temporary Uri returned by Photo Picker into
     * the app's persistent private directory (internal filesDir or externalFilesDir).
     * Returns the absolute path of the saved local file.
     */
    fun saveImageToAppStorage(
        context: Context,
        uri: Uri,
        habitId: Long,
        date: String
    ): String? {
        return try {
            val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) 
                ?: context.filesDir

            if (!picturesDir.exists()) {
                picturesDir.mkdirs()
            }

            val filename = "checkin_${habitId}_${date}_${UUID.randomUUID()}.jpg"
            val destFile = File(picturesDir, filename)

            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val outputStream = FileOutputStream(destFile)

            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }

            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes a local image file if it exists.
     */
    fun deleteImageFile(path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
