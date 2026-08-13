package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SampleComicGenerator {

    suspend fun checkAndGenerateSampleComics(context: Context) = withContext(Dispatchers.IO) {
        val targetDirs = mutableListOf<File>()

        context.getExternalFilesDir("comics")?.let { targetDirs.add(it) }

        val internalDir = File(context.filesDir, "sample_comics")
        if (!internalDir.exists()) internalDir.mkdirs()
        targetDirs.add(internalDir)

        for (dir in targetDirs) {
            try {
                if (!dir.exists()) dir.mkdirs()

                // 1. Sample CBZ
                val cbzFile = File(dir, "Cyber_Ninja_Ch1.cbz")
                if (!cbzFile.exists() || cbzFile.length() == 0L) {
                    createSampleMangaCbz(cbzFile)
                }

                // 2. Sample PDF
                val pdfFile = File(dir, "Space_Odyssey.pdf")
                if (!pdfFile.exists() || pdfFile.length() == 0L) {
                    createSamplePdf(pdfFile)
                }

                // 3. Sample Nested CBZ (Archive containing nested subfolders & nested chapter zip)
                val nestedCbzFile = File(dir, "Nested_Manhwa_Vol1.cbz")
                if (!nestedCbzFile.exists() || nestedCbzFile.length() == 0L) {
                    createSampleNestedMangaCbz(nestedCbzFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createSampleMangaCbz(outputCbz: File) {
        val pageTitles = listOf(
            "Page 1: Neon District Arrival",
            "Page 2: Shadow in the Alley",
            "Page 3: Cyber Blade Activation",
            "Page 4: Rooftop Pursuit",
            "Page 5: High Voltage Clash",
            "Page 6: Escape into the Rain",
            "Page 7: To Be Continued..."
        )

        val pageBgColors = listOf(
            Color.parseColor("#0F172A"),
            Color.parseColor("#1E1B4B"),
            Color.parseColor("#311042"),
            Color.parseColor("#1E293B"),
            Color.parseColor("#020617"),
            Color.parseColor("#1E1B4B"),
            Color.parseColor("#0F172A")
        )

        ZipOutputStream(FileOutputStream(outputCbz)).use { zipOut ->
            for (i in pageTitles.indices) {
                val bitmap = createComicPageBitmap(
                    pageNumber = i + 1,
                    title = pageTitles[i],
                    subtitle = "OPENCOMIC SAMPLE MANGA • READ RTL",
                    bgColor = pageBgColors[i],
                    accentColor = Color.parseColor("#06B6D4")
                )

                val entryName = String.format("page_%03d.jpg", i + 1)
                zipOut.putNextEntry(ZipEntry(entryName))
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, zipOut)
                zipOut.closeEntry()
                bitmap.recycle()
            }
        }
    }

    private fun createSampleNestedMangaCbz(outputCbz: File) {
        // Creates a CBZ containing subfolders (Chapter 1) AND an embedded zip (Chapter 2)
        ZipOutputStream(FileOutputStream(outputCbz)).use { outerZip ->
            // Chapter 1 images in subfolder "Chapter 01/"
            for (i in 1..4) {
                val bitmap = createComicPageBitmap(
                    pageNumber = i,
                    title = "Chapter 1 - Section $i",
                    subtitle = "NESTED MANHWA • SUBFOLDER CHAPTER 1",
                    bgColor = Color.parseColor("#18181B"),
                    accentColor = Color.parseColor("#10B981")
                )
                outerZip.putNextEntry(ZipEntry("Chapter 01/page_$i.jpg"))
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outerZip)
                outerZip.closeEntry()
                bitmap.recycle()
            }

            // Chapter 2 embedded zip file "Chapter 02.zip"
            val ch2Bytes = ByteArrayOutputStream()
            ZipOutputStream(ch2Bytes).use { ch2Zip ->
                for (i in 1..4) {
                    val bitmap = createComicPageBitmap(
                        pageNumber = i + 4,
                        title = "Chapter 2 - Section $i",
                        subtitle = "NESTED MANHWA • EMBEDDED ZIP CHAPTER 2",
                        bgColor = Color.parseColor("#27272A"),
                        accentColor = Color.parseColor("#8B5CF6")
                    )
                    ch2Zip.putNextEntry(ZipEntry("page_$i.jpg"))
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, ch2Zip)
                    ch2Zip.closeEntry()
                    bitmap.recycle()
                }
            }

            outerZip.putNextEntry(ZipEntry("Chapter 02.zip"))
            outerZip.write(ch2Bytes.toByteArray())
            outerZip.closeEntry()
        }
    }

    private fun createSamplePdf(pdfFile: File) {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageTitles = listOf(
            "Chapter 1: The Launch",
            "Chapter 2: Asteroid Belt Navigation",
            "Chapter 3: Signal from Unknown Planet",
            "Chapter 4: Deep Space Exploration"
        )

        for (i in pageTitles.indices) {
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(800, 1200, i + 1).create()
            val page = pdfDocument.startPage(pageInfo)

            val bitmap = createComicPageBitmap(
                pageNumber = i + 1,
                title = pageTitles[i],
                subtitle = "OPENCOMIC SAMPLE PDF • SCROLL WEBTOON",
                bgColor = Color.parseColor("#18181B"),
                accentColor = Color.parseColor("#F59E0B")
            )

            val canvas = page.canvas
            canvas.drawBitmap(bitmap, null, RectF(0f, 0f, 800f, 1200f), null)
            pdfDocument.finishPage(page)
            bitmap.recycle()
        }

        FileOutputStream(pdfFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
    }

    private fun createComicPageBitmap(
        pageNumber: Int,
        title: String,
        subtitle: String,
        bgColor: Int,
        accentColor: Int
    ): Bitmap {
        val width = 1080
        val height = 1620
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawColor(bgColor)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.parseColor("#33FFFFFF")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 6f

        canvas.drawRoundRect(RectF(60f, 60f, width - 60f, 260f), 24f, 24f, paint)
        canvas.drawRoundRect(RectF(60f, 290f, width - 60f, 1050f), 24f, 24f, paint)
        canvas.drawRoundRect(RectF(60f, 1080f, width - 60f, height - 100f), 24f, 24f, paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(120f, 400f, width - 120f, 620f), 32f, 32f, paint)

        val path = Path()
        path.moveTo(250f, 620f)
        path.lineTo(300f, 680f)
        path.lineTo(350f, 620f)
        path.close()
        canvas.drawPath(path, paint)

        paint.color = Color.BLACK
        paint.textSize = 48f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("⚡ OPENCOMIC READER ⚡", width / 2f, 500f, paint)

        paint.textSize = 36f
        paint.color = Color.DKGRAY
        canvas.drawText(title, width / 2f, 570f, paint)

        paint.color = accentColor
        paint.textSize = 32f
        canvas.drawText(subtitle, width / 2f, 160f, paint)

        paint.color = Color.parseColor("#66FFFFFF")
        paint.textSize = 240f
        canvas.drawText("$pageNumber", width / 2f, 920f, paint)

        paint.color = Color.LTGRAY
        paint.textSize = 28f
        canvas.drawText("Page $pageNumber • High Definition Local Extraction", width / 2f, height - 50f, paint)

        return bitmap
    }
}
