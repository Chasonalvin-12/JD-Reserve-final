package com.jdreserve.app

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class RoomAvailability(val room: Room, val free: Boolean)

data class RoomOccupancy(val room: Room, val bookedNights: Int, val percent: Int)

data class AnalyticsResult(
    val windowStart: LocalDate,
    val windowEnd: LocalDate,          // exclusive
    val confirmedCount: Int,
    val pendingCount: Int,
    val revenue: Long,
    val averageDailyRate: Long,
    val sellableNights: Int,
    val bookedNights: Int,
    val occupancyPercent: Int,
    val perRoom: List<RoomOccupancy>,
    val eco: EcoImpact
)

/** Screens register one of these to be told the moment bookings, reviews or alerts change (live updates). */
fun interface ChangeListener {
    fun onChanged()
}

/** In-memory data store shared by every activity (resets when the app process is killed). */
object Repository {

    /** Audience key for staff-side alerts (guest alerts use the guest's lowercase email). */
    const val STAFF_AUDIENCE = "staff"

    /**
     * Lab setting: true lets a guest review any CONFIRMED booking straight away so the feature can be demoed
     * with December dates. Set to false for the real rule (review only once the stay has started).
     */
    const val DEMO_ALLOW_EARLY_REVIEWS = true
    const val MAX_REVIEW_CHARS = 300

    val rooms: List<Room> = listOf(
        Room(21, "Seaview Deluxe", RoomType.DELUXE, 2, "1 queen bed", "Sea view, Breakfast, Wi-Fi, Aircon", 4500),
        Room(22, "Family Suite", RoomType.SUITE, 6, "2 queen beds", "Garden view, Kitchenette, Wi-Fi, Aircon", 6800),
        Room(23, "Garden Villa", RoomType.VILLA, 4, "1 queen + 1 bunk", "Private patio, Wi-Fi, Aircon", 5200),
        Room(24, "Beach Cabin", RoomType.CABIN, 2, "1 double bed", "Beachfront, Fan, Wi-Fi", 2500),
        Room(25, "Barkada Suite", RoomType.SUITE, 8, "4 double beds", "Kitchenette, Sea view, Wi-Fi, Aircon", 7500),
        Room(26, "Twin Deluxe", RoomType.DELUXE, 2, "2 single beds", "Breakfast, Wi-Fi, Aircon", 3800)
    )

    /** Demo staff accounts (hard-coded on purpose for the lab; real apps must never do this). */
    private val staffAccounts: Map<String, Pair<String, String>> = mapOf(
        "cathy@jdreserve.ph" to Pair("staff123", "Cathy"),
        "rina@jdreserve.ph" to Pair("staff123", "Rina (Front desk)")
    )

    private val bookingList = mutableListOf<Booking>()
    val bookings: List<Booking> get() = bookingList

    /** Newest entry first. */
    val activityLog = mutableListOf<String>()

    // --- live updates, alerts and reviews (declared before init{} because seed() uses them) ---
    private val listeners = mutableListOf<ChangeListener>()

    /** Goes up by one on every change; screens can compare it to know if they are stale. */
    var version: Int = 0
        private set
    var lastChangeMillis: Long = System.currentTimeMillis()
        private set

    val notifications = mutableListOf<AppNotification>()

    /** Set by JDApp. Called for every new alert so a real system notification can be shown. */
    var notificationSink: ((AppNotification) -> Unit)? = null

    private val reviewList = mutableListOf<Review>()
    val reviews: List<Review> get() = reviewList
    private var nextNotificationId = 1
    private var nextReviewId = 1

    private var nextSequence = 87
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    init {
        seed()
        seedReviews()
    }

    // ------------------------------------------------------------ live updates

