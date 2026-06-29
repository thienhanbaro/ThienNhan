package com.yourapp.gamebooster

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), Shizuku.OnRequestPermissionResultListener {

    private lateinit var tvShizukuStatus: TextView
    private lateinit var tvDeviceName: TextView
    private lateinit var tvDeviceModel: TextView
    private lateinit var tvAndroidVersion: TextView
    private lateinit var tvVulkan: TextView
    private lateinit var btnGameBooster: MaterialButton
    private lateinit var btnTouchLatency: MaterialButton

    private val shell = ShizukuShell(this)
    private var originalWidth = 0
    private var originalHeight = 0
    private var originalDensity = 0
    private var targetWidth = 0
    private var targetHeight = 0
    private var targetDensity = 0

    private var gameBoosterActive = false
    private var touchLatencyActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ánh xạ view
        tvShizukuStatus = findViewById(R.id.tv_shizuku_status)
        tvDeviceName = findViewById(R.id.tv_device_name)
        tvDeviceModel = findViewById(R.id.tv_device_model)
        tvAndroidVersion = findViewById(R.id.tv_android_version)
        tvVulkan = findViewById(R.id.tv_vulkan)
        btnGameBooster = findViewById(R.id.btn_game_booster)
        btnTouchLatency = findViewById(R.id.btn_touch_latency)

        // Hiển thị thông tin thiết bị
        tvDeviceName.text = "Thiết bị: ${DeviceInfo.getDeviceName()}"
        tvDeviceModel.text = "Model: ${DeviceInfo.getDeviceModel()}"
        tvAndroidVersion.text = "Android: ${DeviceInfo.getAndroidVersion()}"
        tvVulkan.text = "Vulkan: ${if (DeviceInfo.isVulkanSupported(this)) "✅ Hỗ trợ" else "❌ Không hỗ trợ"}"

        // Yêu cầu quyền overlay nếu chưa có (cho Dynamic Island sau này)
        checkOverlayPermission()

        // Đăng ký listener Shizuku
        Shizuku.addRequestPermissionResultListener(this)

        // Kiểm tra Shizuku định kỳ
        checkShizukuStatus()

        // Xử lý nút Game Booster
        btnGameBooster.setOnClickListener {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(0)
                showToast("Yêu cầu quyền Shizuku")
                return@setOnClickListener
            }
            gameBoosterActive = !gameBoosterActive
            if (gameBoosterActive) {
                runGameBooster()
                btnGameBooster.text = "BOOST ON"
                btnGameBooster.setBackgroundResource(R.drawable.custom_toggle_bg)
            } else {
                // Tắt booster (chưa có hoàn tác chi tiết)
                btnGameBooster.text = "GAME\nBOOSTER"
                showToast("Game Booster đã tắt")
            }
        }

        // Xử lý nút Touch Latency
        btnTouchLatency.setOnClickListener {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(0)
                showToast("Yêu cầu quyền Shizuku")
                return@setOnClickListener
            }
            touchLatencyActive = !touchLatencyActive
            if (touchLatencyActive) {
                runTouchLatencyReduction()
                btnTouchLatency.text = "LATENCY ON"
                btnTouchLatency.setBackgroundResource(R.drawable.custom_toggle_bg)
            } else {
                restoreOriginal()
                btnTouchLatency.text = "TOUCH\nLATENCY"
                showToast("Đã khôi phục màn hình gốc")
            }
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Cần quyền hiển thị")
                .setMessage("Ứng dụng cần quyền hiển thị trên ứng dụng khác để hoạt động.")
                .setPositiveButton("Cấp quyền") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"))
                    startActivity(intent)
                }
                .setNegativeButton("Để sau", null)
                .show()
        }
    }

    private fun checkShizukuStatus() {
        if (!Shizuku.pingBinder()) {
            tvShizukuStatus.text = "Shizuku: Chưa chạy"
            tvShizukuStatus.setBackgroundResource(R.drawable.pill_shizuku)
        } else if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            tvShizukuStatus.text = "Shizuku: Phiên bản cũ"
        } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            tvShizukuStatus.text = "Shizuku: ✅ Đã kết nối"
            tvShizukuStatus.setBackgroundColor(0xFF00AA00.toInt())
        } else {
            tvShizukuStatus.text = "Shizuku: Cần cấp quyền"
            Shizuku.requestPermission(0)
        }
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        runOnUiThread { checkShizukuStatus() }
    }

    private fun runGameBooster() {
        showToast("Đang kích hoạt Game Booster...")
        val commands = listOf(
            "settings put global window_animation_scale 0.5",
            "settings put global transition_animation_scale 0.5",
            "settings put global animator_duration_scale 0.5",
            "settings put secure user_refresh_rate 1",
            "settings put secure miui_refresh_rate 1",
            // ... các lệnh khác giữ nguyên như code cũ
        )
        shell.execCommands(commands)
    }

    private fun runTouchLatencyReduction() {
        // Lấy độ phân giải gốc
        val sizeOut = execShell("wm size")
        val densityOut = execShell("wm density")
        if (sizeOut.isEmpty() || densityOut.isEmpty()) {
            showToast("Không thể lấy thông tin màn hình")
            return
        }
        val sizeRegex = Regex("\\d+x\\d+").find(sizeOut)
        val densityRegex = Regex("\\d+").find(densityOut)
        if (sizeRegex == null || densityRegex == null) {
            showToast("Lỗi phân giải")
            return
        }
        val parts = sizeRegex.value.split("x")
        originalWidth = parts[0].toInt()
        originalHeight = parts[1].toInt()
        originalDensity = densityRegex.value.toInt()

        val target = calculateTargetResolution(originalWidth, originalHeight)
        targetWidth = target.first
        targetHeight = target.second
        targetDensity = (originalDensity * targetHeight.toFloat() / originalHeight).toInt()

        execShell("wm size ${targetWidth}x${targetHeight}")
        execShell("wm density $targetDensity")
        showToast("Độ phân giải: ${targetWidth}x${targetHeight}")

        // Lên lịch hoàn tác sau 10s
        Executors.newSingleThreadExecutor().submit {
            Thread.sleep(10000)
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Giữ độ phân giải?")
                    .setMessage("Bạn có muốn giữ ${targetWidth}x${targetHeight} không?")
                    .setPositiveButton("Hoàn tác") { _, _ -> restoreOriginal() }
                    .setNegativeButton("Giữ") { _, _ -> }
                    .show()
            }
        }
    }

    private fun restoreOriginal() {
        execShell("wm size ${originalWidth}x${originalHeight}")
        execShell("wm density $originalDensity")
    }

    private fun execShell(cmd: String): String {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            val reader = process.inputStream.bufferedReader()
            val output = reader.readText()
            process.waitFor()
            output.trim()
        } catch (e: Exception) {
            ""
        }
    }

    private fun calculateTargetResolution(w: Int, h: Int): Pair<Int, Int> {
        // Giữ logic như cũ
        if (h <= 800) {
            val tH = 2000
            val tW = (w.toFloat() / h * tH).toInt()
            return if (testResolution(tW, tH)) Pair(tW, tH) else {
                Pair((w.toFloat() / h * 2400).toInt(), 2400)
            }
        } else if (h <= 1600) {
            val tH = 2600
            val tW = (w.toFloat() / h * tH).toInt()
            return if (testResolution(tW, tH)) Pair(tW, tH) else {
                Pair((w.toFloat() / h * 2400).toInt(), 2400)
            }
        } else if (h >= 2400) {
            val tH = 3088
            val tW = (w.toFloat() / h * tH).toInt()
            return if (testResolution(tW, tH)) Pair(tW, tH) else {
                Pair((w.toFloat() / h * 2900).toInt(), 2900)
            }
        }
        return Pair(w, h)
    }

    private fun testResolution(w: Int, h: Int): Boolean {
        execShell("wm size ${w}x${h}")
        return execShell("wm size").contains("${w}x${h}")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(this)
        super.onDestroy()
    }
}
