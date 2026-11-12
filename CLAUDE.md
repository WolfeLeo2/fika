# CLAUDE.md — Rider App

**Fika Rider**, the Android rider app of a multi-tenant delivery-tracking platform for small Kenyan shops (first shop: a Nakuru clothes shop). One app on the store serves every shop; a rider signs in to their shop and only ever sees that shop's data. Riders see their assigned deliveries, navigate to customers with in-app Google turn-by-turn navigation, and report live position, ETA and delivery status. Customers track via a web link (separate repo), not an app.

Full requirements: @docs/mobile/PRD.md. The web app and backend PRD (`docs/web/PRD.md`) owns the data model, RLS and RPC contract; read it when touching anything backend-facing.

## Stack

- **Compose Multiplatform 1.12.2**, shipping **Android only**. The iOS targets and `iosApp/` scaffold stay so an iOS build can be added later, but do not build iOS features or ship it. Keep `commonMain` platform-free.
- **Material 3 Expressive** via `org.jetbrains.compose.material3:material3:1.12.0-alpha03` (based on Jetpack Material3 `1.5.0-alpha22`). Some components that exist in newer AndroidX alphas may be missing; check before using.
- Navigation: **Navigation 3** (`org.jetbrains.androidx.navigation3:navigation3-*:1.2.0-rc01`).
- Lifecycle / ViewModel: `org.jetbrains.androidx.lifecycle:lifecycle-*:2.11.0`.
- Backend: **Supabase** via **supabase-kt** (Auth, Postgrest, Realtime, Storage). Ktor client engine: OkHttp on Android, Darwin on iOS. No Functions: the app calls no edge functions.
- DI: **Koin**. Serialization: **kotlinx-serialization**. Dates: **kotlinx-datetime**. Coroutines + Flow throughout.
- Maps and navigation (androidMain only): **Google Navigation SDK for Android**. It bundles the Maps SDK, so do not also add `play-services-maps`. Check the current Navigation SDK docs for whether maps-compose works with its bundled Maps; if not, wrap `MapView` / `NavigationView` in `AndroidView`.
- Location: `FusedLocationProviderClient` in a foreground service.
- minSdk 31, target/compile the latest stable SDK.

All versions live in `gradle/libs.versions.toml`. Never hardcode versions in module build files.

## Modules

```
:shared       KMP library, no Compose. commonMain: models, Supabase client and
              repositories, use cases, ViewModels, interfaces (LocationTracker,
              NavigationLauncher, Clock). androidMain/iosMain: platform actuals.
:riderApp     KMP Compose Multiplatform UI library (shared UI). commonMain: theme,
              screens, navigation, Compose resources (strings, fonts, icons),
              `expect` composables (DeliveryMap). androidMain: Navigation SDK and
              map composables. iosMain: MainViewController (unshipped).
:androidApp   Android application: Activity, Application (Koin start), manifest and
              permissions, foreground location service, LocationTracker and
              NavigationLauncher implementations, BuildConfig secrets.
iosApp/       Xcode entry point embedding the RiderApp framework. Not shipped.
```

Dependencies: `:androidApp` → `:riderApp` → `:shared`. A future `:customerApp` would depend on `:shared`. Never put rider-only UI or permissions in `:shared`.

## Architecture

- MVVM with unidirectional data flow. Each screen has a `ViewModel` exposing one `StateFlow<UiState>` and accepting events via functions. Screens are stateless composables taking `state` and lambdas.
- Repositories wrap Supabase and return `Result`-style sealed types, never throw into the UI.
- Platform features sit behind interfaces in `commonMain` with Android implementations:
    - `LocationTracker` — start/stop the foreground service for an order.
    - `NavigationLauncher` — start in-app navigation; fallback opens Google Maps via `google.navigation:q=lat,lng`.
    - `DeliveryMap` — `expect` composable for the preview map.
- Status changes go through Supabase RPCs (see Backend contract), not direct table updates.

## Backend contract (owned by the web repo)

The Supabase project, migrations and edge functions live in the **web repo** under `supabase/`. This app only consumes them. Do not create migrations here. If the app needs a schema change, write the SQL in a note for the web repo.

Key tables: `tenants`, `memberships`, `profiles`, `customers`, `orders`, `rider_positions`, `order_events` (audit trail); view `rider_history`. Every business row has `tenant_id`; RLS keeps a rider inside their own shop. The app never sends `tenant_id` to RPCs: the server derives it from the rider's membership. Order status enum: `created`, `assigned`, `picked_up`, `on_the_way`, `delivered`, `returned`, `cancelled`.

What a signed-in rider can read directly (RLS, `select` only):
- `orders` assigned to them.
- `customers` linked to one of their `assigned` / `picked_up` / `on_the_way` orders (name, phone, saved location, landmark).
- Their own shop's row in `tenants` (name, `logo_path`, till/paybill, `status`).
- Their own `profiles` row.
- `rider_history` for past orders (customer phone hidden after 24 h).

Realtime: subscribe to `postgres_changes` on `orders` (RLS applies). A reassigned order stops matching the rider's policy, so Realtime sends nothing for it: refetch the list on every event, on resume and on pull to refresh, and treat an order that disappears as reassigned.

