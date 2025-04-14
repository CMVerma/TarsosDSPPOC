package com.example.tarsosdsppoc

import kotlin.math.*

class FFT4g(private val n: Int) {
    private val ip = IntArray(2 + sqrt(n / 2.0).toInt() + 1)
    private val w = DoubleArray(n / 2)

    init {
        ip[0] = 0
    }

    fun rdft(isgn: Int, a: DoubleArray) {
        if (a.size < n) {
            throw IllegalArgumentException("Input array size ${a.size} is smaller than FFT size $n")
        }
        
        var nw = ip[0]
        if (n > nw shl 2) {
            nw = n shr 2
            makewt(nw)
        }
        var nc = ip[1]
        if (n > nc shl 2) {
            nc = n shr 2
            makect(nc, w, nw)
        }
        if (isgn >= 0) {
            if (n > 4) {
                bitrv2(n, a)
                cftfsub(a)
                rftfsub(a, nc, w, nw)
            } else if (n == 4) {
                cftfsub(a)
            }
            val xi = a[0] - a[1]
            a[0] += a[1]
            a[1] = xi
        } else {
            a[1] = 0.5 * (a[0] - a[1])
            a[0] -= a[1]
            if (n > 4) {
                rftbsub(a, nc, w, nw)
                bitrv2(n, a)
                cftbsub(a)
            } else if (n == 4) {
                cftfsub(a)
            }
        }
    }

    private fun makewt(nw: Int) {
        ip[0] = nw
        ip[1] = 1
        if (nw > 2) {
            val nwh = nw shr 1
            val delta = atan(1.0) / nwh
            w[0] = 1.0
            w[1] = 0.0
            w[nwh] = cos(delta * nwh)
            w[nwh + 1] = w[nwh]
            if (nwh > 2) {
                for (j in 2 until nwh step 2) {
                    val x = cos(delta * j)
                    val y = sin(delta * j)
                    w[j] = x
                    w[j + 1] = y
                    w[nw - j] = y
                    w[nw - j + 1] = x
                }
                bitrv2(nw, w)
            }
        }
    }

    private fun makect(nc: Int, c: DoubleArray, nw: Int) {
        ip[1] = nc
        if (nc > 1) {
            val nch = nc shr 1
            val delta = atan(1.0) / nch
            c[nw] = cos(delta * nch)
            c[nw + nch] = 0.5 * c[nw]
            for (j in 1 until nch) {
                c[nw + j] = 0.5 * cos(delta * j)
                c[nw + nc - j] = 0.5 * sin(delta * j)
            }
        }
    }

