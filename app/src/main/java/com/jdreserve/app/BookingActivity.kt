package com.jdreserve.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import java.time.LocalDate

/**
 * Screens G2 + G3: room details and booking form.
 *
 * Feature 1 - Smart Booking Guard (SDG 8 / 9): live availability, transparent pricing, full validation.
 * Feature 2 - Green Stay Pledge (SDG 12 / 13 / 14): live environmental impact estimate.
 */
class BookingActivity : Activity() {

    private lateinit var room: Room
    private var checkIn: LocalDate = LocalDate.now()
    private var checkOut: LocalDate = LocalDate.now().plusDays(1)

    private lateinit var checkInButton: Button
    private lateinit var checkOutButton: Button
    private lateinit var guestSpinner: Spinner
    private lateinit var availabilityText: TextView
    private lateinit var summaryText: TextView
    private lateinit var ecoText: TextView
    private lateinit var nameInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var pledgeLinen: CheckBox
    private lateinit var pledgeBottle: CheckBox
    private lateinit var pledgeEnergy: CheckBox
    private lateinit var pledgeCleanup: CheckBox
    private lateinit var submitButton: Button
    private lateinit var ratingText: TextView
    private var ready = false

    /** Live booking: if the room is taken or released while this screen is open, availability updates at once. */
    private val listener = ChangeListener { runOnUiThread { if (ready) refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Session.role == null) {
            logout()
            return
        }
        val found = Repository.roomById(intent.getIntExtra(Extras.ROOM_ID, -1))
        if (found == null) {
            toast("Room not found.")
            finish()
            return
        }
        room = found
        checkIn = LocalDate.ofEpochDay(intent.getLongExtra(Extras.CHECK_IN, checkIn.toEpochDay()))
        checkOut = LocalDate.ofEpochDay(intent.getLongExtra(Extras.CHECK_OUT, checkOut.toEpochDay()))
        val requestedGuests = intent.getIntExtra(Extras.GUESTS, 1)

        setContentView(R.layout.activity_booking)

        findViewById<TextView>(R.id.roomTitle).text = "Room ${room.id} · ${room.name}"
        findViewById<TextView>(R.id.roomInfo).text =
            "${room.type.label} · up to ${room.capacity} guests · ${room.beds}\n${room.perks}\n${Pricing.peso(room.ratePerNight)} / night"

        checkInButton = findViewById(R.id.checkInButton)
        checkOutButton = findViewById(R.id.checkOutButton)
        guestSpinner = findViewById(R.id.guestSpinner)
        availabilityText = findViewById(R.id.availabilityText)
        summaryText = findViewById(R.id.summaryText)
        ecoText = findViewById(R.id.ecoText)
        nameInput = findViewById(R.id.nameInput)
        phoneInput = findViewById(R.id.phoneInput)
        emailInput = findViewById(R.id.emailInput)
        pledgeLinen = findViewById(R.id.pledgeLinen)
        pledgeBottle = findViewById(R.id.pledgeBottle)
        pledgeEnergy = findViewById(R.id.pledgeEnergy)
        pledgeCleanup = findViewById(R.id.pledgeCleanup)
        submitButton = findViewById(R.id.submitButton)
        ratingText = findViewById(R.id.ratingText)
        findViewById<Button>(R.id.seeReviewsButton).setOnClickListener {
            val i = Intent(this, ReviewsActivity::class.java)
            i.putExtra(Extras.ROOM_ID, room.id)
            startActivity(i)
        }

        emailInput.setText(Session.email)
        nameInput.setText(Session.displayName)

        val guestOptions = (1..room.capacity).map { if (it == 1) "1 guest" else "$it guests" }
        guestSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, guestOptions)
        guestSpinner.setSelection(minOf(requestedGuests, room.capacity) - 1)

        checkInButton.setOnClickListener {
            pickDate(checkIn) { picked ->
                checkIn = picked
                if (!checkOut.isAfter(checkIn)) checkOut = checkIn.plusDays(1)
                refresh()
            }
        }
        checkOutButton.setOnClickListener {
            pickDate(checkOut) { picked ->
                checkOut = picked
                refresh()
            }
        }

