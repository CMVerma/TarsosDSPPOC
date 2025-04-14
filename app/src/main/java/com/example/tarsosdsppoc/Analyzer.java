package com.example.tarsosdsppoc;

public class Analyzer {
    public static int ANALYZE_INTERVAL = 1470;
    private static final double FREQ_A3 = (Math.pow(2.0d, 0.0d) * 220.0d);
    private static final double FREQ_A3_SHARP = (Math.pow(2.0d, 0.08333333333333333d) * 220.0d);
    public static final int PITCH_BUF_SIZE = 800;
    public static final double SAMPLE_FREQ = 44100.0d;
    private static final double f_c1 = (55.0d * Math.pow(2.0d, -0.75d));
    private static final double f_c8 = (7040.0d * Math.pow(2.0d, -0.75d));
    public static final int FFTSIZE = ((int) Math.pow(2.0d, (double) (((int) log2(SAMPLE_FREQ / (FREQ_A3_SHARP - FREQ_A3))) + 1)));
    private static double[] han_window = new double[FFTSIZE];
    public static final double log2_f_c1 = log2(f_c1);
    private double[] acf_data = new double[FFTSIZE];
    int analyze_cnt = 0;
    private FFT4g fft = new FFT4g(FFTSIZE);
    private double[] fft_data = new double[FFTSIZE];
    double peak_freq = -1.0d;
    private float[] pitch_buf = new float[PITCH_BUF_SIZE];
    private int pitch_buf_pos = 0;
    private final int range = 3;
    private double threshold = 3.0d;
    int total_analyze_cnt = 0;
    private double[] wave_data = new double[FFTSIZE];
    private int wave_data_pos = 0;

    static {
        for (int i = 0; i < FFTSIZE; i++) {
            han_window[i] = (0.5d - (Math.cos((6.283185307179586d * ((double) i)) / ((double) FFTSIZE)) * 0.5d)) / 32767.0d;
        }
    }

    private double power(double re, double im) {
        return (re * re) + (im * im);
    }

    public static double log2(double d) {
        return Math.log(d) / Math.log(2.0d);
    }

    public static float freq_to_cent(double freq) {
        if (freq < 0.0d) {
            return -1.0f;
        }
        return (float) (((log2(freq) - log2_f_c1) * 12.0d) * 100.0d);
    }

    public float[] get_pitch_buf() {
        return this.pitch_buf;
    }

    public int get_pitch_buf_pos() {
        return this.pitch_buf_pos;
    }

    public int get_pitch_buf_size() {
        return PITCH_BUF_SIZE;
    }

    public double get_peak_freq() {
        return this.peak_freq;
    }

    public void set_threshold(double v) {
        this.threshold = v;
    }

    public double get_threshold() {
        return this.threshold;
    }

    public static float get_interval_sec() {
        return ((float) ANALYZE_INTERVAL) / 44100.0f;
    }

    public int get_total_analyze_cnt() {
        return this.total_analyze_cnt;
    }

    public void set_total_analyze_cnt(int analyze_cnt) {
        this.total_analyze_cnt = analyze_cnt;
    }

    public void clearData() {
        this.wave_data_pos = 0;
        this.analyze_cnt = 0;
    }

    public Analyzer() {
        for (int i = 0; i < PITCH_BUF_SIZE; i++) {
            this.pitch_buf[i] = -1.0f;
        }
    }

    public void addData(short d) {
        double[] dArr = this.wave_data;
        int i = this.wave_data_pos;
        this.wave_data_pos = i + 1;
        dArr[i] = (double) d;
        if (this.wave_data_pos == FFTSIZE) {
            this.wave_data_pos = 0;
        }
        this.analyze_cnt++;
        if (this.analyze_cnt == ANALYZE_INTERVAL) {
            analyze();
            this.analyze_cnt = 0;
        }
    }

    private void analyze() {
        int i;
        for (i = 0; i < FFTSIZE; i++) {
            this.fft_data[i] = han_window[i] * this.wave_data[((this.wave_data_pos + FFTSIZE) + i) % FFTSIZE];
        }
        this.fft.rdft(1, this.fft_data);
        this.acf_data[0] = power(this.fft_data[0], 0.0d);
        this.acf_data[1] = power(this.fft_data[1], 0.0d);
        for (i = 1; i < FFTSIZE / 2; i++) {
            this.acf_data[i * 2] = power(this.fft_data[i * 2], this.fft_data[(i * 2) + 1]);
            this.acf_data[(i * 2) + 1] = 0.0d;
        }
        this.fft.rdft(-1, this.acf_data);
        if (Math.sqrt(this.acf_data[0]) >= this.threshold) {
            this.peak_freq = detect_pitch();
        } else {
            this.peak_freq = -1.0d;
        }
        float[] fArr = this.pitch_buf;
        int i2 = this.pitch_buf_pos;
        this.pitch_buf_pos = i2 + 1;
        fArr[i2] = freq_to_cent(this.peak_freq);
        if (this.pitch_buf_pos == PITCH_BUF_SIZE) {
            this.pitch_buf_pos = 0;
        }
        this.total_analyze_cnt++;
    }

