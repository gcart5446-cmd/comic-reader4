package com.example.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream

object DualPageSplitter {
    private const val TAG = "DualPageSplitter"

    fun processPages(
        context: Context,
        rawFiles: List<File>,
        isDualPageSplit: Boolean,
        isRtl: Boolean
    ): List<File> {
        if (!isDualPageSplit || rawFiles.isEmpty()) {
            return rawFiles
        }

        val cacheDir = File(context.cacheDir, "split_pages")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val resultList = mutableListOf<File>()

        for (file in rawFiles) {
            if (!file.exists()) {
                resultList.add(file)
                continue
            }

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            val w = options.outWidth
            val h = options.outHeight

            if (w <= 0 || h <= 0 || w <= h * 1.2f) {
                // Single page
                resultList.add(file)
            } else {
                // Wide spread page candidate for split
                val fileHash = file.nameWithoutExtension.hashCode()
                val part1File = File(cacheDir, "${fileHash}_left.jpg")
                val part2File = File(cacheDir, "${fileHash}_right.jpg")

                if (!part1File.exists() || !part2File.exists()) {
                    try {
                        splitAndSave(file, part1File, part2File, w, h)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to split spread image ${file.name}: ${e.message}")
                        resultList.add(file)
                        continue
                    }
                }

                if (part1File.exists() && part2File.exists()) {
                    if (isRtl) {
                        // RTL: Right half comes first, then Left half
                        resultList.add(part2File)
                        resultList.add(part1File)
                    } else {
                        // LTR: Left half comes first, then Right half
                        resultList.add(part1File)
                        resultList.add(part2File)
                    }
                } else {
                    resultList.add(file)
                }
            }
        }

        return resultList
    }

    private fun splitAndSave(
        inputFile: File,
        leftFile: File,
        rightFile: File,
        w: Int,
        h: Int
    ) {
        val halfW = w / 2

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val decoder = BitmapRegionDecoder.newInstance(inputFile.absolutePath)
            val leftBmp = decoder.decodeRegion(Rect(0, 0, halfW, h), null)
            val rightBmp = decoder.decodeRegion(Rect(halfW, 0, w, h), null)

            leftBmp?.let { bmp ->
                FileOutputStream(leftFile).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                bmp.recycle()
            }
            rightBmp?.let { bmp ->
                FileOutputStream(rightFile).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                bmp.recycle()
            }
            decoder.recycle()
        } else {
            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(inputFile.absolutePath, false)
            val leftBmp = decoder?.decodeRegion(Rect(0, 0, halfW, h), null)
            val rightBmp = decoder?.decodeRegion(Rect(halfW, 0, w, h), null)

            leftBmp?.let { bmp ->
                FileOutputStream(leftFile).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                bmp.recycle()
            }
            rightBmp?.let { bmp ->
                FileOutputStream(rightFile).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                bmp.recycle()
            }
            decoder?.recycle()
        }
    }
}
