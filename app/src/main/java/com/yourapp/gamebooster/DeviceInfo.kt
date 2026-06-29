package com.yourapp.gamebooster

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

object DeviceInfo {
    fun getDeviceName(): String = Build.MODEL
    fun getDeviceModel(): String = Build.MODEL
    fun getAndroidVersion(): String = Build.VERSION.RELEASE

    fun isVulkanSupported(context: Context): Boolean {
        return context.packageManager.hasSystemFeature("android.hardware.vulkan.level")
                || File("/system/lib64/libvulkan.so").exists()
                || File("/system/lib/libvulkan.so").exists()
    }
}