    private fun bitrv2(n: Int, a: DoubleArray) {
        var l = n
        var m = 1
        while (m shl 3 < l) {
            l = l shr 1
            for (j in 0 until m) {
                ip[2 + m + j] = ip[2 + j] + l
            }
            m = m shl 1
        }
        val m2 = 2 * m
        if (m shl 3 == l) {
            for (k in 0 until m) {
                for (j in 0 until k) {
                    val j1 = 2 * j + ip[2 + k]
                    val k1 = 2 * k + ip[2 + j]
                    if (j1 < n && k1 < n) {
                        val xr = a[j1]
                        val xi = a[j1 + 1]
                        val yr = a[k1]
                        val yi = a[k1 + 1]
                        a[j1] = yr
                        a[j1 + 1] = yi
                        a[k1] = xr
                        a[k1 + 1] = xi
                    }
                    val j2 = j1 + m2
                    val k2 = k1 + 2 * m2
                    if (j2 < n && k2 < n) {
                        val xr2 = a[j2]
                        val xi2 = a[j2 + 1]
                        val yr2 = a[k2]
                        val yi2 = a[k2 + 1]
                        a[j2] = yr2
                        a[j2 + 1] = yi2
                        a[k2] = xr2
                        a[k2 + 1] = xi2
                    }
                    val j3 = j2 + m2
                    val k3 = k2 - m2
                    if (j3 < n && k3 < n) {
                        val xr3 = a[j3]
                        val xi3 = a[j3 + 1]
                        val yr3 = a[k3]
                        val yi3 = a[k3 + 1]
                        a[j3] = yr3
                        a[j3 + 1] = yi3
                        a[k3] = xr3
                        a[k3 + 1] = xi3
                    }
                    val j4 = j3 + m2
                    val k4 = k3 + 2 * m2
                    if (j4 < n && k4 < n) {
                        val xr4 = a[j4]
                        val xi4 = a[j4 + 1]
                        val yr4 = a[k4]
                        val yi4 = a[k4 + 1]
                        a[j4] = yr4
                        a[j4 + 1] = yi4
                        a[k4] = xr4
                        a[k4 + 1] = xi4
                    }
                }
                val j1 = 2 * k + m2 + ip[2 + k]
                val k1 = j1 + m2
                if (j1 < n && k1 < n) {
                    val xr = a[j1]
                    val xi = a[j1 + 1]
                    val yr = a[k1]
                    val yi = a[k1 + 1]
                    a[j1] = yr
                    a[j1 + 1] = yi
                    a[k1] = xr
                    a[k1 + 1] = xi
                }
            }
        } else {
            for (k in 1 until m) {
                for (j in 0 until k) {
                    val j1 = 2 * j + ip[2 + k]
                    val k1 = 2 * k + ip[2 + j]
                    if (j1 < n && k1 < n) {
                        val xr = a[j1]
                        val xi = a[j1 + 1]
                        val yr = a[k1]
                        val yi = a[k1 + 1]
                        a[j1] = yr
                        a[j1 + 1] = yi
                        a[k1] = xr
                        a[k1 + 1] = xi
                    }
                    val j2 = j1 + m2
                    val k2 = k1 + m2
                    if (j2 < n && k2 < n) {
                        val xr2 = a[j2]
                        val xi2 = a[j2 + 1]
                        val yr2 = a[k2]
                        val yi2 = a[k2 + 1]
                        a[j2] = yr2
                        a[j2 + 1] = yi2
                        a[k2] = xr2
                        a[k2 + 1] = xi2
                    }
                }
            }
        }
    }

    private fun rftfsub(a: DoubleArray, nc: Int, c: DoubleArray, nw: Int) {
        val m = n shr 1
        val ks = 2 * nc / m
        var kk = 0
        for (j in 2 until m step 2) {
            val k = n - j
            kk += ks
            val wkr = 0.5 - c[nw + nc - kk]
            val wki = c[nw + kk]
            val xr = a[j] - a[k]
            val xi = a[j + 1] + a[k + 1]
            val yr = wkr * xr - wki * xi
            val yi = wkr * xi + wki * xr
            a[j] -= yr
            a[j + 1] -= yi
            a[k] += yr
            a[k + 1] -= yi
        }
    }

    private fun rftbsub(a: DoubleArray, nc: Int, c: DoubleArray, nw: Int) {
        a[1] = -a[1]
        val m = n shr 1
        val ks = 2 * nc / m
        var kk = 0
        for (j in 2 until m step 2) {
            val k = n - j
            kk += ks
            val wkr = 0.5 - c[nw + nc - kk]
            val wki = c[nw + kk]
            val xr = a[j] - a[k]
            val xi = a[j + 1] + a[k + 1]
            val yr = wkr * xr + wki * xi
            val yi = wkr * xi - wki * xr
            a[j] -= yr
            a[j + 1] = yi - a[j + 1]
            a[k] += yr
            a[k + 1] = yi - a[k + 1]
        }
    }

    private fun cftfsub(a: DoubleArray) {
        if (n > 8) {
            cft1st(a)
            cftmdl(n, a)
        } else if (n == 4) {
            val xr = a[0] + a[2]
            val xi = a[1] + a[3]
            val yr = a[0] - a[2]
            val yi = a[1] - a[3]
            a[0] = xr + xi
            a[1] = yr + yi
            a[2] = xr - xi
            a[3] = yr - yi
        } else if (n == 8) {
            cft1st(a)
        }
    }

    private fun cftbsub(a: DoubleArray) {
        if (n > 8) {
            cft1st(a)
            cftmdl(n, a)
        } else if (n == 4) {
            val xr = a[0] + a[2]
            val xi = a[1] - a[3]
            val yr = a[0] - a[2]
            val yi = a[1] + a[3]
            a[0] = xr + xi
            a[1] = yr - yi
            a[2] = xr - xi
            a[3] = yr + yi
        } else if (n == 8) {
            cft1st(a)
        }
    }

