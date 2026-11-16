package com.onlinechallenges.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.onlinechallenges.app.data.Attachment

fun openAttachment(context: Context, attachment: Attachment, vm: AppViewModel) {
    val local = vm.attachmentLocalFile(attachment)
    val uri: Uri?
    if (local != null) {
        uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", local)
    } else {
        val remote = vm.remotePageUrl(attachment)
        uri = remote?.let(Uri::parse)
    }
    if (uri == null) {
        Toast.makeText(context, "Файл ещё не загружен", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, attachment.mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (error: ActivityNotFoundException) {
        Toast.makeText(context, "Нет приложения для просмотра этого файла", Toast.LENGTH_SHORT).show()
    }
}

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (error: ActivityNotFoundException) {
        Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}
