# PRD — Rider App (Android)

## 1. Summary

A small Android app, **Fika Rider**, for the riders of any shop on the platform (first shop: a Nakuru clothes business). One app serves all shops; each rider signs in to their own shop. Riders see the deliveries the owner assigns them, navigate to each customer with in-app turn-by-turn directions, and mark progress. While a delivery is on the way, the app shares the rider's live position and ETA so the customer can watch the rider approach on a web tracking page.

Volume: about 10 deliveries a day, a handful of riders. Orders are already paid for or paid on delivery; there is no shopping or payment integration.

## 2. Users

- **Rider** — uses the app on a personal Android phone, often mid-range, on mobile data, outdoors and in a hurry.
- The **owner** manages everything from the web dashboard (separate PRD) and does not use this app.

## 3. Goals

1. A rider can go from opening the app to navigating to the customer in under 10 seconds.
2. The customer sees the rider's position within ~15 s of reality and a sensible ETA.
3. No location is collected outside an active delivery.
4. Battery and data use are low enough that riders do not uninstall it.

## 4. Non-goals (v1)

- iOS build.
- Customer app.
- Payments (M-Pesa or otherwise). The rider only records whether cash was collected.
- Chat. Riders call customers using the phone dialer.
- Route optimisation across multiple drops.
- Rider sign-up, password reset, SMS OTP.

## 5. Delivery lifecycle (as seen by the rider)

```
created ──(owner assigns)──▶ assigned ──(rider)──▶ picked_up ──(rider)──▶ on_the_way ──(rider)──▶ delivered
                                                                                 └──(rider)──▶ returned
                                   └──────────────(owner cancels at any point)──────────────▶ cancelled
```

- A rider may hold several `assigned` / `picked_up` orders but only **one `on_the_way`** at a time.
- `returned`: the customer couldn't pay or refused the items, and the rider brings them back to the shop. The owner confirms receipt of the items on the dashboard.
- Location tracking runs only while an order is `on_the_way`.

## 6. Screens and requirements

### 6.1 Sign in
- **Shop code** (6 characters, given by the owner), phone number and 6-digit PIN. Account created by the owner from the dashboard.
- The shop code is checked as soon as all 6 characters are typed (`rider_shop_lookup`). A valid code shows the shop's name and logo above the phone field, so the rider knows they picked the right shop.
- The shop code is remembered after the first sign-in and shown prefilled with the shop's name, with "Change shop".
- After sign-in, the shop's name (and logo if set) appears on Today and Account.
- One clear error per case:

| Case | How the app knows | Message |
| --- | --- | --- |
| Unknown shop code | `rider_shop_lookup` returns null | "We couldn't find that shop code. Check it with the shop." |
| Suspended shop | lookup returns `suspended` | "Your shop's account is paused. Talk to the shop owner." |
| Wrong phone or PIN | auth error `invalid_credentials` | "Wrong phone number or PIN." |
| Deactivated rider | auth error `user_banned` | "Your rider account is turned off. Talk to the shop owner." |
| No network | request fails before a response | "No connection. Check your data and try again." |

- An unknown phone and a wrong PIN share one message on purpose, so the screen doesn't reveal which phone numbers have accounts.
- Remember the session; riders should rarely sign in again.
- No lockout UI in v1 (rely on Supabase rate limits).
- "Forgot your PIN? Ask the shop to reset it." text, no reset flow.

### 6.2 Today
- List of the rider's orders that are `assigned`, `picked_up` or `on_the_way`, newest first, with the `on_the_way` one pinned on top.
- Each card: customer first name, area/landmark, items summary, payment badge (*Paid* or *Collect KES X*), status, and a warning if the customer has not confirmed a location yet.
- Pull to refresh, plus live updates when the owner assigns or cancels an order (Supabase Realtime on `orders`). The list is refetched on every Realtime event and when the app comes back to the foreground. A reassigned order sends no event to the old rider, so an order that disappears on refetch is treated as reassigned (see 9).
- Empty state: "No deliveries yet. New ones will show up here."
- Today's delivered and returned orders in a collapsed section.

