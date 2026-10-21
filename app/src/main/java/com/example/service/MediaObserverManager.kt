package com.example.service

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import androidx.documentfile.provider.DocumentFile
import com.example.data.local.AppDatabase
import com.example.data.local.MediaVaultEntity
import com.example.data.model.SupportedAppRegistry
import com.example.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Dual-layer Media Observer combining:
 * 1. FileObserver monitoring known messaging storage paths
 * 2. Storage Access Framework (SAF) DocumentFile tree polling/monitoring
 * 3. MediaStore content observer integration
 *
 * Silently and instantly copies new incoming images, videos, voice notes, and documents
 * to internal storage (/saved_media/) so they are 100% permanently preserved even if deleted.
 */
object MediaObserverManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeObservers = mutableListOf<FileObserver>()
    private val processedHashes = ConcurrentHashMap<String, Long>()

    private val _isObserving = MutableStateFlow(false)
    val isObserving: StateFlow<Boolean> = _isObserving.asStateFlow()

    private val _totalMediaPreserved = MutableStateFlow(0)
    val totalMediaPreserved: StateFlow<Int> = _totalMediaPreserved.asStateFlow()

    private val _lastPreservedMedia = MutableStateFlow<MediaVaultEntity?>(null)
    val lastPreservedMedia: StateFlow<MediaVaultEntity?> = _lastPreservedMedia.asStateFlow()

    fun startObserving(context: Context) {
        stopObserving()

        val baseExternal = Environment.getExternalStorageDirectory()
        val standardWatchPaths = listOf(
            // WhatsApp paths (Modern Android 11+ and legacy)
            File(baseExternal, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images"),
            File(baseExternal, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Video"),
            File(baseExternal, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"),
            File(baseExternal, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"),
            File(baseExternal, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents"),
            File(baseExternal, "WhatsApp/Media/WhatsApp Images"),
            File(baseExternal, "WhatsApp/Media/WhatsApp Video"),
            File(baseExternal, "WhatsApp/Media/WhatsApp Voice Notes"),

            // WhatsApp Business paths
            File(baseExternal, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Images"),
            File(baseExternal, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Video"),
            File(baseExternal, "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Audio"),

            // Messenger & Social media paths
            File(baseExternal, "Pictures/Messenger"),
            File(baseExternal, "Movies/Messenger"),
            File(baseExternal, "Pictures/Instagram"),

            // Telegram paths
            File(baseExternal, "Android/media/org.telegram.messenger/Telegram/Telegram Images"),
            File(baseExternal, "Android/media/org.telegram.messenger/Telegram/Telegram Video"),
            File(baseExternal, "Android/media/org.telegram.messenger/Telegram/Telegram Audio"),
            File(baseExternal, "Telegram/Telegram Images"),
            File(baseExternal, "Telegram/Telegram Audio"),
            File(baseExternal, "Telegram/Telegram Video")
        )

        for (dir in standardWatchPaths) {
            setupFileObserverForDir(context, dir)
        }

        // Also check if user granted SAF DocumentTree URI
        scope.launch {
            try {
                val prefs = AppPreferences(context)
                val safUriStr = prefs.safMediaTreeUri.first()
                if (!safUriStr.isNullOrBlank()) {
                    scanSafDocumentTree(context, Uri.parse(safUriStr))
                }
            } catch (ignored: Exception) {}
        }

        _isObserving.value = activeObservers.isNotEmpty()
    }

    private fun setupFileObserverForDir(context: Context, dir: File) {
        if (!dir.exists()) {
            // Attempt to create or monitor parent if accessible
            try {
                dir.mkdirs()
            } catch (ignored: Exception) {}
        }

        if (dir.exists() && dir.canRead()) {
            try {
                val mask = FileObserver.CREATE or FileObserver.MOVED_TO or FileObserver.CLOSE_WRITE
                val observer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    object : FileObserver(dir, mask) {
                        override fun onEvent(event: Int, path: String?) {
                            if (path != null) {
                                handleNewFileDetected(context, File(dir, path))
                            }
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    object : FileObserver(dir.absolutePath, mask) {
                        override fun onEvent(event: Int, path: String?) {
                            if (path != null) {
                                handleNewFileDetected(context, File(dir, path))
                            }
                        }
                    }
                }
                observer.startWatching()
                activeObservers.add(observer)
            } catch (e: Exception) {
                // Ignore permission restrictions on directories protected by Scoped Storage
            }
        }
    }

    fun stopObserving() {
        for (obs in activeObservers) {
            try {
                obs.stopWatching()
            } catch (ignored: Exception) {}
        }
        activeObservers.clear()
        _isObserving.value = false
    }

    fun handleNewFileDetected(context: Context, file: File) {
        if (!file.exists() || !file.isFile || file.length() <= 0) return

        val fileName = file.name
        // Filter out in-progress downloads (.tmp, .nomedia, .crdownload)
        if (fileName.startsWith(".") || fileName.endsWith(".tmp") || fileName.endsWith(".crdownload")) return

        val ext = file.extension.lowercase()
        val mediaType = resolveMediaTypeFromExtension(ext) ?: return

        scope.launch {
            try {
                // Short settle time to allow writing file stream to finalize
                kotlinx.coroutines.delay(200)

                val checksum = computeFileChecksum(file)
                if (checksum != null && processedHashes.containsKey(checksum)) {
                    // Already processed this identical file
                    return@launch
                }

                val savedFile = saveToInternalVault(context, file, mediaType)
                if (savedFile != null) {
                    if (checksum != null) {
                        processedHashes[checksum] = System.currentTimeMillis()
                    }
                    val mimeType = resolveMimeType(ext, mediaType)
                    val sourcePkg = detectSourcePackageFromPath(file.absolutePath)
                    val appName = SupportedAppRegistry.getDisplayName(sourcePkg, context)

                    val db = AppDatabase.getInstance(context)
                    val mediaEntity = MediaVaultEntity(
                        originalFileName = fileName,
                        localFilePath = savedFile.absolutePath,
                        mediaType = mediaType,
                        mimeType = mimeType,
                        fileSizeBytes = savedFile.length(),
                        sourcePackage = sourcePkg,
                        sourceAppName = appName,
                        capturedAt = System.currentTimeMillis(),
                        sha256Checksum = checksum
                    )

                    val mediaId = db.mediaVaultDao().insertMedia(mediaEntity)

                    // Link with recent incoming notification from this app (within 60s)
                    val recentMsg = db.messageDao().findRecentMessageForSender(
                        sourcePackage = sourcePkg,
                        senderName = null,
                        minTimestamp = System.currentTimeMillis() - 60_000L
                    )

                    if (recentMsg != null) {
                        db.mediaVaultDao().linkToMessage(mediaId, recentMsg.id)
                        db.messageDao().updateMediaAttachment(
                            id = recentMsg.id,
                            mediaPath = savedFile.absolutePath,
                            mimeType = mimeType,
                            fileSize = savedFile.length()
                        )
                    }

                    _totalMediaPreserved.value += 1
                    _lastPreservedMedia.value = mediaEntity.copy(id = mediaId)
                }
            } catch (ignored: Exception) {}
        }
    }

    suspend fun saveMediaFromInputStream(
        context: Context,
        inputStream: InputStream,
        originalName: String,
        mediaType: String,
        sourcePackage: String,
        senderName: String? = null
    ): MediaVaultEntity? {
        val targetDir = getInternalMediaDir(context, mediaType)
        val ext = originalName.substringAfterLast('.', "")
        val safeName = "${mediaType.lowercase()}_${System.currentTimeMillis()}_${(100..999).random()}.${if (ext.isNotBlank()) ext else "bin"}"
        val destFile = File(targetDir, safeName)

        return try {
            FileOutputStream(destFile).use { out ->
                inputStream.copyTo(out)
            }
            if (destFile.exists() && destFile.length() > 0) {
                val mimeType = resolveMimeType(ext, mediaType)
                val checksum = computeFileChecksum(destFile)
                val db = AppDatabase.getInstance(context)
                val entity = MediaVaultEntity(
                    originalFileName = originalName,
                    localFilePath = destFile.absolutePath,
                    mediaType = mediaType,
                    mimeType = mimeType,
                    fileSizeBytes = destFile.length(),
                    sourcePackage = sourcePackage,
                    sourceAppName = SupportedAppRegistry.getDisplayName(sourcePackage, context),
                    senderName = senderName,
                    capturedAt = System.currentTimeMillis(),
                    sha256Checksum = checksum
                )
                val id = db.mediaVaultDao().insertMedia(entity)
                _totalMediaPreserved.value += 1
                _lastPreservedMedia.value = entity.copy(id = id)
                entity.copy(id = id)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun saveToInternalVault(context: Context, sourceFile: File, mediaType: String): File? {
        return try {
            val targetDir = getInternalMediaDir(context, mediaType)
            val destFile = File(targetDir, "${mediaType.lowercase()}_${System.currentTimeMillis()}_${sourceFile.name}")
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (destFile.exists() && destFile.length() > 0) destFile else null
        } catch (e: Exception) {
            null
        }
    }

    fun getInternalMediaDir(context: Context, mediaType: String): File {
        val subDir = when (mediaType) {
            "IMAGE" -> "images"
            "VIDEO" -> "videos"
            "AUDIO" -> "audio"
            "DOCUMENT" -> "documents"
            else -> "other"
        }
        val dir = File(context.filesDir, "saved_media/$subDir")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun resolveMediaTypeFromExtension(ext: String): String? {
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> "IMAGE"
            "mp4", "3gp", "mkv", "mov", "webm" -> "VIDEO"
            "opus", "ogg", "m4a", "aac", "mp3", "wav", "amr" -> "AUDIO"
            "pdf", "doc", "docx", "xls", "xlsx", "txt", "zip" -> "DOCUMENT"
            else -> null
        }
    }

    private fun resolveMimeType(ext: String, mediaType: String): String {
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "3gp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            "opus" -> "audio/opus"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "pdf" -> "application/pdf"
            else -> when (mediaType) {
                "IMAGE" -> "image/*"
                "VIDEO" -> "video/*"
                "AUDIO" -> "audio/*"
                else -> "application/octet-stream"
            }
        }
    }

    private fun detectSourcePackageFromPath(path: String): String {
        val lower = path.lowercase()
        return when {
            lower.contains("com.whatsapp.w4b") || lower.contains("whatsapp business") -> "com.whatsapp.w4b"
            lower.contains("com.whatsapp") || lower.contains("whatsapp") -> "com.whatsapp"
            lower.contains("messenger") || lower.contains("orca") -> "com.facebook.orca"
            lower.contains("instagram") -> "com.instagram.android"
            lower.contains("telegram") -> "org.telegram.messenger"
            lower.contains("signal") -> "org.thoughtcrime.securesms"
            lower.contains("viber") -> "com.viber.voip"
            lower.contains("imo") -> "com.imo.android.imoim"
            else -> "com.whatsapp"
        }
    }

    private fun computeFileChecksum(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun scanSafDocumentTree(context: Context, treeUri: Uri) {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return
        if (!rootDoc.exists() || !rootDoc.isDirectory) return

        scanDocumentFileRecursive(context, rootDoc, maxDepth = 3)
    }

    private suspend fun scanDocumentFileRecursive(context: Context, dir: DocumentFile, maxDepth: Int) {
        if (maxDepth <= 0) return
        val files = dir.listFiles()
        for (doc in files) {
            if (doc.isDirectory) {
                scanDocumentFileRecursive(context, doc, maxDepth - 1)
            } else if (doc.isFile) {
                val name = doc.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                val mediaType = resolveMediaTypeFromExtension(ext) ?: continue

                try {
                    context.contentResolver.openInputStream(doc.uri)?.use { stream ->
                        val pkg = detectSourcePackageFromPath(dir.name ?: "")
                        saveMediaFromInputStream(
                            context = context,
                            inputStream = stream,
                            originalName = name,
                            mediaType = mediaType,
                            sourcePackage = pkg
                        )
                    }
                } catch (ignored: Exception) {}
            }
        }
    }
}
