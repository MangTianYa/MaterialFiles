/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import java8.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.METADATA_KEY_SAMPLERATE
import me.zhanghai.android.files.compat.use
import me.zhanghai.android.files.databinding.AudioPlayerFragmentBinding
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.fileProviderUri
import me.zhanghai.android.files.file.guessFromPath
import me.zhanghai.android.files.file.isAppPrivatePath
import me.zhanghai.android.files.filelist.name
import me.zhanghai.android.files.provider.linux.isLinuxPath
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableListParceler
import me.zhanghai.android.files.util.ParcelableState
import me.zhanghai.android.files.util.asFileNameOrNull
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.createViewIntent
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.extraPathList
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.getColorByAttr
import me.zhanghai.android.files.util.getState
import me.zhanghai.android.files.util.isTrustedInternalIntent
import me.zhanghai.android.files.util.putState
import me.zhanghai.android.files.util.setDataSource
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.withChooser
import java.util.Locale

class AudioPlayerFragment : Fragment() {
    private val args by args<Args>()
    private val argsPaths by lazy { args.intent.extraPathList }

    private lateinit var paths: List<Path>
    private var currentPosition = 0
    private var repeatSingle = false
    private var pendingSeekMs = 0
    private var pendingAutoPlay = true

    private lateinit var binding: AudioPlayerFragmentBinding

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private var isUserSeeking = false
    private var pausedByTransientFocusLoss = false
    private var progressJob: Job? = null
    private var metadataJob: Job? = null

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isNoisyReceiverRegistered = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByTransientFocusLoss = false
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pausedByTransientFocusLoss = mediaPlayer?.isPlaying == true
                pausePlayback(abandonFocus = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(0.25f, 0.25f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1f, 1f)
                if (pausedByTransientFocusLoss && isPrepared) {
                    pausedByTransientFocusLoss = false
                    mediaPlayer?.let { player ->
                        startNoisyReceiverIfNeeded()
                        player.start()
                        updatePlayPauseIcon(isPlaying = true)
                        startProgressUpdater()
                    }
                }
            }
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pausedByTransientFocusLoss = false
                pausePlayback()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val allowOpenCache = args.intent.isTrustedInternalIntent
        val restoredState = savedInstanceState?.getState<State>()
        paths = (restoredState?.paths ?: argsPaths)
            .filter { !it.isAppPrivatePath(allowOpenCache = allowOpenCache) }
        currentPosition = restoredState?.position?.coerceIn(0, (paths.size - 1).coerceAtLeast(0))
            ?: args.position.coerceIn(0, (paths.size - 1).coerceAtLeast(0))
        repeatSingle = restoredState?.repeatSingle ?: false
        pendingSeekMs = restoredState?.playbackPositionMs ?: 0
        pendingAutoPlay = restoredState?.shouldAutoPlay ?: true

        setHasOptionsMenu(true)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        AudioPlayerFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (paths.isEmpty()) {
            finish()
            return
        }

