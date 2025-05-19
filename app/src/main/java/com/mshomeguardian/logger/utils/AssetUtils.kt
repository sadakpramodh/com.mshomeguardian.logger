package com.mshomeguardian.logger.utils

import android.content.res.AssetManager
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object AssetUtils {
    private const val TAG = "AssetUtils"

    /**
     * Synchronizes asset files to the specified destination directory
     *
     * @param assetManager The app's asset manager
     * @param sourceDir The source directory in assets
     * @param destDir The destination directory path
     * @return The path to the synchronized directory
     */
    fun syncAssets(assetManager: AssetManager, sourceDir: String, destDir: String?): String {
        try {
            if (destDir == null) {
                throw IOException("Destination directory is null")
            }

            val targetDir = File(destDir, sourceDir)

            // Delete target directory if it exists to ensure a clean sync
            if (targetDir.exists()) {
                deleteRecursive(targetDir)
            }

            // Create target directory
            if (!targetDir.mkdirs()) {
                throw IOException("Failed to create directory: ${targetDir.absolutePath}")
            }

            // Copy all assets
            copyAssetFolder(assetManager, sourceDir, targetDir.absolutePath)

            return targetDir.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing assets", e)
            throw e
        }
    }

    /**
     * Recursively copy an asset folder to a destination directory
     */
    private fun copyAssetFolder(assetManager: AssetManager, sourceDir: String, destDir: String) {
        try {
            val files = assetManager.list(sourceDir)

            if (files == null || files.isEmpty()) {
                // It's a file, not a directory
                copyAssetFile(assetManager, sourceDir, destDir)
                return
            }

            // It's a directory, create it and copy contents
            File(destDir).mkdirs()

            for (file in files) {
                val subSourceDir = if (sourceDir.isEmpty()) file else "$sourceDir/$file"
                val subDestDir = if (destDir.isEmpty()) file else "$destDir/$file"

                copyAssetFolder(assetManager, subSourceDir, subDestDir)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error copying asset folder from $sourceDir to $destDir", e)
            throw e
        }
    }

    /**
     * Copy a single asset file to a destination path
     */
    private fun copyAssetFile(assetManager: AssetManager, sourceFile: String, destPath: String) {
        try {
            val destFile = File(destPath)

            // Ensure parent directories exist
            destFile.parentFile?.mkdirs()

            assetManager.open(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error copying asset file from $sourceFile to $destPath", e)
            throw e
        }
    }

    /**
     * Recursively delete a directory and all its contents
     */
    private fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach { child ->
                deleteRecursive(child)
            }
        }
        fileOrDirectory.delete()
    }
}