### 6.3 Delivery detail
- Customer name, phone (tap to call), items, landmark note, payment mode and amount.
- Preview map showing the destination pin and the rider's current position. If no pin: show the landmark note and a banner "Customer hasn't shared their location yet. Call them before you set off."
- Primary action changes with status:
    - `assigned` → **Picked up**
    - `picked_up` → **Start delivery** (moves to `on_the_way`, starts tracking, opens navigation)
    - `on_the_way` → **Navigate** (resume) and **Delivered**
- Secondary: **Call customer**, **Open in Google Maps**.
- If the rider already has another order `on_the_way`, *Start delivery* is disabled with an explanation.

### 6.4 Navigation
- Full-screen Google Navigation SDK view to the destination pin.
- Bottom card: customer name, call button, **Arrived / Delivered** button.
- Remaining time and distance are sent to the backend with each position ping.
- First use: Navigation SDK terms dialog. If declined, fall back to opening Google Maps; tracking continues via the foreground service.

### 6.5 Collect and deliver
- Full screen, opened from *Delivered*. Close (X) and system back return to navigation; there is no separate "go back" button.
- **Pay on delivery:** the amount is the largest element on screen ("Collect from Wanjiru, KES 3,500").
    - The rider picks how the customer paid: **M-Pesa** or **Cash** (connected button group, nothing preselected in production; confirm stays disabled until one is picked).
    - M-Pesa: show the shop's till number large, plus an optional field for the M-Pesa code from the customer's SMS, uppercase. Reminder to check the amount on the SMS.
    - Cash: reminder to count the amount; it is added to the rider's cash to hand in.
    - **Customer can't pay** (secondary, red text): opens a sheet (see below).
- **Prepaid:** "Nothing to collect, Paid" card; primary button reads *Delivered*.
    - **Customer didn't take it** (secondary, red text): opens the same sheet, with only the return option.
- **Problem sheet:**
    - Short reason, required (e.g. "Didn't have the money", "Wrong size").
    - **Call shop** button: the owner decides whether the customer keeps the items, so the rider asks when unsure.
    - Two outcomes (pay on delivery), or only the second (prepaid):
        - **Leave items, not paid** → `rider_mark_delivered` with `unpaid_reason`. Order is delivered-unpaid; the owner sees it flagged under Payments.
        - **Bring items back** → `rider_mark_returned(order_id, return_reason)`. Order becomes `returned`; tracking stops; the rider goes back to Today with "Marked as returned. Take the items back to the shop." No success screen.
    - The save-location checkbox still applies to *Leave items, not paid* (the customer was reached at that spot); it is not offered for returns.
- If no destination pin existed: checkbox "Save this spot as the customer's location" (default on) using the rider's current position.
- Primary button: *Paid and delivered* (or *Delivered* for prepaid), green, 88 px.
- On confirm: call `rider_mark_delivered`, stop tracking, show the success screen (6.5a).

### 6.5a Delivered (success)
- Full-screen green confirmation, about 1 s of motion: burst shape springs in and slowly rotates, two rings ripple out, the check draws itself, text and receipt chips rise in staggered. A short haptic tick with the burst.
- Shows customer name and time, a receipt chip ("KES 3,500 by M-Pesa", "Cash KES 3,500" or "Prepaid") and today's count.
- Primary: **Next delivery** with the next order's name and area; a bar along its bottom fills over 5 s, then opens that order unless the rider acts first. No next order: *Back to today*, no countdown.
- All motion disabled when the system's reduce-motion setting is on.

