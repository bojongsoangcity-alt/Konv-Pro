package com.dooyappwork.konveksipro

import com.dooyappwork.konveksipro.core.utils.formatRupiah
import org.junit.Assert.assertEquals
import org.junit.Test

/** Uji format rupiah: uang disimpan sebagai INTEGER rupiah (keputusan desain). */
class FormattersTest {
    @Test fun zero() = assertEquals("Rp0", formatRupiah(0L))
    @Test fun simple() = assertEquals("Rp1.250", formatRupiah(1250L))
    @Test fun million() = assertEquals("Rp1.250.000", formatRupiah(1_250_000L))
    @Test fun billion() = assertEquals("Rp12.500.000.000", formatRupiah(12_500_000_000L))
    @Test fun negative() = assertEquals("-Rp500", formatRupiah(-500L))
}
