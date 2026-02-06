package com.dashboard.android

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Animated background view with subtle moving gradient and particles
 * Similar to Apple Music's now playing screen
 */
class AnimatedBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class BackgroundStyle {
        SOLID,
        GRADIENT,
        ANIMATED_GRADIENT,
        PARTICLES
    }

    var style: BackgroundStyle = BackgroundStyle.ANIMATED_GRADIENT
        set(value) {
            field = value
            invalidate()
        }

    var customSolidColor: Int = Color.parseColor("#0D0D0D")
        set(value) {
            field = value
            invalidate()
        }

    private val particles = mutableListOf<Particle>()
    private var animationProgress = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private val gradientColors = intArrayOf(
        Color.parseColor("#1a0533"),
        Color.parseColor("#0d1b2a"),
        Color.parseColor("#0a0a0a"),
        Color.parseColor("#1a0533")
    )

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 20000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            animationProgress = it.animatedValue as Float
            invalidate()
        }
    }

    private val particleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 60000L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            updateParticles()
            invalidate()
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initParticles()
    }

    private fun initParticles() {
        particles.clear()
        val count = 30
        for (i in 0 until count) {
            particles.add(
                Particle(
                    x = Random.nextFloat() * width,
                    y = Random.nextFloat() * height,
                    radius = Random.nextFloat() * 3f + 1f,
                    alpha = Random.nextFloat() * 0.3f + 0.1f,
                    speedX = (Random.nextFloat() - 0.5f) * 0.5f,
                    speedY = (Random.nextFloat() - 0.5f) * 0.3f
                )
            )
        }
    }

    private fun updateParticles() {
        particles.forEach { p ->
            p.x += p.speedX
            p.y += p.speedY
            
            // Wrap around edges
            if (p.x < 0) p.x = width.toFloat()
            if (p.x > width) p.x = 0f
            if (p.y < 0) p.y = height.toFloat()
            if (p.y > height) p.y = 0f
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        when (style) {
            BackgroundStyle.SOLID -> drawSolid(canvas)
            BackgroundStyle.GRADIENT -> drawStaticGradient(canvas)
            BackgroundStyle.ANIMATED_GRADIENT -> drawAnimatedGradient(canvas)
            BackgroundStyle.PARTICLES -> {
                drawAnimatedGradient(canvas)
                drawParticles(canvas)
            }
        }
    }

    private fun drawSolid(canvas: Canvas) {
        canvas.drawColor(customSolidColor)
    }

    private fun drawStaticGradient(canvas: Canvas) {
        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            gradientColors, null, Shader.TileMode.CLAMP
        )
        gradientPaint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)
    }

    private fun drawAnimatedGradient(canvas: Canvas) {
        // Create moving gradient effect
        val angle = animationProgress * 2 * Math.PI
        val offsetX = (cos(angle) * width * 0.2f).toFloat()
        val offsetY = (sin(angle) * height * 0.2f).toFloat()
        
        val gradient = LinearGradient(
            offsetX, offsetY,
            width + offsetX, height + offsetY,
            gradientColors, null, Shader.TileMode.MIRROR
        )
        gradientPaint.shader = gradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), gradientPaint)
    }

    private fun drawParticles(canvas: Canvas) {
        particles.forEach { p ->
            paint.color = Color.WHITE
            paint.alpha = (p.alpha * 255).toInt()
            canvas.drawCircle(p.x, p.y, p.radius, paint)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
        if (style == BackgroundStyle.PARTICLES) {
            particleAnimator.start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
        particleAnimator.cancel()
    }

    fun setParticlesEnabled(enabled: Boolean) {
        style = if (enabled) BackgroundStyle.PARTICLES else BackgroundStyle.ANIMATED_GRADIENT
        if (enabled && !particleAnimator.isRunning) {
            particleAnimator.start()
        } else if (!enabled) {
            particleAnimator.cancel()
        }
    }

    private data class Particle(
        var x: Float,
        var y: Float,
        val radius: Float,
        val alpha: Float,
        val speedX: Float,
        val speedY: Float
    )
}