    fun addListener(l: ChangeListener) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: ChangeListener) {
        listeners.remove(l)
    }

    private fun changed() {
        version += 1
        lastChangeMillis = System.currentTimeMillis()
        for (l in listeners.toList()) l.onChanged()   // copy: a listener may unregister itself
    }

    // ----------------------------------------------------------------- alerts

    private fun pushNotification(audience: String, title: String, body: String, reference: String? = null) {
        if (audience.isBlank()) return
        val n = AppNotification(
            id = nextNotificationId, audience = audience.trim().lowercase(Locale.ROOT),
            title = title, body = body, timeLabel = LocalTime.now().format(timeFmt), reference = reference
        )
        nextNotificationId += 1
        notifications.add(0, n)
        notificationSink?.invoke(n)
    }

    private fun notifyGuest(b: Booking, title: String, body: String) =
        pushNotification(b.ownerEmail, title, body, b.reference)

    fun notificationsFor(audience: String): List<AppNotification> {
        val key = audience.trim().lowercase(Locale.ROOT)
        return notifications.filter { it.audience == key }
    }

    fun unreadCount(audience: String): Int = notificationsFor(audience).count { !it.read }

    fun markAllRead(audience: String) {
        notificationsFor(audience).forEach { it.read = true }
        changed()
    }

    fun clearNotifications(audience: String) {
        val key = audience.trim().lowercase(Locale.ROOT)
        notifications.removeAll { it.audience == key }
        changed()
    }

    // ---------------------------------------------------------------- lookups

    fun roomById(id: Int): Room? = rooms.firstOrNull { it.id == id }

    fun bookingByReference(ref: String): Booking? = bookingList.firstOrNull { it.reference == ref }

    fun pending(): List<Booking> = bookingList.filter { it.status == BookingStatus.PENDING }.reversed()

    /** Returns the staff display name when email + password match, otherwise null. */
    fun staffLogin(email: String, password: String): String? {
        val entry = staffAccounts[email.trim().lowercase(Locale.ROOT)] ?: return null
        return if (entry.first == password) entry.second else null
    }

    // ------------------------------------------------------------ availability

    /**
     * Two stays overlap only if each one starts BEFORE the other ends.
     * Ranges are half-open [checkIn, checkOut): a guest leaving on Dec 23 and another
     * arriving on Dec 23 do NOT overlap (same-day turnover is allowed).
     */
    fun overlaps(aIn: LocalDate, aOut: LocalDate, bIn: LocalDate, bOut: LocalDate): Boolean =
        aIn.isBefore(bOut) && bIn.isBefore(aOut)

    fun isRoomFree(roomId: Int, checkIn: LocalDate, checkOut: LocalDate, ignoreReference: String? = null): Boolean =
        bookingList.none {
            it.roomId == roomId &&
                it.reference != ignoreReference &&
                it.status.blocksRoom &&
                overlaps(checkIn, checkOut, it.checkIn, it.checkOut)
        }

    /** The first booking/maintenance block that clashes, used to explain WHY a room is unavailable. */
    fun firstConflict(roomId: Int, checkIn: LocalDate, checkOut: LocalDate, ignoreReference: String? = null): Booking? =
        bookingList.firstOrNull {
            it.roomId == roomId &&
                it.reference != ignoreReference &&
                it.status.blocksRoom &&
                overlaps(checkIn, checkOut, it.checkIn, it.checkOut)
        }

    fun searchRooms(checkIn: LocalDate, checkOut: LocalDate, guests: Int, type: RoomType?): List<RoomAvailability> =
        rooms
            .filter { it.capacity >= guests }
            .filter { type == null || it.type == type }
            .map { RoomAvailability(it, isRoomFree(it.id, checkIn, checkOut)) }
            .sortedBy { it.room.ratePerNight }

    // ----------------------------------------------------------- guest actions

    fun submitRequest(
        roomId: Int, name: String, phone: String, email: String, guests: Int,
        checkIn: LocalDate, checkOut: LocalDate, pledges: Pledges, ownerEmail: String = ""
    ): Booking {
        val room = roomById(roomId) ?: throw IllegalArgumentException("Unknown room $roomId")
        val nights = Pricing.nights(checkIn, checkOut)
        val ref = makeReference(checkIn, nextSequence)
        nextSequence += 1
        val booking = Booking(
            reference = ref, roomId = roomId, guestName = name, phone = phone, email = email,
            guests = guests, checkIn = checkIn, checkOut = checkOut, pledges = pledges,
            total = Pricing.total(room, nights), status = BookingStatus.PENDING,
            createdLabel = LocalTime.now().format(timeFmt), ownerEmail = ownerEmail
        )
        bookingList.add(booking)
        log("$name submitted $ref online")
        notifyGuest(
            booking, "Request received",
            "Your request $ref for Room $roomId (${DateFormats.short(checkIn)} - ${DateFormats.short(checkOut)}) was sent. " +
                "The front desk usually verifies within 2 hours."
        )
        pushNotification(
            STAFF_AUDIENCE, "New booking request",
            "$name requested Room $roomId, ${DateFormats.short(checkIn)} - ${DateFormats.short(checkOut)} ($ref).", ref
        )
        changed()
        return booking
    }

    // ----------------------------------------------------------- staff actions

    /**
     * Approval re-checks availability. Without this re-check two PENDING requests for the same
     * room and dates could both be approved (a classic race / double-booking semantic error).
     */
    fun approve(reference: String, staffName: String): ActionResult {
        val b = bookingByReference(reference) ?: return ActionResult(false, "Request not found.")
        if (b.status != BookingStatus.PENDING) return ActionResult(false, "$reference is already ${b.status.name.lowercase(Locale.ROOT)}.")
        val clash = firstConflict(b.roomId, b.checkIn, b.checkOut, ignoreReference = reference)
        if (clash != null) {
            return ActionResult(
                false,
                "Cannot approve: Room ${b.roomId} is already ${if (clash.status == BookingStatus.MAINTENANCE) "under maintenance" else "reserved (${clash.reference})"} " +
                    "for ${DateFormats.short(clash.checkIn)} - ${DateFormats.short(clash.checkOut)}."
            )
        }
        b.status = BookingStatus.CONFIRMED
        log("$staffName approved $reference and sent the confirmation")
        notifyGuest(
            b, "Booking confirmed",
            "Room ${b.roomId} is yours for ${DateFormats.short(b.checkIn)} - ${DateFormats.short(b.checkOut)}. " +
                "Pay the 50% deposit of ${Pricing.peso(Pricing.deposit(b.total))} via the GCash link we emailed you."
        )
        changed()
        return ActionResult(true, "$reference approved. Room ${b.roomId} is now reserved.")
    }

    fun reject(reference: String, staffName: String): ActionResult {
        val b = bookingByReference(reference) ?: return ActionResult(false, "Request not found.")
        if (b.status != BookingStatus.PENDING) return ActionResult(false, "$reference is already ${b.status.name.lowercase(Locale.ROOT)}.")
        b.status = BookingStatus.REJECTED
        log("$staffName rejected $reference")
        notifyGuest(
            b, "Request declined",
            "Sorry, we could not confirm $reference for Room ${b.roomId}. Please try other dates or another room."
        )
        changed()
        return ActionResult(true, "$reference rejected.")
    }

    // ------------------------------------------------------ guest: My Bookings

    /** Every request the guest made (not maintenance blocks), newest stay first. */
    fun bookingsFor(email: String): List<Booking> =
        if (email.isBlank()) emptyList()
        else bookingList
            .filter { it.status != BookingStatus.MAINTENANCE && it.ownerEmail.equals(email, ignoreCase = true) }
            .sortedByDescending { it.checkIn }

    /** A guest may cancel their own PENDING or CONFIRMED booking while the stay has not ended. */
    fun cancel(reference: String, ownerEmail: String): ActionResult {
        val b = bookingByReference(reference) ?: return ActionResult(false, "Booking not found.")
        if (!b.ownerEmail.equals(ownerEmail, ignoreCase = true)) return ActionResult(false, "That booking belongs to another account.")
        if (b.status != BookingStatus.PENDING && b.status != BookingStatus.CONFIRMED) {
            return ActionResult(false, "$reference is already ${b.status.label.lowercase(Locale.ROOT)}.")
        }
        if (!b.checkOut.isAfter(LocalDate.now())) return ActionResult(false, "This stay has already passed, so it cannot be cancelled.")
        val wasConfirmed = b.status == BookingStatus.CONFIRMED
        b.status = BookingStatus.CANCELLED
        log("${b.guestName} cancelled $reference")
        notifyGuest(b, "Booking cancelled", "You cancelled $reference. Deposit and refund questions go to the front desk.")
        pushNotification(
            STAFF_AUDIENCE, "Booking cancelled",
            "${b.guestName} cancelled $reference (Room ${b.roomId})." + if (wasConfirmed) " The room is free again." else "", reference
        )
        changed()
        return ActionResult(true, "$reference cancelled.")
    }

    // --------------------------------------------------------- reviews & ratings

    fun hasReview(reference: String): Boolean = reviewList.any { it.bookingReference == reference }

    fun canReview(b: Booking, today: LocalDate = LocalDate.now()): Boolean =
        b.status == BookingStatus.CONFIRMED && !hasReview(b.reference) &&
            (DEMO_ALLOW_EARLY_REVIEWS || !b.checkIn.isAfter(today))

    fun addReview(reference: String, ownerEmail: String, rating: Int, comment: String): ActionResult {
        val b = bookingByReference(reference) ?: return ActionResult(false, "Booking not found.")
        if (!b.ownerEmail.equals(ownerEmail, ignoreCase = true)) return ActionResult(false, "That booking belongs to another account.")
        if (b.status != BookingStatus.CONFIRMED) return ActionResult(false, "Only confirmed stays can be reviewed.")
        if (hasReview(reference)) return ActionResult(false, "You already reviewed this stay.")
        if (!DEMO_ALLOW_EARLY_REVIEWS && b.checkIn.isAfter(LocalDate.now())) {
            return ActionResult(false, "You can review once your stay has started.")
        }
        if (rating !in 1..5) return ActionResult(false, "Please choose 1 to 5 stars.")
        val text = comment.trim()
        if (text.length > MAX_REVIEW_CHARS) return ActionResult(false, "Please keep your comment under $MAX_REVIEW_CHARS characters.")

        reviewList.add(Review(nextReviewId, b.roomId, reference, b.guestName, b.ownerEmail, rating, text, LocalDate.now()))
        nextReviewId += 1
        log("${b.guestName} reviewed Room ${b.roomId}: $rating/5")
        pushNotification(STAFF_AUDIENCE, "New review", "${b.guestName} rated Room ${b.roomId} $rating/5.", reference)
        changed()
        return ActionResult(true, "Thanks for your review!")
    }

    /** roomId == null means "all rooms". */
    fun reviewsFor(roomId: Int?): List<Review> =
        (if (roomId == null) reviewList else reviewList.filter { it.roomId == roomId })
            .sortedWith(compareByDescending<Review> { it.date }.thenByDescending { it.id })

    fun ratingFor(roomId: Int?): RatingSummary {
        val list = if (roomId == null) reviewList else reviewList.filter { it.roomId == roomId }
        val perStar = List(5) { index -> list.count { it.rating == index + 1 } }
        val average = if (list.isEmpty()) 0.0 else list.sumOf { it.rating }.toDouble() / list.size
        return RatingSummary(average, list.size, perStar)
    }

    fun ratingText(roomId: Int?): String {
        val r = ratingFor(roomId)
        return if (r.count == 0) "No reviews yet"
        else String.format(Locale.US, "\u2605 %.1f (%d review%s)", r.average, r.count, if (r.count == 1) "" else "s")
    }

    // ------------------------------------------------------ live demo helper

    /**
     * Lab demo of "live" booking: pretends another guest just booked at the front desk, so a room
     * disappears from every open Rooms/Booking screen without the user doing anything.
     * Always leaves at least one room free for the dates so the demo never empties the house.
     */
    fun simulateWalkIn(checkIn: LocalDate, checkOut: LocalDate): Booking? {
        if (!checkOut.isAfter(checkIn)) return null
        val free = rooms.filter { isRoomFree(it.id, checkIn, checkOut) }
        if (free.size <= 1) return null
        val room = free.random()
        val ref = makeReference(checkIn, nextSequence)
        nextSequence += 1
        val booking = Booking(
            reference = ref, roomId = room.id, guestName = "Walk-in guest", phone = "09170000000",
            email = "walkin@example.com", guests = 2, checkIn = checkIn, checkOut = checkOut, pledges = Pledges(),
            total = Pricing.total(room, Pricing.nights(checkIn, checkOut)), status = BookingStatus.CONFIRMED,
            createdLabel = LocalTime.now().format(timeFmt)
        )
        bookingList.add(booking)
        log("Walk-in guest booked Room ${room.id} ($ref) - live demo")
        changed()
        return booking
    }

    // --------------------------------------------------------------- analytics

    /**
     * Occupancy for the window [start, end). A stay that starts before the window or ends after it
     * is CLIPPED, so only the nights inside the window are counted.
     * Maintenance nights are removed from the denominator (they cannot be sold).
     */
    fun analytics(start: LocalDate, end: LocalDate): AnalyticsResult {
        val windowDays = Pricing.nights(start, end)
        val confirmed = bookingList.filter { it.status == BookingStatus.CONFIRMED }
        val maintenance = bookingList.filter { it.status == BookingStatus.MAINTENANCE }

        val perRoom = rooms.map { room ->
            val booked = confirmed.filter { it.roomId == room.id }.sumOf { nightsInside(it, start, end) }
            val blocked = maintenance.filter { it.roomId == room.id }.sumOf { nightsInside(it, start, end) }
            val sellable = windowDays - blocked
            RoomOccupancy(room, booked, percent(booked, sellable))
        }

        val totalBooked = perRoom.sumOf { it.bookedNights }
        val totalBlocked = maintenance.sumOf { nightsInside(it, start, end) }
        val sellableNights = rooms.size * windowDays - totalBlocked

        var rateNights = 0L
        var nightsAll = 0L
        var eco = EcoImpact.ZERO
        for (b in confirmed) {
            val n = Pricing.nights(b.checkIn, b.checkOut)
            val room = roomById(b.roomId) ?: continue
            rateNights += room.ratePerNight * n
            nightsAll += n
            eco += EcoCalculator.estimate(b.pledges, n, b.guests)
        }

        return AnalyticsResult(
            windowStart = start,
            windowEnd = end,
            confirmedCount = confirmed.size,
            pendingCount = bookingList.count { it.status == BookingStatus.PENDING },
            revenue = confirmed.sumOf { it.total },
            averageDailyRate = if (nightsAll == 0L) 0L else (rateNights + nightsAll / 2) / nightsAll,
            sellableNights = sellableNights,
            bookedNights = totalBooked,
            occupancyPercent = percent(totalBooked, sellableNights),
            perRoom = perRoom,
            eco = eco
        )
    }

    /** Integer percentage rounded to nearest; avoids the 1/3 == 0 integer-division trap. */
    private fun percent(part: Int, whole: Int): Int =
        if (whole <= 0) 0 else (part * 100 + whole / 2) / whole

    private fun nightsInside(b: Booking, start: LocalDate, end: LocalDate): Int {
        val from = if (b.checkIn.isAfter(start)) b.checkIn else start
        val to = if (b.checkOut.isBefore(end)) b.checkOut else end
        return if (to.isAfter(from)) Pricing.nights(from, to) else 0
    }

    // ----------------------------------------------------------------- helpers

    private fun makeReference(checkIn: LocalDate, seq: Int): String =
        String.format(Locale.US, "JD-%02d%02d-%04d", checkIn.monthValue, checkIn.dayOfMonth, seq)

    private fun log(text: String) {
        activityLog.add(0, "${LocalTime.now().format(timeFmt)}  $text")
    }

    private fun seedBooking(
        ref: String, roomId: Int, name: String, checkIn: LocalDate, checkOut: LocalDate,
        status: BookingStatus, guests: Int = 2, pledges: Pledges = Pledges(), created: String = "Earlier"
    ) {
        val room = roomById(roomId) ?: return
        bookingList.add(
            Booking(
                reference = ref, roomId = roomId, guestName = name, phone = "09170000000",
                email = "seed@example.com", guests = guests, checkIn = checkIn, checkOut = checkOut,
                pledges = pledges, total = Pricing.total(room, Pricing.nights(checkIn, checkOut)),
                status = status, createdLabel = created
            )
        )
    }

    private fun seedReview(roomId: Int, name: String, rating: Int, comment: String, daysAgo: Long, ref: String) {
        reviewList.add(
            Review(nextReviewId, roomId, ref, name, "", rating, comment, LocalDate.now().minusDays(daysAgo))
        )
        nextReviewId += 1
    }

    /** Sample reviews from earlier guests (fictional, for the lab). Their booking refs are not in the booking list. */
    private fun seedReviews() {
        seedReview(21, "Marites L.", 5, "Woke up to the sea right outside the window. Breakfast was a nice touch.", 40, "JD-0912-0044")
        seedReview(21, "Joel P.", 4, "Great view and a spotless room. Wi-Fi dropped a little at night.", 22, "JD-1001-0052")
        seedReview(22, "Reyes Family", 5, "Plenty of space for six of us, and the kitchenette saved us money.", 31, "JD-0920-0047")
        seedReview(23, "Cheska D.", 5, "The private patio was perfect for evening coffee. Very quiet.", 18, "JD-1008-0055")
        seedReview(24, "Dante R.", 4, "Steps from the sand. Fan only, but the sea breeze is plenty.", 27, "JD-0928-0049")
        seedReview(25, "Barkada Batch 12", 5, "Fit all eight of us with room to spare. The sunset from the deck was unreal.", 35, "JD-0916-0045")
        seedReview(25, "Imee C.", 4, "Great for groups. Check-in took a little longer than expected.", 12, "JD-1012-0058")
        seedReview(26, "Paolo & Gia", 3, "Comfortable and good value, but the aircon was loud.", 9, "JD-1015-0060")
    }

    private fun seed() {
        fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)
        val all = Pledges(true, true, true, true)

        // Blocks that shape the demo
        seedBooking("MAINT-23", 23, "Maintenance", d(12, 23), d(12, 24), BookingStatus.MAINTENANCE)
        // Lea checks out on Dec 20 and the Dec 20-23 search checks in on Dec 20: same-day turnover, no clash
        seedBooking("JD-1219-0083", 23, "Lea Mendoza", d(12, 19), d(12, 20), BookingStatus.CONFIRMED, guests = 3, pledges = Pledges(reuseLinen = true))
        seedBooking("JD-1219-0082", 24, "Jose Dizon", d(12, 19), d(12, 22), BookingStatus.CONFIRMED)
        seedBooking("JD-1224-0081", 21, "Ana Cruz", d(12, 24), d(12, 28), BookingStatus.CONFIRMED, pledges = Pledges(reuseLinen = true, refillableBottle = true))
        seedBooking("JD-1226-0080", 22, "Santos Family", d(12, 26), d(12, 30), BookingStatus.CONFIRMED, guests = 5, pledges = all)
        seedBooking("JD-1229-0079", 25, "Barkada Group", d(12, 29), d(1, 2, 2027), BookingStatus.CONFIRMED, guests = 8, pledges = Pledges(refillableBottle = true, coastalCleanup = true))
        seedBooking("JD-1210-0078", 26, "Paolo Reyes", d(12, 10), d(12, 13), BookingStatus.CONFIRMED, pledges = Pledges(energySaver = true))

        // More confirmed stays across December so the analytics screen has realistic numbers
        seedBooking("JD-1201-0077", 21, "Miguel Tan", d(12, 1), d(12, 6), BookingStatus.CONFIRMED)
        seedBooking("JD-1205-0076", 22, "Cabrera Family", d(12, 5), d(12, 12), BookingStatus.CONFIRMED, guests = 4, pledges = Pledges(reuseLinen = true, refillableBottle = true))
        seedBooking("JD-1205-0075", 23, "Grace Flores", d(12, 5), d(12, 10), BookingStatus.CONFIRMED, guests = 3, pledges = Pledges(energySaver = true))
        seedBooking("JD-1212-0074", 25, "Office Outing", d(12, 12), d(12, 19), BookingStatus.CONFIRMED, guests = 6, pledges = Pledges(refillableBottle = true, coastalCleanup = true))
        seedBooking("JD-1215-0073", 26, "Carlo Bautista", d(12, 15), d(12, 20), BookingStatus.CONFIRMED)
        seedBooking("JD-1223-0072", 24, "Ellen Ramos", d(12, 23), d(12, 31), BookingStatus.CONFIRMED, pledges = all)

        // Pending queue
        seedBooking("JD-0102-0084", 25, "Bea Villanueva", d(1, 2, 2027), d(1, 4, 2027), BookingStatus.PENDING, guests = 6, created = "3 hrs ago")
        seedBooking("JD-1221-0086", 26, "Marco Santos", d(12, 21), d(12, 23), BookingStatus.PENDING, created = "1 hr ago")

        activityLog.add("9:05 AM  Rina (Front desk) set Room 23 to maintenance (Dec 23 - 24)")
        activityLog.add(0, "11:40 AM  Cathy approved JD-1219-0083 (Garden Villa)")
    }
}
