package com.tend.app.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.tend.app.R
import com.tend.app.data.CalEvent
import com.tend.app.data.db.PlanBlock
import com.tend.app.domain.Time
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Renders one day's plan as an on-brand A4 PDF and opens the share sheet. */
object PlanPdfExporter {

    private const val PAGE_W = 595 // A4 @ 72dpi
    private const val PAGE_H = 842
    private const val MARGIN = 48f

    private val CREAM = Color.rgb(245, 241, 232)
    private val CARD = Color.rgb(255, 253, 247)
    private val INK = Color.rgb(36, 32, 26)
    private val MUTED = Color.rgb(138, 128, 114)
    private val FAINT = Color.rgb(180, 172, 156)
    private val BORDER = Color.rgb(232, 226, 211)
    private val TERRACOTTA = Color.rgb(217, 111, 78)
    private val VIOLET = Color.rgb(125, 116, 201)
    private val TEAL = Color.rgb(47, 156, 130)

    private fun kindColor(kind: String): Int = when (kind) {
        "focus" -> VIOLET
        "event" -> TEAL
        else -> TERRACOTTA
    }

    private data class PdfRow(
        val startMin: Int,
        val endMin: Int,
        val title: String,
        val tag: String,
        val color: Int,
        val done: Boolean,
    )

    fun export(
        context: Context,
        date: LocalDate,
        blocks: List<PlanBlock>,
        calendarEvents: List<CalEvent>,
    ): File {
        val grotesk = try {
            ResourcesCompat.getFont(context, R.font.space_grotesk_600)
        } catch (_: Exception) {
            null
        } ?: Typeface.DEFAULT_BOLD
        val groteskBold = try {
            ResourcesCompat.getFont(context, R.font.space_grotesk_700)
        } catch (_: Exception) {
            null
        } ?: Typeface.DEFAULT_BOLD

        val rows = (
            blocks.map {
                PdfRow(
                    it.startMin, it.endMin, it.title,
                    when (it.kind) {
                        "focus" -> "FOCUS"
                        "habit" -> "HABIT"
                        else -> "EVENT"
                    },
                    kindColor(it.kind), it.done,
                )
            } + calendarEvents.map {
                PdfRow(it.startMin, it.endMin, it.title, "CALENDAR", TEAL, false)
            }
            ).sortedBy { it.startMin }

        val doc = PdfDocument()
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, 1).create())
        var canvas = page.canvas
        var pageNo = 1

        fun paint(color: Int, size: Float, tf: Typeface? = null, letterSpacing: Float = 0f) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                tf?.let { typeface = it }
                this.letterSpacing = letterSpacing
            }

        fun drawBackground() {
            canvas.drawColor(CREAM)
        }

        fun drawHeader(): Float {
            val kicker = date.format(DateTimeFormatter.ofPattern("EEEE · MMM d, yyyy", Locale.ENGLISH))
                .uppercase(Locale.ENGLISH)
            canvas.drawText(kicker, MARGIN, MARGIN + 12f, paint(FAINT, 10f, groteskBold, 0.14f))
            canvas.drawText("Daily Plan", MARGIN, MARGIN + 44f, paint(INK, 30f, groteskBold))
            val line = Paint().apply { color = BORDER; strokeWidth = 1.2f }
            canvas.drawLine(MARGIN, MARGIN + 62f, PAGE_W - MARGIN, MARGIN + 62f, line)
            return MARGIN + 84f
        }

        fun drawFooter() {
            canvas.drawText(
                "Made with Tend",
                MARGIN, PAGE_H - 30f, paint(FAINT, 9f, grotesk, 0.08f),
            )
        }

        drawBackground()
        var y = drawHeader()
        val rowHeight = 58f

        if (rows.isEmpty()) {
            canvas.drawText("Nothing planned for this day.", MARGIN, y + 20f, paint(MUTED, 13f))
        }

        for (row in rows) {
            if (y + rowHeight > PAGE_H - 60f) {
                drawFooter()
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                canvas = page.canvas
                drawBackground()
                y = drawHeader()
            }

            // card
            val card = RectF(MARGIN + 64f, y, PAGE_W - MARGIN, y + rowHeight - 10f)
            canvas.drawRoundRect(card, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CARD })
            canvas.drawRoundRect(
                card, 12f, 12f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = BORDER; style = Paint.Style.STROKE; strokeWidth = 1f
                },
            )

            // time rail
            canvas.drawText(Time.clock(row.startMin), MARGIN, y + 20f, paint(INK, 11f, groteskBold))
            canvas.drawText(Time.clock(row.endMin), MARGIN, y + 34f, paint(FAINT, 9.5f))

            // color dot
            canvas.drawCircle(card.left + 18f, y + 23f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = row.color })

            // title (+ strike-through when done)
            val titlePaint = paint(if (row.done) FAINT else INK, 13f, grotesk).apply {
                isStrikeThruText = row.done
            }
            val title = if (row.done) "${row.title}  ✓" else row.title
            canvas.drawText(title, card.left + 34f, y + 27f, titlePaint)

            // tag + duration
            canvas.drawText(
                "${row.tag} · ${Time.duration(row.endMin - row.startMin)}",
                card.left + 34f, y + 42f, paint(MUTED, 9f, null, 0.06f),
            )

            y += rowHeight
        }

        drawFooter()
        doc.finishPage(page)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "tend-plan-${date}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "com.tend.app.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Share your plan").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
