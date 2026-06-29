package com.yourapp.gamebooster

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuApiConstants
import rikka.shizuku.system.api.ShizukuBinder
import java.io.BufferedReader
import java.io.InputStreamReader

class ShizukuShell(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Thực thi lệnh shell thông qua Shizuku Binder (cách chính thức)
     */
    fun exec(command: String): String {
        if (!Shizuku.pingBinder()) return ""
        return try {
            val process = Runtime.getRuntime().exec("sh") // fallback, nhưng sẽ dùng Shizuku
            // Sử dụng ShizukuBinder để gọi IShellService
            val binder = ShizukuBinder.getBinder()
            // Cách đúng: gọi IShizukuService.newProcess
            // Tuy nhiên để đơn giản và tương thích, ta dùng cách sau:
            execWithShizukuBinder(command)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun execWithShizukuBinder(command: String): String {
        // Sử dụng Shizuku.newProcess (phiên bản mới vẫn hỗ trợ nếu dùng ShizukuBinder)
        // Nhưng đảm bảo quyền:
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) return ""
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
     * Thực thi lệnh và hiện Toast nếu lỗi
     */
    fun execOrToast(command: String): Boolean {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
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
     * Chạy một danh sách lệnh tuần tự
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
}
