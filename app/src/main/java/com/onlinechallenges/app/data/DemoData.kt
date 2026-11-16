package com.onlinechallenges.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

/**
 * Демонстрационные данные, чтобы статистику и медали можно было посмотреть
 * сразу после установки. Всё помечено isDemo и никогда не публикуется на GitHub.
 */
object DemoData {

    fun seedIfNeeded(repo: AppRepository, today: LocalDate = LocalDate.now()) {
        val prefs = repo.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        val state = repo.snapshot()
        if (state.challenges.any { !it.isDemo }) {
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
            return
        }
        val demo = build(repo.context, today)
        repo.replace(
            state.copy(
                profiles = state.profiles.filterNot { it.isDemo } + demo.profiles,
                challenges = state.challenges.filterNot { it.isDemo } + demo.challenges,
                checkIns = state.checkIns.filterNot { it.isDemo } + demo.checkIns
            )
        )
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()
    }

    fun clear(repo: AppRepository) {
        val state = repo.snapshot()
        val demoMedia = state.checkIns.filter { it.isDemo }.flatMap { checkIn ->
            checkIn.results.flatMap { result -> result.attachments.mapNotNull { it.localPath } }
        }.toSet()
        repo.replace(
            state.copy(
                profiles = state.profiles.filterNot { it.isDemo },
                challenges = state.challenges.filterNot { it.isDemo },
                checkIns = state.checkIns.filterNot { it.isDemo }
            )
        )
        demoMedia.forEach { path -> runCatching { File(path).delete() } }
    }

    private data class DemoBundle(
        val profiles: List<Profile>,
        val challenges: List<Challenge>,
        val checkIns: List<CheckIn>
    )