### 6.6 History
- The rider's own completed, returned and cancelled deliveries, newest first, grouped by day.
- Each row: date and time, customer first name, area/landmark, items summary, payment outcome (*Paid upfront*, *M-Pesa KES X*, *Cash KES X*, *Not paid*, *Returned*), and time taken (picked up to delivered or returned).
- Day header shows totals: deliveries completed (returns not counted) and **cash** collected (M-Pesa goes straight to the shop's till, so it is not part of what the rider hands in).
- Filters: Today, This week, This month.
- Read-only. Customer phone and exact coordinates are hidden for orders delivered more than 24 h ago.
- Orders reassigned away from the rider before delivery do not appear.

### 6.7 Account
- Rider name and phone, app version, **Check for updates**, **Sign out**.
- Update check: compare the installed version with the latest GitHub Release (if distributed that way) and link to the APK. Hidden when installed from Play.

## 7. Location tracking requirements

| Rule | Value |
| --- | --- |
| Starts | On *Start delivery* (`on_the_way`) |
| Stops | On *Delivered* or *Bring items back*, cancel by owner (detected via Realtime or the next ping response, which returns the active order's status), or sign out |
| Mechanism | Foreground service, type `location`, persistent notification "Delivering to {first name}" |
| Interval | ~10 s; skip if moved < 15 m and < 30 s since last ping |
| Payload | lat, lng, heading, speed, accuracy, eta_seconds?, distance_m? |
| Offline | Keep only the latest ping; send it when back online. No backlog |
| Permissions | Fine + coarse location, foreground service location, notifications. Never background location |
| Battery | One-time prompt to exempt the app from battery optimisation |

## 8. Permissions UX

- Ask for location on the first *Start delivery*, not at launch, with a one-line reason: "So the customer can see you on the map while you deliver."
- Ask for notifications at the same time (needed for the foreground service notification on Android 13+).
- If location is denied: allow the delivery to proceed without tracking, show a persistent banner, and offer a button to open settings.

## 9. Error and edge cases

- **No network on status change:** show the action as pending, retry automatically, and do not let the rider double-submit.
- **Order cancelled while on the way:** stop tracking, show "This delivery was cancelled by the shop", return to Today.
- **Order reassigned to another rider:** detected when a refetch no longer returns it (Realtime sends the old rider nothing). Remove it from the list with "{first name}'s delivery was moved to another rider." If its detail screen is open, close it with the same message.
- **Shop suspended while signed in:** on launch and resume the app re-reads the shop's status, and rider RPCs fail with `shop_suspended`. Show a full-screen "Your shop's account is paused. Talk to the shop owner." with Sign out. Stop tracking if it was running.
- **Rider deactivated while signed in:** the session refresh fails with `user_banned` and RPCs fail with `rider_inactive`. Stop tracking, sign out, and show the turned-off message on the sign-in screen.
- **GPS accuracy poor (> 100 m):** still send, flagged by `accuracy`; do not block.
- **Phone restarts mid-delivery:** on next launch, detect the `on_the_way` order and offer **Resume tracking**.

## 10. Non-functional

- Runs well on mid-range Android phones (minSdk 31).
- Cold start to Today in under 2 s on a mid-range device.
- Data use during a 20-minute delivery stays well under 1 MB excluding map tiles.
- No analytics or third-party trackers.
- UI copy never uses middots (·).

## 11. Acceptance criteria (v1)

- [ ] Rider signs in with shop code + phone + PIN created on the dashboard.
- [ ] Unknown shop code, suspended shop, wrong phone or PIN, and deactivated rider each show their own message.
- [ ] Assigned orders appear without restarting the app.
- [ ] *Start delivery* starts tracking and navigation in one tap.
- [ ] The web tracking page shows the rider moving and an ETA from the phone.
- [ ] *Delivered* stops tracking; no further pings are sent.
- [ ] No location requests happen outside `on_the_way`.
- [ ] Customer location can be saved from the rider's position on delivery.
- [ ] When the customer can't pay or refuses the items, the rider can either leave them unpaid or mark the order returned; returning stops tracking and the owner sees it on the dashboard.
- [ ] Works through a 5-minute connectivity drop without crashing or flooding the backend.
- [ ] Release APK installs from a GitHub Release link.
- [ ] History shows the rider's past deliveries with daily cash totals and no customer phone after 24 h.

## 12. Open questions

Decided: customers see the rider's phone number on the tracking page only while the order is `on_the_way`. Riders must be told this when their account is created.

1. Distribution: GitHub Releases or Google Play? (Developer verification for sideloaded apps becomes global in 2027.)
2. Should riders be able to reject an assignment? (v1 assumption: no; they call the owner.)