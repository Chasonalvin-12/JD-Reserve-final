package com.jdreserve.app

import java.time.LocalDate

enum class RoomType(val label: String) {
    DELUXE("Deluxe"),
    SUITE("Suite"),
    VILLA("Villa"),
    CABIN("Cabin")
}

data class Room(
    val id: Int,                 // room number shown to guests, e.g. 21
    val name: String,
    val type: RoomType,
    val capacity: Int,
    val beds: String,
    val perks: String,
    val ratePerNight: Long       // whole pesos (never Double for money)
)

enum class BookingStatus {
    PENDING,       // guest sent it, staff has not verified yet
    CONFIRMED,     // staff approved it -> the room is really taken
    REJECTED,
    CANCELLED,     // the guest cancelled it
    MAINTENANCE;   // staff blocked the room (not a guest booking)

    /** Text shown to guests in My Bookings. */
    val label: String
        get() = when (this) {
            PENDING -> "Awaiting verification"
            CONFIRMED -> "Confirmed"
            REJECTED -> "Declined"
            CANCELLED -> "Cancelled"
            MAINTENANCE -> "Maintenance"
        }

    /** Only these statuses make a room unavailable. PENDING deliberately does not. */
    val blocksRoom: Boolean
        get() = this == CONFIRMED || this == MAINTENANCE
}

/** The Green Stay Pledge (SDG 12 / 13 / 14). */
data class Pledges(
    val reuseLinen: Boolean = false,
    val refillableBottle: Boolean = false,
    val energySaver: Boolean = false,
    val coastalCleanup: Boolean = false
) {
    fun count(): Int = listOf(reuseLinen, refillableBottle, energySaver, coastalCleanup).count { it }
}

data class Booking(
    val reference: String,
    val roomId: Int,
    val guestName: String,
    val phone: String,
    val email: String,
    val guests: Int,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val pledges: Pledges,
    val total: Long,
    var status: BookingStatus,
    val createdLabel: String,
    /** Login email of the guest who made the request. Drives My Bookings and notifications. */
    val ownerEmail: String = ""
)

enum class Role { GUEST, STAFF }

data class ActionResult(val ok: Boolean, val message: String)

/** One guest review. A booking can be reviewed once. */
data class Review(
    val id: Int,
    val roomId: Int,
    val bookingReference: String,
    val guestName: String,
    val guestEmail: String,
    val rating: Int,             // 1..5
    val comment: String,
    val date: LocalDate
)

data class RatingSummary(val average: Double, val count: Int, val perStar: List<Int>) // perStar[0] = 1 star

/** A message in the in-app inbox. [audience] is a guest's lowercase email, or Repository.STAFF_AUDIENCE. */
data class AppNotification(
    val id: Int,
    val audience: String,
    val title: String,
    val body: String,
    val timeLabel: String,
    val reference: String?,
    var read: Boolean = false
)