    private fun build(context: Context, today: LocalDate): DemoBundle {
        val users = listOf(
            DemoUser("D-ANNA", "Анна К.", "1994-04-18", Gender.FEMALE, listOf("#0F766E", "#14B8A6")),
            DemoUser("D-MIKE", "Михаил", "1990-09-02", Gender.MALE, listOf("#1D4ED8", "#60A5FA")),
            DemoUser("D-IRINA", "Ирина", "1998-12-21", Gender.FEMALE, listOf("#BE123C", "#FB7185")),
            DemoUser("D-DENIS", "Денис", "1988-02-11", Gender.MALE, listOf("#7C3AED", "#A78BFA"))
        )
        val profiles = users.map { user ->
            val avatarFile = File(File(context.filesDir, "avatars").apply { mkdirs() }, "${user.code}.jpg")
            drawAvatar(avatarFile, user.name, user.colors)
            Profile(
                code = user.code,
                name = user.name,
                birthDate = user.birthDate,
                gender = user.gender,
                avatarLocalPath = avatarFile.absolutePath,
                updatedAt = isoNow(),
                isDemo = true
            )
        }

        val proofFiles = mutableMapOf<String, String>()
        fun proof(user: DemoUser, activity: String, title: String, subtitle: String): Attachment {
            val dir = File(context.filesDir, "media/${user.code}/demo-proofs").apply { mkdirs() }
            val file = File(dir, "${activity}.jpg")
            if (!file.exists() || file.length() == 0L) {
                drawProof(file, title, subtitle, user.colors[0])
            }
            proofFiles["${user.code}/$activity"] = file.absolutePath
            return Attachment(
                id = java.util.UUID.randomUUID().toString(),
                fileName = file.name,
                mime = "image/jpeg",
                type = AttachmentType.PHOTO,
                sizeBytes = file.length(),
                localPath = file.absolutePath
            )
        }

        fun goals(prefix: String, steps: Double, sleep: Double, run: Double): List<ChallengeGoal> = listOf(
            ChallengeGoal("$prefix-steps", "steps", "Шаги", "шаг", "👟", steps, ProofType.PHOTO),
            ChallengeGoal("$prefix-sleep", "sleep", "Сон", "ч", "😴", sleep, ProofType.PHOTO),
            ChallengeGoal("$prefix-run", "running", "Пробежка", "км", "🏃", run, ProofType.PHOTO)
        )

        val activeStart = today.minusDays(6)
        val activeEnd = today.plusDays(7)
        val active = Challenge(
            id = "demo-active",
            title = "Демо: неделя привычек",
            description = "Показывает, как считаются баллы, цвета и места. Это демонстрационный челлендж — его данные не публикуются на GitHub.",
            startDate = activeStart.toString(),
            endDate = activeEnd.toString(),
            mode = ChallengeMode.DAILY,
            intervalDays = 1,
            goals = goals("demo-active", 10_000.0, 8.0, 3.0),
            creatorCode = "D-ANNA",
            creatorName = "Анна К.",
            participantCodes = users.map { it.code },
            createdAt = isoNow(),
            updatedAt = isoNow(),
            isDemo = true
        )

        val finishedEnd = today.minusDays(12)
        val finishedStart = finishedEnd.minusDays(29)
        val finished = Challenge(
            id = "demo-finished",
            title = "Демо: апрельский вызов",
            description = "Завершённый демонстрационный челлендж. По его итогам участникам начислены медали.",
            startDate = finishedStart.toString(),
            endDate = finishedEnd.toString(),
            mode = ChallengeMode.DAILY,
            intervalDays = 1,
            goals = goals("demo-finished", 8_000.0, 7.0, 4.0),
            creatorCode = "D-MIKE",
            creatorName = "Михаил",
            participantCodes = users.map { it.code },
            createdAt = isoNow(),
            updatedAt = isoNow(),
            isDemo = true
        )

        val checkIns = mutableListOf<CheckIn>()
        val stepPattern = listOf(11_200.0, 9_800.0, 12_400.0, 0.0, 10_300.0, 8_900.0, 11_100.0)
        val sleepPattern = listOf(8.2, 7.0, 8.5, 6.5, 8.0, 7.5, 8.3)
        val runPattern = listOf(3.4, 0.0, 4.0, 2.1, 3.1, 0.0, 3.7)

        users.forEachIndexed { userIndex, user ->
            for (day in 0..6) {
                val date = activeStart.plusDays(day.toLong())
                if (date.isAfter(today)) continue
                if ((day + userIndex) % 7 == 3 && userIndex != 0) continue
                val pattern = (day + userIndex) % stepPattern.size
                val steps = stepPattern[pattern]
                val sleep = sleepPattern[pattern]
                val run = runPattern[pattern]
                val results = mutableListOf<GoalResult>()
                if (steps > 0) {
                    results += GoalResult(
                        "demo-active-steps",
                        steps,
                        listOf(proof(user, "steps", "${steps.toInt()} шагов", "Демо-скрин трекера"))
                    )
                }
                if (sleep > 0) {
                    results += GoalResult(
                        "demo-active-sleep",
                        sleep,
                        listOf(proof(user, "sleep", "${formatNumber(sleep)} ч сна", "Демо-скрин трекера"))
                    )
                }
                if (run > 0) {
                    results += GoalResult(
                        "demo-active-run",
                        run,
                        listOf(proof(user, "run", "${formatNumber(run)} км", "Демо-скрин пробежки"))
                    )
                }
                if (results.isEmpty()) continue
                checkIns += CheckIn(
                    id = "demo-active-${user.code}-$date",
                    challengeId = active.id,
                    userCode = user.code,
                    date = date.toString(),
                    results = results,
                    createdAt = "${date}T18:00:00Z",
                    updatedAt = "${date}T18:00:00Z",
                    isDemo = true
                )
            }

            for (day in 0..21) {
                val date = finishedStart.plusDays(day.toLong() * 2)
                if ((day + userIndex) % 5 == 4) continue
                val steps = 7_000.0 + ((day * 431 + userIndex * 977) % 3_000)
                val run = 3.2 + ((day + userIndex) % 4) * 0.6
                val results = listOf(
                    GoalResult(
                        "demo-finished-steps",
                        steps,
                        listOf(proof(user, "steps", "${steps.toInt()} шагов", "Демо-скрин трекера"))
                    ),
                    GoalResult(
                        "demo-finished-run",
                        if (day % 3 == 0) run else 0.0,
                        if (day % 3 == 0) listOf(proof(user, "run", "${formatNumber(run)} км", "Демо-скрин пробежки")) else emptyList()
                    )
                )
                checkIns += CheckIn(
                    id = "demo-finished-${user.code}-$date",
                    challengeId = finished.id,
                    userCode = user.code,
                    date = date.toString(),
                    results = results,
                    createdAt = "${date}T19:00:00Z",
                    updatedAt = "${date}T19:00:00Z",
                    isDemo = true
                )
            }
        }

        var finalProfiles = profiles
        listOf(active, finished).filter { today.isAfter(it.end) }.forEach { challenge ->
            val changed = ChallengeEngine.awardMedals(challenge, checkIns, finalProfiles, today)
            finalProfiles = finalProfiles.map { profile ->
                changed.firstOrNull { it.code == profile.code } ?: profile
            }
        }

        return DemoBundle(finalProfiles, listOf(active, finished), checkIns)
    }

