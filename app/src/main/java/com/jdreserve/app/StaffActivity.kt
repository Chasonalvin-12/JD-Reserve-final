package com.jdreserve.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import java.time.LocalDate

/**
 * Screens S1 / S2 / S3: staff front desk.
 * Feature 3 - Verification queue with audit trail (SDG 16 accountable institutions, SDG 8).
 * Only a signed-in STAFF user may open this screen.
 */
class StaffActivity : Activity() {

    private lateinit var statsText: TextView
    private lateinit var pendingHeader: TextView
    private lateinit var noPending: TextView
    private lateinit var pendingList: ListView
    private lateinit var logText: TextView
    private lateinit var alertsButton: Button
    private var ready = false

    /** Live: new requests, cancellations and approvals show up without reopening the screen. */
    private val listener = ChangeListener { runOnUiThread { if (ready) refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Session.role != Role.STAFF) {
            toast("Staff access only. Please sign in as staff.")
            logout()
            return
        }
        setContentView(R.layout.activity_staff)

        findViewById<TextView>(R.id.staffHeader).text = "Front desk · ${Session.displayName}"
        statsText = findViewById(R.id.statsText)
        pendingHeader = findViewById(R.id.pendingHeader)
        noPending = findViewById(R.id.noPendingText)
        pendingList = findViewById(R.id.pendingList)
        logText = findViewById(R.id.logText)

        findViewById<Button>(R.id.analyticsButton).setOnClickListener {
            startActivity(Intent(this, AnalyticsActivity::class.java))
        }
        alertsButton = findViewById(R.id.alertsButton)
        alertsButton.setOnClickListener { startActivity(Intent(this, NotificationsActivity::class.java)) }
        findViewById<Button>(R.id.reviewsButton).setOnClickListener { startActivity(Intent(this, ReviewsActivity::class.java)) }
        findViewById<Button>(R.id.logoutButton).setOnClickListener { logout() }
        ready = true
    }

    override fun onResume() {
        super.onResume()
        if (ready && Session.role == Role.STAFF) {
            Repository.addListener(listener)
            refresh()
        }
    }

    override fun onPause() {
        super.onPause()
        Repository.removeListener(listener)
    }

    private fun refresh() {
        val pending = Repository.pending()
        val from = LocalDate.of(2026, 12, 20)
        val to = LocalDate.of(2026, 12, 23)
        val freeRooms = Repository.rooms.count { Repository.isRoomFree(it.id, from, to) }
        val maintenance = Repository.bookings.count { it.status == BookingStatus.MAINTENANCE }
        val confirmed = Repository.bookings.count { it.status == BookingStatus.CONFIRMED }

        statsText.text =
            "Pending requests: ${pending.size}\n" +
                "Rooms free ${DateFormats.short(from)} - ${DateFormats.short(to)}: $freeRooms of ${Repository.rooms.size}\n" +
                "Confirmed bookings: $confirmed · Under maintenance: $maintenance"
        val unread = Repository.unreadCount(Repository.STAFF_AUDIENCE)
        alertsButton.text = if (unread > 0) "Alerts ($unread)" else "Alerts"
        pendingHeader.text = "Pending online requests (${pending.size} to verify)"
        noPending.visibility = if (pending.isEmpty()) View.VISIBLE else View.GONE
        pendingList.adapter = RequestAdapter(pending)
        logText.text = Repository.activityLog.take(12).joinToString("\n")
    }

    private fun review(b: Booking) {
        val room = Repository.roomById(b.roomId)
        val nights = Pricing.nights(b.checkIn, b.checkOut)
        val conflict = Repository.firstConflict(b.roomId, b.checkIn, b.checkOut, ignoreReference = b.reference)
        val message =
            "${b.guestName}\n${b.phone} · ${b.email}\n\n" +
                "${room?.name ?: "Room"} · Room ${b.roomId}\n" +
                "${DateFormats.long(b.checkIn)} to ${DateFormats.long(b.checkOut)} ($nights night(s))\n" +
                "Guests: ${b.guests}\nTotal: ${Pricing.peso(b.total)} · 50% deposit: ${Pricing.peso(Pricing.deposit(b.total))}\n" +
                "Green Stay pledges: ${b.pledges.count()}\n\n" +
                (if (conflict != null) "WARNING: this room is already ${if (conflict.status == BookingStatus.MAINTENANCE) "under maintenance" else "reserved (${conflict.reference})"} for these dates."
                else "Room is free for these dates.")

        AlertDialog.Builder(this)
            .setTitle(b.reference)
            .setMessage(message)
            .setPositiveButton("Approve") { _, _ -> apply(Repository.approve(b.reference, Session.displayName)) }
            .setNegativeButton("Reject") { _, _ -> apply(Repository.reject(b.reference, Session.displayName)) }
            .setNeutralButton("Close", null)
            .show()
    }

    private fun apply(result: ActionResult) {
        toast(result.message)
        refresh()
    }

    private inner class RequestAdapter(private val items: List<Booking>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: layoutInflater.inflate(R.layout.item_request, parent, false)
            val b = items[position]
            val room = Repository.roomById(b.roomId)
            val nights = Pricing.nights(b.checkIn, b.checkOut)

            row.findViewById<TextView>(R.id.reqGuest).text = b.guestName
            row.findViewById<TextView>(R.id.reqRef).text = "${b.reference} · ${b.createdLabel}"
            row.findViewById<TextView>(R.id.reqRoom).text =
                "${room?.name ?: "Room"} · Room ${b.roomId}\n${DateFormats.short(b.checkIn)} - ${DateFormats.short(b.checkOut)} · $nights night(s)"
            row.findViewById<TextView>(R.id.reqTotal).text = Pricing.peso(b.total)
            row.findViewById<Button>(R.id.reviewButton).setOnClickListener { review(b) }
            return row
        }
    }
}
