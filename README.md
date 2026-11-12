Fika Rider: the rider app for the Fika delivery-tracking platform. Kotlin Multiplatform with a shared Compose UI; ships Android only for now.

* [/shared](./shared/src): logic module, no Compose. Models, Supabase repositories, use cases, ViewModels.
* [/riderApp](./riderApp/src): shared Compose Multiplatform UI (screens, theme, resources).
* [/androidApp](./androidApp/src): Android application entry point, foreground location service, Navigation SDK wiring.
* [/iosApp](./iosApp/iosApp): iOS entry point embedding the `RiderApp` framework. Not shipped.

Requirements: [docs/mobile/PRD.md](./docs/mobile/PRD.md). Backend contract: [docs/web/PRD.md](./docs/web/PRD.md).

### Running

- Android app: `./gradlew :androidApp:assembleDebug`
- iOS app: open [/iosApp](./iosApp) in Xcode and run it.

### Tests

- `./gradlew :shared:testAndroidHostTest`
- `./gradlew :shared:allTests` (includes iOS simulator, needs Xcode)
