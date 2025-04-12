package com.example.tarsosdsppoc

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.math.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val SAMPLE_RATE = 44100  // Standard audio rate
        private const val BUFFER_SIZE = 4096   // Increased buffer size for better analysis
        private const val BUFFER_OVERLAP = 2048 // 50% overlap
        private const val PERMISSION_REQUEST_CODE = 123
        private const val MIN_FREQUENCY = 50.0f  // Hz
        private const val MAX_FREQUENCY = 1000.0f // Hz
    }

    private lateinit var startButton: Button
    private lateinit var pitchTextView: TextView
    private lateinit var amplitudeTextView: TextView
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val audioData = FloatArray(BUFFER_SIZE)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        startButton = findViewById(R.id.startButton)
        pitchTextView = findViewById(R.id.pitchTextView)
        amplitudeTextView = findViewById(R.id.amplitudeTextView)
        
        startButton.setOnClickListener {
            if (!isRecording) {
                startAudioProcessing()
            } else {
                stopAudioProcessing()
            }
        }
    }
    
    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAudioProcessing()
            } else {
                Toast.makeText(this, "Audio permission is required for this feature", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun startAudioProcessing() {
        try {
            Log.d(TAG, "Starting audio processing...")
            
            if (!hasAudioPermission()) {
                Log.e(TAG, "Audio permission not granted")
                requestAudioPermission()
                return
            }
            
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
            
            startButton.text = getString(R.string.stop)
            isRecording = true
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in startAudioProcessing: ${e.message}", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            stopAudioProcessing()
        }
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
                
                while (isRecording) {
                    val readSize = audioRecord?.read(buffer, 0, BUFFER_SIZE, AudioRecord.READ_BLOCKING) ?: 0
                    
                    if (readSize > 0) {
                        var maxAmplitude = 0f
                        for (sample in buffer) {
                            val abs = kotlin.math.abs(sample)
                            if (abs > maxAmplitude) {
                                maxAmplitude = abs
                            }
                        }
                        
                        Log.d(TAG, "Custom AudioRecord amplitude: $maxAmplitude")
                        
                        if (maxAmplitude > 0.01f) {
                            val pitch = detectPitch(buffer)
                            runOnUiThread {
                                if (pitch > 0) {
                                    pitchTextView.text = String.format("%.1f Hz", pitch)
                                } else {
                                    pitchTextView.text = "No pitch detected"
                                }
                                amplitudeTextView.text = getString(R.string.amplitude_format, String.format("%.2f", maxAmplitude))
                            }
                        } else {
                            runOnUiThread {
                                pitchTextView.text = "No pitch detected"
                                amplitudeTextView.text = getString(R.string.amplitude_format, String.format("%.2f", maxAmplitude))
                            }
                        }
                    }
                }
            }.start()
            
            Log.d(TAG, "Custom audio recording started")
            
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
            
            startButton.text = getString(R.string.start)
            Log.d(TAG, "Audio processing stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio processing: ${e.message}", e)
            Toast.makeText(this, "Error stopping audio processing: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAudioProcessing()
    }
} 