    double detect_pitch() {
        int j;
        int i;
        int peak_i = 0;
        double peak_max_v = 0.0d;
        double d_pre = -1.0d;
        int i0 = ((int) (SAMPLE_FREQ / f_c8)) - 1;
        double v_pre = this.acf_data[i0];
        for (j = 1; j < 5; j++) {
            if (this.acf_data[i0 + j] > v_pre) {
                v_pre = this.acf_data[i0 + j];
            }
        }
        for (i = i0; ((double) i) < (SAMPLE_FREQ / f_c1) + 1.0d; i++) {
            double v = this.acf_data[i + 1];
            for (j = 1; j < 5; j++) {
                if (this.acf_data[(i + 1) + j] > v) {
                    v = this.acf_data[(i + 1) + j];
                }
            }
            double d = v - v_pre;
            if (d < 0.0d && d_pre > 0.0d && v_pre > peak_max_v) {
                peak_max_v = v_pre;
                peak_i = i;
            }
            if (d != 0.0d) {
                d_pre = d;
                v_pre = v;
            }
        }
        if (peak_i == 0 || peak_max_v < this.acf_data[0] * 0.5d) {
            return -1.0d;
        }
        double peak_f = SAMPLE_FREQ / ((double) peak_i);
        double peak_v = get_fft_value_around_f(peak_f);
        double peak_v3 = get_fft_value_around_f((peak_f / 3.0d) * 2.0d);
        if (peak_v < 0.24d || peak_v3 <= 3.15d * peak_v || peak_f / 3.0d < f_c1) {
            double peak_v2 = get_fft_value_around_f(peak_f * 1.5d);
            if (peak_v < 0.24d || peak_v2 <= 1.0d * peak_v || peak_f / 2.0d < f_c1) {
                double peak_f1_2 = peak_f * 2.0d;
                double peak_v1_2 = get_fft_value_around_f(peak_f1_2);
                double peak_f1_3 = peak_f * 3.0d;
                double peak_v1_3 = get_fft_value_around_f(peak_f1_3);
                if (peak_v1_2 < 0.24d || peak_v1_2 <= 1.25d * peak_v || peak_v1_3 >= 0.06d * peak_v1_2 || peak_f1_2 > f_c8) {
                    if (peak_v1_3 >= 0.24d && peak_v1_3 > 1.25d * peak_v && peak_v1_2 < 0.06d * peak_v1_3 && peak_f1_3 <= f_c8) {
                        peak_f = peak_f1_3;
                    }
                    if (Math.sqrt(((peak_v * peak_v) + (peak_v1_2 * peak_v1_2)) + (peak_v1_3 * peak_v1_3)) < 0.7d) {
                        return -1.0d;
                    }
                }
                peak_f = peak_f1_2;
            } else {
                peak_f /= 2.0d;
            }
        } else {
            peak_f /= 3.0d;
        }
        int N = (int) (((double) ((FFTSIZE / 2) - 1)) / (SAMPLE_FREQ / peak_f));
        double sum_f = peak_f;
        int sum_cnt = 1;
        for (int n = 2; n <= N; n++) {
            int peak_i_n = (int) ((SAMPLE_FREQ * ((double) n)) / (sum_f / ((double) sum_cnt)));
            int peak_i_n_ubound = peak_i_n + 3;
            if (peak_i_n_ubound >= FFTSIZE / 2) {
                peak_i_n_ubound = (FFTSIZE / 2) - 1;
            }
            int peak_i_n_peak = peak_i_n_ubound;
            double peak_i_n_max = this.acf_data[peak_i_n_peak];
            for (i = peak_i_n - 3; i <= peak_i_n_ubound; i++) {
                if (this.acf_data[i] >= peak_i_n_max) {
                    peak_i_n_max = this.acf_data[i];
                    peak_i_n_peak = i;
                }
            }
            if (!(peak_i_n_peak == peak_i_n - 3 || peak_i_n_peak == peak_i_n_ubound)) {
                sum_f += (SAMPLE_FREQ * ((double) n)) / ((double) peak_i_n_peak);
                sum_cnt++;
            }
        }
        return sum_f / ((double) sum_cnt) / 2; // divided by 2 here as a bug fix.
    }

    private double get_fft_value_around_f(double f) {
        int i2 = (int) ((f * 1.0208333333333333d) / (SAMPLE_FREQ / ((double) FFTSIZE)));
        double max_v = 0.0d;
        int max_i = 0;
        for (int i = (int) ((f * 0.9791666666666666d) / (SAMPLE_FREQ / ((double) FFTSIZE))); i <= i2; i++) {
            double v = power(this.fft_data[i * 2], this.fft_data[(i * 2) + 1]);
            if (v > max_v) {
                max_v = v;
                max_i = i;
            }
        }
        return Math.sqrt(((power(this.fft_data[(max_i - 1) * 2], this.fft_data[((max_i - 1) * 2) + 1]) + max_v) + power(this.fft_data[(max_i + 1) * 2], this.fft_data[((max_i + 1) * 2) + 1])) / 3.0d);
    }
}