RPCs the app calls:
- `rider_shop_lookup(shop_code)` — callable before sign-in. Returns `{ name, logo_url, status }` or null for an unknown code. Rate-limited.
- `rider_mark_picked_up(order_id)`
- `rider_start_delivery(order_id)` → `on_the_way`; fails if the rider already has another `on_the_way` order.
- `rider_report_position(lat, lng, heading, speed, accuracy, eta_seconds?, distance_m?)` — upserts the rider's single row in `rider_positions` and, when ETA is provided, updates the active order's ETA. Returns `{ order_id, status }` for the rider's active order (or null); stop tracking when the status is no longer `on_the_way`.
- `rider_mark_delivered(order_id, payment_method?, mpesa_code?, unpaid_reason?, save_lat?, save_lng?)` — `payment_method` is `cash` or `mpesa` for pay-on-delivery orders, null for prepaid; `unpaid_reason` when the customer couldn't pay and keeps the items (delivered-unpaid).
- `rider_mark_returned(order_id, return_reason)` — `on_the_way` → `returned`: the customer couldn't pay or refused the items and the rider is bringing them back. Stops tracking.

Rider RPCs fail with error codes the app maps to copy: `shop_suspended`, `rider_inactive`, `not_your_order`, `already_on_the_way`, `invalid_status`.

Rider auth: the rider types **shop code + phone number + 6-digit PIN**. Accounts use a synthetic email built from the normalised phone and the lowercased shop code (`254712345678@k7q2mx.riders.invalid`). Sign-in flow:
1. Uppercase and validate the 6-character shop code, then call `rider_shop_lookup`. Null → "We couldn't find that shop code." `suspended` → "Your shop's account is paused. Talk to the shop owner." Otherwise show the shop's name and logo on the sign-in screen.
2. Normalise the phone (accept `07…`, `01…`, `+254…`, `254…`) and call `signInWithPassword(email, pin)`. Auth error `invalid_credentials` → "Wrong phone number or PIN." `user_banned` → "Your rider account is turned off. Talk to the shop owner." (deactivating a rider bans their auth user). Network failure → retry message, not a credentials error.
3. Remember the shop code on the device. Load the shop's name, logo and till from `tenants` and show them in the app bar. On launch and resume, re-read `tenants.status`; if `suspended`, show the paused screen. If the session refresh fails with `user_banned`, sign out and show the turned-off message.

A rider who works for two shops has two accounts and signs out to switch. Accounts are created by the owner from the web dashboard. There is no sign-up, no SMS OTP, and no password reset in the app.

Edge functions (`track`, `track-set-location`, `admin-rider`, `eta-fallback`) belong to the web app and backend; the rider app does not call them.

## Location and navigation rules

- Tracking runs only while an order is `on_the_way`; it stops on `delivered`, `returned` or `cancelled`. Never track otherwise.
- Foreground service with `foregroundServiceType="location"`. Permissions: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS`. **Never** request `ACCESS_BACKGROUND_LOCATION`.
- Ping interval ~10 s while moving; skip pings when moved < 15 m and < 30 s elapsed. Keep only the latest unsent ping when offline; never build a backlog.
- During a Navigation SDK session, read remaining time and distance from the navigator and send them with each ping as `eta_seconds` and `distance_m`.
- The rider must accept the Navigation SDK terms dialog once; handle decline gracefully by falling back to the Google Maps intent.
- Prompt once to exclude the app from battery optimisation, with a plain explanation.

## UI conventions

- Wrap the app in `MaterialExpressiveTheme` and opt in with `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` where needed. Use expressive components where they fit (e.g. `LoadingIndicator`, `ButtonGroup`, split/large buttons, `FloatingToolbar`) and the expressive motion scheme. Do not hand-roll components that M3 Expressive provides.
- Big tap targets and high contrast: riders use the app outdoors, one-handed, on a motorbike stop.
- Typography: **Google Sans Flex** (variable, SIL OFL), bundled in Compose resources and set as the typography of `MaterialExpressiveTheme`. Display text and key numbers (ETA, KES amounts) use a wide width and heavy weight with full roundness (`ROND` 100); body text stays at normal width.
- English only for v1. All user-facing strings in Compose resources, not hardcoded.
- **Icons: Material Symbols, Rounded style only.** Import the needed symbols as vector drawables (Google Fonts icons page, Android export) into Compose resources. Outlined (FILL 0) by default, filled (FILL 1) for the selected navigation item and the near_me/location_on markers. No other icon set (no Lucide, Feather or Material Icons legacy).
- **Never use middots (·) in UI copy.** Use line breaks, commas or separate text elements.
- Money is KES, integers, formatted `KES 1,500`.
- Times shown in Africa/Nairobi.

## Secrets

Supabase URL + anon key and the Google Maps/Navigation API key come from `local.properties` via the Secrets Gradle Plugin or BuildConfig. Never commit keys. Restrict the Google key to the app's package name and SHA-1.

## Commands

```bash
./gradlew :androidApp:assembleDebug        # build
./gradlew :androidApp:installDebug         # install on device
./gradlew :shared:testAndroidHostTest      # shared tests (JVM, fast)
./gradlew :shared:allTests                 # shared tests on every target (needs Xcode)
./gradlew :androidApp:assembleRelease      # signed release APK (GitHub Releases / Play)
```

## Working rules

- Prefer small, reviewable changes. When changing a file, explain the change as a short diff first, then give the full updated file.
- Write unit tests for ViewModels and use cases in `commonTest`. Fake the repositories; do not hit the network in tests.
- Do not add libraries without saying why and checking they support Kotlin Multiplatform if they go in `:shared`.
- Do not add analytics, crash reporting or ads unless asked.