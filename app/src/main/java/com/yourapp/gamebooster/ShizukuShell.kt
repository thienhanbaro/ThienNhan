package com.yourapp.gamebooster

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.io.IOException

class ShizukuShell(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())

    fun exec(command: String): Boolean {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            process.waitFor()
            process.exitValue() == 0
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun execOrToast(command: String): Boolean {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            process.waitFor()
            if (process.exitValue() != 0) {
                handler.post {
                    Toast.makeText(context, "Lỗi: $command", Toast.LENGTH_SHORT).show()
                }
            }
            process.exitValue() == 0
        } catch (e: IOException) {
            handler.post {
                Toast.makeText(context, "Không thể chạy lệnh: $command", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    fun execCommands(commands: List<String>) {
        Thread {
            for (cmd in commands) {
                if (!execOrToast(cmd)) {
                    // tiếp tục
                }
                Thread.sleep(50)
            }
        }.start()
    }
}