        val activity = requireActivity() as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)

        audioManager = activity.getSystemService()
        setupControls()
        updateRepeatUi()
        loadTrack(currentPosition, autoPlay = pendingAutoPlay, initialSeekMs = pendingSeekMs)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (this::paths.isInitialized) {
            val currentPosMs = if (isPrepared) {
                runCatching { mediaPlayer?.currentPosition ?: 0 }.getOrDefault(pendingSeekMs)
            } else {
                pendingSeekMs
            }
            val wasPlaying = if (isPrepared) {
                runCatching { mediaPlayer?.isPlaying == true }.getOrDefault(pendingAutoPlay)
            } else {
                pendingAutoPlay
            }
            outState.putState(
                State(paths, currentPosition, repeatSingle, currentPosMs, wasPlaying)
            )
        }
    }

    override fun onDestroyView() {
        binding.coverImage.setImageDrawable(null)
        releasePlayback()
        super.onDestroyView()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.audio_player, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.action_open_with -> {
                openCurrentWithExternalApp()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun setupControls() {
        binding.titleText.isSelected = true
        binding.playPauseButton.setOnClickListener { togglePlayPause() }
        binding.previousButton.setOnClickListener {
            if (paths.isNotEmpty()) {
                val prev = (currentPosition - 1 + paths.size) % paths.size
                loadTrack(prev, autoPlay = true)
            }
        }
        binding.nextButton.setOnClickListener {
            if (paths.isNotEmpty()) {
                val next = (currentPosition + 1) % paths.size
                loadTrack(next, autoPlay = true)
            }
        }
        binding.repeatButton.setOnClickListener {
            repeatSingle = !repeatSingle
            updateRepeatUi()
            showToast(
                if (repeatSingle) R.string.audio_player_repeat_one else R.string.audio_player_repeat_all
            )
        }
        binding.playlistButton.setOnClickListener { showPlaylistDialog() }

        val hasMultiple = paths.size > 1
        binding.previousButton.alpha = if (hasMultiple) 1f else 0.45f
        binding.nextButton.alpha = if (hasMultiple) 1f else 0.45f

        binding.progressSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                isUserSeeking = false
                if (isPrepared) {
                    mediaPlayer?.seekTo(slider.value.toInt())
                }
            }
        })
        binding.progressSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                binding.currentTimeText.text = formatDurationMs(value.toLong())
            }
        }
    }

    private fun updateRepeatUi() {
        binding.repeatButton.setImageResource(
            if (repeatSingle) {
                R.drawable.repeat_one_icon_white_24dp
            } else {
                R.drawable.repeat_icon_white_24dp
            }
        )
        val tintAttr = if (repeatSingle) {
            androidx.appcompat.R.attr.colorPrimary
        } else {
            com.google.android.material.R.attr.colorOnSurfaceVariant
        }
        binding.repeatButton.imageTintList =
            android.content.res.ColorStateList.valueOf(requireContext().getColorByAttr(tintAttr))
    }

    private fun loadTrack(index: Int, autoPlay: Boolean, initialSeekMs: Int = 0) {
        if (paths.isEmpty()) {
            finish()
            return
        }
        currentPosition = index.coerceIn(0, paths.lastIndex)
        val path = paths[currentPosition]

        releasePlayerOnly()
        isPrepared = false
        pausedByTransientFocusLoss = false
        pendingAutoPlay = autoPlay
        pendingSeekMs = initialSeekMs
        binding.progressSlider.value = 0f
        binding.progressSlider.valueFrom = 0f
        binding.progressSlider.valueTo = 1f
        binding.currentTimeText.text = formatDurationMs(0L)
        binding.durationTimeText.text = formatDurationMs(0L)
        updatePlayPauseIcon(isPlaying = false)

        binding.titleText.text = path.name
        binding.artistText.setText(R.string.audio_player_unknown_artist)
        binding.coverImage.isVisible = false
        binding.coverImage.setImageDrawable(null)
        binding.placeholderIcon.isVisible = true
        updateFormatChip(path, bitrateBps = null, sampleRateHz = null)

        val activity = requireActivity()
        activity.title = getString(R.string.audio_player_title)
        (activity as? AppCompatActivity)?.supportActionBar?.subtitle =
            if (paths.size > 1) "${currentPosition + 1}/${paths.size}" else null

        loadMetadataAsync(path)

        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            setOnPreparedListener { mp ->
                if (!isAdded || view == null || mp !== mediaPlayer) {
                    return@setOnPreparedListener
                }
                isPrepared = true
                val rawDurationMs = mp.duration.coerceAtLeast(0)
                val durationMs = rawDurationMs.coerceAtLeast(1)
                val clampedSeek = initialSeekMs.coerceIn(0, rawDurationMs)
                if (clampedSeek > 0) {
                    runCatching { mp.seekTo(clampedSeek) }
                }
                binding.progressSlider.valueFrom = 0f
                binding.progressSlider.valueTo = durationMs.toFloat()
                binding.progressSlider.value = clampedSeek.toFloat().coerceIn(0f, durationMs.toFloat())
                binding.currentTimeText.text = formatDurationMs(clampedSeek.toLong())
                binding.durationTimeText.text = formatDurationMs(rawDurationMs.toLong())
                if (autoPlay && requestAudioFocus()) {
                    startNoisyReceiverIfNeeded()
                    mp.start()
                    updatePlayPauseIcon(isPlaying = true)
                    startProgressUpdater()
                }
            }
            setOnCompletionListener { mp ->
                if (!isAdded || view == null || mp !== mediaPlayer) {
                    return@setOnCompletionListener
                }
                if (repeatSingle) {
                    mp.seekTo(0)
                    mp.start()
                    updatePlayPauseIcon(isPlaying = true)
                } else if (paths.size > 1) {
                    val next = (currentPosition + 1) % paths.size
                    loadTrack(next, autoPlay = true)
                } else {
                    mp.seekTo(0)
                    binding.progressSlider.value = 0f
                    binding.currentTimeText.text = formatDurationMs(0L)
                    updatePlayPauseIcon(isPlaying = false)
                    pendingAutoPlay = false
                    progressJob?.cancel()
                    abandonAudioFocus()
                }
            }
            setOnErrorListener { mp, _, _ ->
                if (mp === mediaPlayer) {
                    releasePlayerOnly()
                    abandonAudioFocus()
                }
                if (isAdded) {
                    showToast(R.string.audio_player_error_load)
                    updatePlayPauseIcon(isPlaying = false)
                }
                true
            }
        }
        mediaPlayer = player

        try {
            if (path.isLinuxPath && path.toFile().canRead()) {
                player.setDataSource(path.toFile().path)
            } else {
                player.setDataSource(requireContext(), path.fileProviderUri)
            }
            player.prepareAsync()
        } catch (e: Exception) {
            e.printStackTrace()
            releasePlayerOnly()
            showToast(R.string.audio_player_error_load)
        }
    }

    private fun loadMetadataAsync(path: Path) {
        metadataJob?.cancel()
        val maxCoverPx = (256 * resources.displayMetrics.density).toInt().coerceAtLeast(256)
        metadataJob = viewLifecycleOwner.lifecycleScope.launch {
            val metadata = withContext(Dispatchers.IO) {
                extractMetadata(path, maxCoverPx)
            }
            if (!isAdded || view == null || paths.getOrNull(currentPosition) != path) {
                return@launch
            }
            if (!metadata.title.isNullOrBlank()) {
                binding.titleText.text = metadata.title
            }
            val artistAlbum = listOfNotNull(
                metadata.artist?.takeIf { it.isNotBlank() },
                metadata.album?.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            binding.artistText.text = artistAlbum.ifEmpty {
                getString(R.string.audio_player_unknown_artist)
            }
            if (metadata.artwork != null) {
                binding.coverImage.setImageBitmap(metadata.artwork)
                binding.coverImage.isVisible = true
                binding.placeholderIcon.isVisible = false
            }
            updateFormatChip(path, metadata.bitrateBps, metadata.sampleRateHz)
        }
    }

    private fun updateFormatChip(path: Path, bitrateBps: Int?, sampleRateHz: Int?) {
        val ext = path.name.asFileNameOrNull()?.singleExtension?.uppercase(Locale.ROOT).orEmpty()
        val parts = buildList {
            if (ext.isNotEmpty()) add(ext)
            if (sampleRateHz != null && sampleRateHz > 0) {
                val khz = sampleRateHz / 1000.0
                val text = if (sampleRateHz % 1000 == 0) {
                    "${sampleRateHz / 1000} kHz"
                } else {
                    String.format(Locale.ROOT, "%.1f kHz", khz)
                }
                add(text)
            }
            if (bitrateBps != null && bitrateBps > 0) {
                add("${bitrateBps / 1000} kbps")
            }
        }
        if (parts.isNotEmpty()) {
            binding.formatChipText.text = parts.joinToString(" · ")
            binding.formatChipText.isVisible = true
        } else {
            binding.formatChipText.isVisible = false
        }
    }

    private fun extractMetadata(path: Path, maxCoverPx: Int): AudioMetadata =
        try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(path)
                val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull()
                val sampleRate = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    retriever.extractMetadata(MediaMetadataRetriever::class.METADATA_KEY_SAMPLERATE)
                        ?.toIntOrNull()
                } else {
                    null
                }
                val artwork = retriever.embeddedPicture?.let {
                    decodeSampledArtwork(it, maxCoverPx)
                }
                AudioMetadata(title, artist, album, bitrate, sampleRate, artwork)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AudioMetadata(null, null, null, null, null, null)
        }

    private fun decodeSampledArtwork(bytes: ByteArray, maxSizePx: Int): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            return null
        }
        var sampleSize = 1
        while (boundsOptions.outWidth / sampleSize > maxSizePx * 2 ||
            boundsOptions.outHeight / sampleSize > maxSizePx * 2
        ) {
            sampleSize *= 2
        }
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }

    private fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (!isPrepared) {
            return
        }
        if (player.isPlaying) {
            pausedByTransientFocusLoss = false
            pausePlayback()
        } else if (requestAudioFocus()) {
            pausedByTransientFocusLoss = false
            pendingAutoPlay = true
            startNoisyReceiverIfNeeded()
            player.setVolume(1f, 1f)
            player.start()
            updatePlayPauseIcon(isPlaying = true)
            startProgressUpdater()
        }
    }

    private fun pausePlayback(abandonFocus: Boolean = true) {
        val player = mediaPlayer ?: return
        if (isPrepared && player.isPlaying) {
            player.pause()
        }
        pendingAutoPlay = false
        updatePlayPauseIcon(isPlaying = false)
        progressJob?.cancel()
        if (abandonFocus) {
            abandonAudioFocus()
        }
    }

    private fun updatePlayPauseIcon(isPlaying: Boolean) {
        binding.playPauseButton.setImageResource(
            if (isPlaying) {
                R.drawable.pause_icon_white_24dp
            } else {
                R.drawable.play_arrow_icon_white_24dp
            }
        )
    }

    private fun startProgressUpdater() {
        progressJob?.cancel()
        progressJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && isPrepared && !isUserSeeking) {
                    val posMs = player.currentPosition.coerceAtLeast(0)
                    val maxVal = binding.progressSlider.valueTo
                    binding.progressSlider.value = posMs.toFloat().coerceIn(0f, maxVal)
                    binding.currentTimeText.text = formatDurationMs(posMs.toLong())
                }
                delay(250L)
            }
        }
    }

    private fun showPlaylistDialog() {
        val names = paths.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.audio_player_playlist)
            .setSingleChoiceItems(names, currentPosition) { dialog, which ->
                dialog.dismiss()
                if (which != currentPosition) {
                    loadTrack(which, autoPlay = true)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openCurrentWithExternalApp() {
        val path = paths.getOrNull(currentPosition) ?: return
        pausePlayback()
        val mimeType = MimeType.guessFromPath(path.toString())
        val intent = path.fileProviderUri.createViewIntent(mimeType)
            .apply { extraPath = path }
            .withChooser()
        startActivitySafe(intent)
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .build()
                .also { audioFocusRequest = it }
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
    }

    private fun startNoisyReceiverIfNeeded() {
        if (!isNoisyReceiverRegistered) {
            ContextCompat.registerReceiver(
                requireContext(),
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_EXPORTED
            )
            isNoisyReceiverRegistered = true
        }
    }

    private fun releasePlayerOnly() {
        progressJob?.cancel()
        metadataJob?.cancel()
        mediaPlayer?.let { player ->
            runCatching {
                if (isPrepared && player.isPlaying) {
                    player.stop()
                }
            }
            runCatching { player.release() }
        }
        mediaPlayer = null
        isPrepared = false
    }

    private fun releasePlayback() {
        releasePlayerOnly()
        abandonAudioFocus()
        if (isNoisyReceiverRegistered) {
            runCatching { requireContext().unregisterReceiver(becomingNoisyReceiver) }
            isNoisyReceiverRegistered = false
        }
    }

    private fun formatDurationMs(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }

    private data class AudioMetadata(
        val title: String?,
        val artist: String?,
        val album: String?,
        val bitrateBps: Int?,
        val sampleRateHz: Int?,
        val artwork: Bitmap?
    )

    @Parcelize
    class Args(val intent: Intent, val position: Int) : ParcelableArgs

    @Parcelize
    private class State(
        val paths: @WriteWith<ParcelableListParceler> List<Path>,
        val position: Int,
        val repeatSingle: Boolean,
        val playbackPositionMs: Int = 0,
        val shouldAutoPlay: Boolean = true
    ) : ParcelableState
}
