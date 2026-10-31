package com.jdreserve.app

import java.util.Locale

data class EcoImpact(
    val waterLiters: Long,
    val plasticBottles: Long,
    val co2Kg: Double,
    val points: Int
) {
    operator fun plus(o: EcoImpact) = EcoImpact(
        waterLiters + o.waterLiters,
        plasticBottles + o.plasticBottles,
        co2Kg + o.co2Kg,
        points + o.points
    )

    fun describe(): String = String.format(
        Locale.US,
        "Water saved: %d L\nSingle-use plastic bottles avoided: %d\nCO2 avoided: %.1f kg\nEco points: %d",
        waterLiters, plasticBottles, co2Kg, points
    )

    companion object {
        val ZERO = EcoImpact(0, 0, 0.0, 0)
    }
}

/**
 * Green Stay Pledge calculator.
 * SDG 12 (Responsible Consumption), SDG 13 (Climate Action), SDG 14 (Life Below Water).
 *
 * NOTE: the constants below are CLASSROOM ASSUMPTIONS to make the feature work.
 * Replace them with figures from a source you cite in your report.
 */
object EcoCalculator {
    const val WATER_L_PER_SKIPPED_LINEN_CHANGE = 40L   // per room, per night
    const val BOTTLES_PER_GUEST_NIGHT = 2L             // 500 mL bottles avoided
    const val KWH_SAVED_PER_ROOM_NIGHT = 1.5           // AC at 24 C / fan-first
    const val CO2_KG_PER_KWH = 0.7                     // assumed grid factor
    const val POINTS_PER_PLEDGE = 10
    const val POINTS_PER_CLEANUP_VOLUNTEER = 5

    fun estimate(p: Pledges, nights: Int, guests: Int): EcoImpact {
        if (nights <= 0 || guests <= 0) return EcoImpact.ZERO
        val water = if (p.reuseLinen) WATER_L_PER_SKIPPED_LINEN_CHANGE * nights else 0L
        val bottles = if (p.refillableBottle) BOTTLES_PER_GUEST_NIGHT * guests * nights else 0L
        val co2 = if (p.energySaver) KWH_SAVED_PER_ROOM_NIGHT * CO2_KG_PER_KWH * nights else 0.0
        var points = p.count() * POINTS_PER_PLEDGE
        if (p.coastalCleanup) points += POINTS_PER_CLEANUP_VOLUNTEER * guests
        return EcoImpact(water, bottles, co2, points)
    }
}
