package com.blazify.music.playback

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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

/** Lightweight native iOS-style music bubble. It lives in the media playback service,
 * so it survives the Activity being removed from recents while playback continues. */
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

    fun attach(player: Player) {
        if (attached || !Settings.canDrawOverlays(context)) return
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
        }
    }

    fun detach() {
        animator?.cancel()
        animator = null
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        attached = false
    }

    fun update(player: Player) {
        if (!attached) return
        view?.update(player)
    }

    private fun toggleExpanded() {
        expanded = !expanded
        val v = view ?: return
        val targetW = if (expanded) widthExpanded.coerceAtMost(context.resources.displayMetrics.widthPixels - dp(24)) else widthCompact
        val targetH = if (expanded) heightExpanded else heightCompact
        val lp = v.layoutParams as WindowManager.LayoutParams
        val startW = lp.width
        val startH = lp.height
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260L
            interpolator = android.view.animation.PathInterpolator(0.22f, 1f, 0.36f, 1f)
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

    private fun dp(v: Int): Int = (v * density).roundToInt()

    private inner class BubbleView(context: Context) : FrameLayout(context) {
        private val compact = LinearLayout(context)
        private val expandedPanel = LinearLayout(context)
        private val cover = ImageView(context)
        private val expandedCover = ImageView(context)
        private val title = TextView(context)
        private val artist = TextView(context)
        private val progress = View(context)
        private val progressTrack = View(context)
        private val play = ImageButton(context)
        private val previous = ImageButton(context)
        private val next = ImageButton(context)
        private var downX = 0f
        private var downY = 0f
        private var startWindowX = 0
        private var startWindowY = 0
        private var moved = false
        private var lastPlayer: Player? = null
        private var lastArtwork: Uri? = null

        init {
            clipChildren = true
            background = glassBackground(30)
            elevation = dp(16).toFloat()
            setupCompact()
            setupExpanded()
            addView(compact, LayoutParams(-1, -1))
            addView(expandedPanel, LayoutParams(-1, -1))
            expandedPanel.isVisible = false
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY; moved = false
                        val lp = layoutParams as WindowManager.LayoutParams
                        startWindowX = lp.x
                        startWindowY = lp.y
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                        if (moved) moveWindow(dx, dy)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) {
                            val p = event.x
                            val local = event.y
                            if (expanded && local > dp(108)) {
                                // Controls consume their own click listeners.
                            } else {
                                toggleExpanded()
                            }
                        } else snap()
                        true
                    }
                    else -> true
                }
            }
        }

        private fun setupCompact() {
            compact.gravity = Gravity.CENTER
            compact.orientation = LinearLayout.HORIZONTAL
            compact.setPadding(dp(8), 0, dp(8), 0)
            cover.layoutParams = LinearLayout.LayoutParams(dp(23), dp(23))
            cover.background = rounded(8f)
            cover.scaleType = ImageView.ScaleType.CENTER_CROP
            compact.addView(cover)
            val dot = View(context).apply { background = circle(Color.argb(150, 255, 255, 255)) }
            compact.addView(dot, LinearLayout.LayoutParams(dp(4), dp(4)).apply { leftMargin = dp(7); rightMargin = dp(7) })
            val wave = LinearLayout(context).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
            repeat(3) { i ->
                val bar = View(context).apply { background = rounded(3f) }
                wave.addView(bar, LinearLayout.LayoutParams(dp(2), dp(5 + i * 3)).apply { leftMargin = dp(2) })
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
            val info = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
            info.addView(title, LinearLayout.LayoutParams(0, dp(22), 1f))
            title.textSize = 14f; title.setTextColor(Color.WHITE); title.maxLines = 1
            info.addView(artist, LinearLayout.LayoutParams(0, dp(18), 1f))
            artist.textSize = 11f; artist.setTextColor(Color.argb(125,255,255,255)); artist.maxLines = 1
            header.addView(info, LinearLayout.LayoutParams(0, dp(54), 1f).apply { leftMargin = dp(12) })
            expandedPanel.addView(header, LinearLayout.LayoutParams(-1, dp(54)))

            val track = FrameLayout(context)
            track.background = rounded(99f, Color.argb(42,255,255,255))
            progress.background = rounded(99f, Color.WHITE)
            track.addView(progress, FrameLayout.LayoutParams(0, dp(3), Gravity.START or Gravity.CENTER_VERTICAL))
            expandedPanel.addView(track, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(10) })

            val controls = LinearLayout(context).apply { gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL }
            previous.setImageResource(android.R.drawable.ic_media_previous)
            play.setImageResource(android.R.drawable.ic_media_pause)
            next.setImageResource(android.R.drawable.ic_media_next)
            styleButton(previous, false); styleButton(play, true); styleButton(next, false)
            controls.addView(previous, LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(20) })
            controls.addView(play, LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(20) })
            controls.addView(next, LinearLayout.LayoutParams(dp(42), dp(42)))
            expandedPanel.addView(controls, LinearLayout.LayoutParams(-1, 0, 1f))

            previous.setOnClickListener { lastPlayer?.seekToPreviousMediaItem() }
            next.setOnClickListener { lastPlayer?.seekToNextMediaItem() }
            play.setOnClickListener { lastPlayer?.let { if (it.isPlaying) it.pause() else it.play() } }
        }

        private fun styleButton(b: ImageButton, main: Boolean) {
            b.background = if (main) circle(Color.WHITE) else circle(Color.argb(24,255,255,255))
            b.setColorFilter(if (main) Color.rgb(20,20,22) else Color.WHITE)
            b.scaleType = ImageView.ScaleType.CENTER
            b.setPadding(dp(10), dp(10), dp(10), dp(10))
            b.isClickable = true
        }

        fun update(player: Player) {
            lastPlayer = player
            val md = player.mediaMetadata
            title.text = md.title?.toString().orEmpty().ifBlank { "Strix Music" }
            artist.text = md.artist?.toString().orEmpty().ifBlank { "Now Playing" }
            val art = md.artworkUri
            if (art != null && art != lastArtwork) {
                lastArtwork = art
                val request = ImageRequest.Builder(context).data(art).target { image ->
                    val drawable = image.asDrawable(context.resources)
                    cover.setImageDrawable(drawable)
                    expandedCover.setImageDrawable(drawable)
                }.build()
                SingletonImageLoader.get(context).enqueue(request)
            }
            play.setImageResource(if (player.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            val duration = player.duration
            val pos = player.currentPosition
            val fraction = if (duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else 0f
            progress.layoutParams = (progress.layoutParams as FrameLayout.LayoutParams).apply { width = ((widthExpanded - dp(28)) * fraction).roundToInt() }
            progress.requestLayout()
        }

        fun applyExpanded(isExpanded: Boolean, f: Float) {
            compact.alpha = if (isExpanded) 1f - f else f
            expandedPanel.alpha = if (isExpanded) f else 1f - f
            expandedPanel.isVisible = isExpanded || f < 0.99f
        }

        private fun moveWindow(dx: Float, dy: Float) {
            val lp = layoutParams as WindowManager.LayoutParams
            lp.gravity = Gravity.TOP or Gravity.START
            val screenW = context.resources.displayMetrics.widthPixels
            lp.x = (startWindowX + dx).roundToInt().coerceIn(dp(8), screenW - width - dp(8))
            lp.y = (startWindowY + dy).roundToInt().coerceIn(dp(8), context.resources.displayMetrics.heightPixels - height - dp(8))
            runCatching { wm.updateViewLayout(this, lp) }
        }

        private fun snap() {
            val lp = layoutParams as WindowManager.LayoutParams
            val screenW = context.resources.displayMetrics.widthPixels
            val center = lp.x + width / 2
            lp.x = if (center < screenW / 2) dp(8) else screenW - width - dp(8)
            runCatching { wm.updateViewLayout(this, lp) }
        }

        private fun rounded(radius: Float, color: Int = Color.argb(255, 255, 255, 255)) = GradientDrawable().apply { cornerRadius = dp(radius.toInt()).toFloat(); setColor(color) }
        private fun circle(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        private fun glassBackground(radius: Int) = GradientDrawable().apply { cornerRadius = dp(radius).toFloat(); setColor(Color.argb(238, 16, 17, 22)); setStroke(dp(1), Color.argb(35,255,255,255)) }
    }
}
