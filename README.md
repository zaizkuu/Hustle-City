# Hustle City

A business life-simulation game for Android, inspired by *Big Ambitions*. Start with a small apartment, a beat-up car and $5,000. Rent a storefront, furnish it, stock it, hire staff, and grow a business empire in a living 3D city.

Native Kotlin + Jetpack Compose. The 3D city is rendered with a small custom engine on Android `Canvas`, so the whole game is a 1.4 MB release APK that runs on mid-range phones.

## Features

**The city**
- Procedural Manhattan-style grid: 6 neighborhoods (Garment District, Hell's Kitchen, Midtown, Murray Hill, Lower Manhattan, Industry City), about 600 buildings with addresses, parks, parking lots and gas stations.
- 2.5D perspective rendering with windows, storefronts, awnings, rooftop units, sun-tracking shadows, a day/night cycle with lit windows and street lamps.
- Traffic with traffic lights: cars plan their turns and take smooth arcs, keep their distance and give way to pedestrians. Pedestrians cross on the walk signal, step aside for each other and walk into open shops. Buildings turn see-through when they block your view.
- Walk with the on-screen joystick, or drive your own car with fuel, condition and cargo space. Taxis are available from any building card.
- 3D people built from boxes, with walking, sitting, dancing and treadmill-running animations. Pedestrians carry umbrellas in the rain.
- Dynamic weather that follows the seasons (the game starts in April): clear, cloudy, rain, thunderstorms with lightning and thunder, snow that settles on roofs and sidewalks, and fog. Streets look wet after rain, and the top bar shows a three-day forecast.

**Your character**
- Energy, hunger and happiness. Sleep at home, eat from your fridge or at restaurants, and avoid passing out.
- Courses at Hustle Business School unlock hiring, logistics, marketing, importing and real estate.

**Businesses** (21 types, the full Big Ambitions roster)
- Shops: Florist, Fruit & Vegetable Store, Coffee Shop, Supermarket, Fast Food, Hairdresser, Gym, Gift Shop, Liquor Store, Clothing, Bookstore, Electronics, Jewelry.
- Nightlife and shows: Nightclub (needs a DJ), Cinema (projectionist, licence fees) and Theater (actors, licence fees).
- Office firms: Law Firm, Web Development Agency, Graphic Designer, Travel Agency and Event Planning Agency. Professionals at desks bill clients by the hour; clients come from reputation and advertising.
- Rent a building in **Realty** and pick a business type, comparing inventory sources, nearby competitors and primary products.
- Buy furniture at three appliance stores. Load it into your car (or pay for delivery) and place it with the grid-based **Interior Designer**, with floor and wall colors.
- Buy wholesale stock at Metro Wholesale, CityDistro or Global Imports, then unload it onto shelves, fridges and machines.
- Customer simulation is driven by traffic index, marketing, hour of day, weather, competitors, price acceptance, wait times, cleanliness, interior and staff skill.
- Weather changes who shops and what they buy. Bad weather keeps walk-in shoppers home but fills cinemas, cafés and bookstores. Umbrellas sell in the rain, ice cream and cold drinks in the heat, coffee and coat checks in the cold.
- Procedural floor dirt: customers track dirt along the routes they walk, entrances get muddy when it's wet, and spills and litter appear. Trash cans catch litter, and cleaners scrub the dirtiest spots first.
- Shoplifting hits jewelry, electronics, clothing and liquor stores; security cameras and anti-theft gates stop it.
- A full furniture range for every business type: checkout counters, shopping baskets, security, trash cans, pizza ovens, ice cream freezers, digital scales, more gym machines, coat checks, lounge sofas, stage lights, round racks, shoe and magazine racks, mannequins, rugs, copiers, meeting tables and more. Each piece is sold at one of the four furniture stores.
- Inside, customers browse displays, queue at the register, pay, then sit down to eat, watch the film or dance. They never share a spot, and staff walk to their posts and cleaners make their rounds.

**Growing the empire**
- **Warehouses**: rent one, fill it with pallet racks, and buy wholesale in bulk (10% off at 6+ boxes). Delivery contracts and purchasing agents use warehouse stock first, and you can load boxes into your van from inside.
- **Headquarters**: rent an office and add desks. HR Managers make staff happier and replace anyone who quits; Purchasing Agents restock four stores each every morning.
- Bankruptcy after three midnights deep in debt (or keep playing in sandbox mode), and a victory screen when you're #1 with a $5M net worth.

**Sound**
- A phone radio with four procedurally generated stations (Lo-fi, Pop, Jazz, Electro), plus effects for purchases, the cash register, doors, notifications and the car engine. No audio files are shipped; everything is synthesized at runtime.

**Management** (on the in-game HustlePhone)
- **BizHub** has the tabs Insight, Inventory & Pricing, Schedule, Deliveries, Marketing and Settings.
- **Staff** lets you hire candidates from Apex Recruitment, give raises, train and fire. Unhappy staff quit.
- **Finance** shows a daily operating-profit chart, a cost breakdown and your loans. Hudson Bank offers three loan tiers.
- **Rivals** has 8 AI companies, each with its own strategy (balanced, aggressive, premium, discounter, specialist). Their stores earn money with the same demand model as yours. They expand into good locations (always leaving buildings for you), renovate busy stores, advertise, buy their buildings, close money-losers and start price wars with your best stores. Your goal is to become #1.
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

Settings include Low/Medium/High graphics quality, a 30/60 FPS cap, music and effects volume, the radio station, an FPS counter, pausing while menus are open, and a left-handed layout.

## Building

Requirements: JDK 17 or newer and the Android SDK (API 35).

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # minified, signed with the debug key for sideloading
./gradlew testDebugUnitTest      # simulation tests + offscreen render snapshots
```

Every push also builds both APKs on GitHub Actions. Download them from the run's **Artifacts** section.

The unit tests use Robolectric's native graphics to render the city, interiors and every main UI screen to PNG files in `app/build/snapshots/`. `AudioTest` renders each radio station to WAV files in `app/build/audio/`. `BalanceReportTest` writes a per-business-type profit report to `app/build/balance.txt`.

## Project layout

```
app/src/main/java/com/hustlecity/game/
  data/      Catalog: products, furniture models, business types, vehicles, courses, places
  model/     Serializable save state
  world/     City generator, traffic and pedestrians
  render/    Projector, triangle batcher, city / interior renderers, 3D thumbnails
  audio/     Procedural music stations, sound effects and the audio mixer
  engine/    Game loop, economy simulation, interiors, actions, objectives, saving
  ui/        Compose HUD, phone apps, BizHub, shops, menus
```

*Hustle City is an independent fan project. It is not affiliated with Hovgaard Games or Big Ambitions. All names of in-game companies are fictional.*
