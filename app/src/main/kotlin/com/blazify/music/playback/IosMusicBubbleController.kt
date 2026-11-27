package com.blazify.music.playback

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.allowHardware
import androidx.media3.common.Player
import com.blazify.music.constants.BubbleTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import timber.log.Timber

/**
 * Low-spec native floating Now Playing bubble.
 *
 * The compact bubble is intentionally non-expandable: only Prev/Stop/Next are
 * clickable. Keeping one stable view hierarchy avoids the touch/resize bugs
 * that previously made the overlay disappear or zoom its artwork.
 */
class IosMusicBubbleController(
    private val service: MusicService,
    private var theme: BubbleTheme = BubbleTheme.IOS_LIKE,
) {
    private val context: Context = service
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density

    private var view: BubbleView? = null
    private var player: Player? = null
    private var attached = false
    private val handler = Handler(Looper.getMainLooper())

    private val progressTicker = object : Runnable {
        override fun run() {
            if (!attached) return
            player?.let { view?.updateProgressOnly(it) }
            handler.postDelayed(this, 750L)
        }
    }

    fun attach(player: Player) {
        if (attached || !Settings.canDrawOverlays(context)) return
        this.player = player

        // Construct the view inside runCatching too. A runtime failure while
        // creating a child must never take down MusicService and hide playback.
        runCatching {
            val v = BubbleView(context)
            val width = v.themeWidth().coerceAtMost(context.resources.displayMetrics.widthPixels - dp(16))
            val height = v.themeHeight()
            val lp = WindowManager.LayoutParams(
                width,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT,
            ).apply {
                // START positioning lets us keep the bubble exactly where the
                // user's finger leaves it. Initial position is centered.
                gravity = Gravity.TOP or Gravity.START
                x = ((context.resources.displayMetrics.widthPixels - width) / 2).coerceAtLeast(dp(8))
                y = dp(18)
            }
            wm.addView(v, lp)
            view = v
            attached = true
            v.update(player)
            handler.removeCallbacks(progressTicker)
            handler.post(progressTicker)
            Timber.tag("IosMusicBubble").i("bubble attached theme=%s", theme)
        }.onFailure { error ->
            view?.let { runCatching { wm.removeView(it) } }
            view = null
            attached = false
            Timber.tag("IosMusicBubble").e(error, "bubble attach failed")
            // Retry after a short delay for OEMs that temporarily reject an
            // overlay while the foreground service is being promoted.
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                if (!attached && Settings.canDrawOverlays(context) && player != null) {
                    attach(player!!)
                }
            }, 1000L)
        }
    }

    fun update(player: Player) {
        this.player = player
        if (attached) view?.update(player)
    }

    fun setTheme(newTheme: BubbleTheme) {
        if (theme == newTheme) return
        theme = newTheme
        val current = player ?: return
        if (!attached) return

        val old = view ?: return
        val oldLp = old.layoutParams as? WindowManager.LayoutParams ?: return
        val replacement = runCatching { BubbleView(context) }.getOrElse {
            Timber.tag("IosMusicBubble").e(it, "theme view creation failed")
            return
        }
        val newLp = WindowManager.LayoutParams(
            replacement.themeWidth().coerceAtMost(context.resources.displayMetrics.widthPixels - dp(16)),
            replacement.themeHeight(),
            oldLp.type,
            oldLp.flags,
            oldLp.format,
        ).apply {
            gravity = oldLp.gravity
            x = oldLp.x
            y = oldLp.y
        }

        runCatching {
            replacement.alpha = 0f
            wm.addView(replacement, newLp)
            replacement.update(current)
            view = replacement
            replacement.animate()
                .alpha(1f)
                .setDuration(170L)
                .setInterpolator(android.view.animation.PathInterpolator(.16f, 1f, .3f, 1f))
                .start()
            old.animate()
                .alpha(0f)
                .setDuration(120L)
                .withEndAction { runCatching { wm.removeView(old) } }
                .start()
        }.onFailure {
            view = old
            Timber.tag("IosMusicBubble").e(it, "theme switch failed")
        }
    }

    fun detach() {
        handler.removeCallbacks(progressTicker)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        player = null
        attached = false
    }

    private fun dp(value: Int) = (value * density).roundToInt()

    private inner class BubbleView(context: Context) : LinearLayout(context) {
        private val cover = ImageView(context)
        private val title = TextView(context)
        private val artist = TextView(context)
        private val waveform = LinearLayout(context)
        private val previous = ImageView(context)
        private val stop = ImageView(context)
        private val next = ImageView(context)
        private var lastArtwork: Uri? = null
        private var lastPlayer: Player? = null
        private var downX = 0f
        private var downY = 0f
        private var startWindowX = 0
        private var startWindowY = 0
        private var dragging = false

        private val accent = when (theme) {
            BubbleTheme.IOS_LIKE -> Color.WHITE
            BubbleTheme.MODERN_LIKE -> Color.rgb(190, 225, 255)
            BubbleTheme.BASIC_LIKE -> Color.WHITE
        }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, dp(7), 0)
            // Keep the overlay draggable, but never let it leave the visible
            // display. No edge snapping: the finger's final position is kept.
            background = backgroundForTheme()
            elevation = dp(16).toFloat()
            isClickable = true
            isFocusable = false
            setupContent()
        }

        fun themeWidth(): Int = dp(
            when (theme) {
                BubbleTheme.IOS_LIKE -> 196
                BubbleTheme.MODERN_LIKE -> 210
                BubbleTheme.BASIC_LIKE -> 188
            },
        )

        fun themeHeight(): Int = dp(
            when (theme) {
                BubbleTheme.IOS_LIKE -> 44
                BubbleTheme.MODERN_LIKE -> 48
                BubbleTheme.BASIC_LIKE -> 44
            },
        )

        private fun setupContent() {
            val artSize = when (theme) {
                BubbleTheme.IOS_LIKE -> 28
                BubbleTheme.MODERN_LIKE -> 30
                BubbleTheme.BASIC_LIKE -> 27
            }
            cover.layoutParams = LayoutParams(dp(artSize), dp(artSize))
            cover.scaleType = ImageView.ScaleType.CENTER_CROP
            cover.background = rounded(if (theme == BubbleTheme.IOS_LIKE) 9f else 7f)
            addView(cover)

            val info = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            title.textSize = if (theme == BubbleTheme.BASIC_LIKE) 10f else 10.5f
            title.setTextColor(Color.WHITE)
            title.maxLines = 1
            title.ellipsize = android.text.TextUtils.TruncateAt.END
            artist.textSize = 8.5f
            artist.setTextColor(Color.argb(145, 255, 255, 255))
            artist.maxLines = 1
            artist.ellipsize = android.text.TextUtils.TruncateAt.END
            info.addView(title, LayoutParams(0, dp(16), 1f))
            info.addView(artist, LayoutParams(0, dp(13), 1f))
            addView(info, LayoutParams(0, -1, 1f).apply {
                leftMargin = dp(8)
                rightMargin = dp(5)
            })

            waveform.orientation = HORIZONTAL
            waveform.gravity = Gravity.CENTER_VERTICAL
            val count = when (theme) {
                BubbleTheme.IOS_LIKE -> 6
                BubbleTheme.MODERN_LIKE -> 7
                BubbleTheme.BASIC_LIKE -> 4
            }
            repeat(count) { i ->
                val bar = View(context).apply {
                    background = rounded(2f, accent)
                    alpha = .55f
                }
                waveform.addView(bar, LayoutParams(dp(2), dp(6 + (i % 4) * 3)).apply {
                    leftMargin = if (i == 0) 0 else dp(2)
                })
                android.animation.ValueAnimator.ofFloat(.35f, 1f, .45f).apply {
                    duration = 480L + i * 55L
                    startDelay = i * 60L
                    repeatCount = android.animation.ValueAnimator.INFINITE
                    interpolator = android.view.animation.PathInterpolator(.22f, .8f, .25f, 1f)
                    addUpdateListener { a ->
                        val f = a.animatedValue as Float
                        bar.scaleY = f
                        bar.alpha = .45f + f * .5f
                    }
                    start()
                }
            }
            addView(waveform, LayoutParams(if (theme == BubbleTheme.BASIC_LIKE) dp(22) else dp(28), dp(30)))

            configureButton(previous, LineIconDrawable.PREV)
            configureButton(stop, LineIconDrawable.STOP)
            configureButton(next, LineIconDrawable.NEXT)
            addView(previous, LayoutParams(dp(28), dp(34)).apply { leftMargin = dp(1) })
            addView(stop, LayoutParams(dp(28), dp(34)).apply { leftMargin = dp(1) })
            addView(next, LayoutParams(dp(28), dp(34)).apply { leftMargin = dp(1) })

            previous.setOnClickListener { lastPlayer?.seekToPreviousMediaItem() }
            stop.setOnClickListener { lastPlayer?.stop() }
            next.setOnClickListener { lastPlayer?.seekToNextMediaItem() }
        }

        private fun configureButton(icon: ImageView, type: Int) {
            icon.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(34, 255, 255, 255))
                setStroke(dp(1), Color.argb(42, 255, 255, 255))
            }
            icon.setPadding(dp(5), dp(5), dp(5), dp(5))
            icon.setImageDrawable(LineIconDrawable(type, Color.WHITE))
            icon.isClickable = true
            icon.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> v.animate().scaleX(.82f).scaleY(.82f).setDuration(55L).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        v.animate().scaleX(1f).scaleY(1f).setDuration(130L).start()
                }
                false
            }
        }

        // Child buttons receive their own taps. Empty/background areas are
        // draggable and the window is clamped to the physical display.
        override fun onInterceptTouchEvent(event: MotionEvent): Boolean = false

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val lp = layoutParams as? WindowManager.LayoutParams ?: return true
            val screenW = context.resources.displayMetrics.widthPixels
            val screenH = context.resources.displayMetrics.heightPixels
            val maxX = (screenW - width - dp(8)).coerceAtLeast(dp(8))
            val maxY = (screenH - height - dp(8)).coerceAtLeast(dp(8))

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startWindowX = lp.x
                    startWindowY = lp.y
                    dragging = false
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > dp(4) || abs(dy) > dp(4))) {
                        dragging = true
                    }
                    if (dragging) {
                        lp.x = (startWindowX + dx.roundToInt()).coerceIn(dp(8), maxX)
                        lp.y = (startWindowY + dy.roundToInt()).coerceIn(dp(8), maxY)
                        runCatching { wm.updateViewLayout(this, lp) }
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
            return true
        }

        fun update(player: Player) {
            lastPlayer = player
            title.text = player.mediaMetadata.title?.toString().orEmpty().ifBlank { "Strix Music" }
            artist.text = player.mediaMetadata.artist?.toString().orEmpty().ifBlank { "Now Playing" }
            val artwork = player.mediaMetadata.artworkUri
            if (artwork != null && artwork != lastArtwork) {
                lastArtwork = artwork
                SingletonImageLoader.get(context).enqueue(
                    ImageRequest.Builder(context)
                        .data(artwork)
                        .allowHardware(false)
                        .target { image ->
                            val drawable: Drawable = image.asDrawable(context.resources)
                            cover.setImageDrawable(drawable)
                        }
                        .build(),
                )
            }
            updateProgressOnly(player)
        }

        fun updateProgressOnly(player: Player) {
            // The compact bubble has no progress bar; this method intentionally
            // stays cheap and exists for the shared 750 ms ticker.
        }

        private fun rounded(radius: Float, color: Int = Color.WHITE): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(radius.toInt()).toFloat()
                setColor(color)
            }

        private fun circle(color: Int): GradientDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

        private fun backgroundForTheme(): GradientDrawable = when (theme) {
            BubbleTheme.IOS_LIKE -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(247, 30, 31, 39), Color.argb(236, 9, 10, 15), Color.argb(244, 20, 21, 28)),
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.argb(48, 255, 255, 255))
            }
            BubbleTheme.MODERN_LIKE -> GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.argb(246, 20, 26, 35), Color.argb(240, 10, 17, 27)),
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.argb(60, 190, 225, 255))
            }
            BubbleTheme.BASIC_LIKE -> GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.argb(245, 20, 20, 23), Color.argb(245, 13, 13, 15)),
            ).apply {
                cornerRadius = dp(21).toFloat()
                setStroke(dp(1), Color.argb(30, 255, 255, 255))
            }
        }
    }

    private class LineIconDrawable(
        private val type: Int,
        private val color: Int,
    ) : android.graphics.drawable.Drawable() {
        companion object {
            const val PREV = 1
            const val PLAY = 2
            const val PAUSE = 3
            const val NEXT = 4
            const val STOP = 5
            const val MENU = 6
        }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.0f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        override fun draw(canvas: Canvas) {
            paint.color = color
            val b = bounds
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            val p = Path()
            when (type) {
                PREV -> {
                    paint.style = Paint.Style.FILL
                    p.moveTo(cx - w * .23f, cy)
                    p.lineTo(cx + w * .06f, cy - h * .25f)
                    p.lineTo(cx + w * .06f, cy + h * .25f)
                    p.close()
                    canvas.drawPath(p, paint)
                    canvas.drawRoundRect(
                        cx + w * .10f, cy - h * .25f,
                        cx + w * .17f, cy + h * .25f,
                        w * .035f, w * .035f, paint,
                    )
                    paint.style = Paint.Style.STROKE
                }
                NEXT -> {
                    paint.style = Paint.Style.FILL
                    p.moveTo(cx + w * .23f, cy)
                    p.lineTo(cx - w * .06f, cy - h * .25f)
                    p.lineTo(cx - w * .06f, cy + h * .25f)
                    p.close()
                    canvas.drawPath(p, paint)
                    canvas.drawRoundRect(
                        cx - w * .17f, cy - h * .25f,
                        cx - w * .10f, cy + h * .25f,
                        w * .035f, w * .035f, paint,
                    )
                    paint.style = Paint.Style.STROKE
                }
                STOP -> {
                    paint.style = Paint.Style.FILL
                    canvas.drawRoundRect(
                        cx - w * .19f, cy - h * .19f,
                        cx + w * .19f, cy + h * .19f,
                        w * .07f, w * .07f, paint,
                    )
                    paint.style = Paint.Style.STROKE
                }
                PLAY -> {
                    p.moveTo(cx - w * .16f, cy - h * .28f)
                    p.lineTo(cx + w * .25f, cy)
                    p.lineTo(cx - w * .16f, cy + h * .28f)
                    p.close()
                    canvas.drawPath(p, paint)
                }
                PAUSE -> {
                    canvas.drawLine(cx - w * .13f, cy - h * .25f, cx - w * .13f, cy + h * .25f, paint)
                    canvas.drawLine(cx + w * .13f, cy - h * .25f, cx + w * .13f, cy + h * .25f, paint)
                }
                MENU -> {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy - h * .22f, 1.5f, paint)
                    canvas.drawCircle(cx, cy, 1.5f, paint)
                    canvas.drawCircle(cx, cy + h * .22f, 1.5f, paint)
                    paint.style = Paint.Style.STROKE
                }
            }
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
    }
}
