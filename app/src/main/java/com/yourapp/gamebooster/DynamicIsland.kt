package com.yourapp.gamebooster

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

object DynamicIsland {
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())

    fun show(context: Context, message: String) {
        handler.post {
            removeOverlay()
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val inflater = LayoutInflater.from(context)
            overlayView = inflater.inflate(R.layout.dynamic_island, null)
            overlayView?.findViewById<TextView>(R.id.tv_message)?.text = message

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            params.y = 100
            windowManager?.addView(overlayView, params)

            handler.postDelayed({ removeOverlay() }, 2000)
        }
    }

    fun removeOverlay() {
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        overlayView = null
    }
}
