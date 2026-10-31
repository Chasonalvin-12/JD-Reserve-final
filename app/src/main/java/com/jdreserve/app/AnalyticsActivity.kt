package com.jdreserve.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.time.LocalDate

/** Screen S4: revenue, occupancy and the sustainability impact of Green Stay pledges. */
class AnalyticsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Session.role != Role.STAFF) {
            toast("Staff access only.")
            finish()
            return
        }
        setContentView(R.layout.activity_analytics)

        // December 2026 = [Dec 1, Jan 1) - the end date is exclusive
        val start = LocalDate.of(2026, 12, 1)
        val end = LocalDate.of(2027, 1, 1)
        val r = Repository.analytics(start, end)

        findViewById<TextView>(R.id.analyticsTitle).text = "Analytics · December 2026"
        findViewById<TextView>(R.id.kpiText).text =
            "Confirmed bookings: ${r.confirmedCount} (pending: ${r.pendingCount})\n" +
                "Total revenue: ${Pricing.peso(r.revenue)}\n" +
                "Average daily rate: ${Pricing.peso(r.averageDailyRate)}\n" +
                "Occupancy: ${r.occupancyPercent}% (${r.bookedNights} of ${r.sellableNights} sellable nights)\n" +
                "Guest rating: ${Repository.ratingText(null)}"

        val container = findViewById<LinearLayout>(R.id.occupancyList)
        for (o in r.perRoom) {
            val label = TextView(this)
            label.text = "#${o.room.id} ${o.room.name}: ${o.percent}% (${o.bookedNights} nights)"
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
            bar.max = 100
            bar.progress = o.percent
            container.addView(label)
            container.addView(bar)
        }

        findViewById<TextView>(R.id.ecoTotalsText).text = r.eco.describe()

        findViewById<Button>(R.id.shareButton).setOnClickListener {
            val report = buildString {
                append("J & D Reserve - December 2026\n")
                append("Confirmed bookings: ${r.confirmedCount}\n")
                append("Revenue: ${Pricing.peso(r.revenue)}\n")
                append("ADR: ${Pricing.peso(r.averageDailyRate)}\n")
                append("Occupancy: ${r.occupancyPercent}%\n")
                append("Guest rating: ${Repository.ratingText(null)}\n\n")
                append("Green Stay impact\n${r.eco.describe()}")
            }
            val send = Intent(Intent.ACTION_SEND)
            send.type = "text/plain"
            send.putExtra(Intent.EXTRA_SUBJECT, "J & D Reserve report")
            send.putExtra(Intent.EXTRA_TEXT, report)
            startActivity(Intent.createChooser(send, "Export report"))
        }
    }
}
