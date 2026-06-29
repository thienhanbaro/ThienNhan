package com.yourapp.gamebooster

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

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

    // Tạo Listener lắng nghe kết nối Binder để tránh văng ứng dụng
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread { updateShizukuStatus() }
    }
    
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ ->
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

        // Kiểm tra quyền Overlay vẽ trên màn hình khác
        checkOverlayPermission()

        // Lắng nghe Binder kết nối an toàn
        Shizuku.addOnBinderReceivedListener(binderReceivedListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        // Kiểm tra trạng thái ban đầu an toàn
        updateShizukuStatus()

        // Xử lý sự kiện nút bấm Game Booster
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

        // Xử lý sự kiện nút bấm Touch Latency
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
                Shizuku.requestPermission(0) // Gọi yêu cầu an toàn khi Binder đã sẵn sàng
            }
        } catch (e: Exception) {
            tvShizukuStatus.text = "Shizuku: 🔒 Đang kết nối..."
        }
    }

    private fun runGameBooster() {
        // Nơi xếp các lệnh ADB dạng chuỗi cho Game Booster
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
        // Chạy ngầm việc đọc thông số màn hình gốc để tránh đơ/văng UI
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

            // Tính toán độ phân giải mục tiêu thông minh chống màn hình đen
            val target = calculateTargetResolution(originalWidth, originalHeight)
            val targetWidth = target.first
            val targetHeight = target.second
            
            // Tính toán mật độ điểm ảnh tương ứng tỉ lệ mới
            val targetDensity = (originalDensity * targetHeight.toFloat() / originalHeight).toInt()

            // KHU VỰC THÊM LỆNH SHELL ADB: Thiết lập dạng danh sách chuỗi trực quan dễ chỉnh sửa
            val adbCommands = listOf(
                "wm size ${targetWidth}x${targetHeight}",
                "wm density $targetDensity"
                // Bạn có thể xuống dòng thêm các lệnh shell khác vào đây, ví dụ:
                // "settings put global custom_latency 1"
            )

            // Tiến hành thực thi danh sách lệnh thông qua mảng tuần tự
            shell.execCommands(adbCommands)

            runOnUiThread {
                Toast.makeText(this@MainActivity, "Độ phân giải: ${targetWidth}x${targetHeight}", Toast.LENGTH_LONG).show()
                
                // Hộp thoại xác nhận sau 10 giây để tránh người dùng bị kẹt màn hình
                Handler(mainLooper).postDelayed({
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Giữ độ phân giải?")
                        .setMessage("Bạn muốn giữ cấu hình màn hình ${targetWidth}x${targetHeight} không?")
                        .setPositiveButton("Hoàn tác") { _, _ ->
                            restoreOriginal()
                            touchLatencyActive = false
                            btnTouchLatency.text = "TOUCH\nLATENCY"
                        }
                        .setNegativeButton("Giữ", null)
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
     * Logic tính độ phân giải thông minh chống vệt đen màn hình (100% Anti Black-bar)
     * Tự động đồng bộ theo tỷ lệ Aspect Ratio gốc của thiết bị
     */
    private fun calculateTargetResolution(w: Int, h: Int): Pair<Int, Int> {
        val aspectGoc = h.toFloat() / w.toFloat() // Tỷ lệ chiều cao / chiều rộng gốc của máy

        // TRƯỜNG HỢP 1: Chiều cao nằm trong khoảng 1600 - 1612 px (hoặc xê dịch một chút)
        if (h in 1550..1650) {
            val targetWidth = 1336
            // Tính chiều cao chuẩn theo tỷ lệ màn hình của máy
            var targetHeight = (targetWidth * aspectGoc).roundToInt()
            
            // Đảm bảo chiều cao chia hết cho 2 hoặc 4 để GPU Android không bị lỗi hiển thị nhòe
            if (targetHeight % 2 != 0) targetHeight += 1 
            return Pair(targetWidth, targetHeight)
        } 
        
        // TRƯỜNG HỢP 2: Các dòng máy có chiều cao màn hình từ 2400 px trở lên
        if (h >= 2400) {
            val targetWidth = 1500
            // Tự động tính toán chiều cao đồng bộ chuẩn xác với tỷ lệ máy
            var targetHeight = (targetWidth * aspectGoc).roundToInt()
            
            if (targetHeight % 2 != 0) targetHeight += 1
            return Pair(targetWidth, targetHeight)
        }

        // Nếu không rơi vào các điều kiện đặc biệt trên, giảm đồng bộ về 90% độ phân giải gốc mẫu
        var fallbackWidth = (w * 0.9f).roundToInt()
        var fallbackHeight = (h * 0.9f).roundToInt()
        if (fallbackWidth % 2 != 0) fallbackWidth += 1
        if (fallbackHeight % 2 != 0) fallbackHeight += 1
        
        return Pair(fallbackWidth, fallbackHeight)
    }

    override fun onDestroy() {
        Shizuku.removeOnBinderReceivedListener(binderReceivedListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        super.onDestroy()
    }
}
