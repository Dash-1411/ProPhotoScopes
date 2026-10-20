package com.grv.proscopes

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private val requestCapture = 44
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF101318.toInt())
        }
        fun label(text: String, size: Float = 16f) = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(0xFFF2F4F8.toInt())
            setPadding(0, 10, 0, 10)
        }
        root.addView(label("PRO PHOTO SCOPES", 24f))
        root.addView(label("Live floating scopes for Google Photos. Uses Android screen capture to analyse the visible preview."))
        root.addView(label("Included: RGB + luminance histograms, RGB parade, waveform and vectorscope."))
        root.addView(label("Setup: allow overlay permission, then start screen capture. Keep the Google Photos image visible while editing."))
        val overlay = Button(this).apply { text = "1 · Allow floating overlay"; setOnClickListener {
            if (!Settings.canDrawOverlays(this@MainActivity)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        } }
        root.addView(overlay)
        root.addView(Button(this).apply {
            text = "2 · Start live scopes"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } else {
                    val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    startActivityForResult(mgr.createScreenCaptureIntent(), requestCapture)
                }
            }
        })
        root.addView(Button(this).apply { text = "Stop scopes"; setOnClickListener { stopService(Intent(this@MainActivity, ScopeService::class.java)) } })
        root.addView(label("Prototype limitation: scopes analyse the whole screen, not only the photo. Status bars, controls and the image editor UI can affect readings."))
        setContentView(root)
    }

    @Deprecated("Deprecated in Android, kept for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestCapture && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, ScopeService::class.java).apply {
                putExtra("resultCode", resultCode); putExtra("data", data)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service) else startService(service)
        }
    }
}
