package com.jdreserve.app

import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale

enum class ChatAction { OPEN_ROOMS, OPEN_MY_BOOKINGS, OPEN_REVIEWS, OPEN_MAPS, OPEN_ALERTS }

data class ChatMessage(
    val fromBot: Boolean,
    val text: String,
    val actionLabel: String? = null,
    val action: ChatAction? = null
)

data class ConciergeContext(
    val email: String,
    val name: String,
    val checkIn: LocalDate,
    val checkOut: LocalDate,
    val guests: Int
)

/**
 * Rule-based concierge. It answers ONLY from data the app really has (rooms, rates, availability,
 * the guest's own bookings, reviews). For anything else it says so and points to the front desk
 * instead of guessing. It is a keyword matcher, not an AI model; reply() is the single place to
 * swap in a real AI backend later.
 */
object Concierge {

    /** The conversation survives screen rotation; Session.clear() resets it on logout. */
    val transcript = mutableListOf<ChatMessage>()

    val quickReplies = listOf(
        "Availability", "Prices", "Recommend a room", "Check-in time",
        "Directions", "My bookings", "Green Stay", "Reviews"
    )

    fun reset() {
        transcript.clear()
    }

    fun greeting(name: String): ChatMessage = ChatMessage(
        true,
        "Hi $name! I'm the J & D Beach House concierge. Ask me about room availability, prices, " +
            "check-in, directions, your bookings or reviews. Try: \"Is anything free Dec 20-23 for 4 guests?\""
    )

    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private const val MONTH_PATTERN = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec"
    private val RANGE_SAME_MONTH =
        Regex("""\b($MONTH_PATTERN)[a-z]*\.?\s+(\d{1,2})\s*(?:-|to|until|till)\s*(\d{1,2})\b""")
    private val MONTH_DAY = Regex("""\b($MONTH_PATTERN)[a-z]*\.?\s+(\d{1,2})\b""")
    private val GUEST_COUNT = Regex("""\b(\d{1,2})\s*(?:guests?|people|persons?|pax|adults?)\b""")
    private val REFERENCE = Regex("""\bjd-\d{4}-\d{4}\b""")
    private val ROOM_NUMBER = Regex("""\broom\s*#?\s*(\d{2})\b""")

    fun reply(input: String, ctx: ConciergeContext, today: LocalDate = LocalDate.now()): ChatMessage {
        val text = input.trim()
        if (text.isEmpty()) return bot("Type a question and I'll do my best. Try \"prices\" or \"what's free this weekend?\"")
        val q = text.lowercase(Locale.ROOT)

        // Dates / guest count the guest mentioned, otherwise what they last searched.
        val parsed = parseDates(q, today)
        val checkIn = parsed?.first ?: ctx.checkIn
        val checkOut = parsed?.second ?: ctx.checkOut
        val guests = GUEST_COUNT.find(q)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 20) ?: ctx.guests

        REFERENCE.find(q)?.let { return statusOf(it.value.uppercase(Locale.ROOT), ctx) }

        if (hasAny(q, "thank", "salamat")) return bot("You're welcome! Anything else I can help with?")
        if (hasAny(q, "bye", "goodbye")) return bot("Enjoy your stay at Patar Beach! See you soon.")
        if (isGreeting(q)) return bot("Hello ${ctx.name}! How can I help with your stay?")

        if (hasAny(q, "review", "rating", "stars", "feedback")) return reviewsReply()
        if (hasAny(q, "my booking", "my reservation", "my request", "my stay", "booking status", "wallet")) return myBookings(ctx)
        if (hasAny(q, "cancel", "refund")) {
            return bot(
                "You can cancel a pending or confirmed booking yourself in My Bookings. " +
                    "I can't see the resort's refund or deposit terms, so the front desk will advise on those.",
                "Open My Bookings", ChatAction.OPEN_MY_BOOKINGS
            )
        }
        if (hasAny(q, "notification", "alert", "notify")) {
            return bot(
                "You get a notification when your request is received, confirmed, declined or cancelled. " +
                    "They are also kept in your Alerts inbox.",
                "Open Alerts", ChatAction.OPEN_ALERTS
            )
        }

