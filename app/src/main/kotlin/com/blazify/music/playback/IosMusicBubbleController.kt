package com.blazify.music.playback

import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import androidx.media3.common.Player
import com.blazify.music.R
import com.blazify.music.constants.BubbleTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import timber.log.Timber

/**
 * Native floating Now Playing bubble.
 *
 * The window is owned by MusicService, while this class only owns the visual layer
 * and forwards transport controls to the real Media3 Player.
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
    private var expanded = false
    private var animator: ValueAnimator? = null
    private var snapAnimator: ValueAnimator? = null

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

        val v = BubbleView(context)
        view = v

        val p = WindowManager.LayoutParams(
            v.themeWidth(false),
            v.themeHeight(false),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(14)
        }

        runCatching { wm.addView(v, p) }
            .onSuccess {
                attached = true
                v.update(player)
                handler.removeCallbacks(progressTicker)
                handler.post(progressTicker)
                Timber.tag("IosMusicBubble").i("Floating Now Playing bubble attached theme=%s", theme)
            }
            .onFailure { error ->
                view = null
                this.player = null
                attached = false
                Timber.tag("IosMusicBubble").e(error, "Failed to attach floating bubble")
                handler.postDelayed({
                    if (!attached && Settings.canDrawOverlays(context)) attach(player)
                }, 700L)
            }
    }

    fun setTheme(newTheme: BubbleTheme) {
        if (theme == newTheme) return
        theme = newTheme
        val current = player
        if (!attached || current == null) return

        // Rebuild the visual tree inside the same overlay window. The window position
        // is preserved, so changing theme does not make the bubble jump.
        val old = view ?: return
        animator?.cancel()
        snapAnimator?.cancel()
        val lp = old.layoutParams as WindowManager.LayoutParams
        val replacement = BubbleView(context)
        replacement.alpha = 0f
        replacement.scaleX = 0.96f
        replacement.scaleY = 0.96f
        view = replacement
        runCatching {
            wm.addView(replacement, lp)
            replacement.update(current)
            replacement.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220L)
                .start()
            old.animate().alpha(0f).setDuration(100L).withEndAction {
                runCatching { wm.removeView(old) }
            }.start()
        }.onFailure {
            view = old
        }
    }

    fun update(player: Player) {
        this.player = player
        if (attached) view?.update(player)
    }

    fun detach() {
        animator?.cancel()
        snapAnimator?.cancel()
        handler.removeCallbacks(progressTicker)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        player = null
        attached = false
        expanded = false
    }

    private fun toggleExpanded() {
        expanded = !expanded
        val v = view ?: return
        val screenW = context.resources.displayMetrics.widthPixels
        val targetW = v.themeWidth(expanded).coerceAtMost(screenW - dp(20))
        val targetH = v.themeHeight(expanded)
        val lp = v.layoutParams as WindowManager.LayoutParams
        val startW = lp.width
        val startH = lp.height

        animator?.cancel()
        snapAnimator?.cancel()
        v.animate()
            .scaleX(0.985f)
            .scaleY(0.985f)
            .setDuration(70L)
            .start()

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (expanded) 400L else 300L
            interpolator = android.view.animation.PathInterpolator(.16f, 1f, .3f, 1f)
            addUpdateListener { a ->
                val f = a.animatedFraction
                lp.width = (startW + (targetW - startW) * f).roundToInt()
                lp.height = (startH + (targetH - startH) * f).roundToInt()
                val contentF = if (expanded) f else 1f - f
                v.applyExpanded(expanded, contentF)
                runCatching { wm.updateViewLayout(v, lp) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    v.scaleX = 1f
                    v.scaleY = 1f
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    v.scaleX = 1f
                    v.scaleY = 1f
                }
            })
            start()
        }
    }

    private fun dp(v: Int) = (v * density).roundToInt()

    private inner class BubbleView(context: Context) : FrameLayout(context) {
        private val compact = LinearLayout(context)
        private val expandedPanel = LinearLayout(context)
        private val cover = ImageView(context)
        private val expandedCover = ImageView(context)
        private val title = TextView(context)
        private val artist = TextView(context)
        private val progressTrack = FrameLayout(context)
        private val progress = View(context)
        private val previous = ImageView(context)
        private val play = ImageView(context)
        private val next = ImageView(context)
        private val waveform = LinearLayout(context)

        private var downX = 0f
        private var downY = 0f
        private var startWindowX = 0
        private var startWindowY = 0
        private var moved = false
        private var lastArtwork: Uri? = null
        private var lastPlayer: Player? = null

        private val accent = when (theme) {
            BubbleTheme.IOS_LIKE -> Color.WHITE
            BubbleTheme.MODERN_LIKE -> Color.rgb(190, 225, 255)
            BubbleTheme.BASIC_LIKE -> Color.WHITE
        }

        init {
            clipChildren = true
            clipToPadding = true
            background = backgroundForTheme()
            elevation = dp(18).toFloat()
            setupCompact()
            setupExpanded()
            addView(compact, LayoutParams(-1, -1))
            addView(expandedPanel, LayoutParams(-1, -1))
            expandedPanel.isVisible = false
            isClickable = true
        }

        fun themeWidth(expanded: Boolean): Int = when (theme) {
            BubbleTheme.IOS_LIKE -> dp(if (expanded) 390 else 154)
            BubbleTheme.MODERN_LIKE -> dp(if (expanded) 400 else 180)
            BubbleTheme.BASIC_LIKE -> dp(if (expanded) 360 else 166)
        }

        fun themeHeight(expanded: Boolean): Int = when (theme) {
            BubbleTheme.IOS_LIKE -> dp(if (expanded) 166 else 44)
            BubbleTheme.MODERN_LIKE -> dp(if (expanded) 174 else 48)
            BubbleTheme.BASIC_LIKE -> dp(if (expanded) 154 else 44)
        }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            // Give transport controls first refusal in expanded mode.
            if (expanded && event.actionMasked == MotionEvent.ACTION_DOWN &&
                event.y >= height - dp(62)
            ) return false
            return true
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    moved = false
                    val lp = layoutParams as WindowManager.LayoutParams
                    startWindowX = lp.x
                    startWindowY = lp.y
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) moveWindow(dx, dy)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) snap() else toggleExpanded()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (moved) snap()
                    return true
                }
            }
            return true
        }

        private fun setupCompact() {
            compact.orientation = LinearLayout.HORIZONTAL
            compact.gravity = Gravity.CENTER_VERTICAL
            compact.setPadding(dp(8), 0, dp(8), 0)

            val artSize = when (theme) {
                BubbleTheme.IOS_LIKE -> 28
                BubbleTheme.MODERN_LIKE -> 30
                BubbleTheme.BASIC_LIKE -> 26
            }
            cover.layoutParams = LinearLayout.LayoutParams(dp(artSize), dp(artSize))
            cover.background = rounded(if (theme == BubbleTheme.IOS_LIKE) 9f else 7f)
            cover.scaleType = ImageView.ScaleType.CENTER_CROP
            compact.addView(cover)

            val info = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val compactTitle = TextView(context).apply {
                textSize = if (theme == BubbleTheme.BASIC_LIKE) 10f else 10.5f
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val compactArtist = TextView(context).apply {
                textSize = 8.5f
                setTextColor(Color.argb(145, 255, 255, 255))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            info.addView(compactTitle, LinearLayout.LayoutParams(0, dp(16), 1f))
            info.addView(compactArtist, LinearLayout.LayoutParams(0, dp(13), 1f))
            compact.addView(info, LinearLayout.LayoutParams(0, -1, 1f).apply {
                leftMargin = dp(8)
                rightMargin = dp(7)
            })

            waveform.gravity = Gravity.CENTER_VERTICAL
            waveform.orientation = LinearLayout.HORIZONTAL
            val count = when (theme) {
                BubbleTheme.IOS_LIKE -> 6
                BubbleTheme.MODERN_LIKE -> 8
                BubbleTheme.BASIC_LIKE -> 4
            }
            repeat(count) { i ->
                val bar = View(context).apply {
                    background = rounded(2f, accent)
                    alpha = .62f
                }
                waveform.addView(bar, LinearLayout.LayoutParams(dp(if (theme == BubbleTheme.MODERN_LIKE) 2 else 2), dp(6 + (i % 4) * 3)).apply {
                    leftMargin = if (i == 0) 0 else dp(2)
                })
                ValueAnimator.ofFloat(.38f, 1f, .45f).apply {
                    duration = when (theme) {
                        BubbleTheme.IOS_LIKE -> 560L + i * 55L
                        BubbleTheme.MODERN_LIKE -> 430L + i * 35L
                        BubbleTheme.BASIC_LIKE -> 700L + i * 70L
                    }
                    startDelay = i * 65L
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = android.view.animation.PathInterpolator(.22f, .8f, .25f, 1f)
                    addUpdateListener { a ->
                        val f = a.animatedValue as Float
                        bar.scaleY = f
                        bar.alpha = .48f + f * .52f
                    }
                    start()
                }
            }
            compact.addView(waveform, LinearLayout.LayoutParams(
                if (theme == BubbleTheme.BASIC_LIKE) dp(22) else dp(39),
                dp(30),
            ))
            compactTitle.tag = "compactTitle"
            compactArtist.tag = "compactArtist"
        }

        private fun setupExpanded() {
            expandedPanel.orientation = LinearLayout.VERTICAL
            expandedPanel.setPadding(dp(14), dp(12), dp(14), dp(7))

            val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            val coverSize = if (theme == BubbleTheme.BASIC_LIKE) 48 else 54
            expandedCover.layoutParams = LinearLayout.LayoutParams(dp(coverSize), dp(coverSize))
            expandedCover.background = rounded(if (theme == BubbleTheme.IOS_LIKE) 16f else 12f)
            expandedCover.scaleType = ImageView.ScaleType.CENTER_CROP
            header.addView(expandedCover)

            val info = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            title.textSize = if (theme == BubbleTheme.BASIC_LIKE) 13f else 14f
            title.setTextColor(Color.WHITE)
            title.maxLines = 1
            title.ellipsize = android.text.TextUtils.TruncateAt.END
            artist.textSize = 11f
            artist.setTextColor(Color.argb(145, 255, 255, 255))
            artist.maxLines = 1
            artist.ellipsize = android.text.TextUtils.TruncateAt.END
            info.addView(title, LinearLayout.LayoutParams(0, dp(23), 1f))
            info.addView(artist, LinearLayout.LayoutParams(0, dp(18), 1f))
            header.addView(info, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                leftMargin = dp(12)
            })

            val menu = ImageView(context).apply {
                setImageDrawable(LineIconDrawable(LineIconDrawable.MENU, Color.WHITE))
                background = circle(Color.argb(18, 255, 255, 255))
                setPadding(dp(9), dp(9), dp(9), dp(9))
            }
            header.addView(menu, LinearLayout.LayoutParams(dp(34), dp(34)))
            expandedPanel.addView(header, LinearLayout.LayoutParams(-1, dp(54)))

            progressTrack.background = rounded(99f, Color.argb(40, 255, 255, 255))
            progress.background = rounded(99f, accent)
            progressTrack.addView(progress, FrameLayout.LayoutParams(0, dp(3), Gravity.START or Gravity.CENTER_VERTICAL))
            expandedPanel.addView(progressTrack, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(9) })

            val controls = LinearLayout(context).apply {
                gravity = Gravity.CENTER
                orientation = LinearLayout.HORIZONTAL
            }
            configureTransport(previous, LineIconDrawable.PREV, false)
            configureTransport(play, LineIconDrawable.PAUSE, true)
            configureTransport(next, LineIconDrawable.NEXT, false)

            controls.addView(previous, LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(18) })
            controls.addView(play, LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(18) })
            controls.addView(next, LinearLayout.LayoutParams(dp(42), dp(42)))
            expandedPanel.addView(controls, LinearLayout.LayoutParams(-1, 0, 1f))

            previous.setOnClickListener { lastPlayer?.seekToPreviousMediaItem() }
            next.setOnClickListener { lastPlayer?.seekToNextMediaItem() }
            play.setOnClickListener {
                lastPlayer?.let { p -> if (p.isPlaying) p.pause() else p.play() }
            }
        }

        private fun configureTransport(icon: ImageView, type: Int, main: Boolean) {
            icon.background = if (main) circle(Color.WHITE) else circle(Color.argb(24, 255, 255, 255))
            icon.setPadding(dp(if (main) 12 else 10), dp(if (main) 12 else 10), dp(if (main) 12 else 10), dp(if (main) 12 else 10))
            icon.setImageDrawable(LineIconDrawable(type, if (main) Color.rgb(18, 18, 20) else Color.WHITE))
            icon.isClickable = true
            icon.isFocusable = false
            icon.setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(.88f).scaleY(.88f).setDuration(70L).start()
                    }
                    MotionEvent.ACTION_UP -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(170L)
                            .setInterpolator(android.view.animation.PathInterpolator(.2f, .9f, .2f, 1f)).start()
                    }
                    MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                }
                false
            }
        }

        fun update(player: Player) {
            lastPlayer = player
            val t = player.mediaMetadata.title?.toString().orEmpty().ifBlank { "Strix Music" }
            val a = player.mediaMetadata.artist?.toString().orEmpty().ifBlank { "Now Playing" }
            title.text = t
            artist.text = a
            (compact.findViewWithTag<TextView>("compactTitle"))?.text = t
            (compact.findViewWithTag<TextView>("compactArtist"))?.text = a

            val artwork = player.mediaMetadata.artworkUri
            if (artwork != null && artwork != lastArtwork) {
                lastArtwork = artwork
                SingletonImageLoader.get(context).enqueue(
                    ImageRequest.Builder(context).data(artwork).target { image ->
                        val d = image.asDrawable(context.resources)
                        cover.setImageDrawable(d)
                        expandedCover.setImageDrawable(d)
                    }.build()
                )
            }

            play.setImageDrawable(
                LineIconDrawable(
                    if (player.isPlaying) LineIconDrawable.PAUSE else LineIconDrawable.PLAY,
                    if (theme == BubbleTheme.IOS_LIKE || theme == BubbleTheme.MODERN_LIKE) Color.rgb(18,18,20) else Color.WHITE,
                )
            )
            updateProgressOnly(player)
        }

        fun updateProgressOnly(player: Player) {
            val duration = player.duration
            val fraction = if (duration > 0) {
                (player.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
            } else 0f
            val trackWidth = progressTrack.width.takeIf { it > 0 } ?: themeWidth(true) - dp(28)
            progress.layoutParams = (progress.layoutParams as FrameLayout.LayoutParams).apply {
                width = (trackWidth * fraction).roundToInt()
            }
            progress.requestLayout()
        }

        fun applyExpanded(isExpanded: Boolean, fraction: Float) {
            compact.alpha = if (isExpanded) 1f - fraction else fraction
            expandedPanel.alpha = if (isExpanded) fraction else 1f - fraction
            expandedPanel.isVisible = isExpanded || fraction < .99f
        }

        private fun moveWindow(dx: Float, dy: Float) {
            val lp = layoutParams as WindowManager.LayoutParams
            lp.gravity = Gravity.TOP or Gravity.START
            val sw = context.resources.displayMetrics.widthPixels
            val sh = context.resources.displayMetrics.heightPixels
            lp.x = (startWindowX + dx).roundToInt().coerceIn(dp(6), sw - width - dp(6))
            lp.y = (startWindowY + dy).roundToInt().coerceIn(dp(6), sh - height - dp(6))
            runCatching { wm.updateViewLayout(this, lp) }
        }

        private fun snap() {
            val lp = layoutParams as WindowManager.LayoutParams
            val sw = context.resources.displayMetrics.widthPixels
            val targetX = if (lp.x + width / 2 < sw / 2) dp(8) else sw - width - dp(8)
            val start = lp.x
            snapAnimator?.cancel()
            snapAnimator = ValueAnimator.ofInt(start, targetX).apply {
                duration = when (theme) {
                    BubbleTheme.IOS_LIKE -> 300L
                    BubbleTheme.MODERN_LIKE -> 240L
                    BubbleTheme.BASIC_LIKE -> 190L
                }
                interpolator = android.view.animation.PathInterpolator(.22f, 1f, .36f, 1f)
                addUpdateListener { a ->
                    lp.x = a.animatedValue as Int
                    runCatching { wm.updateViewLayout(this@BubbleView, lp) }
                }
                start()
            }
        }

        private fun rounded(radius: Float, color: Int = Color.WHITE) =
            GradientDrawable().apply {
                cornerRadius = dp(radius.toInt()).toFloat()
                setColor(color)
            }

        private fun circle(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

        private fun backgroundForTheme() = when (theme) {
            BubbleTheme.IOS_LIKE -> GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(247, 30, 31, 39), Color.argb(236, 9, 10, 15), Color.argb(244, 20, 21, 28)),
            ).apply {
                cornerRadius = dp(30).toFloat()
                setStroke(dp(1), Color.argb(48,255,255,255))
            }
            BubbleTheme.MODERN_LIKE -> GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.argb(246, 20, 26, 35), Color.argb(240, 10, 17, 27)),
            ).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.argb(60,190,225,255))
            }
            BubbleTheme.BASIC_LIKE -> GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.argb(245, 20,20,23), Color.argb(245, 13,13,15)),
            ).apply {
                cornerRadius = dp(21).toFloat()
                setStroke(dp(1), Color.argb(30,255,255,255))
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
            const val MENU = 5
        }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.4f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val cx = b.exactCenterX()
            val cy = b.exactCenterY()
            paint.color = color
            fill.color = color
            val w = b.width().toFloat()
            val h = b.height().toFloat()
            when (type) {
                PLAY -> {
                    val path = Path().apply {
                        moveTo(cx - w*.20f, cy - h*.25f)
                        lineTo(cx + w*.24f, cy)
                        lineTo(cx - w*.20f, cy + h*.25f)
                        close()
                    }
                    canvas.drawPath(path, fill)
                }
                PAUSE -> {
                    canvas.drawRoundRect(cx-w*.20f, cy-h*.24f, cx-w*.03f, cy+h*.24f, 2f,2f,fill)
                    canvas.drawRoundRect(cx+w*.03f, cy-h*.24f, cx+w*.20f, cy+h*.24f, 2f,2f,fill)
                }
                PREV, NEXT -> {
                    val dir = if (type == PREV) -1f else 1f
                    val x1 = cx + dir*w*.18f
                    val x2 = cx - dir*w*.22f
                    val x3 = cx + dir*w*.22f
                    val path = Path().apply {
                        moveTo(x1, cy-h*.27f)
                        lineTo(x2, cy)
                        lineTo(x1, cy+h*.27f)
                        close()
                    }
                    canvas.drawPath(path, fill)
                    val lineX = cx + dir*w*.31f
                    canvas.drawLine(lineX, cy-h*.25f, lineX, cy+h*.25f, paint)
                }
                MENU -> {
                    canvas.drawCircle(cx-w*.18f, cy, 1.8f, fill)
                    canvas.drawCircle(cx, cy, 1.8f, fill)
                    canvas.drawCircle(cx+w*.18f, cy, 1.8f, fill)
                }
            }
        }

        override fun setAlpha(alpha: Int) { paint.alpha = alpha; fill.alpha = alpha }
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter; fill.colorFilter = colorFilter }
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
}
