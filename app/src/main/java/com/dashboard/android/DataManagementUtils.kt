package com.dashboard.android

import android.content.Context
import android.net.Uri
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DataManagementUtils {

    private const val TAG = "DataManagementUtils"

    fun exportData(context: Context, uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataDir = File(context.applicationInfo.dataDir)

                val outputStream = context.contentResolver.openOutputStream(uri)
                    ?: throw java.io.IOException("Failed to open output stream")

                outputStream.use { stream ->
                    ZipOutputStream(stream).use { zos ->
                        zipDirectory(dataDir, dataDir, zos)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Data exported successfully", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export data", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to export data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun zipDirectory(baseDir: File, currentDir: File, zos: ZipOutputStream) {
        val files = currentDir.listFiles() ?: return
        for (file in files) {
            // Skip cache directories to save space and avoid lock issues
            if (file.name == "cache" || file.name == "code_cache" || file.name == "lib" || file.name == "no_backup") {
                continue
            }

            // Relativize path
            val entryName = file.absolutePath.substring(baseDir.absolutePath.length + 1)

            if (file.isDirectory) {
                // Add directory entry (important for empty directories)
                val zipEntry = ZipEntry("$entryName/")
                zos.putNextEntry(zipEntry)
                zos.closeEntry()
                // Recurse into directory
                zipDirectory(baseDir, file, zos)
            } else {
                try {
                    val zipEntry = ZipEntry(entryName)
                    zos.putNextEntry(zipEntry)

                    FileInputStream(file).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to zip file: ${file.name}", e)
                    // Continue zipping other files
                }
            }
        }
    }

    fun importData(context: Context, uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataDir = File(context.applicationInfo.dataDir)

                // Note: We don't delete the entire data dir first, as that would delete our own running code/lib
                // We overwrite existing files. This means deleted files won't be pruned during import,
                // but it's much safer than wiping the whole directory.

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw java.io.IOException("Failed to open input stream")

                inputStream.use { stream ->
                    ZipInputStream(stream).use { zis ->
                        var entry: ZipEntry? = zis.nextEntry
                        while (entry != null) {
                            val outputFile = File(dataDir, entry.name)

                            // Security check: ensure path traversal is prevented
                            if (!outputFile.canonicalPath.startsWith(dataDir.canonicalPath)) {
                                throw SecurityException("Zip entry is outside of target directory")
                            }

                            if (entry.isDirectory) {
                                outputFile.mkdirs()
                            } else {
                                // Ensure parent directories exist
                                outputFile.parentFile?.mkdirs()

                                try {
                                    FileOutputStream(outputFile).use { fos ->
                                        zis.copyTo(fos)
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to write extracted file: ${outputFile.name}", e)
                                }
                            }
                            zis.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Data imported successfully. App will restart.", Toast.LENGTH_LONG).show()

                    // Restart the app to apply preferences and data changes
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (intent != null) {
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        context.startActivity(intent)
                        Runtime.getRuntime().exit(0)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to import data", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to import data: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
