package com.grv.proscopes

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import kotlin.math.*

class ScopeService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var window: WindowManager? = null
    private var panel: ScopeView? = null
    private var captureHandler: Handler? = null
    private var captureThread: HandlerThread? = null
    private var screenW = 720
    private var screenH = 1600
    private var density = 1
    private var busy = false

    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotification()
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data == null || resultCode != Activity.RESULT_OK) { stopSelf(); return START_NOT_STICKY }
        val metrics = resources.displayMetrics
        screenW = metrics.widthPixels; screenH = metrics.heightPixels; density = metrics.densityDpi
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(resultCode, data)
        // Android 14+ requires a callback to be registered before creating a virtual display.
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, Handler(Looper.getMainLooper()))
        reader = ImageReader.newInstance(screenW, screenH, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay("ProPhotoScopes", screenW, screenH, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, null)
        captureThread = HandlerThread("ScopeCapture").also { it.start() }
        captureHandler = Handler(captureThread!!.looper)
        showOverlay()
        captureHandler?.postDelayed(::capture, 700)
        return START_NOT_STICKY
    }

    private fun createNotification() {
        val channel = "pro_scopes"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(channel, "Photo scopes", NotificationManager.IMPORTANCE_LOW))
        }
        val n = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channel) else Notification.Builder(this)
        startForeground(7, n.setContentTitle("Pro Photo Scopes")
            .setContentText("Live screen analysis is active")
            .setSmallIcon(android.R.drawable.ic_menu_view).build())
    }

    private fun showOverlay() {
        window = getSystemService(WINDOW_SERVICE) as WindowManager
        panel = ScopeView(this)
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val p = WindowManager.LayoutParams((screenW * 0.88f).toInt(), (screenH * 0.42f).toInt(), type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT)
        p.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        p.y = (screenH * 0.20f).toInt()
        p.alpha = 0.92f
        window?.addView(panel, p)
        panel?.setOnTouchListener(object : View.OnTouchListener {
            var startY = 0; var touchY = 0
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { startY = p.y; touchY = e.rawY.toInt(); return true }
                    MotionEvent.ACTION_MOVE -> { p.y = startY + e.rawY.toInt() - touchY; window?.updateViewLayout(panel, p); return true }
                }
                return false
            }
        })
    }

    private fun capture() {
        if (busy) { captureHandler?.postDelayed(::capture, 800); return }
        busy = true
        try {
            val image = reader?.acquireLatestImage()
            if (image != null) {
                val bitmap = imageToBitmap(image)
                image.close()
                if (bitmap != null) {
                    val data = ScopeData.from(bitmap)
                    panel?.post { panel?.data = data; panel?.invalidate() }
                    bitmap.recycle()
                }
            }
        } catch (_: Exception) { }
        busy = false
        captureHandler?.postDelayed(::capture, 850)
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val padded = Bitmap.createBitmap(image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return Bitmap.createScaledBitmap(cropped, 360, (360f * image.height / image.width).toInt().coerceAtLeast(1), true).also { cropped.recycle() }
    }

    override fun onDestroy() {
        try { window?.removeView(panel) } catch (_: Exception) {}
        virtualDisplay?.release(); reader?.close(); projection?.stop()
        captureThread?.quitSafely()
        super.onDestroy()
    }
}

