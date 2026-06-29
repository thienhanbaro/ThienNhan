package com.yourapp.gamebooster

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), Shizuku.OnRequestPermissionResultListener {

    private lateinit var tvShizukuStatus: TextView
    private lateinit var tvDeviceName: TextView
    private lateinit var tvDeviceModel: TextView
    private lateinit var tvAndroidVersion: TextView
    private lateinit var tvVulkan: TextView
    private lateinit var btnGameBooster: ToggleButton
    private lateinit var btnTouchLatency: ToggleButton

    private val shell = ShizukuShell(this)
    private var originalWidth = 0
    private var originalHeight = 0
    private var originalDensity = 0
    private var targetWidth = 0
    private var targetHeight = 0
    private var targetDensity = 0

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

        tvDeviceName.text = "Device: ${DeviceInfo.getDeviceName()}"
        tvDeviceModel.text = "ID Device: ${DeviceInfo.getDeviceModel()}"
        tvAndroidVersion.text = "Android version: ${DeviceInfo.getAndroidVersion()}"
        tvVulkan.text = "Vulkan support: ${if (DeviceInfo.isVulkanSupported(this)) "YES" else "NO"}"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Cần quyền hiển thị trên ứng dụng khác", Toast.LENGTH_LONG).show()
        }

        Shizuku.addRequestPermissionResultListener(this)
        checkShizukuStatus()

        btnGameBooster.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                runGameBooster()
            } else {
                Toast.makeText(this, "Game Booster đã tắt", Toast.LENGTH_SHORT).show()
            }
        }

        btnTouchLatency.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                runTouchLatencyReduction()
            } else {
                Toast.makeText(this, "Touch Latency Reduction đã tắt", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkShizukuStatus() {
        if (!Shizuku.pingBinder()) {
            tvShizukuStatus.text = "Shizuku: Not Running"
            tvShizukuStatus.setTextColor(getColor(R.color.red))
        } else if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            tvShizukuStatus.text = "Shizuku: Old Version"
            tvShizukuStatus.setTextColor(getColor(R.color.red))
        } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            tvShizukuStatus.text = "Shizuku: Active"
            tvShizukuStatus.setTextColor(getColor(android.R.color.holo_green_light))
        } else {
            tvShizukuStatus.text = "Shizuku: Permission Required"
            Shizuku.requestPermission(0)
        }
    }

    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        runOnUiThread { checkShizukuStatus() }
    }

    private fun runGameBooster() {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Shizuku chưa được cấp quyền!", Toast.LENGTH_SHORT).show()
            btnGameBooster.isChecked = false
            return
        }

        DynamicIsland.show(this, "Game Booster ON")
        val commands = mutableListOf(
            "settings put global window_animation_scale 0.5",
            "settings put global transition_animation_scale 0.5",
            "settings put global animator_duration_scale 0.5",
            "settings put secure user_refresh_rate 1",
            "settings put secure miui_refresh_rate 1",
            "service call SurfaceFlinger 1008 i32 1",
            "settings put global force_gpu_rendering 1",
            "setprop debug.composition.type gpu",
            "setprop debug.sf.hw 1",
            "setprop debug.egl.hw 1",
            "setprop persist.sys.composition.type gpu",
            "setprop debug.hwui.renderer skiavk",
            "setprop debug.performance.tuning 1",
            "setprop video.accelerate.hw 1",
            "setprop debug.egl.profiler 1",
            "setprop debug.egl.swapinterval -1",
            "setprop debug.gr.swapinterval 0",
            "cmd activity kill-all",
            "settings put global fstrim_mandatory_interval 1",
            "settings put global activity_manager_constants max_cached_processes 20",
            "echo 0 > /proc/sys/vm/swappiness",
            "echo 1 > /proc/sys/vm/drop_caches",
            "settings put global ram_expand_size 0",
            "setprop ro.config.hw_quickpoweron true",
            "setprop persist.sys.scrollingcache 4",
            "settings put global touch_response_time 1",
            "settings put system pointer_speed 0"
        )
        if (!DeviceInfo.isVulkanSupported(this)) {
            commands.remove("setprop debug.hwui.renderer skiavk")
        }
        shell.execCommands(commands)
    }

    private fun runTouchLatencyReduction() {
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Shizuku chưa được cấp quyền!", Toast.LENGTH_SHORT).show()
            btnTouchLatency.isChecked = false
            return
        }

        val sizeOut = execShell("wm size")
        val densityOut = execShell("wm density")
        if (sizeOut.isEmpty() || densityOut.isEmpty()) {
            Toast.makeText(this, "Không thể lấy thông tin màn hình", Toast.LENGTH_SHORT).show()
            btnTouchLatency.isChecked = false
            return
        }
        val sizeRegex = Regex("\\d+x\\d+").find(sizeOut)
        val densityRegex = Regex("\\d+").find(densityOut)
        if (sizeRegex == null || densityRegex == null) {
            btnTouchLatency.isChecked = false
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
        DynamicIsland.show(this, "Độ phân giải: ${targetWidth}x${targetHeight}")

        val extraCmds = listOf(
            "settings put secure user_refresh_rate 1",
            "settings put secure miui_refresh_rate 1",
            "settings put global force_4x_msaa 1"
        )
        shell.execCommands(extraCmds)

        Executors.newSingleThreadExecutor().submit {
            Thread.sleep(10000)
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Hoàn tác?")
                    .setMessage("Bạn có muốn giữ độ phân giải ${targetWidth}x${targetHeight} không?")
                    .setPositiveButton("Không (hoàn tác)") { _, _ -> restoreOriginal() }
                    .setNegativeButton("Giữ") { _, _ -> }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun restoreOriginal() {
        execShell("wm size ${originalWidth}x${originalHeight}")
        execShell("wm density $originalDensity")
        DynamicIsland.show(this, "Đã khôi phục gốc")
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
        if (h <= 800) {
            val tH = 2000
            val tW = (w.toFloat() / h * tH).toInt()
            return if (testResolution(tW, tH)) Pair(tW, tH) else {
                val fallH = 2400
                val fallW = (w.toFloat() / h * fallH).toInt()
                Pair(fallW, fallH)
            }
        } else if (h <= 1600) {
            val tH = 2600
            val tW = (w.toFloat() / h * tH).toInt()
            return if (testResolution(tW, tH)) Pair(tW, tH) else {
                val fallH = 2400
                val fallW = (w.toFloat() / h * fallH).toInt()
                Pair(fallW, fallH)
            }
        } else if (h >= 2400) {
            val tH = 3088
            val tW = (w.toFloat() / h * tH).toInt()
            return if (testResolution(tW, tH)) Pair(tW, tH) else {
                val fallH = 2900
                val fallW = (w.toFloat() / h * fallH).toInt()
                Pair(fallW, fallH)
            }
        } else {
            return Pair(w, h)
        }
    }

    private fun testResolution(w: Int, h: Int): Boolean {
        execShell("wm size ${w}x${h}")
        val current = execShell("wm size")
        return current.contains("${w}x${h}")
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(this)
        super.onDestroy()
    }
}
