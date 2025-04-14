package com.example.tarsosdsppoc

import kotlin.math.*
import android.util.Log

class FFTAnalyzer {
    companion object {
        private const val TAG = "FFTAnalyzer"
        private const val SAMPLE_RATE = 44100
        private const val MIN_FREQUENCY = 50.0f
        private const val MAX_FREQUENCY = 1000.0f
        private const val FREQ_A3 = 220.0f
        private const val FREQ_A3_SHARP = 233.08f
        private const val F_C1 = 32.7f
        private const val F_C8 = 4186.0f
        private const val LOG2_F_C1 = 5.03f
        private const val DEFAULT_THRESHOLD = 0.001f  // Further lowered threshold for better sensitivity
    }

    private val fftSize: Int
    private val hanningWindow: FloatArray
    private var threshold = DEFAULT_THRESHOLD

    init {
        // Calculate FFT size based on sample rate and minimum frequency
        fftSize = 2048  // Match buffer size
        hanningWindow = FloatArray(fftSize)
        for (i in 0 until fftSize) {
            hanningWindow[i] = 0.5f * (1 - cos(2 * PI.toFloat() * i / (fftSize - 1)))
        }
        Log.d(TAG, "Initialized FFTAnalyzer with fftSize: $fftSize")
    }

    fun setThreshold(threshold: Float) {
        this.threshold = threshold
        Log.d(TAG, "Set threshold to: $threshold")
    }

    fun detectPitch(buffer: FloatArray): Float {
        try {
            Log.d(TAG, "Starting pitch detection with buffer size: ${buffer.size}")
            
            // Check if buffer is empty or all zeros
            var isAllZeros = true
            for (sample in buffer) {
                if (sample != 0f) {
                    isAllZeros = false
                    break
                }
            }
            
            if (isAllZeros) {
                Log.d(TAG, "Buffer contains all zeros, skipping FFT")
                return -1f
            }
            
            // Zero pad if buffer is smaller than FFT size
            val windowedBuffer = FloatArray(fftSize)
            val copySize = minOf(buffer.size, fftSize)
            System.arraycopy(buffer, 0, windowedBuffer, 0, copySize)
            Log.d(TAG, "Copied $copySize samples to windowed buffer")
            
            // Apply Hanning window
            for (i in 0 until fftSize) {
                windowedBuffer[i] *= hanningWindow[i]
            }
            Log.d(TAG, "Applied Hanning window")

            // Perform FFT
            val fft = FFT4g(fftSize)
            val fftData = DoubleArray(fftSize)
            for (i in 0 until fftSize) {
                fftData[i] = windowedBuffer[i].toDouble()
            }
            Log.d(TAG, "Prepared FFT data")
            
            fft.rdft(1, fftData)
            Log.d(TAG, "Performed FFT")

            // Calculate power spectrum with interpolation
            val powerSpectrum = FloatArray(fftSize / 2)
            for (i in 0 until fftSize / 2) {
                val real = fftData[2 * i].toFloat()
                val imag = fftData[2 * i + 1].toFloat()
                powerSpectrum[i] = sqrt(real * real + imag * imag)
            }
            Log.d(TAG, "Calculated power spectrum")

            // Find fundamental frequency with quadratic interpolation
            return findFundamentalFrequency(powerSpectrum)
        } catch (e: Exception) {
            Log.e(TAG, "Error in detectPitch: ${e.message}", e)
            return -1f
        }
    }

    private fun findFundamentalFrequency(powerSpectrum: FloatArray): Float {
        try {
            var maxPower = 0f
            var peakIndex = 0

            // Find the peak in the power spectrum within the frequency range of interest
            val minBinIndex = (MIN_FREQUENCY * fftSize / SAMPLE_RATE).toInt()
            val maxBinIndex = (MAX_FREQUENCY * fftSize / SAMPLE_RATE).toInt()
            
            Log.d(TAG, "Power spectrum size: ${powerSpectrum.size}")
            Log.d(TAG, "Searching between bins $minBinIndex and $maxBinIndex")
            
            for (i in minBinIndex..maxBinIndex) {
                if (powerSpectrum[i] > maxPower) {
                    maxPower = powerSpectrum[i]
                    peakIndex = i
                }
            }

            Log.d(TAG, "Max power: $maxPower at bin $peakIndex")
            
            // Check if the signal is strong enough
            if (maxPower < threshold) {
                Log.d(TAG, "Signal too weak: $maxPower < $threshold")
                return -1f
            }

            // Quadratic interpolation for better frequency estimation
            if (peakIndex > 0 && peakIndex < powerSpectrum.size - 1) {
                val alpha = powerSpectrum[peakIndex - 1]
                val beta = powerSpectrum[peakIndex]
                val gamma = powerSpectrum[peakIndex + 1]
                val p = 0.5f * (alpha - gamma) / (alpha - 2*beta + gamma)
                val interpolatedIndex = peakIndex + p
                
                // Convert index to frequency
                val frequency = interpolatedIndex * SAMPLE_RATE.toFloat() / fftSize
                Log.d(TAG, "Interpolated frequency: $frequency Hz")
                return frequency
            }

            // Fallback to simple frequency calculation
            val frequency = peakIndex * SAMPLE_RATE.toFloat() / fftSize
            Log.d(TAG, "Simple frequency: $frequency Hz")
            return frequency
        } catch (e: Exception) {
            Log.e(TAG, "Error in findFundamentalFrequency: ${e.message}", e)
            return -1f
        }
    }

    private fun getFftValueAroundF(powerSpectrum: FloatArray, f: Float): Float {
        val index = (f * fftSize / SAMPLE_RATE).toInt()
        if (index < 0 || index >= powerSpectrum.size) {
            return 0f
        }
        return powerSpectrum[index]
    }
} 