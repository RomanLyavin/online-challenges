package com.onlinechallenges.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.onlinechallenges.app.data.Attachment

/**
 * Открывает вложение: локальный файл сразу, публичное — по ссылке,
 * приватное — после скачивания и расшифровки.
 */
fun openAttachment(context: Context, attachment: Attachment, challengeId: String, vm: AppViewModel) {
    vm.openAttachment(context, attachment, challengeId)
}

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (error: ActivityNotFoundException) {
        Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}
