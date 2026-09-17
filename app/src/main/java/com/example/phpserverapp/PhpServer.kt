package com.example.phpserverapp

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Wraps a background `php -S` process so the app can serve local PHP
 * pages to its own WebView.
 *
 * Requirements this class assumes:
 *  - A php-cli binary has been placed under app/src/main/jniLibs/<ABI>/
 *    named "libphp.so" (see README in the project root for why it must
 *    be named that way and where to get the binary).
 *  - Your PHP source files are under app/src/main/assets/www/.
 */
class PhpServer(private val context: Context) {

    companion object {
        private const val TAG = "PhpServer"
        const val PORT = 8080
        const val HOST = "127.0.0.1"
    }

    private var process: Process? = null

    /** Copies assets/www/* into internal storage so PHP can read/execute them from disk. */
    private fun copyWebRootFromAssets(): File {
        val webRoot = File(context.filesDir, "www")
        copyAssetDir("www", webRoot)
        return webRoot
    }

    private fun copyAssetDir(assetPath: String, destDir: File) {
        val am = context.assets
        val entries = am.list(assetPath) ?: return
        if (!destDir.exists()) destDir.mkdirs()

        if (entries.isEmpty()) {
            // It's a file, not a directory.
            am.open(assetPath).use { input: InputStream ->
                FileOutputStream(File(destDir.parentFile, destDir.name)).use { output ->
                    input.copyTo(output)
                }
            }
            return
        }

        for (entry in entries) {
            val childAssetPath = "$assetPath/$entry"
            val childEntries = am.list(childAssetPath)
            if (childEntries != null && childEntries.isNotEmpty()) {
                copyAssetDir(childAssetPath, File(destDir, entry))
            } else {
                am.open(childAssetPath).use { input ->
                    FileOutputStream(File(destDir, entry)).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    /** Finds the php binary that was bundled for this device's ABI. */
    private fun resolvePhpBinary(): File {
        // Android extracts jniLibs into this app-specific, executable directory.
        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val binary = File(nativeLibDir, "libphp.so")
        if (!binary.exists()) {
            throw IllegalStateException(
                "php binary not found at $binary. Did you add libphp.so under jniLibs/<ABI>/?"
            )
        }
        return binary
    }

    /** Starts `php -S host:port -t webRoot` as a child process, if not already running. */
    @Synchronized
    fun start() {
        if (process != null) return

        val php = resolvePhpBinary()
        val webRoot = copyWebRootFromAssets()

        val cmd = listOf(
            php.absolutePath,
            "-S", "$HOST:$PORT",
            "-t", webRoot.absolutePath
        )

        Log.i(TAG, "Starting PHP server: $cmd")

        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        pb.directory(webRoot)
        process = pb.start()

        // Optional: drain the process output to the logcat so php errors are visible.
        Thread {
            process?.inputStream?.bufferedReader()?.forEachLine { line ->
                Log.d(TAG, "php: $line")
            }
        }.start()
    }

    /** Blocks briefly until the server responds, or times out. Call off the main thread. */
    fun waitUntilReady(timeoutMs: Long = 5000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            try {
                val conn = URL("http://$HOST:$PORT/").openConnection() as HttpURLConnection
                conn.connectTimeout = 300
                conn.readTimeout = 300
                conn.requestMethod = "GET"
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..499) return true
            } catch (e: Exception) {
                // Not up yet, retry.
            }
            Thread.sleep(150)
        }
        return false
    }

    @Synchronized
    fun stop() {
        process?.destroy()
        process = null
    }
}
