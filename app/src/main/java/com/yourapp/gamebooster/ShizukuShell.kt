package com.yourapp.gamebooster

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

class ShizukuShell(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())

    private fun isShizukuReady(): Boolean {
        return Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun exec(command: String): String {
        if (!isShizukuReady()) return ""
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            ""
        }
    }

    fun execOrToast(command: String): Boolean {
        if (!isShizukuReady()) {
            showToast("Shizuku chưa sẵn sàng")
            return false
        }
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                showToast("Lệnh thất bại: $command")
            }
            exitCode == 0
        } catch (e: Exception) {
            showToast("Lỗi thực thi lệnh")
            false
        }
    }

    fun execCommands(commands: List<String>) {
        Thread {
            for (cmd in commands) {
                execOrToast(cmd)
                Thread.sleep(50)
            }
            showToast("Hoàn tất tối ưu!")
        }.start()
    }

    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
