package com.example.tarsosdsppoc

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.*
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.os.Environment
import android.widget.Spinner
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Switch

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val SAMPLE_RATE = 44100  // Standard audio rate
        private const val BUFFER_SIZE = 2048   // Reduced buffer size for lower latency
        private const val BUFFER_OVERLAP = 1024 // 50% overlap
        private const val PERMISSION_REQUEST_CODE = 123
        private const val STORAGE_PERMISSION_REQUEST_CODE = 124
        private const val MIN_FREQUENCY = 50.0f  // Hz
        private const val MAX_FREQUENCY = 1000.0f // Hz
        private const val DEFAULT_GAIN = 1.0f
        private const val MAX_GAIN = 3.0f
        private const val DEFAULT_REVERB_MIX = 0.4f
        private const val DEFAULT_REVERB_DECAY = 0.7f
        private val DELAY_OPTIONS = arrayOf(29, 37, 43, 47, 53, 59)
    }

    private lateinit var playButton: Button
    private lateinit var recordButton: Button
    private lateinit var pitchTextView: TextView
    private lateinit var amplitudeTextView: TextView
    private lateinit var gainSeekBar: SeekBar
    private lateinit var reverbMixSeekBar: SeekBar
    private lateinit var reverbDecaySeekBar: SeekBar
    private lateinit var delay1Spinner: Spinner
    private lateinit var delay2Spinner: Spinner
    private lateinit var delay3Spinner: Spinner
    private lateinit var reverbSwitch: Switch
    private lateinit var playerView: PlayerView
    
    private var audioRecord: AudioRecord? = null
    private var player: ExoPlayer? = null
    private var isRecording = false
    private val audioData = FloatArray(BUFFER_SIZE)
    private var currentGain = DEFAULT_GAIN
    private var currentReverbMix = DEFAULT_REVERB_MIX
    private var currentReverbDecay = DEFAULT_REVERB_DECAY
    private var currentDelay1 = DELAY_OPTIONS[0]
    private var currentDelay2 = DELAY_OPTIONS[1]
    private var currentDelay3 = DELAY_OPTIONS[2]
    private var isReverbEnabled = true
    private var recordingFile: File? = null
    private var recordingOutputStream: FileOutputStream? = null
    private var totalBytesWritten = 0
    private var isManualRecording = false
    private var pitchAmplitudeData = mutableListOf<PitchAmplitudeData>()
    
    // Data class to store pitch and amplitude data with timestamps
    data class PitchAmplitudeData(
        val timestamp: Long,
        val pitch: Float,
        val amplitude: Float
    )
    
    // Persistent delay buffers for reverb
    private var delayBuffer1 = FloatArray((DELAY_OPTIONS[0] * SAMPLE_RATE / 1000).toInt())
    private var delayBuffer2 = FloatArray((DELAY_OPTIONS[1] * SAMPLE_RATE / 1000).toInt())
    private var delayBuffer3 = FloatArray((DELAY_OPTIONS[2] * SAMPLE_RATE / 1000).toInt())
    private var delayPos1 = 0
    private var delayPos2 = 0
    private var delayPos3 = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        playButton = findViewById(R.id.playButton)
        recordButton = findViewById(R.id.recordButton)
        pitchTextView = findViewById(R.id.pitchTextView)
        amplitudeTextView = findViewById(R.id.amplitudeTextView)
        gainSeekBar = findViewById(R.id.gainSeekBar)
        reverbMixSeekBar = findViewById(R.id.reverbMixSeekBar)
        reverbDecaySeekBar = findViewById(R.id.reverbDecaySeekBar)
        delay1Spinner = findViewById(R.id.delay1Spinner)
        delay2Spinner = findViewById(R.id.delay2Spinner)
        delay3Spinner = findViewById(R.id.delay3Spinner)
        reverbSwitch = findViewById(R.id.reverbSwitch)
        playerView = findViewById(R.id.playerView)
        
        setupGainControl()
        setupReverbControls()
        setupExoPlayer()
        
        playButton.setOnClickListener {
            if (player?.isPlaying == true) {
                stopPlayback()
            } else {
                playAudio()
            }
        }
        
        recordButton.setOnClickListener {
            if (isManualRecording) {
                stopManualRecording()
            } else {
                startManualRecording()
            }
        }
        
        // Check for all required permissions
        if (hasAllPermissions()) {
            startAudioProcessing()
        } else {
            requestAllPermissions()
        }
    }

    private fun hasAllPermissions(): Boolean {
        return hasAudioPermission() && hasStoragePermission()
    }

    private fun hasStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            true // For Android 13 and above, we don't need storage permissions for app-specific files
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()
        
        if (!hasAudioPermission()) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        
        if (!hasStoragePermission() && android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startAudioProcessing()
            } else {
                Toast.makeText(this, "Required permissions are needed for this feature", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun setupExoPlayer() {
        player = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_ENDED -> {
                            playerView.visibility = android.view.View.GONE
                            playButton.text = getString(R.string.play)
                        }
                    }
                }
            })
        }
        playerView.player = player
    }
    
    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun startAudioProcessing() {
        try {
            Log.d(TAG, "Starting audio processing...")
            
            if (!hasAudioPermission()) {
                Log.e(TAG, "Audio permission not granted")
                requestAllPermissions()
                return
            }
            
            // Create a new file for recording
            recordingFile = File(cacheDir, "recording_${System.currentTimeMillis()}.wav")
            recordingOutputStream = FileOutputStream(recordingFile)
            
            // Write WAV header
            writeWavHeader(recordingOutputStream!!)
            totalBytesWritten = 0
            
            try {
                val minBufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT
                )
                
                if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "Invalid buffer size: $minBufferSize")
                    Toast.makeText(this, "Error: Invalid audio buffer size", Toast.LENGTH_LONG).show()
                    return
                }
                
                Log.d(TAG, "Min buffer size: $minBufferSize")
                
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT,
                    minBufferSize
                )
                
                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord initialization failed")
                    Toast.makeText(this, "Error: Failed to initialize audio recorder", Toast.LENGTH_LONG).show()
                    return
                }
                
                Log.d(TAG, "AudioRecord initialized successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing AudioRecord: ${e.message}", e)
                Toast.makeText(this, "Error initializing audio: ${e.message}", Toast.LENGTH_LONG).show()
                return
            }
            
            startCustomAudioRecording()
            
            playButton.isEnabled = false
            isRecording = true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in startAudioProcessing: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            stopAudioProcessing()
        }
    }
    
    private fun writeWavHeader(outputStream: FileOutputStream) {
        // WAV header format
        val header = ByteArray(44)
        
        // RIFF header
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        
        // File size (to be filled later)
        header[4] = 0
        header[5] = 0
        header[6] = 0
        header[7] = 0
        
        // WAVE format
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        
        // Format chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        
        // Format chunk size (16 bytes)
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        
        // Audio format (IEEE float = 3)
        header[20] = 3
        header[21] = 0
        
        // Number of channels (1 = mono)
        header[22] = 1
        header[23] = 0
        
        // Sample rate
        header[24] = (SAMPLE_RATE and 0xFF).toByte()
        header[25] = (SAMPLE_RATE shr 8 and 0xFF).toByte()
        header[26] = (SAMPLE_RATE shr 16 and 0xFF).toByte()
        header[27] = (SAMPLE_RATE shr 24 and 0xFF).toByte()
        
        // Byte rate (sample rate * channels * bytes per sample)
        val byteRate = SAMPLE_RATE * 1 * 4
        header[28] = (byteRate and 0xFF).toByte()
        header[29] = (byteRate shr 8 and 0xFF).toByte()
        header[30] = (byteRate shr 16 and 0xFF).toByte()
        header[31] = (byteRate shr 24 and 0xFF).toByte()
        
        // Block align (channels * bytes per sample)
        header[32] = 4
        header[33] = 0
        
        // Bits per sample (32 for float)
        header[34] = 32
        header[35] = 0
        
        // Data chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        
        // Data chunk size (to be filled later)
        header[40] = 0
        header[41] = 0
        header[42] = 0
        header[43] = 0
        
        outputStream.write(header)
    }
    
    private fun updateWavHeader() {
        recordingFile?.let { file ->
            try {
                val raf = RandomAccessFile(file, "rw")
                
                // Update file size (total bytes written + 36 for header)
                val fileSize = totalBytesWritten + 36
                raf.seek(4)
                raf.writeByte((fileSize and 0xFF).toInt())
                raf.writeByte((fileSize shr 8 and 0xFF).toInt())
                raf.writeByte((fileSize shr 16 and 0xFF).toInt())
                raf.writeByte((fileSize shr 24 and 0xFF).toInt())
                
                // Update data chunk size
                raf.seek(40)
                raf.writeByte((totalBytesWritten and 0xFF).toInt())
                raf.writeByte((totalBytesWritten shr 8 and 0xFF).toInt())
                raf.writeByte((totalBytesWritten shr 16 and 0xFF).toInt())
                raf.writeByte((totalBytesWritten shr 24 and 0xFF).toInt())
                
                raf.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating WAV header: ${e.message}", e)
            }
        }
    }
    
    private fun setupReverbControls() {
        // Setup reverb switch
        reverbSwitch.isChecked = isReverbEnabled
        reverbSwitch.setOnCheckedChangeListener { _, isChecked ->
            isReverbEnabled = isChecked
            updateReverbControlsState()
        }

        // Setup reverb mix control
        reverbMixSeekBar.max = 100
        reverbMixSeekBar.progress = (DEFAULT_REVERB_MIX * 100).toInt()
        reverbMixSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentReverbMix = progress / 100f
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Setup reverb decay control
        reverbDecaySeekBar.max = 100
        reverbDecaySeekBar.progress = (DEFAULT_REVERB_DECAY * 100).toInt()
        reverbDecaySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentReverbDecay = progress / 100f
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Setup delay spinners
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, DELAY_OPTIONS.map { "$it ms" })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        delay1Spinner.adapter = adapter
        delay2Spinner.adapter = adapter
        delay3Spinner.adapter = adapter

        delay1Spinner.setSelection(0)
        delay2Spinner.setSelection(1)
        delay3Spinner.setSelection(2)

        val delayListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                when (parent?.id) {
                    R.id.delay1Spinner -> {
                        currentDelay1 = DELAY_OPTIONS[position]
                        updateDelayBuffers()
                    }
                    R.id.delay2Spinner -> {
                        currentDelay2 = DELAY_OPTIONS[position]
                        updateDelayBuffers()
                    }
                    R.id.delay3Spinner -> {
                        currentDelay3 = DELAY_OPTIONS[position]
                        updateDelayBuffers()
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        delay1Spinner.onItemSelectedListener = delayListener
        delay2Spinner.onItemSelectedListener = delayListener
        delay3Spinner.onItemSelectedListener = delayListener

        // Initial state update
        updateReverbControlsState()
    }

    private fun updateReverbControlsState() {
        val alpha = if (isReverbEnabled) 1.0f else 0.5f
        reverbMixSeekBar.alpha = alpha
        reverbDecaySeekBar.alpha = alpha
        delay1Spinner.alpha = alpha
        delay2Spinner.alpha = alpha
        delay3Spinner.alpha = alpha
    }

    private fun updateDelayBuffers() {
        // Create new delay buffers with updated sizes
        val newDelayBuffer1 = FloatArray((currentDelay1 * SAMPLE_RATE / 1000).toInt())
        val newDelayBuffer2 = FloatArray((currentDelay2 * SAMPLE_RATE / 1000).toInt())
        val newDelayBuffer3 = FloatArray((currentDelay3 * SAMPLE_RATE / 1000).toInt())

        // Copy existing data if possible
        val minSize1 = minOf(delayBuffer1.size, newDelayBuffer1.size)
        val minSize2 = minOf(delayBuffer2.size, newDelayBuffer2.size)
        val minSize3 = minOf(delayBuffer3.size, newDelayBuffer3.size)

        System.arraycopy(delayBuffer1, 0, newDelayBuffer1, 0, minSize1)
        System.arraycopy(delayBuffer2, 0, newDelayBuffer2, 0, minSize2)
        System.arraycopy(delayBuffer3, 0, newDelayBuffer3, 0, minSize3)

        // Update the buffers
        delayBuffer1 = newDelayBuffer1
        delayBuffer2 = newDelayBuffer2
        delayBuffer3 = newDelayBuffer3

        // Reset positions
        delayPos1 = 0
        delayPos2 = 0
        delayPos3 = 0
    }
    
    private fun applyReverb(input: FloatArray): FloatArray {
        if (!isReverbEnabled) {
            return input
        }

        val output = FloatArray(input.size)
        
        // Apply reverb effect with multiple delay lines
        for (i in input.indices) {
            // Get delayed samples from each delay line
            val delayedSample1 = delayBuffer1[delayPos1]
            val delayedSample2 = delayBuffer2[delayPos2]
            val delayedSample3 = delayBuffer3[delayPos3]
            
            // Mix dry signal with all delay lines
            val wetSignal = (delayedSample1 + delayedSample2 + delayedSample3) / 3f
            
            // Mix dry and wet signals using current mix value
            output[i] = (1 - currentReverbMix) * input[i] + currentReverbMix * wetSignal
            
            // Update delay buffers with current decay value
            delayBuffer1[delayPos1] = input[i] + currentReverbDecay * delayedSample1
            delayBuffer2[delayPos2] = input[i] + currentReverbDecay * delayedSample2
            delayBuffer3[delayPos3] = input[i] + currentReverbDecay * delayedSample3
            
            // Update delay positions
            delayPos1 = (delayPos1 + 1) % delayBuffer1.size
            delayPos2 = (delayPos2 + 1) % delayBuffer2.size
            delayPos3 = (delayPos3 + 1) % delayBuffer3.size
        }
        
        return output
    }
    
    private fun startCustomAudioRecording() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "No permission to record audio")
                Toast.makeText(this, "No permission to record audio", Toast.LENGTH_SHORT).show()
                return
            }
            
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
            
            Log.d(TAG, "Min buffer size: $minBufferSize")
            
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_FLOAT,
                    minBufferSize
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception when creating AudioRecord: ${e.message}", e)
                Toast.makeText(this, "Permission denied for audio recording", Toast.LENGTH_SHORT).show()
                return
            }
            
            isRecording = true
            audioRecord?.startRecording()
            
            Thread {
                val buffer = FloatArray(BUFFER_SIZE)
                val byteBuffer = ByteBuffer.allocate(buffer.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                
                while (isRecording) {
                    val readSize = audioRecord?.read(buffer, 0, BUFFER_SIZE, AudioRecord.READ_BLOCKING) ?: 0
                    
                    if (readSize > 0) {
                        // Apply gain only
                        for (i in 0 until readSize) {
                            buffer[i] = buffer[i].coerceIn(-1f, 1f) * currentGain
                        }
                        
                        // Save to file only if manual recording is active
                        if (isManualRecording) {
                            byteBuffer.clear()
                            for (i in 0 until readSize) {
                                val sample = buffer[i].coerceIn(-1f, 1f)
                                byteBuffer.putFloat(sample)
                            }
                            recordingOutputStream?.write(byteBuffer.array(), 0, readSize * 4)
                            totalBytesWritten += readSize * 4
                        }
                        
                        var maxAmplitude = 0f
                        for (sample in buffer) {
                            val abs = kotlin.math.abs(sample)
                            if (abs > maxAmplitude) {
                                maxAmplitude = abs
                            }
                        }
                        
                        // Increased amplitude threshold to reduce false detections
                        if (maxAmplitude > 0.05f) {  // Changed from 0.01f to 0.05f
                            val pitch = detectPitch(buffer)
                            runOnUiThread {
                                if (pitch > 0 && pitch <= MAX_FREQUENCY) {  // Added upper limit check
                                    pitchTextView.text = String.format("%.1f Hz", pitch)
                                    Log.d(TAG, "Detected pitch: $pitch Hz, Amplitude: $maxAmplitude")
                                    
                                    // Store pitch and amplitude data if manual recording is active
                                    if (isManualRecording) {
                                        pitchAmplitudeData.add(PitchAmplitudeData(
                                            System.currentTimeMillis(),
                                            pitch,
                                            maxAmplitude
                                        ))
                                    }
                                } else {
                                    pitchTextView.text = "No pitch detected"
                                    Log.d(TAG, "No valid pitch detected. Amplitude: $maxAmplitude")
                                }
                                amplitudeTextView.text = getString(R.string.amplitude_format, String.format("%.2f", maxAmplitude))
                            }
                        } else {
                            runOnUiThread {
                                pitchTextView.text = "No pitch detected"
                                amplitudeTextView.text = getString(R.string.amplitude_format, String.format("%.2f", maxAmplitude))
                                Log.d(TAG, "Amplitude too low: $maxAmplitude")
                            }
                        }
                    }
                }
            }.start()
            
            Log.d(TAG, "Custom audio recording started with dry signal")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error starting custom audio recording: ${e.message}", e)
            Toast.makeText(this, "Error starting audio recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun detectPitch(buffer: FloatArray): Float {
        try {
            // Calculate amplitude and log it
            var maxAmplitude = 0f
            for (sample in buffer) {
                val abs = kotlin.math.abs(sample)
                if (abs > maxAmplitude) {
                    maxAmplitude = abs
                }
            }
            
            Log.d(TAG, "Pitch detection - Max amplitude: $maxAmplitude")
            
            if (maxAmplitude < 0.01f) {
                Log.d(TAG, "Pitch detection - Amplitude too low, skipping")
                return -1f
            }
            
            // Apply Hanning window and normalize
            val windowedBuffer = FloatArray(buffer.size)
            var maxWindowedAmplitude = 0f
            for (i in buffer.indices) {
                val windowValue = 0.5f * (1 - cos(2 * PI.toFloat() * i / (buffer.size - 1)))
                windowedBuffer[i] = buffer[i] * windowValue
                maxWindowedAmplitude = maxOf(maxWindowedAmplitude, abs(windowedBuffer[i]))
            }
            
            // Normalize windowed buffer
            for (i in windowedBuffer.indices) {
                windowedBuffer[i] /= maxWindowedAmplitude
            }
            
            Log.d(TAG, "Pitch detection - Applied Hanning window and normalized")
            
            // Calculate autocorrelation
            val correlation = FloatArray(buffer.size / 2)
            for (lag in correlation.indices) {
                var sum = 0f
                for (i in 0 until buffer.size - lag) {
                    sum += windowedBuffer[i] * windowedBuffer[i + lag]
                }
                correlation[lag] = sum
            }
            
            Log.d(TAG, "Pitch detection - Calculated autocorrelation")
            
            // Find peaks in autocorrelation
            var maxCorrelation = Float.NEGATIVE_INFINITY
            var peakLag = -1
            var foundPeak = false
            val minLag = (SAMPLE_RATE / MAX_FREQUENCY).toInt()
            val maxLag = (SAMPLE_RATE / MIN_FREQUENCY).toInt()
            
            Log.d(TAG, "Pitch detection - Searching for peaks between $minLag and $maxLag samples")
            
            // First pass: find the maximum correlation
            for (lag in minLag until maxLag) {
                if (correlation[lag] > maxCorrelation) {
                    maxCorrelation = correlation[lag]
                    peakLag = lag
                    foundPeak = true
                }
            }
            
            if (!foundPeak) {
                Log.d(TAG, "Pitch detection - No peak found")
                return -1f
            }
            
            // Verify the peak is significant
            val threshold = maxCorrelation * 0.8f  // Peak should be at least 80% of max
            var isPeakSignificant = true
            
            // Check if there are any larger peaks at lower lags
            for (lag in minLag until peakLag) {
                if (correlation[lag] > correlation[peakLag]) {
                    isPeakSignificant = false
                    break
                }
            }
            
            if (!isPeakSignificant) {
                Log.d(TAG, "Pitch detection - Peak not significant enough")
                return -1f
            }
            
            val frequency = SAMPLE_RATE.toFloat() / peakLag
            Log.d(TAG, "Pitch detection - Found frequency: $frequency Hz at lag $peakLag (correlation: ${correlation[peakLag]})")
            
            return frequency
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in pitch detection: ${e.message}", e)
            return -1f
        }
    }
    
    private fun stopAudioProcessing() {
        try {
            Log.d(TAG, "Stopping audio processing...")
            
            isRecording = false
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            
            recordingOutputStream?.close()
            recordingOutputStream = null
            
            // Update WAV header with final file size
            updateWavHeader()
            
            playButton.isEnabled = true
            Log.d(TAG, "Audio processing stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio processing: ${e.message}", e)
            Toast.makeText(this, "Error stopping audio processing: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playAudio() {
        recordingFile?.let { file ->
            try {
                Log.d(TAG, "Playing audio file: ${file.absolutePath}")
                
                // Create a temporary file for the processed audio
                val processedFile = File(cacheDir, "processed_${System.currentTimeMillis()}.wav")
                val processedOutputStream = FileOutputStream(processedFile)
                
                // Write WAV header
                writeWavHeader(processedOutputStream)
                
                // Read the original file and apply reverb
                val inputStream = file.inputStream()
                val buffer = FloatArray(BUFFER_SIZE)
                val byteBuffer = ByteBuffer.allocate(buffer.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                
                // Skip WAV header
                inputStream.skip(44)
                
                var totalProcessedBytes = 0
                
                while (true) {
                    val bytesRead = inputStream.read(byteBuffer.array())
                    if (bytesRead <= 0) break
                    
                    // Convert bytes to float array
                    byteBuffer.rewind()
                    for (i in 0 until bytesRead / 4) {
                        buffer[i] = byteBuffer.float
                    }
                    
                    // Apply reverb effect
                    val processedBuffer = applyReverb(buffer.copyOf(bytesRead / 4))
                    
                    // Write processed audio
                    byteBuffer.clear()
                    for (i in 0 until bytesRead / 4) {
                        byteBuffer.putFloat(processedBuffer[i])
                    }
                    processedOutputStream.write(byteBuffer.array(), 0, bytesRead)
                    totalProcessedBytes += bytesRead
                }
                
                // Update WAV header with final file size
                val raf = RandomAccessFile(processedFile, "rw")
                raf.seek(4)
                raf.writeInt(Integer.reverseBytes(totalProcessedBytes + 36))
                raf.seek(40)
                raf.writeInt(Integer.reverseBytes(totalProcessedBytes))
                raf.close()
                
                // Play the processed file
                val mediaItem = MediaItem.fromUri(processedFile.toURI().toString())
                player?.apply {
                    setMediaItem(mediaItem)
                    prepare()
                    play()
                }
                playerView.visibility = android.view.View.VISIBLE
                playButton.text = getString(R.string.stop)
                
                // Clean up the processed file after playback
                player?.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) {
                            processedFile.delete()
                        }
                    }
                })
                
            } catch (e: Exception) {
                Log.e(TAG, "Error playing audio: ${e.message}", e)
                Toast.makeText(this, "Error playing audio: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "No recorded audio to play", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopPlayback() {
        player?.stop()
        playerView.visibility = android.view.View.GONE
        playButton.text = getString(R.string.play)
    }

    private fun setupGainControl() {
        gainSeekBar.max = 100 // 0-100 range
        gainSeekBar.progress = 50 // Default to middle position (1.0 gain)
        
        gainSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // Convert progress (0-100) to gain (0.5-3.0)
                    currentGain = 0.5f + (progress / 100f) * (MAX_GAIN - 0.5f)
                    // Update playback volume if player is active
                    player?.volume = currentGain
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun startManualRecording() {
        if (!hasAllPermissions()) {
            requestAllPermissions()
            return
        }
        
        // Create a new file for recording in the downloads folder
        val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val timestamp = System.currentTimeMillis()
        recordingFile = File(downloadsDir, "recording_${timestamp}.wav")
        recordingOutputStream = FileOutputStream(recordingFile)
        
        // Clear previous pitch and amplitude data
        pitchAmplitudeData.clear()
        
        // Write WAV header
        writeWavHeader(recordingOutputStream!!)
        totalBytesWritten = 0
        
        isManualRecording = true
        recordButton.text = getString(R.string.stop_recording)
        
        Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show()
    }
    
    private fun stopManualRecording() {
        isManualRecording = false
        recordButton.text = getString(R.string.record)
        
        // Update WAV header with final file size
        updateWavHeader()
        
        // Save pitch and amplitude data to a separate file
        savePitchAmplitudeData()
        
        // Enable play button and update player with new recording
        playButton.isEnabled = true
        recordingFile?.let { file ->
            try {
                val mediaItem = MediaItem.fromUri(file.toURI().toString())
                player?.apply {
                    setMediaItem(mediaItem)
                    prepare()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error preparing player with new recording: ${e.message}", e)
            }
        }
        
        Toast.makeText(this, "Recording saved to Downloads folder", Toast.LENGTH_SHORT).show()
    }
    
    private fun savePitchAmplitudeData() {
        try {
            val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val timestamp = System.currentTimeMillis()
            val dataFile = File(downloadsDir, "pitch_amplitude_${timestamp}.csv")
            
            FileOutputStream(dataFile).use { outputStream ->
                // Write CSV header
                outputStream.write("Timestamp,Pitch (Hz),Amplitude\n".toByteArray())
                
                // Write data rows
                for (data in pitchAmplitudeData) {
                    val row = "${data.timestamp},${data.pitch},${data.amplitude}\n"
                    outputStream.write(row.toByteArray())
                }
            }
            
            Log.d(TAG, "Pitch and amplitude data saved to: ${dataFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving pitch and amplitude data: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudioProcessing()
        player?.release()
        player = null
        recordingFile?.delete()
    }
}