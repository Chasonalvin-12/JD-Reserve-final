package com.jdreserve.app

import android.app.Activity
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Extras {
    const val ROOM_ID = "room_id"
    const val CHECK_IN = "check_in_epoch_day"
    const val CHECK_OUT = "check_out_epoch_day"
    const val GUESTS = "guests"
    const val REFERENCE = "reference"
}

object DateFormats {
    private val long = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.ENGLISH)
    private val short = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
    fun long(d: LocalDate): String = long.format(d)
    fun short(d: LocalDate): String = short.format(d)
}

object Session {
    var role: Role? = null
    var displayName: String = ""
    var email: String = ""

    /** The dates/guests the guest last searched. The Concierge uses them to answer "is anything free?". */
    var searchCheckIn: LocalDate = LocalDate.of(2026, 12, 20)
    var searchCheckOut: LocalDate = LocalDate.of(2026, 12, 23)
    var searchGuests: Int = 2

    /** The key this user's alerts are stored under (see Repository.notificationsFor). */
    val alertAudience: String
        get() = if (role == Role.STAFF) Repository.STAFF_AUDIENCE else email

    fun clear() {
        role = null
        displayName = ""
        email = ""
        searchCheckIn = LocalDate.of(2026, 12, 20)
        searchCheckOut = LocalDate.of(2026, 12, 23)
        searchGuests = 2
        Concierge.reset()
    }
}

/** Guest-only screens call this first. Returns true when the screen may continue. */
fun Activity.requireGuest(): Boolean {
    if (Session.role == Role.GUEST) return true
    if (Session.role == null) logout()
    else {
        toast("This screen is for guests.")
        finish()
    }
    return false
}

/** Star string for a 1..5 rating, e.g. 3 gives three filled and two empty stars. */
fun stars(rating: Int): String {
    val r = rating.coerceIn(0, 5)
    return "\u2605".repeat(r) + "\u2606".repeat(5 - r)
}

fun Activity.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

/**
 * DatePickerDialog months are 0-based (January = 0) but LocalDate months are 1-based.
 * Forgetting the +1 / -1 compiles fine and silently shifts every date by one month.
 */
fun Activity.pickDate(initial: LocalDate, onPicked: (LocalDate) -> Unit) {
    DatePickerDialog(
        this,
        { _, year, monthZeroBased, day -> onPicked(LocalDate.of(year, monthZeroBased + 1, day)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth
    ).show()
}

fun Activity.logout() {
    Session.clear()
    val i = Intent(this, LoginActivity::class.java)
    i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    startActivity(i)
    finish()
}

fun Activity.openMaps() {
    val uri = Uri.parse("geo:0,0?q=" + Uri.encode("Patar Beach, Bolinao, Pangasinan"))
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        toast("No maps app found on this device.")
    }
}

/** Opens the home (rooms) screen without stacking a second copy on top of an existing one. */
fun Activity.openRooms() {
    val i = Intent(this, RoomsActivity::class.java)
    i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
    startActivity(i)
}
