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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class LiquidBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // Particle Paint (Cyan/Blue - "The Perfect Version")
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#44D9E6") // Bright Cyan
    }

    private val particles = mutableListOf<SpiralParticle>()
    
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000 
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
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
            for (i in 0 until 180) { 
                 particles.add(createParticle(w, h, true))
            }
        }
    }
    
    private fun createParticle(w: Float, h: Float, randomizeDist: Boolean): SpiralParticle {
        val maxDist = hypot(w / 2.0, h / 2.0).toFloat()
        val startDist = if (randomizeDist) Random.nextFloat() * maxDist else 0f
        
        return SpiralParticle(
            angle = Random.nextDouble(0.0, 2 * Math.PI),
            dist = startDist,
            // SLOWER SPEED (Reduced by ~50%)
            radialSpeed = Random.nextFloat() * 2f + 1f, 
            angularSpeed = (Random.nextDouble(0.5, 1.5) * 0.01).toFloat(),
            // INCREASED THICKNESS ("Little bit of width")
            baseLength = Random.nextFloat() * 20f + 5f, 
            thickness = Random.nextFloat() * 6f + 4f    // 4px to 10px (Thicker than original 2-6px)
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2
        val cy = h / 2
        val maxDist = hypot(w / 2.0, h / 2.0).toFloat()

        // Draw Background
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Draw Particles
        val iter = particles.listIterator()
        while (iter.hasNext()) {
            val p = iter.next()
            
            // Update Physics
            p.dist += p.radialSpeed
            p.angle += p.angularSpeed

            // Respawn
            if (p.dist > maxDist * 1.1f) {
                 iter.set(createParticle(w, h, false))
                 continue
            }

            // Calculations
            val progress = (p.dist / maxDist).coerceIn(0f, 1f)
            val currentLength = p.baseLength * (1f + progress)
            
            // Alpha fade
            val alpha = when {
                progress < 0.15f -> (progress / 0.15f * 255).toInt() 
                progress > 0.85f -> ((1f - progress) / 0.15f * 255).toInt()
                else -> 255
            }

            // VIBRATION EFFECT
            // Add random jitter to angle and distance for this frame only
            val vibrateAngle = p.angle + (Random.nextDouble() - 0.5) * 0.005 // Jitter angle
            val vibrateDist = p.dist + (Random.nextFloat() - 0.5f) * 2f    // Jitter distance (shaking)

            val cosA = cos(vibrateAngle).toFloat()
            val sinA = sin(vibrateAngle).toFloat()
            
            val headX = cx + cosA * vibrateDist
            val headY = cy + sinA * vibrateDist
            
            // Tangent Alignment
            val tanV = p.dist * p.angularSpeed
            val radV = p.radialSpeed
            val speedTotal = hypot(tanV, radV)
            
            val dirRad = radV / speedTotal
            val dirTan = tanV / speedTotal
            
            val vecX = dirRad * cosA - dirTan * sinA
            val vecY = dirRad * sinA + dirTan * cosA
            
            val tailX = headX - vecX * currentLength
            val tailY = headY - vecY * currentLength

            particlePaint.strokeWidth = p.thickness
            particlePaint.alpha = alpha   
            
            canvas.drawLine(tailX, tailY, headX, headY, particlePaint)
        }
    }

    private class SpiralParticle(
        var angle: Double,
        var dist: Float,
        val radialSpeed: Float,
        val angularSpeed: Float,
        val baseLength: Float,
        val thickness: Float
    )
}
