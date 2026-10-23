package com.example.gesturemouse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * 全螢幕透明、觸控可穿透的游標層（電腦版 Overlay 的對應）。
 * 辨識執行緒只更新「目標位置」，實際顯示位置由 Choreographer 每個螢幕幀向目標插值，
 * 所以游標的流暢度跟螢幕更新率一致，不受鏡頭幀率限制。
 * 所有方法都必須在主執行緒呼叫。
 */
class CursorView(context: Context) : View(context) {

    private val tau = 0.025f  // 插值時間常數（秒），對應電腦版 CURSOR_SMOOTH_TAU

    private var tx = 0f
    private var ty = 0f
    private var px = 0f
    private var py = 0f
    private var visibleCursor = false
    private var pressed = false
    private var hasPos = false

    private var armed = false
    private var debugLabel = ""
    private var hudText = ""
    private var hudUntil = 0L

    private var running = false
    private var lastNs = 0L
    private val loc = IntArray(2)

    private val dp = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * dp
        color = Color.BLACK
    }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6202124.toInt() }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 16f * dp
        textAlign = Paint.Align.CENTER
    }

    // ---- 👍 彈幕 ----
    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        val size: Float, var delay: Float,
    )

    private val particles = ArrayList<Particle>()
    private var gravity = 2000f
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    /** 從螢幕底部往上噴出一堆 👍（和電腦版的彈幕一樣，是拋物線，會落回螢幕外）。 */
    fun burst(count: Int = 30) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        gravity = 1.4f * h
        val sizes = floatArrayOf(28f, 36f, 46f, 60f)
        repeat(count) {
            val size = sizes[(Math.random() * sizes.size).toInt()] * dp
            val peak = (0.35f + Math.random().toFloat() * 0.57f) * h  // 這顆要噴多高
            particles.add(
                Particle(
                    x = (0.08f + Math.random().toFloat() * 0.84f) * w,
                    y = h + size,
                    vx = (Math.random().toFloat() * 2f - 1f) * 120f * dp,
                    vy = -sqrt(2f * gravity * peak),
                    size = size,
                    delay = Math.random().toFloat() * 0.5f,  // 錯開 0.5 秒內陸續噴出
                )
            )
        }
        kick()
    }

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val dt = if (lastNs == 0L) 0.016f else ((frameTimeNanos - lastNs) / 1e9f).coerceIn(0.001f, 0.1f)
            lastNs = frameTimeNanos
            val a = 1f - exp(-dt / tau)
            px += (tx - px) * a
            py += (ty - py) * a
            if (particles.isNotEmpty()) {
                val it = particles.iterator()
                while (it.hasNext()) {
                    val p = it.next()
                    if (p.delay > 0f) { p.delay -= dt; continue }
                    p.vy += gravity * dt
                    p.x += p.vx * dt
                    p.y += p.vy * dt
                    if (p.vy > 0f && p.y > height + p.size) it.remove()  // 掉出螢幕底部就移除
                }
            }
            invalidate()
            val hudOn = SystemClock.uptimeMillis() < hudUntil
            val moving = abs(tx - px) > 0.5f || abs(ty - py) > 0.5f
            if (visibleCursor || hudOn || moving || particles.isNotEmpty()) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                running = false
                lastNs = 0L
                invalidate()
            }
        }
    }

    private fun kick() {
        if (!running) {
            running = true
            lastNs = 0L
            Choreographer.getInstance().postFrameCallback(frame)
        }
    }

    fun setTarget(x: Float, y: Float, visible: Boolean, isPressed: Boolean) {
        tx = x; ty = y
        pressed = isPressed
        if (visible && (!visibleCursor || !hasPos)) {  // 剛出現：直接出現在目標，不要從舊位置飛過去
            px = x; py = y; hasPos = true
        }
        if (!visible) hasPos = false
        visibleCursor = visible
        kick()
    }

    /** 立刻跳到目標位置（點擊前呼叫，確保點在游標指的地方）。 */
    fun snap(x: Float, y: Float) {
        tx = x; ty = y; px = x; py = y
        invalidate()
    }

    fun setLabel(s: String) {
        debugLabel = s
        invalidate()
    }

    fun showHud(isArmed: Boolean, message: String) {
        armed = isArmed
        hudText = message
        hudUntil = SystemClock.uptimeMillis() + 1200
        kick()
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(frame)
        running = false
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        getLocationOnScreen(loc)  // 補償狀態列 / 瀏海造成的位移，讓座標等於螢幕絕對座標
        val ox = loc[0].toFloat()
        val oy = loc[1].toFloat()

        // 👍 彈幕（畫在最底層，游標與提示都蓋在上面）
        for (p in particles) {
            if (p.delay > 0f) continue
            emojiPaint.textSize = p.size
            canvas.drawText("👍", p.x, p.y + p.size * 0.35f, emojiPaint)
        }

        // 右上角小圓點：啟動中＝綠，停用＝灰
        fill.color = if (armed) 0xFF34C759.toInt() else 0x88999999.toInt()
        canvas.drawCircle(width - 14f * dp, 14f * dp + 20f * dp, 5f * dp, fill)

        if (debugLabel.isNotEmpty()) {
            val lp = Paint(text).apply { textAlign = Paint.Align.LEFT; textSize = 14f * dp }
            val w = lp.measureText(debugLabel) + 20f * dp
            val r = RectF(10f * dp, 40f * dp, 10f * dp + w, 40f * dp + 28f * dp)
            canvas.drawRoundRect(r, 14f * dp, 14f * dp, pill)
            canvas.drawText(debugLabel, r.left + 10f * dp, r.centerY() + 5f * dp, lp)
        }

        if (hudText.isNotEmpty() && SystemClock.uptimeMillis() < hudUntil) {
            val w = text.measureText(hudText) + 40f * dp
            val h = 36f * dp
            val r = RectF(width / 2f - w / 2, 60f * dp, width / 2f + w / 2, 60f * dp + h)
            canvas.drawRoundRect(r, h / 2, h / 2, pill)
            canvas.drawText(hudText, r.centerX(), r.centerY() + 6f * dp, text)
        }

        if (visibleCursor) {
            val x = px - ox
            val y = py - oy
            val rad = (if (pressed) 11f else 17f) * dp
            fill.color = if (pressed) 0xFF0A84FF.toInt() else Color.WHITE
            canvas.drawCircle(x, y, rad, fill)
            canvas.drawCircle(x, y, rad, stroke)
        }
    }
}
