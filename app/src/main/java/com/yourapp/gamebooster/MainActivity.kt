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

    private var gameBoosterActive = false
    private var touchLatencyActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ánh xạ
        tvShizukuStatus = findViewById(R.id.tv_shizuku_status)
        tvDeviceName = findViewById(R.id.tv_device_name)
        tvDeviceModel = findViewById(R.id.tv_device_model)
        tvAndroidVersion = findViewById(R.id.tv_android_version)
        tvVulkan = findViewById(R.id.tv_vulkan)
        btnGameBooster = findViewById(R.id.btn_game_booster)
        btnTouchLatency = findViewById(R.id.btn_touch_latency)

        // Thông tin thiết bị
        tvDeviceName.text = "Thiết bị: ${DeviceInfo.getDeviceName()}"
        tvDeviceModel.text = "Model: ${DeviceInfo.getDeviceModel()}"
        tvAndroidVersion.text = "Android: ${DeviceInfo.getAndroidVersion()}"
        tvVulkan.text = "Vulkan: ${if (DeviceInfo.isVulkanSupported(this)) "✅" else "❌"}"

        // Quyền overlay
        checkOverlayPermission()

        // Đăng ký lắng nghe quyền Shizuku
        Shizuku.addRequestPermissionResultListener(this)

        // Cập nhật trạng thái Shizuku
        updateShizukuStatus()

        // Nút Game Booster
        btnGameBooster.setOnClickListener {
            if (!ensureShizukuReady()) return@setOnClickListener
            gameBoosterActive = !gameBoosterActive
            if (gameBoosterActive) {
                runGameBooster()
                btnGameBooster.text = "BOOST ON"
            } else {
                btnGameBooster.text = "GAME\nBOOSTER"
                Toast.makeText(this, "Đã tắt", Toast.LENGTH_SHORT).show()
            }
        }

        // Nút Touch Latency
        btnTouchLatency.setOnClickListener {
            if (!ensureShizukuReady()) return@setOnClickListener
            touchLatencyActive = !touchLatencyActive
            if (touchLatencyActive) {
                runTouchLatencyReduction()
                btnTouchLatency.text = "LATENCY ON"
            } else {
                restoreOriginal()
                btnTouchLatency.text = "TOUCH\nLATENCY"
                Toast.makeText(this, "Đã khôi phục", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateShizukuStatus()
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Quyền hiển thị")
                .setMessage("Cần quyền hiển thị trên ứng dụng khác để dùng Dynamic Island.")
                .setPositiveButton("Cấp") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
                .setNegativeButton("Để sau", null)
                .show()
        }
    }

    private fun ensureShizukuReady(): Boolean {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Shizuku chưa chạy. Hãy mở ứng dụng Shizuku!", Toast.LENGTH_LONG).show()
            return false
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            // Yêu cầu quyền – sẽ hiện popup của Shizuku
            Shizuku.requestPermission(0)
            Toast.makeText(this, "Đang yêu cầu quyền Shizuku...", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun updateShizukuStatus() {
        if (!Shizuku.pingBinder()) {
            tvShizukuStatus.text = "Shizuku: ❌ Chưa chạy"
        } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            tvShizukuStatus.text = "Shizuku: ✅ Đã kết nối"
        } else {
            tvShizukuStatus.text = "Shizuku: 🔒 Cần cấp quyền"
            // Tự động yêu cầu nếu chưa có quyền
            Shizuku.requestPermission(0)
        }
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        runOnUiThread { updateShizukuStatus() }
    }

    private fun runGameBooster() {
        val commands = listOf(
            "settings put global window_animation_scale 0.5",
            "settings put global transition_animation_scale 0.5",
            "settings put global animator_duration_scale 0.5",
            "settings put secure user_refresh_rate 1",
            "settings put secure miui_refresh_rate 1"
        )
        shell.execCommands(commands)
        Toast.makeText(this, "Game Booster ON", Toast.LENGTH_SHORT).show()
    }

    private fun runTouchLatencyReduction() {
        val sizeOut = shell.exec("wm size")
        val densityOut = shell.exec("wm density")
        if (sizeOut.isEmpty() || densityOut.isEmpty()) {
            Toast.makeText(this, "Không lấy được thông tin màn hình", Toast.LENGTH_SHORT).show()
            return
        }
        val sizeRegex = Regex("\\d+x\\d+").find(sizeOut)
        val densityRegex = Regex("\\d+").find(densityOut)
        if (sizeRegex == null || densityRegex == null) return
        val parts = sizeRegex.value.split("x")
        originalWidth = parts[0].toInt()
        originalHeight = parts[1].toInt()
        originalDensity = densityRegex.value.toInt()

        val target = calculateTargetResolution(originalWidth, originalHeight)
        shell.exec("wm size ${target.first}x${target.second}")
        shell.exec("wm density ${(originalDensity * target.second.toFloat() / originalHeight).toInt()}")
        Toast.makeText(this, "Độ phân giải: ${target.first}x${target.second}", Toast.LENGTH_LONG).show()

        Executors.newSingleThreadExecutor().submit {
            Thread.sleep(10000)
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Giữ độ phân giải?")
                    .setMessage("Bạn muốn giữ ${target.first}x${target.second} không?")
                    .setPositiveButton("Hoàn tác") { _, _ ->
                        restoreOriginal()
                        touchLatencyActive = false
                        btnTouchLatency.text = "TOUCH\nLATENCY"
                    }
                    .setNegativeButton("Giữ", null)
                    .show()
            }
        }
    }

    private fun restoreOriginal() {
        if (originalWidth > 0) {
            shell.exec("wm size ${originalWidth}x${originalHeight}")
            shell.exec("wm density $originalDensity")
        }
    }

    private fun calculateTargetResolution(w: Int, h: Int): Pair<Int, Int> {
        if (h <= 800) {
            val tH = 2000
            val tW = (w.toFloat() / h * tH).toInt()
            return if (testResolution(tW, tH)) Pair(tW, tH) else Pair((w.toFloat() / h * 2400).toInt(), 2400)
        } else if (h <= 1600) {
            val tH = 2600
            val tW = (w.toFloat() / h * tH).toInt()
            return if (testResolution(tW, tH)) Pair(tW, tH) else Pair((w.toFloat() / h * 2400).toInt(), 2400)
        } else if (h >= 2400) {
            val tH = 3088
            val tW = (w.toFloat() / h * tH).toInt()
            return if (testResolution(tW, tH)) Pair(tW, tH) else Pair((w.toFloat() / h * 2900).toInt(), 2900)
        }
        return Pair(w, h)
    }

    private fun testResolution(w: Int, h: Int): Boolean {
        shell.exec("wm size ${w}x${h}")
        return shell.exec("wm size").contains("${w}x${h}")
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(this)
        super.onDestroy()
    }
}