        ROOM_NUMBER.find(q)?.groupValues?.get(1)?.toIntOrNull()?.let { id ->
            Repository.roomById(id)?.let { return roomDetails(it, checkIn, checkOut) }
            return bot("I don't have a Room $id. Our rooms are ${Repository.rooms.joinToString(", ") { it.id.toString() }}.")
        }
        Repository.rooms.firstOrNull { nameMentioned(q, it) }?.let { return roomDetails(it, checkIn, checkOut) }

        if (hasAny(q, "recommend", "suggest", "which room", "best room", "what room")) return recommend(checkIn, checkOut, guests, today)
        if (hasAny(q, "available", "availability", "vacan", "free", "open room", "any room", "what rooms")) {
            return availability(checkIn, checkOut, guests, today)
        }
        if (hasAny(q, "how do i book", "how to book", "how can i book", "reserve", "book a room", "make a booking")) {
            return bot(
                "1. Pick your dates, guests and room type on the home screen.\n" +
                    "2. Tap \"View & book\" on an available room and fill in your details.\n" +
                    "3. The front desk verifies your request, usually within 2 hours.\n" +
                    "4. Once confirmed, pay the 50% deposit via the GCash link we email you.",
                "Find a room", ChatAction.OPEN_ROOMS
            )
        }
        if (hasAny(q, "price", "rate", "cost", "how much", "cheap", "magkano", "per night", "vat", "peso")) return prices()
        if (hasAny(q, "deposit", "gcash", "payment", "pay ")) {
            return bot(
                "After the front desk approves your request you pay a 50% deposit through the GCash link we email you. " +
                    "Prices shown include 12% VAT in the total, and the deposit is rounded up to the next peso."
            )
        }
        if (hasAny(q, "check in", "check-in", "checkin", "check out", "check-out", "checkout", "arrival", "what time")) {
            return bot("Check-in is from 2:00 PM and check-out is by 12:00 NN. Same-day turnover is allowed, so a room can be free on the day another guest leaves.")
        }
        if (hasAny(q, "where", "direction", "location", "address", "map", "how to get", "patar")) {
            return bot("We're at Patar Beach, Bolinao, Pangasinan.", "Open in Maps", ChatAction.OPEN_MAPS)
        }
        if (hasAny(q, "green", "eco", "pledge", "sustain", "points", "plastic", "environment")) {
            return bot(
                "The Green Stay Pledge lets you promise to reuse linens, bring a refillable bottle, save energy and join a beach clean-up. " +
                    "When you book, the app estimates the water, plastic and CO2 you'll save and the eco points you earn. " +
                    "Those estimates use classroom assumptions, not audited figures."
            )
        }
        perkMatch(q)?.let { return it }
        if (hasAny(q, "pet", "dog", "cat", "parking", "restaurant", "food", "meal", "swim", "tour", "airport", "transport", "van", "contact", "phone", "call", "karaoke", "wifi speed")) {
            return bot("I don't have verified information on that in the app yet. Send a booking request and the front desk can confirm it for you.")
        }
        if (hasAny(q, "help", "what can you", "menu", "options")) {
            return bot(
                "I can help with:\n• Availability for your dates\n• Prices and room details\n• A room recommendation for your group size\n" +
                    "• Check-in and check-out times\n• Directions\n• Your bookings and their status (try your reference, e.g. JD-1220-0087)\n• Green Stay and reviews"
            )
        }
        return bot("I'm not sure about that one. I can help with availability, prices, recommendations, check-in times, directions, your bookings and reviews. Say \"help\" for the full list.")
    }

    // ---------------------------------------------------------------- intents

    private fun availability(checkIn: LocalDate, checkOut: LocalDate, guests: Int, today: LocalDate): ChatMessage {
        dateProblem(checkIn, checkOut, today)?.let { return bot(it) }
        val all = Repository.searchRooms(checkIn, checkOut, guests, null)
        val free = all.filter { it.free }
        val header = "${DateFormats.short(checkIn)} - ${DateFormats.short(checkOut)} (${Pricing.nights(checkIn, checkOut)} night(s), $guests guest(s))"
        if (free.isEmpty()) {
            return bot("Sorry, nothing is free for $header. Try other dates or fewer guests.", "Search rooms", ChatAction.OPEN_ROOMS)
        }
        val lines = free.joinToString("\n") {
            "• Room ${it.room.id} ${it.room.name}: ${Pricing.peso(it.room.ratePerNight)}/night"
        }
        val taken = all.filter { !it.free }
        val tail = if (taken.isEmpty()) "" else "\nNot available: " + taken.joinToString(", ") { "Room ${it.room.id}" }
        return bot("Free for $header:\n$lines$tail", "Book now", ChatAction.OPEN_ROOMS)
    }

    private fun recommend(checkIn: LocalDate, checkOut: LocalDate, guests: Int, today: LocalDate): ChatMessage {
        dateProblem(checkIn, checkOut, today)?.let { return bot(it) }
        val free = Repository.searchRooms(checkIn, checkOut, guests, null).filter { it.free }
        if (free.isEmpty()) return bot("No room fits $guests guest(s) for those dates. Try other dates.", "Search rooms", ChatAction.OPEN_ROOMS)
        // searchRooms is sorted by price, so the first fit is the cheapest. Prefer the best-rated of the cheapest two.
        val pick = free.take(2).maxByOrNull { Repository.ratingFor(it.room.id).average }!!.room
        val others = free.map { it.room }.filter { it.id != pick.id }.take(2)
        val nights = Pricing.nights(checkIn, checkOut)
        return bot(
            "For $guests guest(s), ${DateFormats.short(checkIn)} - ${DateFormats.short(checkOut)}, I'd suggest Room ${pick.id} ${pick.name} " +
                "(${pick.beds}, ${pick.perks}). ${Pricing.peso(Pricing.total(pick, nights))} total with VAT, ${Repository.ratingText(pick.id)}." +
                if (others.isEmpty()) "" else "\nAlso free: " + others.joinToString(", ") { "Room ${it.id} ${it.name}" },
            "Book now", ChatAction.OPEN_ROOMS
        )
    }

    private fun prices(): ChatMessage {
        val lines = Repository.rooms.sortedBy { it.ratePerNight }.joinToString("\n") {
            "• Room ${it.id} ${it.name} (up to ${it.capacity}): ${Pricing.peso(it.ratePerNight)}/night"
        }
        return bot("Nightly rates:\n$lines\n12% VAT is added to the total, and a 50% deposit is due after approval.")
    }

    private fun roomDetails(room: Room, checkIn: LocalDate, checkOut: LocalDate): ChatMessage {
        val free = checkOut.isAfter(checkIn) && Repository.isRoomFree(room.id, checkIn, checkOut)
        val avail = if (!checkOut.isAfter(checkIn)) "" else
            "\n" + if (free) "Free ${DateFormats.short(checkIn)} - ${DateFormats.short(checkOut)}." else "Not available ${DateFormats.short(checkIn)} - ${DateFormats.short(checkOut)}."
        return bot(
            "Room ${room.id} ${room.name} (${room.type.label})\nUp to ${room.capacity} guests, ${room.beds}\n${room.perks}\n" +
                "${Pricing.peso(room.ratePerNight)}/night, ${Repository.ratingText(room.id)}$avail",
            "See reviews", ChatAction.OPEN_REVIEWS
        )
    }

    private fun perkMatch(q: String): ChatMessage? {
        val perks = listOf(
            "wifi" to "wi-fi", "wi-fi" to "wi-fi", "aircon" to "aircon", "air con" to "aircon", "breakfast" to "breakfast",
            "kitchen" to "kitchenette", "sea view" to "sea view", "beachfront" to "beachfront", "patio" to "patio",
            "garden" to "garden view", "fan" to "fan"
        )
        val hit = perks.firstOrNull { q.contains(it.first) } ?: return null
        val rooms = Repository.rooms.filter { it.perks.lowercase(Locale.ROOT).contains(hit.second) }
        if (rooms.isEmpty()) return bot("None of our listed rooms mention ${hit.first}.")
        return bot("Rooms with ${hit.second}: " + rooms.joinToString(", ") { "Room ${it.id} ${it.name}" } + ".")
    }

    private fun reviewsReply(): ChatMessage {
        val r = Repository.ratingFor(null)
        if (r.count == 0) return bot("No reviews yet. Be the first after your stay!", "Open Reviews", ChatAction.OPEN_REVIEWS)
        val best = Repository.rooms.filter { Repository.ratingFor(it.id).count > 0 }.maxByOrNull { Repository.ratingFor(it.id).average }
        return bot(
            "Guests rate us ${Repository.ratingText(null)}." +
                (if (best != null) " Top rated: Room ${best.id} ${best.name} (${Repository.ratingText(best.id)})." else ""),
            "Read reviews", ChatAction.OPEN_REVIEWS
        )
    }

    private fun myBookings(ctx: ConciergeContext): ChatMessage {
        val mine = Repository.bookingsFor(ctx.email)
        if (mine.isEmpty()) return bot("You have no bookings yet. Want to find a room?", "Find a room", ChatAction.OPEN_ROOMS)
        val lines = mine.take(4).joinToString("\n") {
            "• ${it.reference}: Room ${it.roomId}, ${DateFormats.short(it.checkIn)} - ${DateFormats.short(it.checkOut)}, ${it.status.label}"
        }
        return bot("Your bookings:\n$lines", "Open My Bookings", ChatAction.OPEN_MY_BOOKINGS)
    }

    /** A guest can only look up their OWN reference. */
    private fun statusOf(reference: String, ctx: ConciergeContext): ChatMessage {
        val b = Repository.bookingByReference(reference)
        if (b == null || !b.ownerEmail.equals(ctx.email, ignoreCase = true)) {
            return bot("I can't find $reference under your account. Check the reference on your confirmation, or open My Bookings.", "Open My Bookings", ChatAction.OPEN_MY_BOOKINGS)
        }
        val nights = Pricing.nights(b.checkIn, b.checkOut)
        return bot(
            "${b.reference}: ${b.status.label}\nRoom ${b.roomId}, ${DateFormats.long(b.checkIn)} to ${DateFormats.long(b.checkOut)} ($nights night(s))\n" +
                "Total ${Pricing.peso(b.total)}, deposit ${Pricing.peso(Pricing.deposit(b.total))}",
            "Open My Bookings", ChatAction.OPEN_MY_BOOKINGS
        )
    }

    // ---------------------------------------------------------------- helpers

    private fun bot(text: String, label: String? = null, action: ChatAction? = null) = ChatMessage(true, text, label, action)

    private fun hasAny(q: String, vararg words: String): Boolean = words.any { q.contains(it) }

    private fun isGreeting(q: String): Boolean =
        q.split(Regex("[^a-z]+")).any { it in setOf("hi", "hello", "hey", "kumusta") }

    /** Full room name, or a distinctive first word. ("beach" alone is NOT used: it appears in "Patar Beach".) */
    private fun nameMentioned(q: String, room: Room): Boolean {
        val name = room.name.lowercase(Locale.ROOT)
        val first = name.substringBefore(' ')
        return q.contains(name) || (first in setOf("seaview", "barkada", "twin", "family") && q.contains(first))
    }

    private fun dateProblem(checkIn: LocalDate, checkOut: LocalDate, today: LocalDate): String? = when {
        !checkOut.isAfter(checkIn) -> "Check-out must be after check-in."
        checkIn.isBefore(today) -> "${DateFormats.short(checkIn)} has already passed. Tell me a future date."
        Pricing.nights(checkIn, checkOut) > Pricing.MAX_NIGHTS -> "The maximum stay is ${Pricing.MAX_NIGHTS} nights."
        else -> null
    }

    /** "dec 20-23", "dec 20 to 23", or "dec 20 to dec 23". Returns null when no two dates are found. */
    private fun parseDates(q: String, today: LocalDate): Pair<LocalDate, LocalDate>? {
        RANGE_SAME_MONTH.find(q)?.let { m ->
            val month = MONTHS.indexOf(m.groupValues[1]) + 1
            val from = makeDate(month, m.groupValues[2].toInt(), today)
            val to = makeDate(month, m.groupValues[3].toInt(), today)
            if (from != null && to != null) return Pair(from, to)
        }
        val found = MONTH_DAY.findAll(q).toList()
        if (found.size >= 2) {
            val a = makeDate(MONTHS.indexOf(found[0].groupValues[1]) + 1, found[0].groupValues[2].toInt(), today)
            val b = makeDate(MONTHS.indexOf(found[1].groupValues[1]) + 1, found[1].groupValues[2].toInt(), today)
            if (a != null && b != null) return Pair(a, b)
        }
        return null
    }

    /** The next occurrence of that month/day (this year, or next year if it already passed). */
    private fun makeDate(month: Int, day: Int, today: LocalDate): LocalDate? = try {
        val d = LocalDate.of(today.year, month, day)
        if (d.isBefore(today)) d.plusYears(1) else d
    } catch (e: DateTimeException) {
        null
    }
}
