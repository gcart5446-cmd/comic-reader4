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
import java.security.MessageDigest

object DualPageSplitter {
    private const val TAG = "DualPageSplitter"
    private const val MAX_SPLIT_CACHE_BYTES = 300L * 1024L * 1024L // 300MB

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
        } else {
            cleanSplitCacheIfNeeded(cacheDir)
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
                val fileHash = md5(file.absolutePath)
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

    private fun md5(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            input.hashCode().toString()
        }
    }

    private fun cleanSplitCacheIfNeeded(cacheDir: File) {
        try {
            val files = cacheDir.listFiles() ?: return
            val totalSize = files.sumOf { it.length() }
            if (totalSize > MAX_SPLIT_CACHE_BYTES) {
                val sorted = files.sortedBy { it.lastModified() }
                var currentSize = totalSize
                for (f in sorted) {
                    val len = f.length()
                    if (f.delete()) {
                        currentSize -= len
                    }
                    if (currentSize <= MAX_SPLIT_CACHE_BYTES / 2) break
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed cleaning split cache: ${e.message}")
        }
    }

    private fun splitAndSave(
        inputFile: File,
        leftFile: File,
        rightFile: File,
        w: Int,
        h: Int
    ) {
        val halfW = w / 2

        val decoder: BitmapRegionDecoder? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(inputFile.absolutePath)
        } else {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(inputFile.absolutePath, false)
        }

        if (decoder == null) return

        try {
            val leftBmp = decoder.decodeRegion(Rect(0, 0, halfW, h), null)
            leftBmp?.let { bmp ->
                try {
                    FileOutputStream(leftFile).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                } finally {
                    bmp.recycle()
                }
            }

            val rightBmp = decoder.decodeRegion(Rect(halfW, 0, w, h), null)
            rightBmp?.let { bmp ->
                try {
                    FileOutputStream(rightFile).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 90, out) }
                } finally {
                    bmp.recycle()
                }
            }
        } finally {
            decoder.recycle()
        }
    }
}
