package com.jdreserve.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ListView
import android.widget.SimpleAdapter
import android.widget.TextView

/** In-app inbox for booking alerts. Guests see their own; staff see staff alerts (new requests, cancellations, reviews). */
class NotificationsActivity : Activity() {

    private lateinit var alertList: ListView
    private lateinit var emptyText: TextView
    private var ready = false
    private var shown: List<AppNotification> = emptyList()

    private val listener = ChangeListener { runOnUiThread { if (ready) refresh() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Session.role == null) {
            logout()
            return
        }
        setContentView(R.layout.activity_notifications)
        alertList = findViewById(R.id.alertList)
        emptyText = findViewById(R.id.emptyText)

        findViewById<Button>(R.id.markReadButton).setOnClickListener { Repository.markAllRead(Session.alertAudience) }
        findViewById<Button>(R.id.clearButton).setOnClickListener { Repository.clearNotifications(Session.alertAudience) }
        alertList.setOnItemClickListener { _, _, position, _ ->
            val n = shown.getOrNull(position) ?: return@setOnItemClickListener
            if (Session.role == Role.GUEST && n.reference != null) {
                startActivity(Intent(this, MyBookingsActivity::class.java))
            }
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
        if (!ready) return
        Repository.removeListener(listener)
        Repository.markAllRead(Session.alertAudience)   // everything shown has now been seen
    }

    private fun refresh() {
        shown = Repository.notificationsFor(Session.alertAudience)
        val rows = shown.map {
            mapOf(
                "title" to (if (it.read) "" else "\u25CF ") + it.title + "  ·  " + it.timeLabel,
                "body" to it.body
            )
        }
        alertList.adapter = SimpleAdapter(
            this, rows, android.R.layout.simple_list_item_2,
            arrayOf("title", "body"), intArrayOf(android.R.id.text1, android.R.id.text2)
        )
        emptyText.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }
}