    private data class DemoUser(
        val code: String,
        val name: String,
        val birthDate: String,
        val gender: Gender,
        val colors: List<String>
    )

    private fun drawAvatar(file: File, name: String, colors: List<String>) {
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, size.toFloat(), size.toFloat(),
            Color.parseColor(colors[0]), Color.parseColor(colors[1]), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = null
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 200f
        paint.textAlign = Paint.Align.CENTER
        val initials = name.trim().split(Regex("\\s+")).take(2)
            .joinToString("") { it.take(1).uppercase() }.ifBlank { "Я" }
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(initials, size / 2f, y, paint)
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
    }

    private fun drawProof(file: File, title: String, subtitle: String, accent: String) {
        val width = 720
        val height = 1280
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(246, 248, 250)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        paint.color = Color.parseColor(accent)
        canvas.drawRect(0f, 0f, width.toFloat(), 160f, paint)
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 48f
        canvas.drawText("Мой трекер", 48f, 78f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 28f
        paint.color = Color.argb(220, 255, 255, 255)
        canvas.drawText("демонстрационное подтверждение", 48f, 120f, paint)

        paint.color = Color.WHITE
        val card = RectF(40f, 220f, width - 40f, 690f)
        canvas.drawRoundRect(card, 48f, 48f, paint)

        paint.color = Color.parseColor(accent)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 120f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(title, width / 2f, 430f, paint)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = 34f
        paint.color = Color.rgb(100, 116, 139)
        canvas.drawText(subtitle, width / 2f, 500f, paint)
        paint.textSize = 30f
        canvas.drawText("Цель достигнута", width / 2f, 610f, paint)

        paint.textAlign = Paint.Align.LEFT
        paint.color = Color.WHITE
        val card2 = RectF(40f, 740f, width - 40f, 1080f)
        canvas.drawRoundRect(card2, 40f, 40f, paint)
        paint.color = Color.rgb(15, 23, 42)
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 36f
        canvas.drawText("Динамика за неделю", 80f, 800f, paint)
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        barPaint.color = Color.parseColor(accent)
        val heights = listOf(120f, 190f, 90f, 230f, 160f, 260f, 210f)
        heights.forEachIndexed { index, h ->
            val left = 90f + index * 80f
            val bottom = 1020f
            canvas.drawRoundRect(RectF(left, bottom - h, left + 44f, bottom), 16f, 16f, barPaint)
        }
        paint.textSize = 24f
        paint.color = Color.rgb(100, 116, 139)
        paint.typeface = Typeface.DEFAULT
        listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс").forEachIndexed { index, label ->
            canvas.drawText(label, 94f + index * 80f, 1065f, paint)
        }
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 26f
        canvas.drawText("Демо • Онлайн челленджи", width / 2f, 1180f, paint)

        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        bitmap.recycle()
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)

    const val PREFS = "online_challenges_prefs"
    const val KEY_SEEDED = "demo_seeded_v1"
}
