package com.example.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.github.junrar.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

data class ComicPagesResult(
    val comicUri: String,
    val title: String,
    val format: String,
    val pageFiles: List<File>,
    val coverFile: File?,
    val nestedArchives: List<File> = emptyList()
)

object ComicParser {
    private const val TAG = "ComicParser"

    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "avif", "jxl")
    val ARCHIVE_EXTENSIONS = setOf("cbz", "cbr", "cb7", "cbt", "zip", "rar", "7z", "tar")

    suspend fun parseAndExtract(context: Context, uri: Uri, customTitle: String? = null): ComicPagesResult = withContext(Dispatchers.IO) {
        val uriString = uri.toString()
        val fileName = customTitle ?: getFileNameFromUri(context, uri)

        // 0. Fast path for direct folders
        if (uri.scheme == "file" || (uri.scheme == null && uriString.startsWith("/"))) {
            val rawPath = uri.path ?: uriString
            val dirFile = File(rawPath)
            if (dirFile.exists() && dirFile.isDirectory) {
                val folderPages = collectAndSortImagePages(dirFile)
                if (folderPages.isNotEmpty()) {
                    Log.d(TAG, "Serving ${folderPages.size} direct folder pages for: ${dirFile.name}")
                    return@withContext ComicPagesResult(
                        comicUri = uriString,
                        title = sanitizeTitle(dirFile.name),
                        format = "FOLDER",
                        pageFiles = folderPages,
                        coverFile = folderPages.firstOrNull()
                    )
                }
            }
        }

        val fileLength = getFileLengthFromUri(context, uri)
        val hash = md5("${uriString}_${fileLength}")

        val cacheBaseDir = File(context.cacheDir, "extracted_comics/$hash")

        // Clean up old extracted comic caches if total cache exceeds 500 MB or 10 folders
        cleanCacheDirIfNeeded(File(context.cacheDir, "extracted_comics"), maxSizeBytes = 500 * 1024 * 1024L)

        // 1. Check if comic is already extracted and cached
        if (cacheBaseDir.exists()) {
            val cachedPages = collectAndSortImagePages(cacheBaseDir)
            if (cachedPages.isNotEmpty()) {
                Log.d(TAG, "Serving ${cachedPages.size} cached pages for: $fileName")
                val cover = cachedPages.firstOrNull()
                val detectedExt = detectExtensionFromFileOrUri(context, uri, fileName, null)
                return@withContext ComicPagesResult(
                    comicUri = uriString,
                    title = sanitizeTitle(fileName),
                    format = formatBadge(detectedExt),
                    pageFiles = cachedPages,
                    coverFile = cover
                )
            }
        } else {
            cacheBaseDir.mkdirs()
        }

        // 2. Prepare local input source
        val inputFile = prepareInputFile(context, uri, cacheBaseDir)
        val formatExtension = detectExtensionFromFileOrUri(context, uri, fileName, inputFile)

        Log.d(TAG, "Parsing comic: $fileName, format: $formatExtension, inputFile: ${inputFile?.absolutePath} (${inputFile?.length() ?: 0} bytes)")

        try {
            if (inputFile != null && inputFile.exists() && inputFile.length() > 0) {
                // Unpack root archive
                unpackArchiveRecursive(inputFile, cacheBaseDir, formatExtension, depth = 0)

                // Unroll any discovered nested archives
                unrollNestedArchives(cacheBaseDir, maxDepth = 5)
            } else {
                Log.e(TAG, "Input file is null or empty for URI: $uriString")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error extracting $fileName: ${e.message}", e)
        } finally {
            // Cleanup temporary source_temp.bin file to free storage and RAM
            if (inputFile != null && inputFile.name == "source_temp.bin" && inputFile.exists()) {
                inputFile.delete()
            }
        }

        // 3. Collect all images across all extracted folders & sort naturally
        val pageFiles = collectAndSortImagePages(cacheBaseDir)
        Log.d(TAG, "Extracted ${pageFiles.size} pages for: $fileName")

        val cover = pageFiles.firstOrNull()
        return@withContext ComicPagesResult(
            comicUri = uriString,
            title = sanitizeTitle(fileName),
            format = formatBadge(formatExtension),
            pageFiles = pageFiles,
            coverFile = cover
        )
    }

    private fun unpackArchiveRecursive(sourceFile: File, destDir: File, format: String, depth: Int) {
        if (depth > 5) return

        when (format.lowercase(Locale.ROOT)) {
            "pdf" -> {
                extractPdfPages(sourceFile, destDir)
            }
            "cbz", "zip" -> {
                extractZipFile(sourceFile, destDir)
            }
            "cbr", "rar" -> {
                extractRar(sourceFile, destDir)
            }
            "cb7", "7z" -> {
                extract7z(sourceFile, destDir)
            }
            "cbt", "tar" -> {
                extractTarFile(sourceFile, destDir)
            }
            in IMAGE_EXTENSIONS -> {
                val destPage = File(destDir, "page_0001.${format}")
                sourceFile.copyTo(destPage, overwrite = true)
            }
            else -> {
                // Attempt fallback sequence: ZIP -> RAR -> 7Z -> PDF
                val success = tryExtractZip(sourceFile, destDir) ||
                        tryExtractRar(sourceFile, destDir) ||
                        tryExtract7z(sourceFile, destDir) ||
                        tryExtractPdf(sourceFile, destDir)
                if (!success) {
                    Log.w(TAG, "Unknown format fallback failed for ${sourceFile.name}")
                }
            }
        }
    }

    private fun unrollNestedArchives(baseDir: File, maxDepth: Int) {
        var currentDepth = 0
        while (currentDepth < maxDepth) {
            val nestedArchives = mutableListOf<File>()
            baseDir.walkTopDown().forEach { file ->
                if (file.isFile && file.name != "source_temp.bin") {
                    val ext = file.extension.lowercase(Locale.ROOT)
                    if (ARCHIVE_EXTENSIONS.contains(ext)) {
                        nestedArchives.add(file)
                    }
                }
            }

            if (nestedArchives.isEmpty()) break

            for ((index, archiveFile) in nestedArchives.withIndex()) {
                val ext = archiveFile.extension.lowercase(Locale.ROOT)
                val targetSubDir = File(archiveFile.parentFile, "unpacked_${currentDepth}_$index")
                targetSubDir.mkdirs()

                try {
                    unpackArchiveRecursive(archiveFile, targetSubDir, ext, currentDepth + 1)
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed unrolling nested archive ${archiveFile.name}: ${e.message}")
                } finally {
                    archiveFile.delete() // Remove archive file once extracted
                }
            }
            currentDepth++
        }
    }

    private fun collectAndSortImagePages(baseDir: File): List<File> {
        val imageFiles = mutableListOf<File>()

        baseDir.walkTopDown().forEach { file ->
            if (file.isFile && file.name != "source_temp.bin") {
                val name = file.name
                if (!name.startsWith(".") && !name.startsWith("__MACOSX")) {
                    val ext = file.extension.lowercase(Locale.ROOT)
                    if (IMAGE_EXTENSIONS.contains(ext) && file.length() > 0) {
                        imageFiles.add(file)
                    }
                }
            }
        }

        // Sort naturally using full relative path from baseDir
        return imageFiles.sortedWith { f1, f2 ->
            val relPath1 = f1.relativeTo(baseDir).path
            val relPath2 = f2.relativeTo(baseDir).path
            NaturalOrderComparator.comparePaths(relPath1, relPath2)
        }
    }

    private fun prepareInputFile(context: Context, uri: Uri, cacheBaseDir: File): File? {
        return try {
            val tempFile = File(cacheBaseDir, "source_temp.bin")

            // Fast path: direct file resolution for file scheme or storage paths
            val rawPath = when {
                uri.scheme == "file" -> uri.path
                uri.scheme == null && uri.toString().startsWith("/") -> uri.toString()
                uri.scheme == "content" -> {
                    val pathSegment = uri.path
                    if (pathSegment != null && pathSegment.contains("/storage/")) {
                        val subPath = pathSegment.substringAfter("/storage/")
                        val candidate = File("/storage/$subPath")
                        if (candidate.exists() && candidate.canRead() && candidate.length() > 0) {
                            candidate.absolutePath
                        } else null
                    } else null
                }
                else -> null
            }

            if (!rawPath.isNullOrEmpty()) {
                val directFile = File(rawPath)
                if (directFile.exists() && directFile.canRead() && directFile.length() > 0) {
                    return directFile
                }
            }

            // High-speed stream path with 256KB buffer
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}

                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output, bufferSize = 262144)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ContentResolver copy failed: ${e.message}")
            }

            if (tempFile.exists() && tempFile.length() > 0) tempFile else null
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing input file: ${e.message}", e)
            null
        }
    }

    private val NON_MEDIA_EXTENSIONS = setOf("txt", "xml", "json", "db", "nfo", "url", "exe", "bat", "sh", "html", "htm")

    fun detectImageTypeFromBytes(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return "jpg"
        // PNG: 89 50 4E 47
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return "png"
        // GIF: 47 49 46
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) return "gif"
        // WEBP: RIFF...WEBP
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        ) return "webp"
        // BMP: 42 4D ("BM")
        if (bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) return "bmp"
        // JXL: FF 0A or 00 00 00 0C 4A 58 4C 20
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0x0A.toByte()) return "jxl"
        if (bytes.size >= 12 && bytes[4] == 0x4A.toByte() && bytes[5] == 0x58.toByte() && bytes[6] == 0x4C.toByte()) return "jxl"
        return null
    }

    private fun isSupportedMediaEntry(entryName: String, sampleBytes: ByteArray? = null): Boolean {
        if (entryName.startsWith("__MACOSX") || entryName.startsWith(".") || entryName.contains("/.")) return false
        val ext = entryName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (NON_MEDIA_EXTENSIONS.contains(ext)) return false
        if (IMAGE_EXTENSIONS.contains(ext) || ARCHIVE_EXTENSIONS.contains(ext)) return true
        if (sampleBytes != null) {
            val magicExt = detectImageTypeFromBytes(sampleBytes)
            if (magicExt != null) return true
        }
        return !entryName.contains('.') || ext.length in 1..5
    }

    private fun extractZipFile(zipFile: File, destDir: File) {
        try {
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries().toList()
                val sortedEntries = entries.filter { !it.isDirectory && isSupportedMediaEntry(it.name) }
                    .sortedWith { e1, e2 -> NaturalOrderComparator.comparePaths(e1.name, e2.name) }

                val buffer = ByteArray(262144)

                // Instant path: Extract first 3 entries immediately so Page 1 renders instantly (< 100ms)
                sortedEntries.take(3).forEach { entry ->
                    try {
                        val outFile = File(destDir, sanitizeEntryPath(entry.name))
                        if (!outFile.exists()) {
                            outFile.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                BufferedOutputStream(FileOutputStream(outFile), 262144).use { output ->
                                    var read: Int
                                    while (input.read(buffer).also { read = it } != -1) {
                                        output.write(buffer, 0, read)
                                    }
                                    output.flush()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error extracting zip entry ${entry.name}: ${e.message}")
                    }
                }

                // Extract remaining entries with per-entry error isolation
                sortedEntries.drop(3).forEach { entry ->
                    try {
                        val outFile = File(destDir, sanitizeEntryPath(entry.name))
                        if (!outFile.exists()) {
                            outFile.parentFile?.mkdirs()
                            zip.getInputStream(entry).use { input ->
                                BufferedOutputStream(FileOutputStream(outFile), 262144).use { output ->
                                    var read: Int
                                    while (input.read(buffer).also { read = it } != -1) {
                                        output.write(buffer, 0, read)
                                    }
                                    output.flush()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error extracting zip entry ${entry.name}: ${e.message}")
                    }
                }
            }
        } catch (e: Throwable) {
            val msg = e.message ?: ""
            if (msg.contains("encrypted", ignoreCase = true) || msg.contains("password", ignoreCase = true)) {
                Log.e(TAG, "ZIP archive is password protected: $msg")
            }
            Log.w(TAG, "ZipFile failed, fallback to ZipInputStream: ${e.message}")
            tryExtractZipStream(zipFile, destDir)
        }
    }

    private fun tryExtractZipStream(zipFile: File, destDir: File) {
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile), 262144)).use { zipIn ->
                var entry: ZipEntry? = null
                val buffer = ByteArray(262144)
                while (true) {
                    try {
                        entry = zipIn.nextEntry
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed reading zip entry header: ${e.message}")
                        break
                    }
                    if (entry == null) break

                    try {
                        val entryName = entry.name
                        if (!entry.isDirectory && isSupportedMediaEntry(entryName)) {
                            val outFile = File(destDir, sanitizeEntryPath(entryName))
                            outFile.parentFile?.mkdirs()
                            BufferedOutputStream(FileOutputStream(outFile), 262144).use { out ->
                                var read: Int
                                while (zipIn.read(buffer).also { read = it } != -1) {
                                    out.write(buffer, 0, read)
                                }
                                out.flush()
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed extracting entry ${entry.name}: ${e.message}")
                    } finally {
                        try { zipIn.closeEntry() } catch (_: Throwable) {}
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "ZipInputStream failed: ${e.message}")
        }
    }

    private fun extractRar(rarFile: File, destDir: File) {
        try {
            Archive(rarFile).use { archive ->
                var header = archive.nextFileHeader()
                while (header != null) {
                    if (!header.isDirectory) {
                        val entryName = header.fileName ?: ""
                        if (entryName.isNotEmpty() && isSupportedMediaEntry(entryName)) {
                            val outFile = File(destDir, sanitizeEntryPath(entryName))
                            outFile.parentFile?.mkdirs()
                            BufferedOutputStream(FileOutputStream(outFile), 262144).use { out ->
                                archive.extractFile(header, out)
                                out.flush()
                            }
                        }
                    }
                    header = archive.nextFileHeader()
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Junrar extract failed: ${e.message}", e)
        }
    }

    private fun extract7z(file7z: File, destDir: File) {
        try {
            SevenZFile.Builder().setFile(file7z).get().use { sevenZFile ->
                var entry = sevenZFile.nextEntry
                val buffer = ByteArray(262144)
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val entryName = entry.name ?: ""
                        if (entryName.isNotEmpty() && isSupportedMediaEntry(entryName)) {
                            val outFile = File(destDir, sanitizeEntryPath(entryName))
                            outFile.parentFile?.mkdirs()
                            BufferedOutputStream(FileOutputStream(outFile), 262144).use { out ->
                                var bytesRead: Int
                                while (sevenZFile.read(buffer).also { bytesRead = it } != -1) {
                                    out.write(buffer, 0, bytesRead)
                                }
                                out.flush()
                            }
                        }
                    }
                    entry = sevenZFile.nextEntry
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "7z extract failed: ${e.message}", e)
        }
    }

    private fun extractTarFile(tarFile: File, destDir: File) {
        try {
            TarArchiveInputStream(BufferedInputStream(FileInputStream(tarFile))).use { tarIn ->
                var entry = tarIn.nextEntry
                val buffer = ByteArray(65536)
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val entryName = entry.name ?: ""
                        if (entryName.isNotEmpty() && !entryName.startsWith("__MACOSX") && !entryName.startsWith(".")) {
                            val outFile = File(destDir, sanitizeEntryPath(entryName))
                            outFile.parentFile?.mkdirs()
                            BufferedOutputStream(FileOutputStream(outFile), 65536).use { out ->
                                var read: Int
                                while (tarIn.read(buffer).also { read = it } != -1) {
                                    out.write(buffer, 0, read)
                                }
                                out.flush()
                            }
                        }
                    }
                    entry = tarIn.nextEntry
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Tar extract failed: ${e.message}", e)
        }
    }

    private fun extractPdfPages(pdfFile: File, destDir: File) {
        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                PdfRenderer(pfd).use { pdfRenderer ->
                    val pageCount = pdfRenderer.pageCount
                    Log.d(TAG, "Rendering PDF with $pageCount pages")

                    for (i in 0 until pageCount) {
                        pdfRenderer.openPage(i).use { page ->
                            val pageW = page.width
                            val pageH = page.height
                            val targetW = 1440
                            val scale = if (pageW > 0) (targetW.toFloat() / pageW.toFloat()).coerceAtLeast(1.0f) else 1.5f

                            val width = (pageW * scale).toInt().coerceAtLeast(600)
                            val height = (pageH * scale).toInt().coerceAtLeast(800)

                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)

                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val pageFile = File(destDir, String.format(Locale.ROOT, "pdf_page_%04d.jpg", i + 1))
                            FileOutputStream(pageFile).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                            }
                            bitmap.recycle()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pdf rendering failed: ${e.message}", e)
        }
    }

    private fun tryExtractZip(file: File, destDir: File): Boolean {
        return try {
            extractZipFile(file, destDir)
            collectAndSortImagePages(destDir).isNotEmpty()
        } catch (_: Throwable) { false }
    }

    private fun tryExtractRar(file: File, destDir: File): Boolean {
        return try {
            extractRar(file, destDir)
            collectAndSortImagePages(destDir).isNotEmpty()
        } catch (_: Throwable) { false }
    }

    private fun tryExtract7z(file: File, destDir: File): Boolean {
        return try {
            extract7z(file, destDir)
            collectAndSortImagePages(destDir).isNotEmpty()
        } catch (_: Throwable) { false }
    }

    private fun tryExtractPdf(file: File, destDir: File): Boolean {
        return try {
            extractPdfPages(file, destDir)
            collectAndSortImagePages(destDir).isNotEmpty()
        } catch (_: Throwable) { false }
    }

    private fun sanitizeEntryPath(entryName: String): String {
        return entryName.replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != ".." }
            .joinToString("/")
    }

    private fun detectExtensionFromFileOrUri(
        context: Context,
        uri: Uri,
        fileName: String,
        inputFile: File?
    ): String {
        if (inputFile != null && inputFile.exists() && inputFile.length() >= 4) {
            try {
                FileInputStream(inputFile).use { fis ->
                    val bytes = ByteArray(8)
                    val read = fis.read(bytes)
                    if (read >= 4) {
                        // PDF: %PDF
                        if (bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() &&
                            bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()) {
                            return "pdf"
                        }
                        // ZIP: PK
                        if (bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                            return "cbz"
                        }
                        // RAR: Rar!
                        if (bytes[0] == 0x52.toByte() && bytes[1] == 0x61.toByte() &&
                            bytes[2] == 0x72.toByte() && bytes[3] == 0x21.toByte()) {
                            return "cbr"
                        }
                        // 7Z: 7z
                        if (bytes[0] == 0x37.toByte() && bytes[1] == 0x7A.toByte() &&
                            bytes[2] == 0xBC.toByte() && bytes[3] == 0xAF.toByte()) {
                            return "7z"
                        }
                        // JPEG
                        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()) {
                            return "jpg"
                        }
                        // PNG
                        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
                            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
                            return "png"
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val extFromFileName = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extFromFileName.isNotEmpty() && extFromFileName != fileName.lowercase(Locale.ROOT)) {
            return extFromFileName
        }

        return "cbz"
    }

    private fun getFileLengthFromUri(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") {
            return File(uri.path ?: "").length()
        }
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    return cursor.getLong(sizeIndex)
                }
            }
        } catch (_: Exception) {}
        return 0L
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String {
        if (uri.scheme == "file") {
            return File(uri.path ?: "").name
        }
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}

        val result = name ?: uri.lastPathSegment ?: "Comic_Document"
        return result.substringBefore('?').substringBefore('#')
    }

    private fun sanitizeTitle(fileName: String): String {
        val clean = if (fileName.contains('.')) fileName.substringBeforeLast('.') else fileName
        return clean
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
    }

    private fun formatBadge(ext: String): String {
        return when (ext.lowercase(Locale.ROOT)) {
            "cbz", "zip" -> "CBZ"
            "cbr", "rar" -> "CBR"
            "cb7", "7z" -> "CB7"
            "cbt", "tar" -> "CBT"
            "pdf" -> "PDF"
            else -> ext.uppercase(Locale.ROOT)
        }
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun cleanCacheDirIfNeeded(extractedDir: File, maxSizeBytes: Long) {
        if (!extractedDir.exists() || !extractedDir.isDirectory) return
        try {
            val subdirs = extractedDir.listFiles()?.filter { it.isDirectory } ?: return
            var totalSize = subdirs.sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }

            var remainingCount = subdirs.size
            if (totalSize > maxSizeBytes || remainingCount > 10) {
                val sortedSubdirs = subdirs.sortedBy { dir ->
                    dir.listFiles()?.maxOfOrNull { it.lastModified() } ?: dir.lastModified()
                }
                for (dir in sortedSubdirs) {
                    if (totalSize <= maxSizeBytes && remainingCount <= 8) break
                    val dirSize = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                    if (dir.deleteRecursively()) {
                        totalSize -= dirSize
                        remainingCount--
                        Log.d(TAG, "Evicted old comic cache folder: ${dir.name}, remaining folders: $remainingCount")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cache eviction failed: ${e.message}")
        }
    }
}

object NaturalOrderComparator {
    fun comparePaths(p1: String, p2: String): Int {
        var i1 = 0
        var i2 = 0
        val len1 = p1.length
        val len2 = p2.length

        while (i1 < len1 && i2 < len2) {
            val c1 = p1[i1]
            val c2 = p2[i2]

            if (c1.isDigit() && c2.isDigit()) {
                var end1 = i1
                while (end1 < len1 && p1[end1].isDigit()) end1++
                var end2 = i2
                while (end2 < len2 && p2[end2].isDigit()) end2++

                val num1Str = p1.substring(i1, end1).trimStart('0')
                val num2Str = p2.substring(i2, end2).trimStart('0')

                if (num1Str.length != num2Str.length) {
                    return num1Str.length.compareTo(num2Str.length)
                }
                val numComp = num1Str.compareTo(num2Str)
                if (numComp != 0) return numComp

                i1 = end1
                i2 = end2
            } else {
                val charComp = c1.lowercaseChar().compareTo(c2.lowercaseChar())
                if (charComp != 0) return charComp
                i1++
                i2++
            }
        }
        return len1.compareTo(len2)
    }
}
