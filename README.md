**[Watch the Demonstration Video](https://drive.google.com/file/d/1yEmzEUHiksLkIK8n5qJuga0qSlNEsGAH/view?usp=sharing)**

**[Watch the Demonstration Video](PASTE_YOUR_GOOGLE_DRIVE_LINK_HERE)**

# J & D Reserve - Android app (Lab Act 3)

Beach House booking app for **J & D Beach House, Patar, Bolinao**, built from the Figma prototype
(screens G1-G4, S1-S4, L1-L2). Language: **Kotlin only**. No third-party libraries.

**Team members:** ______________________  ______________________  ______________________

## Screens (Activities) and the Figma frames they implement

| Activity | Figma frames | What it does |
|---|---|---|
| `LoginActivity` | L1, L2 | One screen, two roles. Email/password validation, staff accounts checked, show-password toggle |
| `RoomsActivity` | G1 | Pick dates, guests and room type; live availability; "hide booked rooms" switch; sorted by price |
| `BookingActivity` | G2, G3 | Room details + booking form with live price, availability and Green Stay impact |
| `BookingSentActivity` | G4 | Reference number (`JD-1220-0087` style), what happens next, 50% deposit, directions |
| `StaffActivity` | S1, S2, S3 | Pending queue, review dialog, approve / reject, activity log (who did what) |
| `AnalyticsActivity` | S4 | Revenue, ADR, occupancy per room, guest rating, sustainability totals, export report |
| `MyBookingsActivity` | new | **My Bookings wallet**: every request as a pass with status, full pass dialog, cancel, review |
| `NotificationsActivity` | new | **Alerts inbox** (guest alerts, or staff alerts for new requests / cancellations / reviews) |
| `ChatActivity` | new | **Concierge chatbot**: availability, prices, recommendations, directions, your booking status |
| `ReviewsActivity` | new | **Reviews & ratings**: average, star breakdown, filter by room |

## Features and the SDGs they serve

1. **Smart Booking Guard** (`BookingActivity`, `Repository`) - **SDG 8** (decent work and economic growth,
   target 8.9 sustainable tourism that supports local business) and **SDG 9** (innovation / digital infrastructure).
   Live availability, double-booking prevention (also re-checked when staff approve), transparent pricing
   (nights x rate + 12% VAT, 50% deposit), full form validation (PH mobile format, email, dates, max stay).
2. **Green Stay Pledge + Impact Estimator** (`BookingActivity`, `EcoImpact.kt`, `AnalyticsActivity`) -
   **SDG 12** (responsible consumption), **SDG 13** (climate action), **SDG 14** (life below water - Bolinao's reefs and coast).
   Guests pledge to reuse linens, bring a refillable bottle, save energy, and join a beach clean-up.
   The app shows water saved, plastic bottles avoided, CO2 avoided and eco points, and the resort sees the
   totals for all confirmed bookings in Analytics.
3. **Verified Requests with Audit Trail** (`StaffActivity`) - **SDG 16** (accountable, transparent institutions) and SDG 8.
   Only signed-in staff can open the screen; every approval or rejection is logged with the staff member's name.

4. **Live room booking** (`RoomsActivity`, `BookingActivity`, `StaffActivity`, `Repository`) - **SDG 9**.
   Screens register a `ChangeListener` on the `Repository`, so availability refreshes by itself when a room is approved,
   cancelled or taken: a "LIVE" line shows the last update and what just changed ("Just booked: Room 22"), and the
   booking screen disables *Send* the moment its room becomes unavailable. A demo switch ("simulate other guests booking")
   makes a walk-in guest take a free room every 12 s so you can show it without a second phone.
5. **My Bookings wallet** (`MyBookingsActivity`) - each request is a pass (reference, dates, total, 50% deposit, status:
   awaiting verification / confirmed / declined / cancelled). Filter by upcoming / past / cancelled, cancel your own booking
   (the room is released), and see your Green Stay eco points. Bookings are matched to the **login email**, not the form email.
6. **Push notifications** (`Notifier.kt`, `JDApp`, `NotificationsActivity`) - a system notification (channel "Booking updates",
   Android 13+ permission requested at runtime) when your request is received, confirmed, declined or cancelled; tapping opens
   My Bookings. Every alert is also kept in the Alerts inbox with an unread badge. Staff get in-app alerts for new requests,
   cancellations and reviews.
7. **Concierge chatbot** (`Concierge.kt`, `ChatActivity`) - keyword-based assistant with quick-reply chips. It answers only from
   real app data (rooms, rates, availability for the dates you mention or last searched, your own bookings by reference,
   ratings) and says "I don't have that information" instead of inventing policies. Action buttons open the right screen.
8. **Reviews & ratings** (`Review` in `Models.kt`, `ReviewsActivity`, `MyBookingsActivity`) - 1-5 stars + optional comment,
   one review per confirmed booking, shown as a rating on every room card and the booking screen, and as an average in Analytics.

> The eco constants in `EcoImpact.kt` (40 L per skipped linen change, 2 bottles per guest-night, 1.5 kWh per room-night,
> 0.7 kg CO2/kWh) are **classroom assumptions** so the feature works. Replace them with figures from a source you cite.

## How to run

**Option A - open the project:** Android Studio > *Open* > select the `JDReserve` folder > let Gradle sync > Run.
If Android Studio offers to upgrade the Android Gradle Plugin, accepting is fine.

**Option B - if sync gives trouble:** *New Project > Empty Views Activity*, language Kotlin, package `com.jdreserve.app`, min SDK 26.
Then copy `app/src/main/java/com/jdreserve/app/*.kt`, `app/src/main/res/layout/*.xml`, `res/values/strings.xml` and
`AndroidManifest.xml` over the generated ones (delete the generated `MainActivity` and its layout).

Min SDK 26 (Android 8.0) because the app uses `java.time`. Activities extend the platform `Activity`, so no AndroidX is needed.

**Demo logins**
- Guest: any valid email + a password of 6+ characters (e.g. `calvin@example.com` / `guest123`)
- Staff: `cathy@jdreserve.ph` / `staff123` (or `rina@jdreserve.ph` / `staff123`)

> The seeded data is for **December 2026** and the app rejects check-in dates in the past using the phone's clock.
> If the device date is already past Dec 20, 2026, change the seed dates in `Repository.seed()`.

## Demo flow for the new features

1. Sign in as guest (`calvin@example.com`). Allow notifications when asked. Rooms shows the **LIVE** line and star ratings.
2. Turn on **"Demo: simulate other guests booking"**: within ~5-12 s a room flips to *Booked* and the live line says "Just booked: Room N".
3. Book Room 21 (Dec 20-23) -> **Request received** notification. Open **My bookings**: the pass shows *Awaiting verification*.
4. Log out, sign in as staff (`cathy@jdreserve.ph`), open **Alerts** (new request), approve it.
5. Pull down the notification shade: **Booking confirmed**. Sign back in as the guest: the wallet pass says *Confirmed*; tap **Review**, give stars.
6. Open **Concierge** and try: "Is anything free Dec 20-23 for 4 guests?", "Prices", "Directions", or your reference (e.g. `JD-1220-0087`).
7. Open **Reviews**: your review is listed and the room's average changed.

## Suggested video script (about 3 minutes)

1. **Login** as guest with a wrong password format (shows the error), then a valid one.
2. **Rooms:** dates Dec 20-23, 2 guests > "5 room(s) available". Room 24 shows *Booked*. Flip *Hide booked rooms*. Filter by type.
3. **Book Room 21:** leave the name empty and press send (error), enter a bad phone (error), then fix both.
   Tick the four Green Stay pledges and show the impact numbers and the price (`3 x P4,500 + VAT = P15,120`, deposit `P7,560`).
4. **Request sent** screen: show the reference number.
5. **Double-booking test:** go back home, book Room 21 again for the *same* dates (allowed - requests are only pending).
6. **Staff:** log out, sign in as staff. Approve the first Room 21 request, then try the second: the app **refuses** it with a clear message.
   Show the activity log.
7. **Analytics:** occupancy bars, revenue, and the sustainability totals including the pledges you just made.

## Semantic errors (code that compiles and runs but gives wrong answers)

A syntax error stops the build; a **semantic** error does not - the app runs and silently does the wrong thing.
These are the ones this app is exposed to, why each happens, and where it is handled:

| # | Semantic error | Why it happens | Where it is handled |
|---|---|---|---|
| 1 | Every picked date is one month off | `DatePickerDialog` months are 0-based (Jan = 0) but `LocalDate` months are 1-based. Forgetting the +1 / -1 compiles fine | `Utils.kt` `pickDate` |
| 2 | A 3-night stay is charged as 4 nights | Counting the check-out day as a night (`days + 1`). Nights = check-out minus check-in | `Pricing.nights` |
| 3 | A guest leaving Dec 23 blocks one arriving Dec 23 | Overlap test written with `<=`. Stays are half-open ranges `[in, out)`, so the test is `aIn < bOut && bIn < aOut` | `Repository.overlaps` |
| 4 | Two guests approved for the same room and dates | Each request looked fine when submitted (pending requests do not block). Checking only at submit time lets both through. Approval must re-check | `Repository.approve` |
| 5 | Occupancy shows 0% | Integer division: `57 / 185 == 0` in Kotlin. Multiply by 100 first, and round | `Repository.percent` |
| 6 | Totals off by centavos, deposit short by P1 | `Double` cannot store most decimals exactly; plain integer division rounds down. Money is `Long` pesos, VAT rounds half-up, deposit rounds up | `Pricing.vat`, `Pricing.deposit` |
| 7 | Occupancy overcounted for stays crossing the month | A Dec 29 - Jan 2 stay counted all 4 nights in December. Nights must be clipped to the window | `Repository.nightsInside` |
| 8 | Maintenance made occupancy look worse | Blocked nights counted as sellable nights in the denominator | `Repository.analytics` |
| 9 | A guest opens the staff screen | Screens are separate Activities, so skipping login still "works" unless each one checks the role | `StaffActivity`, `AnalyticsActivity`, `RoomsActivity` guards |
| 10 | Pending list still shows an approved request | The list was built once in `onCreate`. Refresh runs in `onResume` and after each action | `StaffActivity` |

**Known limitations (by design for a lab, not hidden):**
- Data lives in memory only, so it resets when the app process is killed. A real system would use Room/SQLite or a server.
- Staff passwords are hard-coded demo accounts. A real system must never ship credentials in the app.
- The eco numbers are assumptions (see above), and payment (GCash) and email are described on screen but not integrated.
- **Notifications are local, not server push.** There is no backend, so the app raises the notification itself when the
  repository changes. Real remote push needs Firebase Cloud Messaging (a Firebase project, `google-services.json`, and a server
  to send); `Notifier.show()` is where an incoming FCM message would be displayed.
- **"Live" is in-process.** Updates are instant across screens on one device, but two phones will not see each other's bookings
  until the data moves to a shared backend (Firebase, Supabase, or your own API).
- **The concierge is rule-based**, not an AI model. `Concierge.reply()` is the one function to replace with a real AI call.
- Reviews: `Repository.DEMO_ALLOW_EARLY_REVIEWS = true` lets a guest review a confirmed booking before the stay so the feature can be
  demoed with December dates; set it to `false` to require that the stay has started. Reviews are not moderated, and the 8 seeded
  reviews are fictional sample data.
- Guest login is still "any valid email", so My Bookings is only as private as that login. A real app needs real authentication.

## How this was checked

The original screens and business logic were compiled and tested earlier (see the numbers above). The five features added after that
(live booking, wallet, notifications, concierge, reviews) were **not compiled or run**: no Kotlin compiler or Android SDK was
available when they were written. They were checked statically only: every `R.id` / `R.layout` reference matches a layout file,
every activity is declared in the manifest, all layout XML is well-formed, braces and parentheses balance, and the concierge's date
and guest-count regexes were tested. **Sync Gradle and run it in Android Studio once** and send any compiler error back; expect small fixes.


