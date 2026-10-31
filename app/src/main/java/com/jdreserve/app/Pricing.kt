package com.jdreserve.app

import java.text.NumberFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** All money maths lives here so it can be tested and explained in one place. */
object Pricing {
    const val VAT_PERCENT = 12L
    const val MAX_NIGHTS = 14

    /** Nights = check-out minus check-in. The check-out day itself is NOT a night. */
    fun nights(checkIn: LocalDate, checkOut: LocalDate): Int =
        ChronoUnit.DAYS.between(checkIn, checkOut).toInt()

    fun subtotal(room: Room, nights: Int): Long = room.ratePerNight * nights

    /** 12% VAT rounded half-up, in whole pesos (integer maths, no floating point). */
    fun vat(subtotal: Long): Long = (subtotal * VAT_PERCENT + 50) / 100

    fun total(room: Room, nights: Int): Long {
        val sub = subtotal(room, nights)
        return sub + vat(sub)
    }

    /** 50% deposit, rounded UP so the resort is never short by a peso. */
    fun deposit(total: Long): Long = (total + 1) / 2

    fun peso(amount: Long): String =
        "\u20B1" + NumberFormat.getIntegerInstance(Locale.US).format(amount)
}
