package de.henosch.bibelvers

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.MediaPlayer
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.view.Gravity
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import de.henosch.bibelvers.databinding.ActivityStreamBinding
import kotlin.math.max
import kotlin.math.roundToInt

class StreamActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStreamBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStreamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url = intent.getStringExtra(EXTRA_STREAM_URL)
        val label = intent.getStringExtra(EXTRA_STREAM_LABEL) ?: getString(R.string.notification_title)

        if (url.isNullOrBlank()) {
            Toast.makeText(this, R.string.stream_picker_network_error, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = label

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.videoView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        binding.videoView.setOnPreparedListener { mp ->
            binding.videoView.post {
                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                adjustVideoSize(mp)
                binding.streamProgress.isVisible = false
                mp.isLooping = true
                binding.videoView.start()
            }
        }

        binding.videoView.setOnErrorListener { _, _, _ ->
            binding.streamProgress.isVisible = false
            Toast.makeText(this, R.string.stream_picker_network_error, Toast.LENGTH_LONG).show()
            true
        }

        binding.streamProgress.isVisible = true
        binding.videoView.setVideoURI(url.toUri())
        binding.videoView.requestFocus()
    }

    override fun onStop() {
        super.onStop()
        binding.videoView.stopPlayback()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val EXTRA_STREAM_URL = "extra_stream_url"
        private const val EXTRA_STREAM_LABEL = "extra_stream_label"

        fun start(context: Context, stream: KotelStream) {
            val intent = Intent(context, StreamActivity::class.java).apply {
                putExtra(EXTRA_STREAM_URL, stream.url)
                putExtra(EXTRA_STREAM_LABEL, stream.label)
            }
            context.startActivity(intent)
        }
    }

    private fun adjustVideoSize(mp: MediaPlayer) {
        val videoWidth = mp.videoWidth
        val videoHeight = mp.videoHeight
        if (videoWidth == 0 || videoHeight == 0) return

        val parent = binding.videoView.parent as? android.view.View ?: return
        val parentWidth = parent.width
        val parentHeight = parent.height
        if (parentWidth == 0 || parentHeight == 0) return

        val widthScale = parentWidth.toFloat() / videoWidth
        val heightScale = parentHeight.toFloat() / videoHeight
        val scale = max(widthScale, heightScale)

        val params = binding.videoView.layoutParams as android.widget.FrameLayout.LayoutParams
        params.width = (videoWidth * scale).roundToInt()
        params.height = (videoHeight * scale).roundToInt()
        params.gravity = Gravity.CENTER
        binding.videoView.layoutParams = params
    }
}
