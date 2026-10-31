package com.jdreserve.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.Spinner
import android.widget.TextView

/** Reviews & ratings for all rooms or one room. Guests write reviews from My Bookings (one per confirmed stay). */
class ReviewsActivity : Activity() {

    private lateinit var summary: TextView
    private lateinit var breakdown: TextView
    private lateinit var roomFilter: Spinner
    private lateinit var emptyText: TextView
    private lateinit var reviewList: ListView
    private var ready = false

    private val listener = ChangeListener { runOnUiThread { if (ready) refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Session.role == null) {
            logout()
            return
        }
        setContentView(R.layout.activity_reviews)

        summary = findViewById(R.id.ratingSummary)
        breakdown = findViewById(R.id.ratingBreakdown)
        roomFilter = findViewById(R.id.roomFilter)
        emptyText = findViewById(R.id.emptyText)
        reviewList = findViewById(R.id.reviewList)

        val options = listOf("All rooms") + Repository.rooms.map { "Room ${it.id} · ${it.name}" }
        roomFilter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        val startRoom = intent.getIntExtra(Extras.ROOM_ID, -1)
        val startIndex = Repository.rooms.indexOfFirst { it.id == startRoom }
        if (startIndex >= 0) roomFilter.setSelection(startIndex + 1)
        roomFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (ready) refresh()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val write = findViewById<Button>(R.id.writeReviewButton)
        if (Session.role == Role.GUEST) {
            write.visibility = View.VISIBLE
            write.setOnClickListener { startActivity(Intent(this, MyBookingsActivity::class.java)) }
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

    private fun selectedRoomId(): Int? {
        val pos = roomFilter.selectedItemPosition
        return if (pos <= 0) null else Repository.rooms[pos - 1].id
    }

    private fun refresh() {
        val roomId = selectedRoomId()
        val r = Repository.ratingFor(roomId)
        summary.text = Repository.ratingText(roomId)
        breakdown.text =
            if (r.count == 0) ""
            else (5 downTo 1).joinToString("   ") { star -> "$star\u2605 ${r.perStar[star - 1]}" }

        val reviews = Repository.reviewsFor(roomId)
        val rows = reviews.map {
            val room = Repository.roomById(it.roomId)
            mapOf(
                "title" to "${stars(it.rating)}  Room ${it.roomId} ${room?.name ?: ""}",
                "body" to (if (it.comment.isEmpty()) "(no comment)" else it.comment) +
                    "\n\u2014 ${it.guestName}, ${DateFormats.long(it.date)}"
            )
        }
        reviewList.adapter = SimpleAdapter(
            this, rows, android.R.layout.simple_list_item_2,
            arrayOf("title", "body"), intArrayOf(android.R.id.text1, android.R.id.text2)
        )
        emptyText.visibility = if (reviews.isEmpty()) View.VISIBLE else View.GONE
    }
}