    private fun cft1st(a: DoubleArray) {
        var j0 = 0
        var j1 = n / 8
        var j2 = j1 + j1
        var j3 = j2 + j1
        if (j3 < n) {
            var x0r = a[0] + a[j2]
            var x0i = a[1] + a[j2 + 1]
            var x1r = a[0] - a[j2]
            var x1i = a[1] - a[j2 + 1]
            var x2r = a[j1] + a[j3]
            var x2i = a[j1 + 1] + a[j3 + 1]
            var x3r = a[j1] - a[j3]
            var x3i = a[j1 + 1] - a[j3 + 1]
            a[0] = x0r + x2r
            a[1] = x0i + x2i
            a[j1] = x0r - x2r
            a[j1 + 1] = x0i - x2i
            a[j2] = x1r - x3i
            a[j2 + 1] = x1i + x3r
            a[j3] = x1r + x3i
            a[j3 + 1] = x1i - x3r
        }
        val wk1r = w[2]
        for (j in 2 until n / 8) {
            j0 = 2 * j
            j1 = j0 + j1
            j2 = j1 + j1
            j3 = j2 + j1
            if (j3 < n) {
                var x0r = a[j0] + a[j2]
                var x0i = a[j0 + 1] + a[j2 + 1]
                var x1r = a[j0] - a[j2]
                var x1i = a[j0 + 1] - a[j2 + 1]
                var x2r = a[j1] + a[j3]
                var x2i = a[j1 + 1] + a[j3 + 1]
                var x3r = a[j1] - a[j3]
                var x3i = a[j1 + 1] - a[j3 + 1]
                a[j0] = x0r + x2r
                a[j0 + 1] = x0i + x2i
                a[j1] = x0r - x2r
                a[j1 + 1] = x0i - x2i
                x0r = x1r - x3i
                x0i = x1i + x3r
                a[j2] = wk1r * (x0r - x0i)
                a[j2 + 1] = wk1r * (x0i + x0r)
                x0r = x1r + x3i
                x0i = x1i - x3r
                a[j3] = wk1r * (x0i - x0r)
                a[j3 + 1] = wk1r * (x0r + x0i)
            }
        }
    }

    private fun cftmdl(l: Int, a: DoubleArray) {
        var j0 = 0
        var j1 = l / 8
        var j2 = j1 + j1
        var j3 = j2 + j1
        if (j3 < n) {
            var x0r = a[0] + a[j2]
            var x0i = a[1] + a[j2 + 1]
            var x1r = a[0] - a[j2]
            var x1i = a[1] - a[j2 + 1]
            var x2r = a[j1] + a[j3]
            var x2i = a[j1 + 1] + a[j3 + 1]
            var x3r = a[j1] - a[j3]
            var x3i = a[j1 + 1] - a[j3 + 1]
            a[0] = x0r + x2r
            a[1] = x0i + x2i
            a[j1] = x0r - x2r
            a[j1 + 1] = x0i - x2i
            a[j2] = x1r - x3i
            a[j2 + 1] = x1i + x3r
            a[j3] = x1r + x3i
            a[j3 + 1] = x1i - x3r
        }
        val wk1r = w[2]
        for (j in 2 until l / 8) {
            j0 = 2 * j
            j1 = j0 + j1
            j2 = j1 + j1
            j3 = j2 + j1
            if (j3 < n) {
                var x0r = a[j0] + a[j2]
                var x0i = a[j0 + 1] + a[j2 + 1]
                var x1r = a[j0] - a[j2]
                var x1i = a[j0 + 1] - a[j2 + 1]
                var x2r = a[j1] + a[j3]
                var x2i = a[j1 + 1] + a[j3 + 1]
                var x3r = a[j1] - a[j3]
                var x3i = a[j1 + 1] - a[j3 + 1]
                a[j0] = x0r + x2r
                a[j0 + 1] = x0i + x2i
                a[j1] = x0r - x2r
                a[j1 + 1] = x0i - x2i
                x0r = x1r - x3i
                x0i = x1i + x3r
                a[j2] = wk1r * (x0r - x0i)
                a[j2 + 1] = wk1r * (x0i + x0r)
                x0r = x1r + x3i
                x0i = x1i - x3r
                a[j3] = wk1r * (x0i - x0r)
                a[j3 + 1] = wk1r * (x0r + x0i)
            }
        }
    }
} 