data class ScopeData(
    val r: IntArray, val g: IntArray, val b: IntArray, val y: IntArray,
    val paradeR: FloatArray, val paradeG: FloatArray, val paradeB: FloatArray,
    val wave: FloatArray, val vectors: FloatArray
) {
    companion object {
        fun from(bitmap: Bitmap): ScopeData {
            val binsR = IntArray(64); val binsG = IntArray(64); val binsB = IntArray(64); val binsY = IntArray(64)
            val w = bitmap.width; val h = bitmap.height
            val pr = FloatArray(128 * 64); val pg = FloatArray(128 * 64); val pb = FloatArray(128 * 64)
            val wave = FloatArray(128 * 64); val vec = FloatArray(128 * 128)
            val px = IntArray(w * h); bitmap.getPixels(px, 0, w, 0, 0, w, h)
            var n = 0
            for (yy in 0 until h step 2) for (xx in 0 until w step 2) {
                val c = px[yy*w+xx]; val rr = Color.red(c); val gg = Color.green(c); val bb = Color.blue(c)
                val lum = (0.2126*rr + 0.7152*gg + 0.0722*bb).toInt().coerceIn(0,255)
                binsR[rr*64/256]++; binsG[gg*64/256]++; binsB[bb*64/256]++; binsY[lum*64/256]++
                val x = xx * 128 / w; val ly = lum * 63 / 255
                val row = (63-ly)*128 + x
                wave[row] = min(1f, wave[row] + 0.18f)
                val rrow = (63-rr*64/256)*128 + xx*128/w
                val grow = (63-gg*64/256)*128 + xx*128/w
                val brow = (63-bb*64/256)*128 + xx*128/w
                pr[rrow] = min(1f, pr[rrow]+0.15f); pg[grow] = min(1f, pg[grow]+0.15f); pb[brow] = min(1f, pb[brow]+0.15f)
                val cb = bb - lum; val cr = rr - lum
                val vx = (64 + cr * 0.35).roundToInt().coerceIn(0,127)
                val vy = (64 - cb * 0.35).roundToInt().coerceIn(0,127)
                vec[vy*128+vx] = min(1f, vec[vy*128+vx]+0.15f)
                n++
            }
            fun norm(a: IntArray): IntArray { val m = (a.maxOrNull() ?: 1).coerceAtLeast(1); return IntArray(a.size){ (a[it]*100/m) } }
            return ScopeData(norm(binsR), norm(binsG), norm(binsB), norm(binsY), pr,pg,pb,wave,vec)
        }
    }
}

class ScopeView(ctx: Context) : View(ctx) {
    var data: ScopeData? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bg = Paint().apply { color = 0xE8101318.toInt() }
    private val white = 0xFFF2F4F8.toInt()
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawRoundRect(0f,0f,width.toFloat(),height.toFloat(),18f,18f,bg)
        val d = data
        val cellW = width/2f; val cellH = (height-28f)/3f
        val titles = arrayOf("RGB HISTOGRAM","LUMINANCE HISTOGRAM","RGB PARADE","WAVEFORM","VECTORSCOPE")
        for (i in 0..4) {
            val col = i%2; val row = i/2
            val x = col*cellW+8; val y = row*cellH+24
            p.color = 0xFF29313D.toInt(); p.style = Paint.Style.STROKE; c.drawRect(x,y,x+cellW-16,y+cellH-10,p); p.style=Paint.Style.FILL
            p.color=white; p.textSize=10f; c.drawText(titles[i],x+4,y-8,p)
            if (d != null) when(i) {
                0 -> { drawHist(c,d.r,x,y,cellW-24,cellH-38,0xFFFF5252.toInt()); drawHist(c,d.g,x,y,cellW-24,cellH-38,0xFF69F0AE.toInt()); drawHist(c,d.b,x,y,cellW-24,cellH-38,0xFF448AFF.toInt()) }
                1 -> drawHist(c,d.y,x,y,cellW-24,cellH-38,white)
                2 -> { drawScope(c,d.paradeR,128,64,x,y,cellW-24,cellH-38,0xFFFF5252.toInt()); drawScope(c,d.paradeG,128,64,x,y,cellW-24,cellH-38,0xFF69F0AE.toInt()); drawScope(c,d.paradeB,128,64,x,y,cellW-24,cellH-38,0xFF448AFF.toInt()) }
                3 -> drawScope(c,d.wave,128,64,x,y,cellW-24,cellH-38,0xFF80D8FF.toInt())
                4 -> drawScope(c,d.vectors,128,128,x,y,cellW-24,cellH-38,0xFFFFD180.toInt())
            }
        }
        p.color=0xFF8B96A7.toInt(); p.textSize=9f
        c.drawText("Drag to move • Screen preview analysis",8f,height-5f,p)
    }
    private fun drawHist(c:Canvas,a:IntArray,x:Float,y:Float,w:Float,h:Float,color:Int) {
        p.color=color; p.strokeWidth=1.3f
        val path=Path()
        for(i in a.indices){ val xx=x+i*w/(a.size-1); val yy=y+h-h*a[i]/100f; if(i==0) path.moveTo(xx,yy) else path.lineTo(xx,yy) }
        c.drawPath(path,p)
    }
    private fun drawScope(c:Canvas,a:FloatArray,sw:Int,sh:Int,x:Float,y:Float,w:Float,h:Float,color:Int) {
        p.color=color; p.strokeWidth=1.2f
        for(yy in 0 until sh) for(xx in 0 until sw) {
            val v=a[yy*sw+xx]
            if(v>0.03f){ p.alpha=(v*230).toInt().coerceIn(20,230); c.drawPoint(x+xx*w/sw,y+yy*h/sh,p) }
        }
        p.alpha=255
    }
}
