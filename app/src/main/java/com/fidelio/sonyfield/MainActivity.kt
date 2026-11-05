package com.fidelio.sonyfield

import android.app.Activity
import android.os.Bundle
import android.content.pm.ActivityInfo
import android.graphics.*
import android.hardware.*
import android.view.*
import android.widget.*
import kotlin.math.*

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensors: SensorManager
    private lateinit var monitor: MonitorView
    private lateinit var status: TextView
    private lateinit var bridge: CameraBridge
    private val gravity = FloatArray(3)
    private var roll = 0f
    private var pitch = 0f
    private var zeroRoll = 0f
    private var zeroPitch = 0f
    private var rawRoll = 0f
    private var rawPitch = 0f
    private var peaking = true
    private var falseColor = false
    private var threshold = 45

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        bridge = CameraBridge(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val bar = LinearLayout(this)
        fun button(label: String, action: () -> Unit) {
            bar.addView(Button(this).apply { text = label; textSize = 10f; setOnClickListener { action() } },
                LinearLayout.LayoutParams(0, 54, 1f))
        }
        monitor = MonitorView()
        status = TextView(this).apply {
            text = "v0.2 DIAGNOSTICO | USB Streaming Sony firmware 5.00+ | Sin captura UVC aun"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        button("CERO") { zeroRoll = rawRoll; zeroPitch = rawPitch; monitor.invalidate() }
        button("PEAK") { peaking = !peaking; monitor.invalidate() }
        button("FALSE COLOR") { falseColor = !falseColor; monitor.invalidate() }
        button("USB") { status.text = bridge.usbStatus() + " | Para video UVC falta integrar decodificador" }
        bar.addView(SeekBar(this).apply {
            max = 100; progress = threshold
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    threshold = progress; monitor.invalidate()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }, LinearLayout.LayoutParams(180, 54))
        root.addView(bar)
        root.addView(monitor, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(status)
        setContentView(root)
    }
    override fun onResume() {
        super.onResume()
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }
    override fun onPause() { sensors.unregisterListener(this); super.onPause() }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        for (i in 0..2) gravity[i] = gravity[i] * 0.85f + event.values[i] * 0.15f
        val x = gravity[0]; val y = gravity[1]; val z = gravity[2]
        // Landscape: phone X axis follows the long edge of the screen.
        // atan2(y, -x) is the in-plane screen angle, centered at landscape horizontal.
        rawRoll = Math.toDegrees(atan2(y.toDouble(), -x.toDouble())).toFloat()
        rawPitch = Math.toDegrees(atan2(z.toDouble(), sqrt((x*x+y*y).toDouble()))).toFloat()
        roll = ((rawRoll - zeroRoll + 540f) % 360f) - 180f
        pitch = rawPitch - zeroPitch
        monitor.invalidate()
    }
    inner class MonitorView : View(this@MainActivity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val demo = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888).also { bitmap ->
            val c = Canvas(bitmap)
            c.drawColor(Color.rgb(34, 50, 69))
            paint.color = Color.rgb(100, 130, 160)
            c.drawRect(0f, 225f, 640f, 360f, paint)
            paint.color = Color.rgb(220, 190, 150)
            c.drawCircle(320f, 145f, 65f, paint)
            paint.color = Color.WHITE; paint.textSize = 25f
            c.drawText("IMAGEN DEMO - SIN CAMARA", 15f, 36f, paint)
        }
        private val pixels = IntArray(640 * 360).also { demo.getPixels(it, 0, 640, 0, 0, 640, 360) }
        private var tapX = -1f
        private var tapY = -1f
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
            val transformed = IntArray(pixels.size)
            for (y in 0 until 360) for (x in 0 until 640) {
                val i = y * 640 + x
                val c = pixels[i]
                val lum = (Color.red(c) * 77 + Color.green(c) * 150 + Color.blue(c) * 29) / 256
                transformed[i] = if (falseColor) when {
                    lum < 32 -> Color.MAGENTA
                    lum < 64 -> Color.BLUE
                    lum < 112 -> Color.CYAN
                    lum < 160 -> Color.GREEN
                    lum < 208 -> Color.YELLOW
                    else -> Color.RED
                } else c
                if (peaking && x > 0 && x < 639 && y > 0 && y < 359) {
                    val right = pixels[i + 1]; val below = pixels[i + 640]
                    val diff = abs(Color.red(c) - Color.red(right)) + abs(Color.red(c) - Color.red(below))
                    if (diff > 120 - threshold) transformed[i] = Color.RED
                }
            }
            val frame = Bitmap.createBitmap(transformed, 640, 360, Bitmap.Config.ARGB_8888)
            paint.color = Color.WHITE; paint.alpha = 255
            canvas.drawBitmap(frame, null, bounds, paint)
            frame.recycle()
            val cx = width / 2f; val cy = height / 2f
            paint.color = if (abs(roll) < 1.5f) Color.GREEN else Color.YELLOW
            paint.strokeWidth = 5f
            // Rotate the electronic horizon in landscape, not merely move it vertically.
            val angle = Math.toRadians(roll.toDouble())
            val dx = (cos(angle) * 130).toFloat()
            val dy = (sin(angle) * 130).toFloat()
            canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, paint)
            canvas.drawCircle(cx, cy, 7f, paint)
            paint.color = Color.WHITE; paint.textSize = 24f
            canvas.drawText("ROLL %.1f  PITCH %.1f".format(roll, pitch), 18f, height - 24f, paint)
            if (tapX >= 0) {
                paint.color = Color.CYAN; paint.style = Paint.Style.STROKE
                canvas.drawRect(tapX * width - 35f, tapY * height - 35f,
                    tapX * width + 35f, tapY * height + 35f, paint)
                paint.style = Paint.Style.FILL
            }
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action != MotionEvent.ACTION_UP) return true
            tapX = (event.x / width).coerceIn(0f, 1f)
            tapY = (event.y / height).coerceIn(0f, 1f)
            status.text = bridge.track(tapX, tapY)
            invalidate()
            return true
        }
    }
}
