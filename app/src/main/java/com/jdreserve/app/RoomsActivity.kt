package com.jdreserve.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Screen G1: search by dates / guests / type, see LIVE availability, open a room to book.
 *
 * Live booking: this screen listens to the Repository, so the list updates by itself the moment a room
 * is approved, taken, released or reviewed, with no need to press "Check availability" again.
 * Also hosts the shortcuts to My bookings, Alerts, Concierge and Reviews.
 */
class RoomsActivity : Activity() {

    private var checkIn: LocalDate = Session.searchCheckIn
    private var checkOut: LocalDate = Session.searchCheckOut

    private lateinit var checkInButton: Button
    private lateinit var checkOutButton: Button
    private lateinit var guestSpinner: Spinner
    private lateinit var typeSpinner: Spinner
    private lateinit var hideBooked: Switch
    private lateinit var simulate: Switch
    private lateinit var alertsButton: Button
    private lateinit var liveText: TextView
    private lateinit var header: TextView
    private lateinit var emptyText: TextView
    private lateinit var roomList: ListView

    private var ready = false
    private val handler = Handler(Looper.getMainLooper())
    private val clock = SimpleDateFormat("h:mm:ss a", Locale.ENGLISH)

    /** Free rooms from the previous refresh with the same search, used to say what just changed. */
    private var lastKey = ""
    private var lastFree: Set<Int> = emptySet()
    private var liveEvent = ""

    private val listener = ChangeListener {
        runOnUiThread {
            if (ready) {
                search(quiet = true)
                updateAlertsButton()
            }
        }
    }

    /** Demo only: every few seconds another "guest" books a room for the searched dates. */
    private val demoTick = object : Runnable {
        override fun run() {
            if (!simulate.isChecked) return
            val booked = Repository.simulateWalkIn(checkIn, checkOut)
            if (booked == null) {
                liveEvent = "Demo paused: only one room is left for these dates."
                updateLiveText()
            }
            handler.postDelayed(this, 12_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Session.role == null) {
            logout()
            return
        }
        setContentView(R.layout.activity_rooms)

        findViewById<TextView>(R.id.welcomeText).text = "Welcome, ${Session.displayName}"
        findViewById<Button>(R.id.logoutButton).setOnClickListener { logout() }

        checkInButton = findViewById(R.id.checkInButton)
        checkOutButton = findViewById(R.id.checkOutButton)
        guestSpinner = findViewById(R.id.guestSpinner)
        typeSpinner = findViewById(R.id.typeSpinner)
        hideBooked = findViewById(R.id.hideBookedSwitch)
        simulate = findViewById(R.id.simulateSwitch)
        alertsButton = findViewById(R.id.navAlerts)
        liveText = findViewById(R.id.liveText)
        header = findViewById(R.id.resultsHeader)
        emptyText = findViewById(R.id.emptyText)
        roomList = findViewById(R.id.roomList)

        findViewById<Button>(R.id.navBookings).setOnClickListener { startActivity(Intent(this, MyBookingsActivity::class.java)) }
        alertsButton.setOnClickListener { startActivity(Intent(this, NotificationsActivity::class.java)) }
        findViewById<Button>(R.id.navConcierge).setOnClickListener { startActivity(Intent(this, ChatActivity::class.java)) }
        findViewById<Button>(R.id.navReviews).setOnClickListener { startActivity(Intent(this, ReviewsActivity::class.java)) }

        val guestOptions = (1..8).map { if (it == 1) "1 guest" else "$it guests" }
        guestSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, guestOptions)
        guestSpinner.setSelection(Session.searchGuests.coerceIn(1, 8) - 1)

        val typeOptions = listOf("All types") + RoomType.values().map { it.label }
        typeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, typeOptions)

        checkInButton.setOnClickListener {
            pickDate(checkIn) { picked ->
                checkIn = picked
                if (!checkOut.isAfter(checkIn)) checkOut = checkIn.plusDays(1)
                refreshDateButtons()
            }
        }
        checkOutButton.setOnClickListener {
            pickDate(checkOut) { picked ->
                checkOut = picked
                refreshDateButtons()
            }
        }
        findViewById<Button>(R.id.searchButton).setOnClickListener { search() }
        hideBooked.setOnCheckedChangeListener { _, _ -> search() }
        simulate.setOnCheckedChangeListener { _, on ->
            handler.removeCallbacks(demoTick)
            if (on) handler.postDelayed(demoTick, 5_000)
        }

