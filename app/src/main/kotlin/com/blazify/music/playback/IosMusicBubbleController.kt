package com.blazify.music.playback

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
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
import kotlin.math.sin
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
            player?.let { p ->
                view?.updateProgressOnly(p)
                view?.updateWaveform(p)
            }
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
        view?.let {
            it.releaseWaveform()
            runCatching { wm.removeView(it) }
        }
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
        private val waveformBars = mutableListOf<View>()
        private val waveformHandler = Handler(Looper.getMainLooper())

        // HTML waveform transplanted to native Android:
        // 20 independent liquid bars, each with its own max height, speed and
        // phase offset. The motion is continuous rather than random/jittery.
        private var waveformPhase = 0.0
        private var bassLevel = 0f
        private var waveformVisualizer: Visualizer? = null
        private var visualizerSessionId = -1

        private val iosMax = floatArrayOf(
            15f, 27f, 40f, 23f, 34f, 47f, 31f, 44f, 25f, 52f,
            37f, 46f, 29f, 42f, 33f, 49f, 26f, 39f, 18f, 43f
        )
        private val iosSpeed = floatArrayOf(
            1.42f, 1.18f, 1.31f, 1.09f, 1.27f, 1.16f, 1.37f, 1.13f, 1.32f, 1.08f,
            1.24f, 1.17f, 1.34f, 1.11f, 1.29f, 1.15f, 1.36f, 1.21f, 1.40f, 1.14f
        )
        private val iosDelay = floatArrayOf(
            -.30f, -.50f, -.70f, -.40f, -.80f, -1.00f, -.60f, -.90f, -.45f, -1.10f,
            -.70f, -.95f, -.55f, -.85f, -.65f, -1.05f, -.48f, -.78f, -.35f, -.92f
        )

        private val modernMax = floatArrayOf(
            13f, 26f, 43f, 22f, 36f, 51f, 29f, 45f, 24f, 53f,
            38f, 48f, 27f, 44f, 34f, 50f, 25f, 41f, 19f, 46f
        )
        private val modernSpeed = floatArrayOf(
            .82f, .67f, .78f, .71f, .86f, .69f, .81f, .74f, .89f, .65f,
            .77f, .72f, .84f, .68f, .80f, .70f, .87f, .73f, .91f, .76f
        )
        private val modernDelay = floatArrayOf(
            -.12f, -.23f, -.35f, -.16f, -.43f, -.54f, -.29f, -.47f, -.20f, -.59f,
            -.38f, -.52f, -.26f, -.61f, -.34f, -.57f, -.18f, -.49f, -.11f, -.66f
        )

        private val basicMax = floatArrayOf(
            14f, 24f, 35f, 21f, 31f, 40f, 27f, 34f, 22f, 38f,
            30f, 37f, 25f, 33f, 28f, 39f, 23f, 35f, 17f, 36f
        )
        private val basicSpeed = floatArrayOf(
            1.05f, 1.16f, .94f, 1.22f, 1.08f, .98f, 1.15f, 1.03f, 1.19f, .91f,
            1.10f, .99f, 1.18f, 1.04f, 1.13f, .96f, 1.21f, 1.01f, 1.25f, 1.07f
        )
        private val basicDelay = floatArrayOf(
            -.12f, -.25f, -.38f, -.16f, -.44f, -.57f, -.31f, -.49f, -.21f, -.63f,
            -.36f, -.54f, -.27f, -.61f, -.41f, -.58f, -.19f, -.47f, -.10f, -.67f
        )

        private val waveformFrame = object : Runnable {
            override fun run() {
                val p = lastPlayer
                if (p?.isPlaying == true && waveformBars.isNotEmpty()) {
                    // HTML-style continuous liquid motion. Bass only nudges the
                    // amplitude; it never replaces the smooth authored motion.
                    waveformPhase += 0.022 + bassLevel * 0.035

                    val maxArray: FloatArray
                    val speedArray: FloatArray
                    val delayArray: FloatArray
                    when (theme) {
                        BubbleTheme.IOS_LIKE -> {
                            maxArray = iosMax
                            speedArray = iosSpeed
                            delayArray = iosDelay
                        }
                        BubbleTheme.MODERN_LIKE -> {
                            maxArray = modernMax
                            speedArray = modernSpeed
                            delayArray = modernDelay
                        }
                        BubbleTheme.BASIC_LIKE -> {
                            maxArray = basicMax
                            speedArray = basicSpeed
                            delayArray = basicDelay
                        }
                    }

                    waveformBars.forEachIndexed { index, bar ->
                        val local = waveformPhase * speedArray[index] + delayArray[index]
                        val liquid = ((sin(local) + 1.0) * 0.5).toFloat()

                        // The HTML keyframes are intentionally soft rather than
                        // full-range random jumps. Keep bass as a subtle lift.
                        val bassLift = bassLevel * 0.16f
                        val target = (
                            0.24f +
                                liquid * 0.72f +
                                bassLift * (0.25f + liquid * 0.75f)
                            ).coerceIn(0.20f, 1.0f)

                        val current = bar.scaleY
                        val smooth = current + (target - current) * 0.18f
                        bar.scaleY = smooth

                        // Match the HTML opacity curve: quiet bars stay visible,
                        // peaks become brighter without flashing.
                        bar.alpha = (0.30f + smooth * 0.62f).coerceIn(0.32f, 0.98f)
                    }

                    waveformHandler.postDelayed(this, 32L)
                } else {
                    waveformHandler.removeCallbacks(this)
                    fadeWaveform(false)
                }
            }
        }
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
            // 20 bars exactly like the HTML reference. The compact native
            // bubble uses 1.5dp bars + a tiny gap so the whole liquid wave
            // stays visible beside the artwork/title without crowding controls.
            repeat(20) { i ->
                val bar = View(context).apply {
                    background = rounded(2f, accent)
                    alpha = .34f
                    scaleY = .24f
                    pivotY = dp(15).toFloat()
                }
                waveformBars += bar
                waveform.addView(bar, LayoutParams(dp(2), dp(30)).apply {
                    leftMargin = 0
                })
            }
            waveform.alpha = 0f
            waveform.visibility = View.INVISIBLE
            addView(waveform, LayoutParams(dp(40), dp(30)).apply {
                leftMargin = dp(1)
                rightMargin = dp(1)
            })

            configureButton(previous, LineIconDrawable.PREV)
            configureButton(stop, LineIconDrawable.STOP)
            configureButton(next, LineIconDrawable.NEXT)
            addView(previous, LayoutParams(dp(28), dp(34)).apply { leftMargin = dp(1) })
            addView(stop, LayoutParams(dp(28), dp(34)).apply { leftMargin = dp(1) })
            addView(next, LayoutParams(dp(28), dp(34)).apply { leftMargin = dp(1) })

            previous.setOnClickListener {
                animateAction(previous)
                lastPlayer?.seekToPreviousMediaItem()
                refreshControlState()
            }
            stop.setOnClickListener {
                animateAction(stop)
                lastPlayer?.let { p ->
                    if (p.isPlaying) {
                        p.stop()
                    } else {
                        resumePlayback(p)
                    }
                }
                refreshControlState()
            }
            next.setOnClickListener {
                animateAction(next)
                lastPlayer?.seekToNextMediaItem()
                refreshControlState()
            }
            refreshControlState()
        }

        private fun animateAction(view: View) {
            view.animate().cancel()
            view.animate()
                .scaleX(0.82f).scaleY(0.82f).alpha(0.62f)
                .setDuration(65L)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    view.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(170L)
                        .setInterpolator(android.view.animation.PathInterpolator(.16f, 1f, .3f, 1f))
                        .start()
                }
                .start()
        }

        private fun resumePlayback(p: Player) {
            runCatching {
                // stop() leaves Media3 in STATE_IDLE. Calling play() alone can
                // leave some ExoPlayer versions parked in idle after a Stop.
                // Prepare explicitly, then play, so the bubble never breaks
                // the main playback path.
                if (p.playbackState == Player.STATE_IDLE) p.prepare()
                p.play()
            }.onFailure {
                Timber.tag("IosMusicBubble").e(it, "resume playback failed")
            }
        }

        private fun fadeWaveform(show: Boolean) {
            val targetAlpha = if (show) 1f else 0f
            waveform.animate().cancel()
            waveform.animate()
                .alpha(targetAlpha)
                .setDuration(if (show) 180L else 220L)
                .setInterpolator(PathInterpolator(.16f, 1f, .3f, 1f))
                .withEndAction {
                    if (!show) waveform.visibility = View.INVISIBLE
                }
                .start()
            if (show) waveform.visibility = View.VISIBLE
        }

        private fun refreshControlState() {
            val p = lastPlayer ?: return
            val iconType = if (p.isPlaying) LineIconDrawable.STOP else LineIconDrawable.PLAY
            val old = stop.drawable
            stop.animate().cancel()
            stop.animate()
                .alpha(0.35f)
                .scaleX(0.86f).scaleY(0.86f)
                .setDuration(70L)
                .withEndAction {
                    stop.setImageDrawable(LineIconDrawable(iconType, Color.WHITE))
                    stop.animate()
                        .alpha(1f)
                        .scaleX(1f).scaleY(1f)
                        .setDuration(180L)
                        .setInterpolator(android.view.animation.PathInterpolator(.16f, 1f, .3f, 1f))
                        .start()
                }
                .start()
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
                    MotionEvent.ACTION_DOWN -> {
                    v.animate().cancel()
                    v.animate().scaleX(.82f).scaleY(.82f).alpha(.62f)
                        .setDuration(55L)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().cancel()
                    v.animate().scaleX(1f).scaleY(1f).alpha(1f)
                        .setDuration(150L)
                        .setInterpolator(android.view.animation.PathInterpolator(.16f, 1f, .3f, 1f))
                        .start()
                }
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

        fun updateWaveform(player: Player) {
            if (!player.isPlaying) {
                bassLevel = 0f
                waveformVisualizer?.let { runCatching { it.enabled = false; it.release() } }
                waveformVisualizer = null
                visualizerSessionId = -1
                waveformHandler.removeCallbacks(waveformFrame)
                waveformBars.forEach { bar ->
                    bar.animate().cancel()
                    bar.animate()
                        .scaleY(0.28f).alpha(0.38f)
                        .setDuration(180L)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
                return
            }

            fadeWaveform(true)

            val sessionId = player.audioSessionId
            if (sessionId > 0 && sessionId != visualizerSessionId) {
                waveformVisualizer?.release()
                waveformVisualizer = null
                visualizerSessionId = -1
                waveformVisualizer = runCatching {
                    Visualizer(sessionId).apply {
                        captureSize = Visualizer.getCaptureSizeRange().first().coerceAtMost(128)
                        setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(
                                visualizer: Visualizer,
                                waveform: ByteArray,
                                samplingRate: Int,
                            ) = Unit

                            override fun onFftDataCapture(
                                visualizer: Visualizer,
                                fft: ByteArray,
                                samplingRate: Int,
                            ) {
                                var lowEnergy = 0.0
                                var totalEnergy = 0.0
                                var bins = 0
                                var i = 2
                                while (i + 1 < fft.size) {
                                    val re = fft[i].toInt()
                                    val im = fft[i + 1].toInt()
                                    val magnitude = kotlin.math.sqrt((re * re + im * im).toDouble())
                                    totalEnergy += magnitude
                                    if (bins < 5) lowEnergy += magnitude
                                    bins++
                                    i += 2
                                }
                                val ratio = if (totalEnergy > 0.0) lowEnergy / totalEnergy else 0.0
                                val normalized = (ratio * 5.0).coerceIn(0.0, 1.0).toFloat()
                                bassLevel = bassLevel * 0.72f + normalized * 0.28f
                            }
                        }, 16000, false, true)
                        enabled = true
                    }.also { visualizerSessionId = sessionId }
                }.getOrNull()
            }
            if (!waveformHandler.hasCallbacks(waveformFrame)) {
                waveformHandler.post(waveformFrame)
            }
        }

        fun releaseWaveform() {
            waveformHandler.removeCallbacks(waveformFrame)
            waveformVisualizer?.let { runCatching { it.enabled = false; it.release() } }
            waveformVisualizer = null
            visualizerSessionId = -1
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
            updateWaveform(player)
            refreshControlState()
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
