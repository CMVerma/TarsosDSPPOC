# TarsosDSP Pitch Detection POC

This project demonstrates a custom pitch detection implementation for Android using autocorrelation.

## Features

- Real-time audio recording and processing
- Custom pitch detection algorithm using autocorrelation
- Amplitude detection with noise filtering
- Detailed logging for debugging

## Implementation Details

### Audio Processing

- Sample rate: 44.1kHz
- Buffer size: 4096 samples
- Buffer overlap: 2048 samples (50%)
- Audio format: Mono, PCM Float

### Pitch Detection Algorithm

The pitch detection uses autocorrelation with the following steps:

1. **Preprocessing**:
   - Apply Hanning window to reduce spectral leakage
   - Normalize the windowed buffer

2. **Autocorrelation**:
   - Calculate autocorrelation for lags between 44 and 882 samples
   - This corresponds to frequencies between 50Hz and 1000Hz

3. **Peak Detection**:
   - Find the maximum correlation peak
   - Verify the peak is significant (at least 80% of max correlation)
   - Check that there are no larger peaks at lower lags

4. **Frequency Calculation**:
   - Convert the peak lag to frequency: frequency = sample_rate / lag

### Amplitude Detection

- Amplitude threshold: 0.01 (to filter out background noise)
- Amplitude values range from 0.0 to 1.0

## Usage

1. Tap the "Start" button to begin audio processing
2. Make a clear sound (like humming a single note)
3. The app will display:
   - Current amplitude
   - Detected pitch (in Hz) when a clear pitch is detected
   - "No pitch detected" when no clear pitch is present

## Recent Changes

- Removed TarsosDSP dependency
- Implemented custom pitch detection using autocorrelation
- Added comprehensive error handling and logging
- Set appropriate amplitude threshold for noise filtering
- Improved peak detection criteria

## Future Improvements

- Implement additional pitch detection algorithms
- Add visualization of the audio waveform
- Improve real-time performance
- Add note name display (e.g., A4, B3, etc.)

## Requirements

- Android Studio Arctic Fox or newer
- Android SDK 21 or higher
- Kotlin 1.8.0 or higher

## Setup

1. Clone the repository
2. Open the project in Android Studio
3. Build and run the application on a device or emulator

## Dependencies

- AndroidX: Android support libraries
- Kotlin: Programming language

## License

This project is licensed under the MIT License - see the LICENSE file for details. 