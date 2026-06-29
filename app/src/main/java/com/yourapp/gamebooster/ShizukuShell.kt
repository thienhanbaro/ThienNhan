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

    /**
     * Thực thi lệnh shell qua Shizuku, trả về output
     */
    fun exec(command: String): String {
        if (!isShizukuReady()) return ""
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Thực thi lệnh và thông báo lỗi nếu có
     */
    fun execOrToast(command: String): Boolean {
        if (!isShizukuReady()) {
            handler.post {
                Toast.makeText(context, "Shizuku chưa sẵn sàng", Toast.LENGTH_SHORT).show()
            }
            return false
        }
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                handler.post {
                    Toast.makeText(context, "Lệnh thất bại: $command", Toast.LENGTH_SHORT).show()
                }
            }
            exitCode == 0
        } catch (e: Exception) {
            handler.post {
                Toast.makeText(context, "Lỗi thực thi lệnh", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    /**
     * Chạy tuần tự danh sách lệnh
     */
    fun execCommands(commands: List<String>) {
        Thread {
            for (cmd in commands) {
                execOrToast(cmd)
                Thread.sleep(50)
            }
            handler.post {
                Toast.makeText(context, "Hoàn tất tối ưu!", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun isShizukuReady(): Boolean {
        return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }
}