        refreshDateButtons()
        ready = true
        search()
        Notifier.askPermissionIfNeeded(this)
    }

    override fun onResume() {
        super.onResume()
        if (!ready) return
        Repository.addListener(listener)
        search(quiet = true)
        updateAlertsButton()
        if (simulate.isChecked) handler.postDelayed(demoTick, 5_000)
    }

    override fun onPause() {
        super.onPause()
        Repository.removeListener(listener)
        handler.removeCallbacks(demoTick)
    }

    private fun updateAlertsButton() {
        val unread = Repository.unreadCount(Session.alertAudience)
        alertsButton.text = if (unread > 0) "Alerts ($unread)" else "Alerts"
    }

    private fun updateLiveText() {
        liveText.text = "\u25CF LIVE \u00B7 updated ${clock.format(Date())}" + if (liveEvent.isEmpty()) "" else "\n$liveEvent"
    }

    private fun refreshDateButtons() {
        checkInButton.text = "Check-in: ${DateFormats.long(checkIn)}"
        checkOutButton.text = "Check-out: ${DateFormats.long(checkOut)}"
    }

    private fun selectedGuests(): Int = guestSpinner.selectedItemPosition + 1

    private fun selectedType(): RoomType? {
        val pos = typeSpinner.selectedItemPosition
        return if (pos <= 0) null else RoomType.values()[pos - 1]
    }

    /** [quiet] = refresh triggered by a live update: no error toasts, keep the scroll position. */
    private fun search(quiet: Boolean = false) {
        if (!checkOut.isAfter(checkIn)) {
            if (!quiet) toast("Check-out must be after check-in.")
            return
        }
        if (checkIn.isBefore(LocalDate.now())) {
            if (!quiet) toast("Check-in cannot be in the past.")
            return
        }
        val nights = Pricing.nights(checkIn, checkOut)
        if (nights > Pricing.MAX_NIGHTS) {
            if (!quiet) toast("Maximum stay is ${Pricing.MAX_NIGHTS} nights.")
            return
        }

        Session.searchCheckIn = checkIn
        Session.searchCheckOut = checkOut
        Session.searchGuests = selectedGuests()

        val all = Repository.searchRooms(checkIn, checkOut, selectedGuests(), selectedType())
        val shown = if (hideBooked.isChecked) all.filter { it.free } else all
        val freeIds = all.filter { it.free }.map { it.room.id }.toSet()
        val freeCount = freeIds.size

        // Say what changed since the last refresh of the SAME search (live feedback).
        val key = "$checkIn|$checkOut|${selectedGuests()}|${selectedType()}"
        if (key != lastKey) {
            liveEvent = ""
        } else {
            val taken = lastFree - freeIds
            val freed = freeIds - lastFree
            if (taken.isNotEmpty()) liveEvent = "Just booked: Room ${taken.sorted().joinToString(", ")}"
            else if (freed.isNotEmpty()) liveEvent = "Now available: Room ${freed.sorted().joinToString(", ")}"
        }
        lastKey = key
        lastFree = freeIds
        updateLiveText()

        header.text = "$freeCount room(s) available \u00B7 $nights night(s) \u00B7 ${selectedGuests()} guest(s)"
        emptyText.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        val position = if (quiet) roomList.firstVisiblePosition else 0
        roomList.adapter = RoomAdapter(shown)
        roomList.setSelection(position)
    }

    private fun openRoom(room: Room) {
        val i = Intent(this, BookingActivity::class.java)
        i.putExtra(Extras.ROOM_ID, room.id)
        i.putExtra(Extras.CHECK_IN, checkIn.toEpochDay())
        i.putExtra(Extras.CHECK_OUT, checkOut.toEpochDay())
        i.putExtra(Extras.GUESTS, selectedGuests())
        startActivity(i)
    }

    private inner class RoomAdapter(private val items: List<RoomAvailability>) : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = items[position].room.id.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: layoutInflater.inflate(R.layout.item_room, parent, false)
            val item = items[position]
            val room = item.room

            row.findViewById<TextView>(R.id.roomTitle).text = "Room ${room.id} \u00B7 ${room.name} (${room.type.label})"
            row.findViewById<TextView>(R.id.roomRating).text = Repository.ratingText(room.id)
            row.findViewById<TextView>(R.id.roomStatus).text =
                if (item.free) "Available all ${Pricing.nights(checkIn, checkOut)} nights" else "Booked for your dates"
            row.findViewById<TextView>(R.id.roomMeta).text = "${room.capacity} guests \u00B7 ${room.beds}"
            row.findViewById<TextView>(R.id.roomPerks).text = room.perks
            row.findViewById<TextView>(R.id.roomPrice).text = "${Pricing.peso(room.ratePerNight)} / night"

            val button = row.findViewById<Button>(R.id.bookButton)
            button.isEnabled = item.free
            button.text = if (item.free) "View & book" else "Unavailable"
            button.setOnClickListener { openRoom(room) }
            return row
        }
    }
}
