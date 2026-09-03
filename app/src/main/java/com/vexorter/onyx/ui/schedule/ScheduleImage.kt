package com.vexorter.onyx.ui.schedule

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.vexorter.onyx.domain.DaySchedule
import com.vexorter.onyx.domain.Lesson
import com.vexorter.onyx.util.WeekUtils
import java.io.File

/**
 * Рендер расписания в картинку для отправки в чат.
 *
 * Рисуем вручную на Canvas, а не снимаем экран: снимок зависел бы от прокрутки
 * и размера телефона, а так картинка всегда одинаковая и содержит всё целиком.
 *
 * Раскладка считается один раз ([layout]) и используется и для высоты, и для
 * отрисовки — иначе они разъезжаются при первом же длинном названии предмета.
 */
object ScheduleImage {

    private const val WIDTH = 1080
    private const val PADDING = 48f
    private const val CARD_RADIUS = 36f
    private const val CARD_GAP = 20f
    private const val TIME_COLUMN = 190f
    private const val LINE_HEIGHT = 44f

    private const val BACKGROUND = 0xFF0D0F12.toInt()
    private const val CARD = 0xFF161B1F.toInt()
    private const val TEXT = 0xFFE7ECEF.toInt()
    private const val MUTED = 0xFF9FAAB2.toInt()
    private const val OUTLINE = 0xFF5A666E.toInt()

    private const val MINT = 0xFF5FE0AE.toInt()
    private const val AMBER = 0xFFFFC46B.toInt()
    private const val VIOLET = 0xFFB9A5FF.toInt()
    private const val CORAL = 0xFFFF8A80.toInt()

    private val contentWidth = WIDTH - PADDING * 2 - TIME_COLUMN - 40f

    private class LessonLayout(
        val lesson: Lesson,
        val discipline: List<String>,
        val details: List<Pair<String, Int>>,
    ) {
        val height: Float
            get() = 44f + (discipline.size + details.size) * LINE_HEIGHT
    }

    fun render(
        context: Context,
        title: String,
        subtitle: String,
        days: List<DaySchedule>,
        forEmployee: Boolean,
    ): File {
        val plan = days.filter { it.lessons.isNotEmpty() }
            .map { day -> day to day.lessons.map { layout(it, forEmployee) } }

        // Для одного дня его название уже стоит в подзаголовке — второй раз не пишем.
        val showDayHeaders = plan.size > 1

        var height = PADDING + 20f + 76f + 74f
        plan.forEach { (_, lessons) ->
            height += 24f + if (showDayHeaders) 62f else 0f
            lessons.forEach { height += it.height + CARD_GAP }
        }
        height += PADDING

        val bitmap = Bitmap.createBitmap(WIDTH, height.toInt().coerceAtLeast(400), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)

        var y = PADDING + 20f
        canvas.drawText(fit(title, titlePaint, WIDTH - PADDING * 2), PADDING, y + 46f, titlePaint)
        y += 76f
        canvas.drawText(subtitle, PADDING, y + 30f, subtitlePaint)
        y += 74f

        plan.forEach { (day, lessons) ->
            y += 24f
            if (showDayHeaders) {
                canvas.drawText(
                    "${WeekUtils.weekDayName(day.date)}, ${WeekUtils.dayAndMonth(day.date)}",
                    PADDING,
                    y + 34f,
                    dayPaint,
                )
                y += 62f
            }
            lessons.forEach { item ->
                draw(canvas, item, y)
                y += item.height + CARD_GAP
            }
        }

        val file = File(context.cacheDir, "share").apply { mkdirs() }.resolve("onyx-schedule.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
        return file
    }

    private fun layout(lesson: Lesson, forEmployee: Boolean): LessonLayout {
        val details = buildList {
            if (lesson.type.isNotBlank()) {
                val subgroup = lesson.subGroup.takeIf { it.isNotBlank() }
                    ?.let { " · подгруппа $it" }.orEmpty()
                add(fit(lesson.type + subgroup, linePaint, contentWidth) to accentOf(lesson.type))
            }
            val who = if (forEmployee) lesson.group else lesson.employee
            if (who.isNotBlank()) add(fit(who, linePaint, contentWidth) to MUTED)
            if (lesson.classroom.isNotBlank()) {
                add(fit("ауд. ${lesson.classroom}", linePaint, contentWidth) to MUTED)
            }
        }
        return LessonLayout(lesson, wrap(lesson.discipline, disciplinePaint, contentWidth), details)
    }

    private fun draw(canvas: Canvas, item: LessonLayout, top: Float) {
        val rect = RectF(PADDING, top, WIDTH - PADDING, top + item.height)
        cardPaint.color = CARD
        canvas.drawRoundRect(rect, CARD_RADIUS, CARD_RADIUS, cardPaint)

        cardPaint.color = accentOf(item.lesson.type)
        canvas.drawRoundRect(
            RectF(PADDING + TIME_COLUMN, top + 24f, PADDING + TIME_COLUMN + 8f, top + item.height - 24f),
            4f,
            4f,
            cardPaint,
        )

        val timeX = PADDING + 40f
        canvas.drawText(item.lesson.timeStart, timeX, top + 58f, timePaint)
        canvas.drawText("—", timeX, top + 98f, dashPaint)
        canvas.drawText(item.lesson.timeEnd, timeX, top + 138f, timeEndPaint)

        var y = top + 56f
        val x = PADDING + TIME_COLUMN + 40f
        item.discipline.forEach { line ->
            canvas.drawText(line, x, y, disciplinePaint)
            y += LINE_HEIGHT
        }
        item.details.forEach { (text, color) ->
            linePaint.color = color
            canvas.drawText(text, x, y, linePaint)
            y += LINE_HEIGHT
        }
    }

    /** Перенос по словам, максимум две строки — дальше обрезаем. */
    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (paint.measureText(text) <= maxWidth) return listOf(text)

        val words = text.trim().split(" ")
        val lines = mutableListOf<String>()
        var current = StringBuilder()

        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines += current.toString()
                current = StringBuilder(word)
                if (lines.size == 1) break
            }
        }
        if (lines.size < 2 && current.isNotEmpty()) lines += current.toString()

        val rest = text.removePrefix(lines.joinToString(" ")).trim()
        if (rest.isNotEmpty() && lines.size == 2) {
            lines[1] = fit("${lines[1]} $rest", paint, maxWidth)
        }
        return lines.ifEmpty { listOf(fit(text, paint, maxWidth)) }
    }

    /** Обрезка с многоточием, если строка не влезает. */
    private fun fit(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + "…") > maxWidth) {
            end--
        }
        return text.substring(0, end).trimEnd() + "…"
    }

    private fun accentOf(type: String): Int {
        val normalized = type.lowercase()
        return when {
            normalized.startsWith("лекц") -> MINT
            normalized.startsWith("практ") || normalized.startsWith("семин") -> AMBER
            normalized.startsWith("лаборат") -> VIOLET
            normalized.contains("экзамен") || normalized.contains("зачёт") ||
                normalized.contains("зачет") || normalized.contains("консульт") -> CORAL

            else -> OUTLINE
        }
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT
        textSize = 52f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MUTED
        textSize = 34f
    }

    private val dayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT
        textSize = 40f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val disciplinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT
        textSize = 38f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 33f }

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT
        textSize = 38f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = OUTLINE
        textSize = 34f
    }

    private val timeEndPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MUTED
        textSize = 34f
    }

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
}
