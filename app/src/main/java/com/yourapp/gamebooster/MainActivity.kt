package com.yourapp.gamebooster

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), Shizuku.OnRequestPermissionResultListener {

    private lateinit var tvShizukuStatus: TextView
    private lateinit var tvDeviceName: TextView
    private lateinit var tvDeviceModel: TextView
    private lateinit var tvAndroidVersion: TextView
    private lateinit var tvVulkan: TextView
    private lateinit var btnGameBooster: MaterialButton
    private lateinit var btnTouchLatency: MaterialButton

    private val shell by lazy { ShizukuShell(this) }
    private var originalWidth = 0
    private var originalHeight = 0
    private var originalDensity = 0

    private var gameBoosterActive = false
    private var touchLatencyActive = false

    // Đồng bộ xử lý Binder nhận diện từ ShizukuProvider trong Manifest mẫu
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { updateShizukuStatus() }
    }
    
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { updateShizukuStatus() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvShizukuStatus = findViewById(R.id.tv_shizuku_status)
        tvDeviceName = findViewById(R.id.tv_device_name)
        tvDeviceModel = findViewById(R.id.tv_device_model)
        tvAndroidVersion = findViewById(R.id.tv_android_version)
        tvVulkan = findViewById(R.id.tv_vulkan)
        btnGameBooster = findViewById(R.id.btn_game_booster)
        btnTouchLatency = findViewById(R.id.btn_touch_latency)

        tvDeviceName.text = "Thiết bị: ${DeviceInfo.getDeviceName()}"
        tvDeviceModel.text = "Model: ${DeviceInfo.getDeviceModel()}"
        tvAndroidVersion.text = "Android: ${DeviceInfo.getAndroidVersion()}"
        tvVulkan.text = "Vulkan: ${if (DeviceInfo.isVulkanSupported(this)) "✅" else "❌"}"

        checkOverlayPermission()

        // Gắn bộ lắng nghe Binder an toàn kết hợp với Provider trong Manifest mới
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(this)

        updateShizukuStatus()

        // Lệnh tối ưu hiệu năng Game Booster
        btnGameBooster.setOnClickListener {
            if (!ensureShizukuReady()) return@setOnClickListener
            gameBoosterActive = !gameBoosterActive
            if (gameBoosterActive) {
                runGameBooster()
                btnGameBooster.text = "BOOST ON"
            } else {
                btnGameBooster.text = "GAME\nBOOSTER"
                Toast.makeText(this, "Đã tắt tối ưu", Toast.LENGTH_SHORT).show()
            }
        }

        // Thay đổi độ phân giải màn hình thông minh (Touch Latency)
        btnTouchLatency.setOnClickListener {
            if (!ensureShizukuReady()) return@setOnClickListener
            touchLatencyActive = !touchLatencyActive
            if (touchLatencyActive) {
                runTouchLatencyReduction()
                btnTouchLatency.text = "LATENCY ON"
            } else {
                restoreOriginal()
                btnTouchLatency.text = "TOUCH\nLATENCY"
                Toast.makeText(this, "Đã khôi phục màn hình gốc", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateShizukuStatus()
    }

    private fun checkOverlayPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Quyền hiển thị")
                .setMessage("Cần quyền hiển thị trên ứng dụng khác để sử dụng chức năng.")
                .setPositiveButton("Cấp quyền") { _, _ ->
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
                .setNegativeButton("Hủy", null)
                .show()
        }
    }

    private fun ensureShizukuReady(): Boolean {
        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Shizuku chưa chạy. Hãy mở ứng dụng Shizuku!", Toast.LENGTH_LONG).show()
            return false
        }
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(0)
                return false
            }
        } catch (e: Exception) {
            return false
        }
        return true
    }

    private fun updateShizukuStatus() {
        if (!Shizuku.pingBinder()) {
            tvShizukuStatus.text = "Shizuku: ❌ Chưa chạy"
            return
        }
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                tvShizukuStatus.text = "Shizuku: ✅ Đã kết nối"
            } else {
                tvShizukuStatus.text = "Shizuku: 🔒 Chờ cấp quyền"
                Shizuku.requestPermission(0) // Gọi an toàn qua cấu trúc Provider mới, tự động hiện thông báo xin quyền không lo bị đóng ứng dụng
            }
        } catch (e: Exception) {
            tvShizukuStatus.text = "Shizuku: 🔒 Đang liên kết..."
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
        Toast.makeText(this, "Đã tối ưu hiệu năng", Toast.LENGTH_SHORT).show()
    }

    private fun runTouchLatencyReduction() {
        Executors.newSingleThreadExecutor().submit {
            val sizeOut = shell.exec("wm size")
            val densityOut = shell.exec("wm density")
            
            if (sizeOut.isEmpty() || densityOut.isEmpty()) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Lỗi lấy thông số màn hình", Toast.LENGTH_SHORT).show() }
                return@submit
            }
            
            val sizeRegex = Regex("\\d+x\\d+").find(sizeOut)
            val densityRegex = Regex("\\d+").find(densityOut)
            if (sizeRegex == null || densityRegex == null) return@submit
            
            val parts = sizeRegex.value.split("x")
            originalWidth = parts[0].toInt()
            originalHeight = parts[1].toInt()
            originalDensity = densityRegex.value.toInt()

            val target = calculateTargetResolution(originalWidth, originalHeight)
            val targetWidth = target.first
            val targetHeight = target.second
            
            val targetDensity = (originalDensity * targetHeight.toFloat() / originalHeight).toInt()

            // 🛠️ MẢNG SHELL ADB: Bạn dễ dàng xuống dòng thêm các lệnh shell tối ưu khác vào đây
            val adbCommands = listOf(
                "wm size ${targetWidth}x${targetHeight}",
                "wm density $targetDensity"
                // Bạn có thể chèn thêm lệnh shell dạng chuỗi tại đây:
                // "settings put global custom_touch 1"
            )

            shell.execCommands(adbCommands)

            runOnUiThread {
                Toast.makeText(this@MainActivity, "Độ phân giải mới: ${targetWidth}x${targetHeight}", Toast.LENGTH_LONG).show()
                
                Handler(Looper.getMainLooper()).postDelayed({
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Giữ cấu hình hiển thị?")
                        .setMessage("Bạn muốn áp dụng độ phân giải ${targetWidth}x${targetHeight} lâu dài không?")
                        .setPositiveButton("Hoàn tác") { _, _ ->
                            restoreOriginal()
                            touchLatencyActive = false
                            btnTouchLatency.text = "TOUCH\nLATENCY"
                        }
                        .setNegativeButton("Giữ cấu hình", null)
                        .show()
                }, 10000)
            }
        }
    }

    private fun restoreOriginal() {
        if (originalWidth > 0) {
            val restoreCommands = listOf(
                "wm size ${originalWidth}x${originalHeight}",
                "wm density $originalDensity"
            )
            shell.execCommands(restoreCommands)
        }
    }

    /**
     * Logic tính toán độ phân giải thông minh đồng bộ theo Tỷ lệ khung hình thiết bị (Anti Black-bar)
     */
    private fun calculateTargetResolution(w: Int, h: Int): Pair<Int, Int> {
        val aspectGoc = h.toFloat() / w.toFloat()

        // Nếu chiều cao gốc tầm 1600 - 1612 px hoặc hơn một tí
        if (h in 1550..1650) {
            val targetWidth = 1336
            var targetHeight = (targetWidth * aspectGoc).roundToInt()
            if (targetHeight % 2 != 0) targetHeight += 1
            return Pair(targetWidth, targetHeight)
        } 
        
        // Nếu chiều cao gốc từ 2400 px trở lên
        if (h >= 2400) {
            val targetWidth = 1500
            var targetHeight = (targetWidth * aspectGoc).roundToInt()
            if (targetHeight % 2 != 0) targetHeight += 1
            return Pair(targetWidth, targetHeight)
        }

        var fallbackWidth = (w * 0.9f).roundToInt()
        var fallbackHeight = (h * 0.9f).roundToInt()
        if (fallbackWidth % 2 != 0) fallbackWidth += 1
        if (fallbackHeight % 2 != 0) fallbackHeight += 1
        
        return Pair(fallbackWidth, fallbackHeight)
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(this)
        super.onDestroy()
    }
}
