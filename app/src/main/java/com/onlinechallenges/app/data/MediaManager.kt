package com.onlinechallenges.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max

class MediaException(message: String) : Exception(message)

@OptIn(UnstableApi::class)
class MediaManager(private val context: Context, private val repo: AppRepository) {

    companion object {
        const val MAX_VIDEO_BYTES = 25L * 1024 * 1024
        const val MAX_VIDEO_INPUT_BYTES = 300L * 1024 * 1024
        const val MAX_IMAGE_INPUT_BYTES = 40L * 1024 * 1024
        private const val MAX_IMAGE_DIMENSION = 1800
        private const val MAX_AVATAR_DIMENSION = 512
        private const val VIDEO_LIMIT_TEXT = "25 МБ"
        private const val VIDEO_HEIGHT = 720
        private const val VIDEO_BITRATE = 1_500_000
        private const val AUDIO_BITRATE = 96_000
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

    private suspend fun importVideo(
        uri: Uri,
        userCode: String,
        challengeId: String,
        date: String,
        goalId: String
    ): Attachment {
        val inputSize = querySize(uri)
        if (inputSize > MAX_VIDEO_INPUT_BYTES) {
            throw MediaException("Видео больше 300 МБ. Запишите короткий ролик или выберите файл меньше.")
        }
        val mime = context.contentResolver.getType(uri).orEmpty().ifBlank { "video/mp4" }
        val ext = extensionForMime(mime, video = true)
        val dir = goalDir(userCode, challengeId)
        val stamp = System.currentTimeMillis()
        val dest = File(dir, "${date}_${sanitize(goalId)}_${stamp}.mp4")
        val temp = File(context.cacheDir, "video_${stamp}.$ext")
        try {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output ->
                        copyLimited(input, output, MAX_VIDEO_INPUT_BYTES)
                    }
                } ?: throw MediaException("Не удалось открыть видео")
            }
            var finalFile = dest
            var finalMime = "video/mp4"
            val compression = runCatching { transcodeVideo(temp, dest) }
            if (compression.isFailure) {
                dest.delete()
                if (temp.length() > MAX_VIDEO_BYTES) {
                    throw MediaException("Не удалось сжать видео. Запишите ролик короче.")
                }
                // Если перекодирование не поддержано устройством, сохраняем исходник без сжатия.
                finalFile = File(dir, "${date}_${sanitize(goalId)}_${stamp}.$ext")
                withContext(Dispatchers.IO) {
                    temp.copyTo(finalFile, overwrite = true)
                }
                finalMime = mime
            }
            if (!finalFile.exists() || finalFile.length() == 0L) {
                finalFile.delete()
                throw MediaException("Не удалось обработать видео")
            }
            if (finalFile.length() > MAX_VIDEO_BYTES) {
                finalFile.delete()
                throw MediaException(
                    "Видео больше $VIDEO_LIMIT_TEXT даже после сжатия. Запишите ролик короче."
                )
            }
            return Attachment(
                id = UUID.randomUUID().toString(),
                fileName = finalFile.name,
                mime = finalMime,
                type = AttachmentType.VIDEO,
                sizeBytes = finalFile.length(),
                localPath = finalFile.absolutePath
            )
        } finally {
            temp.delete()
        }
    }

    private suspend fun transcodeVideo(source: File, dest: File) {
        dest.delete()
        withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { continuation ->
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(VIDEO_BITRATE)
                            .build()
                    )
                    .setRequestedAudioEncoderSettings(
                        AudioEncoderSettings.Builder()
                            .setBitrate(AUDIO_BITRATE)
                            .build()
                    )
                    .build()
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .setVideoEffects(listOf(Presentation.createForHeight(VIDEO_HEIGHT)))
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(exportException)
                            }
                        }
                    })
                    .build()
                continuation.invokeOnCancellation {
                    Handler(Looper.getMainLooper()).post { transformer.cancel() }
                }
                try {
                    transformer.start(MediaItem.fromUri(Uri.fromFile(source)), dest.absolutePath)
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
            }
        }
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