        val onPledgeChanged = { _: android.widget.CompoundButton, _: Boolean -> refresh() }
        pledgeLinen.setOnCheckedChangeListener(onPledgeChanged)
        pledgeBottle.setOnCheckedChangeListener(onPledgeChanged)
        pledgeEnergy.setOnCheckedChangeListener(onPledgeChanged)
        pledgeCleanup.setOnCheckedChangeListener(onPledgeChanged)

        guestSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                refresh()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        submitButton.setOnClickListener { submit() }
        ready = true
        refresh()
    }

    override fun onResume() {
        super.onResume()
        if (ready) {
            Repository.addListener(listener)
            refresh()
        }
    }

    override fun onPause() {
        super.onPause()
        Repository.removeListener(listener)
    }

    private fun guests(): Int = guestSpinner.selectedItemPosition + 1

    private fun pledges() = Pledges(
        reuseLinen = pledgeLinen.isChecked,
        refillableBottle = pledgeBottle.isChecked,
        energySaver = pledgeEnergy.isChecked,
        coastalCleanup = pledgeCleanup.isChecked
    )

    /** Recomputes availability, price and eco impact whenever anything changes. */
    private fun refresh() {
        checkInButton.text = "Check-in: ${DateFormats.long(checkIn)}"
        checkOutButton.text = "Check-out: ${DateFormats.long(checkOut)}"

        ratingText.text = Repository.ratingText(room.id)

        val nights = Pricing.nights(checkIn, checkOut)
        if (nights <= 0) {
            availabilityText.text = "Check-out must be after check-in."
            summaryText.text = "-"
            ecoText.text = ""
            submitButton.isEnabled = false
            return
        }

        val clash = Repository.firstConflict(room.id, checkIn, checkOut)
        submitButton.isEnabled = clash == null
        availabilityText.text =
            if (clash == null) "\u25CF Live: available for your dates"
            else "\u25CF Live: not available: ${if (clash.status == BookingStatus.MAINTENANCE) "under maintenance" else "already reserved"} " +
                "${DateFormats.short(clash.checkIn)} - ${DateFormats.short(clash.checkOut)}"

        val sub = Pricing.subtotal(room, nights)
        val vat = Pricing.vat(sub)
        val total = sub + vat
        summaryText.text =
            "${Pricing.peso(room.ratePerNight)} x $nights night(s) = ${Pricing.peso(sub)}\n" +
                "VAT (${Pricing.VAT_PERCENT}%) = ${Pricing.peso(vat)}\n" +
                "Total = ${Pricing.peso(total)}\n" +
                "50% deposit after approval = ${Pricing.peso(Pricing.deposit(total))}"

        val eco = EcoCalculator.estimate(pledges(), nights, guests())
        ecoText.text = if (pledges().count() == 0) "Tick a pledge to see your impact." else "Your estimated impact:\n${eco.describe()}"
    }

    private fun submit() {
        nameInput.error = null
        phoneInput.error = null
        emailInput.error = null

        val name = nameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val nights = Pricing.nights(checkIn, checkOut)

        if (nights <= 0) {
            toast("Check-out must be after check-in.")
            return
        }
        if (checkIn.isBefore(LocalDate.now())) {
            toast("Check-in cannot be in the past.")
            return
        }
        if (nights > Pricing.MAX_NIGHTS) {
            toast("Maximum stay is ${Pricing.MAX_NIGHTS} nights.")
            return
        }
        if (name.length < 2) {
            nameInput.error = "Enter your full name"
            nameInput.requestFocus()
            return
        }
        if (!Validators.isPhMobile(phone)) {
            phoneInput.error = "Use 09xxxxxxxxx or +639xxxxxxxxx"
            phoneInput.requestFocus()
            return
        }
        if (!Validators.isEmail(email)) {
            emailInput.error = "Enter a valid email"
            emailInput.requestFocus()
            return
        }
        if (!Repository.isRoomFree(room.id, checkIn, checkOut)) {
            toast("Sorry, this room is no longer available for those dates.")
            refresh()
            return
        }

        val booking = Repository.submitRequest(room.id, name, phone, email, guests(), checkIn, checkOut, pledges(), Session.email)
        val i = Intent(this, BookingSentActivity::class.java)
        i.putExtra(Extras.REFERENCE, booking.reference)
        startActivity(i)
        finish()
    }
}
