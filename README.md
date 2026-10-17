# Hustle City

A business life-simulation game for Android, inspired by *Big Ambitions*. Start with a small apartment, a beat-up car and $5,000. Rent a storefront, furnish it, stock it, hire staff, and grow a business empire in a living 3D city.

Native Kotlin + Jetpack Compose. The 3D city is rendered with a small custom engine on Android `Canvas`, so the whole game is a 1.4 MB release APK that runs on mid-range phones.

## Features

**The city**
- Procedural Manhattan-style grid: 6 neighborhoods (Garment District, Hell's Kitchen, Midtown, Murray Hill, Lower Manhattan, Industry City), about 600 buildings with addresses, parks, parking lots and gas stations.
- 2.5D perspective rendering with windows, storefronts, awnings, rooftop units, sun-tracking shadows, a day/night cycle with lit windows and street lamps.
- Traffic with traffic lights, pedestrians, and buildings that turn see-through when they block your view.
- Walk with the on-screen joystick, or drive your own car with fuel, condition and cargo space. Taxis are available from any building card.

**Your character**
- Energy, hunger and happiness. Sleep at home, eat from your fridge or at restaurants, and avoid passing out.
- Courses at Hustle Business School unlock hiring, logistics, marketing, importing and real estate.

**Businesses** (13 types)
- Florist, Fruit & Vegetable Store, Coffee Shop, Supermarket, Fast Food, Hairdresser, Gym, Gift Shop, Liquor Store, Clothing, Bookstore, Electronics, Jewelry.
- Rent a building in **Realty** and pick a business type, comparing inventory sources, nearby competitors and primary products.
- Buy furniture at three appliance stores. Load it into your car (or pay for delivery) and place it with the grid-based **Interior Designer**, with floor and wall colors.
- Buy wholesale stock at Metro Wholesale, CityDistro or Global Imports, then unload it onto shelves, fridges and machines.
- Customer simulation is driven by traffic index, marketing, hour of day, competitors, price acceptance, wait times, cleanliness, interior and staff skill.

**Management** (on the in-game HustlePhone)
- **BizHub** has the tabs Insight, Inventory & Pricing, Schedule, Deliveries, Marketing and Settings.
- **Staff** lets you hire candidates from Apex Recruitment, give raises, train and fire. Unhappy staff quit.
- **Finance** shows a daily profit chart, a cost breakdown and your loans. Hudson Bank offers three loan tiers.
- **Rivals** has 8 AI companies that open and close stores; your goal is to become #1.
- **Maps** is a zoomable city map with neighborhood, business-type and for-rent filters, plus search.
- **Persona** and **Contacts** cover your stats, education, vehicles and messages.
- Midnight settlement with a day summary and autosave. Guided objectives from Uncle Sal.

## Controls

| Action | Touch |
| --- | --- |
| Walk / drive | Left joystick (a car steers toward the stick direction) |
| Brake / reverse | Red pedal while driving |
| Look around | Drag the city |
| Zoom | Pinch |
| Rotate camera | Two-finger twist (also works inside buildings) |
| Select a building | Tap it |
| Enter a building / get in a car | Context buttons at the bottom of the screen |

Settings include Low/Medium/High graphics quality, a 30/60 FPS cap, an FPS counter, pausing while menus are open, and a left-handed layout.

## Building

Requirements: JDK 17 or newer and the Android SDK (API 35).

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # minified, signed with the debug key for sideloading
./gradlew testDebugUnitTest      # simulation tests + offscreen render snapshots
```

Every push also builds both APKs on GitHub Actions. Download them from the run's **Artifacts** section.

The unit tests use Robolectric's native graphics to render the city, interiors and every main UI screen to PNG files in `app/build/snapshots/`. `BalanceReportTest` writes a per-business-type profit report to `app/build/balance.txt`.

## Project layout

```
app/src/main/java/com/hustlecity/game/
  data/      Catalog: products, furniture models, business types, vehicles, courses, places
  model/     Serializable save state
  world/     City generator, traffic and pedestrians
  render/    Projector, triangle batcher, city / interior renderers, 3D thumbnails
  engine/    Game loop, economy simulation, interiors, actions, objectives, saving
  ui/        Compose HUD, phone apps, BizHub, shops, menus
```

*Hustle City is an independent fan project. It is not affiliated with Hovgaard Games or Big Ambitions. All names of in-game companies are fictional.*
