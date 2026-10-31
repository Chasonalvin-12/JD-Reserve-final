package com.jdreserve.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

/** Screen G4: request sent, reference number, what happens next. */
class BookingSentActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val booking = Repository.bookingByReference(intent.getStringExtra(Extras.REFERENCE) ?: "")
        val room = booking?.let { Repository.roomById(it.roomId) }
        if (booking == null || room == null) {
            toast("Reservation not found.")
            finish()
            return
        }
        setContentView(R.layout.activity_booking_sent)

        val nights = Pricing.nights(booking.checkIn, booking.checkOut)
        findViewById<TextView>(R.id.sentTitle).text = "Request sent, ${booking.guestName.substringBefore(' ')}!"
        findViewById<TextView>(R.id.referenceText).text = "REFERENCE ${booking.reference}\nStatus: Awaiting verification"
        findViewById<TextView>(R.id.stepsText).text =
            "1. Request submitted: ${booking.createdLabel}\n" +
                "2. Front desk verification: usually within 2 hours\n" +
                "3. Pay 50% deposit: ${Pricing.peso(Pricing.deposit(booking.total))} via the GCash link we email you\n" +
                "4. Booking confirmed"
        findViewById<TextView>(R.id.detailsText).text =
            "${room.name} · Room ${room.id}\n" +
                "Check-in: ${DateFormats.long(booking.checkIn)} (from 2:00 PM)\n" +
                "Check-out: ${DateFormats.long(booking.checkOut)} (by 12:00 NN)\n" +
                "Guests: ${booking.guests} · $nights night(s)\n" +
                "Total: ${Pricing.peso(booking.total)}\n" +
                (if (booking.pledges.count() > 0)
                    "\nGreen Stay pledges: ${booking.pledges.count()}\n" +
                        EcoCalculator.estimate(booking.pledges, nights, booking.guests).describe()
                else "")

        findViewById<Button>(R.id.directionsButton).setOnClickListener {
            val uri = Uri.parse("geo:0,0?q=" + Uri.encode("Patar Beach, Bolinao, Pangasinan"))
            try {
                startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: ActivityNotFoundException) {
                toast("No maps app found on this device.")
            }
        }
        findViewById<Button>(R.id.homeButton).setOnClickListener {
            val i = Intent(this, RoomsActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(i)
            finish()
        }
    }
}
