package com.example.healthcare.views.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.hypot
import kotlin.math.min
import kotlin.random.Random

class LiquidBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Dot Paint (Crisp Cyan/White)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#8044D9E6") // Semi-transparent Cyan
    }

    // Line Paint (Faint connectivity)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#44D9E6") // Bright Cyan
    }

    private val particles = mutableListOf<ConstellationParticle>()
    
    // Connection Threshold (pixels)
    private val connectionDist = 250f 

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000 
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            updatePhysics()
            invalidate()
        }
    }

    // Static Background Colors (Dark Theme)
    private val colorCenter = Color.parseColor("#020514") // Very Dark Blue/Black
    private val colorEdge = Color.parseColor("#000000")   // Pure Black

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed || particles.isEmpty()) {
            val w = width.toFloat()
            val h = height.toFloat()
            
            // Background Gradient
            bgPaint.shader = RadialGradient(
                w / 2, h / 2,
                Math.hypot(w.toDouble(), h.toDouble()).toFloat() / 2,
                colorCenter, colorEdge,
                Shader.TileMode.CLAMP
            )

            // Initialize Particles
            particles.clear()
            // Fewer particles for N^2 connection check performance & cleaner look
            for (i in 0 until 65) { 
                 particles.add(createParticle(w, h))
            }
        }
    }
    
    private fun createParticle(w: Float, h: Float): ConstellationParticle {
        return ConstellationParticle(
            x = Random.nextFloat() * w,
            y = Random.nextFloat() * h,
            vx = (Random.nextFloat() - 0.5f) * 0.8f, // Very slow drift
            vy = (Random.nextFloat() - 0.5f) * 0.8f,
            radius = Random.nextFloat() * 3f + 2f     // Small dots (2-5px)
        )
    }

    private fun updatePhysics() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        particles.forEach { p ->
            // Update Position
            p.x += p.vx
            p.y += p.vy

            // Wall Bouncing (Keep them inside)
            if (p.x < 0) { p.x = 0f; p.vx *= -1f }
            if (p.x > w) { p.x = w; p.vx *= -1f }
            if (p.y < 0) { p.y = 0f; p.vy *= -1f }
            if (p.y > h) { p.y = h; p.vy *= -1f }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()

        // Draw Background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Draw Connections First (Behind dots)
        // O(N^2) loop - acceptable for N=65
        for (i in 0 until particles.size) {
            val p1 = particles[i]
            for (j in i + 1 until particles.size) {
                val p2 = particles[j]
                
                val dx = p1.x - p2.x
                val dy = p1.y - p2.y
                val distSq = dx*dx + dy*dy
                
                // Avoid sqrt if possible, check squared distance
                if (distSq < connectionDist * connectionDist) {
                    val dist = kotlin.math.sqrt(distSq)
                    val alpha = ((1f - dist / connectionDist) * 150).toInt() // Max alpha 150
                    
                    linePaint.alpha = alpha
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint)
                }
            }
        }

        // Draw Dots
        particles.forEach { p ->
            canvas.drawCircle(p.x, p.y, p.radius, dotPaint)
        }
    }

    private class ConstellationParticle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        val radius: Float
    )
}
