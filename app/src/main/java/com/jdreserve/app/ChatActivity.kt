package com.jdreserve.app

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView

/** Concierge chat. The answers come from Concierge.reply(), which only uses real app data. */
class ChatActivity : Activity() {

    private lateinit var chatList: ListView
    private lateinit var input: EditText
    private lateinit var adapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireGuest()) return
        setContentView(R.layout.activity_chat)
        title = "Concierge"

        chatList = findViewById(R.id.chatList)
        input = findViewById(R.id.chatInput)

        if (Concierge.transcript.isEmpty()) Concierge.transcript.add(Concierge.greeting(Session.displayName))
        adapter = ChatAdapter()
        chatList.adapter = adapter

        val row = findViewById<LinearLayout>(R.id.quickReplyRow)
        for (label in Concierge.quickReplies) {
            val chip = Button(this, null, android.R.attr.buttonBarButtonStyle)
            chip.text = label
            chip.textSize = 12f
            chip.setOnClickListener { send(label) }
            row.addView(chip)
        }

        findViewById<Button>(R.id.sendButton).setOnClickListener { send(input.text.toString()) }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send(input.text.toString())
                true
            } else {
                false
            }
        }
    }

    private fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        Concierge.transcript.add(ChatMessage(false, trimmed))
        val context = ConciergeContext(
            Session.email, Session.displayName, Session.searchCheckIn, Session.searchCheckOut, Session.searchGuests
        )
        Concierge.transcript.add(Concierge.reply(trimmed, context))
        input.setText("")
        adapter.notifyDataSetChanged()
        chatList.setSelection(adapter.count - 1)
    }

    private fun perform(action: ChatAction) {
        when (action) {
            ChatAction.OPEN_ROOMS -> openRooms()
            ChatAction.OPEN_MY_BOOKINGS -> startActivity(Intent(this, MyBookingsActivity::class.java))
            ChatAction.OPEN_REVIEWS -> startActivity(Intent(this, ReviewsActivity::class.java))
            ChatAction.OPEN_ALERTS -> startActivity(Intent(this, NotificationsActivity::class.java))
            ChatAction.OPEN_MAPS -> openMaps()
        }
    }

    private inner class ChatAdapter : BaseAdapter() {
        override fun getCount(): Int = Concierge.transcript.size
        override fun getItem(position: Int): Any = Concierge.transcript[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = convertView ?: layoutInflater.inflate(R.layout.item_chat, parent, false)
            val m = Concierge.transcript[position]
            val side = if (m.fromBot) Gravity.START else Gravity.END

            val bubble = row.findViewById<TextView>(R.id.bubbleText)
            bubble.text = m.text
            bubble.setTextColor(0xFF000000.toInt())
            val bg = GradientDrawable()
            bg.cornerRadius = 24f
            bg.setColor(if (m.fromBot) 0xFFE3F2FD.toInt() else 0xFFC8E6C9.toInt())
            bubble.background = bg
            (bubble.layoutParams as LinearLayout.LayoutParams).gravity = side

            val button = row.findViewById<Button>(R.id.bubbleAction)
            if (m.actionLabel != null && m.action != null) {
                button.visibility = View.VISIBLE
                button.text = m.actionLabel
                (button.layoutParams as LinearLayout.LayoutParams).gravity = side
                val action = m.action
                button.setOnClickListener { perform(action) }
            } else {
                button.visibility = View.GONE
            }
            return row
        }
    }
}
