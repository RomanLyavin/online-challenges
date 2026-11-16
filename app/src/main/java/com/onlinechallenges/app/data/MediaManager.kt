package com.onlinechallenges.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

class MediaException(message: String) : Exception(message)

class MediaManager(private val context: Context, private val repo: AppRepository) {

    companion object {
        const val MAX_VIDEO_BYTES = 25L * 1024 * 1024
        const val MAX_IMAGE_INPUT_BYTES = 40L * 1024 * 1024
        private const val MAX_IMAGE_DIMENSION = 1800
        private const val MAX_AVATAR_DIMENSION = 512
        private const val VIDEO_LIMIT_TEXT = "25 МБ"
    }

    fun newCaptureFile(video: Boolean): File {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val ext = if (video) "mp4" else "jpg"
        return File(dir, "capture_${System.currentTimeMillis()}.${ext}")
    }

    fun uriFor(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun contentUriFor(attachment: Attachment): Uri? {
        val file = repo.fileFor(attachment.localPath) ?: return null
        return uriFor(file)
    }

    suspend fun importProof(
        uri: Uri,
        userCode: String,
        challengeId: String,
        date: String,
        goalId: String,
        preferredType: ProofType
    ): Attachment = withContext(Dispatchers.IO) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        val isVideo = mime.startsWith("video/") ||
            (mime.isBlank() && preferredType == ProofType.VIDEO)
        if (isVideo) {
            importVideo(uri, userCode, challengeId, date, goalId)
        } else {
            importImage(uri, userCode, challengeId, date, goalId)
        }
    }

    private fun importVideo(
        uri: Uri,
        userCode: String,
        challengeId: String,
        date: String,
        goalId: String
    ): Attachment {
        val size = querySize(uri)
        if (size > MAX_VIDEO_BYTES) {
            throw MediaException("Видео больше $VIDEO_LIMIT_TEXT. Запишите короткий ролик или выберите файл меньше.")
        }
        val mime = context.contentResolver.getType(uri).orEmpty().ifBlank { "video/mp4" }
        val ext = extensionForMime(mime, video = true)
        val dir = goalDir(userCode, challengeId)
        val fileName = "${date}_${sanitize(goalId)}_${System.currentTimeMillis()}.$ext"
        val dest = File(dir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output ->
                val copied = copyLimited(input, output, MAX_VIDEO_BYTES)
                if (copied > MAX_VIDEO_BYTES) {
                    dest.delete()
                    throw MediaException("Видео больше $VIDEO_LIMIT_TEXT. Запишите короткий ролик или выберите файл меньше.")
                }
            }
        } ?: throw MediaException("Не удалось открыть видео")
        return Attachment(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            mime = mime,
            type = AttachmentType.VIDEO,
            sizeBytes = dest.length(),
            localPath = dest.absolutePath
        )
    }

    private fun importImage(
        uri: Uri,
        userCode: String,
        challengeId: String,
        date: String,
        goalId: String
    ): Attachment {
        val size = querySize(uri)
        if (size > MAX_IMAGE_INPUT_BYTES) {
            throw MediaException("Фото больше 40 МБ. Выберите другое изображение.")
        }
        val dir = goalDir(userCode, challengeId)
        val fileName = "${date}_${sanitize(goalId)}_${System.currentTimeMillis()}.jpg"
        val dest = File(dir, fileName)
        val raw = File(context.cacheDir, "raw_${System.currentTimeMillis()}.tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                raw.outputStream().use { output -> copyLimited(input, output, MAX_IMAGE_INPUT_BYTES) }
            } ?: throw MediaException("Не удалось открыть фото")
            val saved = runCatching {
                saveCompressedImage(raw, dest, MAX_IMAGE_DIMENSION)
            }.getOrDefault(false)
            if (!saved) {
                // Не удалось декодировать (например, необычный формат) — сохраняем исходник.
                val ext = extensionForMime(context.contentResolver.getType(uri).orEmpty(), video = false)
                val rawDest = File(dir, "${date}_${sanitize(goalId)}_${System.currentTimeMillis()}.$ext")
                raw.copyTo(rawDest, overwrite = true)
                rawDest.deleteOnExit()
                return Attachment(
                    id = UUID.randomUUID().toString(),
                    fileName = rawDest.name,
                    mime = context.contentResolver.getType(uri).orEmpty().ifBlank { "image/jpeg" },
                    type = AttachmentType.PHOTO,
                    sizeBytes = rawDest.length(),
                    localPath = rawDest.absolutePath
                )
            }
        } finally {
            raw.delete()
        }
        return Attachment(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            mime = "image/jpeg",
            type = AttachmentType.PHOTO,
            sizeBytes = dest.length(),
            localPath = dest.absolutePath
        )
    }

    suspend fun importAvatar(uri: Uri, userCode: String): String = withContext(Dispatchers.IO) {
        val dir = repo.avatarRoot()
        val dest = File(dir, "${sanitize(userCode)}.jpg")
        val raw = File(context.cacheDir, "avatar_${System.currentTimeMillis()}.tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                raw.outputStream().use { output -> copyLimited(input, output, MAX_IMAGE_INPUT_BYTES) }
            } ?: throw MediaException("Не удалось открыть фото профиля")
            if (!saveCompressedImage(raw, dest, MAX_AVATAR_DIMENSION)) {
                throw MediaException("Не удалось обработать изображение")
            }
        } finally {
            raw.delete()
        }
        dest.absolutePath
    }

    fun deleteAttachment(attachment: Attachment) {
        attachment.localPath?.let { path ->
            runCatching {
                val file = File(path)
                if (file.exists() && file.canonicalPath.startsWith(context.filesDir.canonicalPath)) {
                    file.delete()
                }
            }
        }
    }

    private fun goalDir(userCode: String, challengeId: String): File {
        val dir = File(repo.mediaRoot(), "${sanitize(userCode)}/${sanitize(challengeId)}")
        dir.mkdirs()
        return dir
    }

    private fun saveCompressedImage(source: File, dest: File, maxDimension: Int): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(source.absolutePath, options) ?: return false
        val rotated = applyExifRotation(source, decoded)
        val scale = minOf(1f, maxDimension.toFloat() / max(rotated.width, rotated.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                rotated,
                (rotated.width * scale).toInt().coerceAtLeast(1),
                (rotated.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else rotated
        try {
            FileOutputStream(dest).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
        } finally {
            if (scaled !== rotated) scaled.recycle()
            if (rotated !== decoded) rotated.recycle()
            decoded.recycle()
        }
        return dest.length() > 0
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap = runCatching {
        val orientation = ExifInterface(file.absolutePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return@runCatching bitmap
        }
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrDefault(bitmap)

    private fun querySize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0) return cursor.getLong(index)
            }
        }
        return 0L
    }

    private fun copyLimited(input: java.io.InputStream, output: java.io.OutputStream, limit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > limit) throw MediaException("Файл слишком большой")
            output.write(buffer, 0, read)
        }
        return total
    }

    private fun extensionForMime(mime: String, video: Boolean): String = when {
        mime.contains("mp4") -> "mp4"
        mime.contains("webm") -> "webm"
        mime.contains("quicktime") -> "mov"
        mime.contains("3gpp") -> "3gp"
        mime.contains("png") -> "png"
        mime.contains("webp") -> "webp"
        mime.contains("heic") || mime.contains("heif") -> "heic"
        else -> if (video) "mp4" else "jpg"
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifBlank { "item" }
}
