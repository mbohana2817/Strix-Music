package com.blazify.music.playback

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import androidx.media3.common.Player
import com.blazify.music.R
import kotlin.math.abs
import kotlin.math.roundToInt
import timber.log.Timber

/** Native iOS-style Now Playing bubble. The bubble is hosted by MusicService,
 * so it can remain visible while the Activity is removed from recents. */
class IosMusicBubbleController(private val service: MusicService) {
    private val context: Context = service
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density
    private val widthCompact = dp(154)
    private val heightCompact = dp(42)
    private val widthExpanded = dp(390)
    private val heightExpanded = dp(164)
    private var view: BubbleView? = null
    private var expanded = false
    private var attached = false
    private var animator: ValueAnimator? = null
    private var player: Player? = null

    private val handler = Handler(Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            if (!attached) return
            player?.let { view?.updateProgressOnly(it) }
            handler.postDelayed(this, 1000L)
        }
    }

    fun attach(player: Player) {
        if (attached || !Settings.canDrawOverlays(context)) return
        this.player = player

        val v = BubbleView(context)
        view = v

        val params = WindowManager.LayoutParams(
            widthCompact,
            heightCompact,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(16)
        }

        runCatching { wm.addView(v, params) }.onSuccess {
            attached = true
            v.update(player)
            handler.removeCallbacks(progressTicker)
            handler.postDelayed(progressTicker, 1000L)
            Timber.tag("IosMusicBubble").i("Floating Now Playing bubble attached")
        }.onFailure { error ->
            view = null
            this.player = null
            attached = false
            Timber.tag("IosMusicBubble").e(error, "Failed to attach floating Now Playing bubble")
            // A permission/settings transition can race the first addView(). Retry
            // shortly while the service is still alive, without a permanent loop.
            handler.postDelayed({
                if (!attached && Settings.canDrawOverlays(context) && this.player == null) {
                    attach(player)
                }
            }, 700L)
        }
    }

    fun detach() {
        animator?.cancel()
        animator = null
        handler.removeCallbacks(progressTicker)
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        player = null
        attached = false
        expanded = false
    }

    fun update(player: Player) {
        this.player = player
        if (attached) view?.update(player)
    }

    private fun toggleExpanded() {
        expanded = !expanded
        val v = view ?: return
        val screenWidth = context.resources.displayMetrics.widthPixels
        val targetW = if (expanded) widthExpanded.coerceAtMost(screenWidth - dp(24)) else widthCompact
        val targetH = if (expanded) heightExpanded else heightCompact
        val lp = v.layoutParams as WindowManager.LayoutParams
        val startW = lp.width
        val startH = lp.height

        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360L
            interpolator = android.view.animation.PathInterpolator(.22f, 1f, .36f, 1f)
            addUpdateListener { a ->
                val f = a.animatedFraction
                lp.width = (startW + (targetW - startW) * f).roundToInt()
                lp.height = (startH + (targetH - startH) * f).roundToInt()
                v.applyExpanded(expanded, f)
                runCatching { wm.updateViewLayout(v, lp) }
            }
            start()
        }
    }

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private inner class BubbleView(context: Context) : FrameLayout(context) {
        private val compact = LinearLayout(context)
        private val expandedPanel = LinearLayout(context)
        private val cover = ImageView(context)
        private val expandedCover = ImageView(context)
        private val title = TextView(context)
        private val artist = TextView(context)
        private val progressTrack = FrameLayout(context)
        private val progress = View(context)
        private val previous = ImageButton(context)
        private val play = ImageButton(context)
        private val next = ImageButton(context)

        private var downX = 0f
        private var downY = 0f
        private var startWindowX = 0
        private var startWindowY = 0
        private var moved = false
        private var lastArtwork: Uri? = null
        private var lastPlayer: Player? = null

        init {
            clipChildren = true
            clipToPadding = true
            background = glassBackground(30)
            elevation = dp(18).toFloat()

            setupCompact()
            setupExpanded()
            addView(compact, LayoutParams(-1, -1))
            addView(expandedPanel, LayoutParams(-1, -1))
            expandedPanel.isVisible = false
            isClickable = true
        }

        /** Let the three transport buttons receive their own clicks. Everything else
         * is handled by the bubble for tap-to-expand and drag. */
        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            if (expanded && event.actionMasked == MotionEvent.ACTION_DOWN && isTransportArea(event.x, event.y)) {
                return false
            }
            return true
        }

        private fun isTransportArea(x: Float, y: Float): Boolean {
            return y >= height - dp(58) && x >= dp(45) && x <= width - dp(45)
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
            compact.gravity = Gravity.CENTER
            compact.orientation = LinearLayout.HORIZONTAL
            compact.setPadding(dp(8), 0, dp(8), 0)

            cover.layoutParams = LinearLayout.LayoutParams(dp(23), dp(23))
            cover.background = rounded(8f)
            cover.scaleType = ImageView.ScaleType.CENTER_CROP
            compact.addView(cover)

            val dot = View(context).apply {
                background = circle(Color.argb(145, 255, 255, 255))
            }
            compact.addView(dot, LinearLayout.LayoutParams(dp(4), dp(4)).apply {
                leftMargin = dp(7)
                rightMargin = dp(7)
            })

            val wave = LinearLayout(context).apply {
                gravity = Gravity.CENTER
                orientation = LinearLayout.HORIZONTAL
            }
            repeat(3) { i ->
                val bar = View(context).apply {
                    background = rounded(3f, Color.argb(220, 255, 255, 255))
                }
                wave.addView(bar, LinearLayout.LayoutParams(dp(2), dp(5 + i * 3)).apply {
                    leftMargin = dp(2)
                })
            }
            compact.addView(wave, LinearLayout.LayoutParams(dp(18), dp(18)))
        }

        private fun setupExpanded() {
            expandedPanel.orientation = LinearLayout.VERTICAL
            expandedPanel.setPadding(dp(14), dp(12), dp(14), dp(8))

            val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            expandedCover.layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
            expandedCover.background = rounded(16f)
            expandedCover.scaleType = ImageView.ScaleType.CENTER_CROP
            header.addView(expandedCover)

            val info = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
            }
            title.textSize = 14f
            title.setTextColor(Color.WHITE)
            title.maxLines = 1
            title.ellipsize = android.text.TextUtils.TruncateAt.END
            artist.textSize = 11f
            artist.setTextColor(Color.argb(125, 255, 255, 255))
            artist.maxLines = 1
            artist.ellipsize = android.text.TextUtils.TruncateAt.END
            info.addView(title, LinearLayout.LayoutParams(0, dp(22), 1f))
            info.addView(artist, LinearLayout.LayoutParams(0, dp(18), 1f))
            header.addView(info, LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                leftMargin = dp(12)
            })

            val more = TextView(context).apply {
                text = "•••"
                textSize = 13f
                setTextColor(Color.argb(190, 255, 255, 255))
                gravity = Gravity.CENTER
                background = circle(Color.argb(18, 255, 255, 255))
            }
            header.addView(more, LinearLayout.LayoutParams(dp(34), dp(34)))
            expandedPanel.addView(header, LinearLayout.LayoutParams(-1, dp(54)))

            progressTrack.background = rounded(99f, Color.argb(34, 255, 255, 255))
            progress.background = rounded(99f, Color.argb(245, 255, 255, 255))
            progressTrack.addView(progress, FrameLayout.LayoutParams(0, dp(3), Gravity.START or Gravity.CENTER_VERTICAL))
            expandedPanel.addView(progressTrack, LinearLayout.LayoutParams(-1, dp(8)).apply {
                topMargin = dp(10)
            })

            val controls = LinearLayout(context).apply {
                gravity = Gravity.CENTER
                orientation = LinearLayout.HORIZONTAL
            }
            previous.setImageResource(android.R.drawable.ic_media_previous)
            play.setImageResource(android.R.drawable.ic_media_pause)
            next.setImageResource(android.R.drawable.ic_media_next)
            styleButton(previous, false)
            styleButton(play, true)
            styleButton(next, false)

            controls.addView(previous, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                rightMargin = dp(20)
            })
            controls.addView(play, LinearLayout.LayoutParams(dp(46), dp(46)).apply {
                rightMargin = dp(20)
            })
            controls.addView(next, LinearLayout.LayoutParams(dp(42), dp(42)))
            expandedPanel.addView(controls, LinearLayout.LayoutParams(-1, 0, 1f))

            previous.setOnClickListener { lastPlayer?.seekToPreviousMediaItem() }
            next.setOnClickListener { lastPlayer?.seekToNextMediaItem() }
            play.setOnClickListener {
                lastPlayer?.let { p -> if (p.isPlaying) p.pause() else p.play() }
            }
        }

        private fun styleButton(button: ImageButton, main: Boolean) {
            button.background = if (main) {
                circle(Color.argb(245, 255, 255, 255))
            } else {
                circle(Color.argb(18, 255, 255, 255))
            }
            button.setColorFilter(if (main) Color.rgb(18, 18, 20) else Color.WHITE)
            button.scaleType = ImageView.ScaleType.CENTER
            button.setPadding(dp(10), dp(10), dp(10), dp(10))
            button.isClickable = true
        }

        fun update(player: Player) {
            lastPlayer = player
            title.text = player.mediaMetadata.title?.toString().orEmpty().ifBlank { "Strix Music" }
            artist.text = player.mediaMetadata.artist?.toString().orEmpty().ifBlank { "Now Playing" }

            val artwork = player.mediaMetadata.artworkUri
            if (artwork != null && artwork != lastArtwork) {
                lastArtwork = artwork
                val request = ImageRequest.Builder(context)
                    .data(artwork)
                    .target { image ->
                        val drawable = image.asDrawable(context.resources)
                        cover.setImageDrawable(drawable)
                        expandedCover.setImageDrawable(drawable)
                    }
                    .build()
                SingletonImageLoader.get(context).enqueue(request)
            }

            play.setImageResource(
                if (player.isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
            )
            updateProgressOnly(player)
        }

        fun updateProgressOnly(player: Player) {
            val duration = player.duration
            val position = player.currentPosition
            val fraction = if (duration > 0) {
                (position.toFloat() / duration).coerceIn(0f, 1f)
            } else 0f
            val trackWidth = progressTrack.width.takeIf { it > 0 } ?: (widthExpanded - dp(28))
            progress.layoutParams = (progress.layoutParams as FrameLayout.LayoutParams).apply {
                width = (trackWidth * fraction).roundToInt()
            }
            progress.requestLayout()
        }

        fun applyExpanded(isExpanded: Boolean, fraction: Float) {
            compact.alpha = if (isExpanded) 1f - fraction else fraction
            expandedPanel.alpha = if (isExpanded) fraction else 1f - fraction
            expandedPanel.isVisible = isExpanded || fraction < 0.99f
        }

        private fun moveWindow(dx: Float, dy: Float) {
            val lp = layoutParams as WindowManager.LayoutParams
            lp.gravity = Gravity.TOP or Gravity.START
            val screenW = context.resources.displayMetrics.widthPixels
            val screenH = context.resources.displayMetrics.heightPixels
            lp.x = (startWindowX + dx).roundToInt().coerceIn(dp(8), screenW - width - dp(8))
            lp.y = (startWindowY + dy).roundToInt().coerceIn(dp(8), screenH - height - dp(8))
            runCatching { wm.updateViewLayout(this, lp) }
        }

        private fun snap() {
            val lp = layoutParams as WindowManager.LayoutParams
            val screenW = context.resources.displayMetrics.widthPixels
            val center = lp.x + width / 2
            lp.x = if (center < screenW / 2) dp(8) else screenW - width - dp(8)
            runCatching { wm.updateViewLayout(this, lp) }
        }

        private fun rounded(radius: Float, color: Int = Color.argb(255, 255, 255, 255)) =
            GradientDrawable().apply {
                cornerRadius = dp(radius.toInt()).toFloat()
                setColor(color)
            }

        private fun circle(color: Int) = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

        private fun glassBackground(radius: Int) = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(245, 29, 30, 37),
                Color.argb(235, 10, 11, 15),
                Color.argb(242, 18, 19, 25),
            ),
        ).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.argb(36, 255, 255, 255))
        }
    }
}
