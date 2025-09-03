package com.mshomeguardian.logger.services

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.ServerSocket

/**
 * Minimal HTTP server that exposes captured wallpaper and screenshot files.
 *
 *  - GET /wallpaper  -> serves the latest wallpaper capture
 *  - GET /screenshot -> serves the latest screenshot capture
 */
class DashboardServer(private val context: Context, private val port: Int = 8080) {

    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null

    fun start() {
        if (serverJob != null) return
        serverJob = CoroutineScope(Dispatchers.IO).launch {
            serverSocket = ServerSocket(port)
            while (isActive) {
                val socket = serverSocket!!.accept()
                handleClient(socket)
            }
        }
    }

    fun stop() {
        serverJob?.cancel()
        serverSocket?.close()
        serverJob = null
    }

    private fun handleClient(socket: java.net.Socket) {
        socket.getInputStream().bufferedReader().use { reader ->
            val requestLine = reader.readLine() ?: ""
            val path = requestLine.split(" ").getOrNull(1) ?: "/"
            val file = when (path) {
                "/wallpaper" -> File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "wallpaper.png")
                "/screenshot" -> File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "screenshot.png")
                else -> null
            }
            val output = socket.getOutputStream()
            if (file != null && file.exists()) {
                val bytes = file.readBytes()
                output.write("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: ${bytes.size}\r\n\r\n".toByteArray())
                output.write(bytes)
            } else {
                output.write("HTTP/1.1 404 Not Found\r\n\r\n".toByteArray())
            }
            output.flush()
        }
        socket.close()
    }
}
