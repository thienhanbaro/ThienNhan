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

    // Khai báo listener đúng chuẩn của Shizuku để theo dõi kết nối Binder
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { updateShizukuStatus() }
    }
    
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread { updateShizukuStatus() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ánh xạ View
        tvShizukuStatus = findViewById(R.id.tv_shizuku_status)
        tvDeviceName = findViewById(R.id.tv_device_name)
        tvDeviceModel = findViewById(R.id.tv_device_model)
        tvAndroidVersion = findViewById(R.id.tv_android_version)
        tvVulkan = findViewById(R.id.tv_vulkan)
        btnGameBooster = findViewById(R.id.btn_game_booster)
        btnTouchLatency = findViewById(R.id.btn_touch_latency)

        // Hiển thị thông tin máy
        tvDeviceName.text = "Thiết bị: ${DeviceInfo.getDeviceName()}"
        tvDeviceModel.text = "Model: ${DeviceInfo.getDeviceModel()}"
        tvAndroidVersion.text = "Android: ${DeviceInfo.getAndroidVersion()}"
        tvVulkan.text = "Vulkan: ${if (DeviceInfo.isVulkanSupported(this)) "✅" else "❌"}"

        // Kiểm tra quyền vẽ trên ứng dụng khác
        checkOverlayPermission()

        // Đăng ký lắng nghe sự kiện Binder của Shizuku để tránh lỗi văng khi chưa có Binder
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(this)

        // Cập nhật trạng thái hiển thị
        updateShizukuStatus()

        // Sự kiện nút bấm Game Booster
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

        // Sự kiện nút bấm Touch Latency / Thay đổi độ phân giải
        btnTouchLatency.setOnClickListener {
            if (!ensureShizukuReady()) return@setOnClickListener
            touchLatencyActive = !touchLatencyActive
            if (touchLatencyActive) {
                runTouchLatencyReduction()
                btnTouchLatency.text = "LATENCY ON"
            } else {
                restoreOriginal()
                btnTouchLatency.text = "TOUCH\nLATENCY"
                Toast.makeText(this, "Đã khôi phục độ phân giải gốc", Toast.LENGTH_SHORT).show()
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
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(0)
                Toast.makeText(this, "Đang yêu cầu quyền Shizuku...", Toast.LENGTH_SHORT).show()
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
                tvShizukuStatus.text = "Shizuku: 🔒 Cần cấp quyền"
                Shizuku.requestPermission(0)
            }
        } catch (e: Exception) {
            tvShizukuStatus.text = "Shizuku: 🔒 Đang kết nối..."
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
        Executors.newSingleThreadExecutor().submit {
            val sizeOut = shell.exec("wm size")
            val densityOut = shell.exec("wm density")
            
            if (sizeOut.isEmpty() || densityOut.isEmpty()) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Không lấy được thông tin màn hình", Toast.LENGTH_SHORT).show() }
                return@submit
            }
            
            val sizeRegex = Regex("\\d+x\\d+").find(sizeOut)
            val densityRegex = Regex("\\d+").find(densityOut)
            if (sizeRegex == null || densityRegex == null) return@submit
            
            val parts = sizeRegex.value.split("x")
            originalWidth = parts[0].toInt()
            originalHeight = parts[1].toInt()
            originalDensity = densityRegex.value.toInt()

            // Gọi logic tính toán độ phân giải thông minh chống vệt đen màn hình
            val target = calculateTargetResolution(originalWidth, originalHeight)
            val targetWidth = target.first
            val targetHeight = target.second
            
            val targetDensity = (originalDensity * targetHeight.toFloat() / originalHeight).toInt()

            // ĐỊNH DẠNG MẢNG LỆNH ADB SHELL - Bạn có thể dễ dàng thêm bớt lệnh tại đây
            val adbCommands = listOf(
                "wm size ${targetWidth}x${targetHeight}",
                "wm density $targetDensity"
                // Bạn có thể thêm lệnh shell bất kỳ xuống dưới dòng này, ví dụ:
                // "settings put global touch_latency 0"
            )

            shell.execCommands(adbCommands)

            runOnUiThread {
                Toast.makeText(this@MainActivity, "Độ phân giải mới: ${targetWidth}x${targetHeight}", Toast.LENGTH_LONG).show()
                
                // Hộp thoại an toàn để khôi phục tránh đen hẳn màn hình nếu máy không tương thích
                Handler(Looper.getMainLooper()).postDelayed({
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Xác nhận độ phân giải")
                        .setMessage("Bạn có muốn giữ cấu hình màn hình mới này không?")
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
     * Tự động tính toán độ phân giải dựa theo tỉ lệ khung hình gốc (Anti Black-Bar)
     */
    private fun calculateTargetResolution(w: Int, h: Int): Pair<Int, Int> {
        val aspectGoc = h.toFloat() / w.toFloat()

        // Chiều cao gốc từ khoảng 1600 - 1612 px hoặc lân cận
        if (h in 1550..1650) {
            val targetWidth = 1336
            var targetHeight = (targetWidth * aspectGoc).roundToInt()
            if (targetHeight % 2 != 0) targetHeight += 1
            return Pair(targetWidth, targetHeight)
        } 
        
        // Chiều cao gốc từ 2400 px trở lên
        if (h >= 2400) {
            val targetWidth = 1500
            var targetHeight = (targetWidth * aspectGoc).roundToInt()
            if (targetHeight % 2 != 0) targetHeight += 1
            return Pair(targetWidth, targetHeight)
        }

        // Phương án dự phòng cho các màn hình khác (giảm đều về 90% kích thước gốc)
        var fallbackWidth = (w * 0.9f).roundToInt()
        var fallbackHeight = (h * 0.9f).roundToInt()
        if (fallbackWidth % 2 != 0) fallbackWidth += 1
        if (fallbackHeight % 2 != 0) fallbackHeight += 1
        
        return Pair(fallbackWidth, fallbackHeight)
    }

    override fun onDestroy() {
        // Hủy lắng nghe chính xác theo tên hàm của Shizuku SDK
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(this)
        super.onDestroy()
    }
}
