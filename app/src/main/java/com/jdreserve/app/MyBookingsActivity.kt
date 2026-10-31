package com.jdreserve.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.RatingBar
import android.widget.Spinner
import android.widget.TextView
import java.time.LocalDate

/**
 * My Bookings wallet: every request the signed-in guest made, as a pass with its status.
 * From here a guest can open the full pass, cancel, or review a confirmed stay.
 */
class MyBookingsActivity : Activity() {

    private lateinit var summary: TextView
    private lateinit var filterSpinner: Spinner
    private lateinit var emptyText: TextView
    private lateinit var findRoom: Button
    private lateinit var bookingList: ListView
    private var ready = false

    private val listener = ChangeListener { runOnUiThread { if (ready) refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireGuest()) return
        setContentView(R.layout.activity_my_bookings)

        summary = findViewById(R.id.walletSummary)
        filterSpinner = findViewById(R.id.filterSpinner)
        emptyText = findViewById(R.id.emptyText)
        findRoom = findViewById(R.id.findRoomButton)
        bookingList = findViewById(R.id.bookingList)

        val filters = listOf("All bookings", "Upcoming", "Past", "Cancelled / declined")
        filterSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filters)
        filterSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (ready) refresh()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        findRoom.setOnClickListener {
            openRooms()
            finish()
        }
        ready = true
    }

    override fun onResume() {
        super.onResume()
        if (!ready) return
        Repository.addListener(listener)
        refresh()
    }

    override fun onPause() {
        super.onPause()
        Repository.removeListener(listener)
    }

    private fun isActive(b: Booking) = b.status == BookingStatus.PENDING || b.status == BookingStatus.CONFIRMED

    private fun refresh() {
        val today = LocalDate.now()
        val mine = Repository.bookingsFor(Session.email)
        val shown = when (filterSpinner.selectedItemPosition) {
            1 -> mine.filter { isActive(it) && it.checkOut.isAfter(today) }
            2 -> mine.filter { isActive(it) && !it.checkOut.isAfter(today) }
            3 -> mine.filter { it.status == BookingStatus.CANCELLED || it.status == BookingStatus.REJECTED }
            else -> mine
        }

        val upcoming = mine.count { isActive(it) && it.checkOut.isAfter(today) }
        val confirmed = mine.filter { it.status == BookingStatus.CONFIRMED }
        var eco = EcoImpact.ZERO
        for (b in confirmed) eco += EcoCalculator.estimate(b.pledges, Pricing.nights(b.checkIn, b.checkOut), b.guests)
        summary.text = "Upcoming: $upcoming · Confirmed: ${confirmed.size} · Eco points earned: ${eco.points}"

        when {
            mine.isEmpty() -> {
                emptyText.text = "No bookings yet. When you send a request it shows up here as a pass."
                emptyText.visibility = View.VISIBLE
                findRoom.visibility = View.VISIBLE
            }
            shown.isEmpty() -> {
                emptyText.text = "Nothing in this view."
                emptyText.visibility = View.VISIBLE
                findRoom.visibility = View.GONE
            }
            else -> {
                emptyText.visibility = View.GONE
                findRoom.visibility = View.GONE
            }
        }
        val position = bookingList.firstVisiblePosition
        bookingList.adapter = PassAdapter(shown)
        bookingList.setSelection(position)
    }

    private fun showPass(b: Booking) {
        val room = Repository.roomById(b.roomId)
        val nights = Pricing.nights(b.checkIn, b.checkOut)
        val message =
            "Status: ${b.status.label}\n\n" +
                "${room?.name ?: "Room"} · Room ${b.roomId}\n" +
                "Check-in: ${DateFormats.long(b.checkIn)} (from 2:00 PM)\n" +
                "Check-out: ${DateFormats.long(b.checkOut)} (by 12:00 NN)\n" +
                "Guests: ${b.guests} · $nights night(s)\n\n" +
                "Total: ${Pricing.peso(b.total)}\n" +
                "50% deposit: ${Pricing.peso(Pricing.deposit(b.total))}" +
                (if (b.status == BookingStatus.CONFIRMED) " (pay via the GCash link we emailed you)" else "") +
                (if (b.pledges.count() > 0) "\n\nGreen Stay pledges: ${b.pledges.count()}" else "")
        val builder = AlertDialog.Builder(this)
            .setTitle(b.reference)
            .setMessage(message)
            .setPositiveButton("Close", null)
        if (b.status == BookingStatus.CONFIRMED) builder.setNeutralButton("Directions") { _, _ -> openMaps() }
        builder.show()
    }

    private fun confirmCancel(b: Booking) {
        val extra = if (b.status == BookingStatus.CONFIRMED) "\n\nThe room will be released. Ask the front desk about your deposit." else ""
        AlertDialog.Builder(this)
            .setTitle("Cancel ${b.reference}?")
            .setMessage("Room ${b.roomId}, ${DateFormats.short(b.checkIn)} - ${DateFormats.short(b.checkOut)}.$extra")
            .setPositiveButton("Cancel booking") { _, _ -> toast(Repository.cancel(b.reference, Session.email).message) }
            .setNegativeButton("Keep it", null)
            .show()
    }

    private fun writeReview(b: Booking) {
        val room = Repository.roomById(b.roomId)
        val view = layoutInflater.inflate(R.layout.dialog_review, null)
        view.findViewById<TextView>(R.id.reviewRoomText).text = "How was Room ${b.roomId} · ${room?.name ?: ""}?"
        val bar = view.findViewById<RatingBar>(R.id.reviewRatingBar)
        val comment = view.findViewById<EditText>(R.id.reviewCommentInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Rate your stay")
            .setView(view)
            .setPositiveButton("Submit", null)   // real click handler is set below so errors keep the dialog open
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val rating = bar.rating.toInt()
                if (rating < 1) {
                    toast("Tap the stars to choose a rating.")
                    return@setOnClickListener
                }
                val result = Repository.addReview(b.reference, Session.email, rating, comment.text.toString())
                toast(result.message)
                if (result.ok) dialog.dismiss()
            }
        }
        dialog.show()
    }

    private inner class PassAdapter(private val items: List<Booking>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: layoutInflater.inflate(R.layout.item_my_booking, parent, false)
            val b = items[position]
            val room = Repository.roomById(b.roomId)
            val nights = Pricing.nights(b.checkIn, b.checkOut)
            val today = LocalDate.now()

            row.findViewById<TextView>(R.id.passRoom).text = "Room ${b.roomId} · ${room?.name ?: ""}"
            row.findViewById<TextView>(R.id.passStatus).text = "Status: ${b.status.label}"
            row.findViewById<TextView>(R.id.passDates).text =
                "${DateFormats.long(b.checkIn)} to ${DateFormats.short(b.checkOut)} · $nights night(s) · ${Pricing.peso(b.total)}"
            row.findViewById<TextView>(R.id.passRef).text = "Reference ${b.reference}"

            row.findViewById<Button>(R.id.passDetails).setOnClickListener { showPass(b) }

            val review = row.findViewById<Button>(R.id.passReview)
            if (b.status == BookingStatus.CONFIRMED) {
                review.visibility = View.VISIBLE
                val can = Repository.canReview(b)
                review.isEnabled = can
                review.text = if (Repository.hasReview(b.reference)) "Reviewed" else "Review"
                review.setOnClickListener { writeReview(b) }
            } else {
                review.visibility = View.GONE
            }

            val cancel = row.findViewById<Button>(R.id.passCancel)
            if (isActive(b) && b.checkOut.isAfter(today)) {
                cancel.visibility = View.VISIBLE
                cancel.setOnClickListener { confirmCancel(b) }
            } else {
                cancel.visibility = View.GONE
            }
            return row
        }
    }
}
