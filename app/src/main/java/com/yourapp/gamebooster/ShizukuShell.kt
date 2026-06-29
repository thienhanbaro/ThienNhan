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
        return try {
            Shizuku.pingBinder() &&
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
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
            showToast("Shizuku chưa sẵn sàng hoặc thiếu quyền")
            return false
        }
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                showToast("Lệnh lỗi hoặc không tương thích: $command")
            }
            exitCode == 0
        } catch (e: Exception) {
            showToast("Lỗi thực thi hệ thống")
            false
        }
    }

    // Thực thi tuần tự danh sách lệnh ADB đã được định hình dạng mảng
    fun execCommands(commands: List<String>) {
        Thread {
            for (cmd in commands) {
                if (cmd.isNotBlank()) {
                    execOrToast(cmd)
                    Thread.sleep(80) // Độ trễ nhỏ giúp hệ thống áp dụng cấu hình mượt mà hơn
                }
            }
            showToast("Áp dụng lệnh hệ thống hoàn tất!")
        }.start()
    }

    private fun showToast(message: String) {
        handler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
