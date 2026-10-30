# Hustle City

A business life-simulation game for Android and Windows, inspired by *Big Ambitions*. Start with a small apartment, a beat-up car and $5,000. Rent a storefront, furnish it, stock it, hire staff, and grow a business empire in a living 3D city.

Native Kotlin + Jetpack Compose with a custom 3D engine. In HD mode the world is drawn by an OpenGL ES 3 renderer with per-pixel lighting, real-time sun shadows, photo materials scanned from real surfaces (brick, stone, asphalt, concrete, sidewalk slabs, grass, wood, tiles, corrugated metal, gravel, plaster) with bump maps, glass that reflects the sky, water with wind waves, a glittering sun path and foam along the quays, modelled cars, and 4x anti-aliasing; a lightweight Canvas renderer with procedural materials is the fallback for older devices.

The Windows version is built from the same code with Compose Multiplatform: same game, same saves format, with keyboard and mouse controls and an interface that scales to the monitor.

## Windows

**Install:** open the latest run of the **Windows build** workflow on GitHub Actions and download `hustle-city-windows-installer` from its **Artifacts**. It holds `HustleCity-1.7.0.exe`, a setup wizard that installs the game with Start menu and desktop shortcuts, and the same installer as an `.msi`. Tagged versions (`v1.7.0`, ...) also publish them on the Releases page. Java comes bundled, so there's nothing else to install. Windows SmartScreen may warn about an unsigned app the first time: choose **More info → Run anyway**.

No installer wanted? `hustle-city-windows-portable` is a zip you can unpack anywhere and run `HustleCity.exe` from.

Saves and settings are kept in `%APPDATA%\HustleCity`.

**Keyboard and mouse:** keyboard controls are on by default. The on-screen joystick, steering wheel, pedals and the buttons the keys replace are hidden, and a key panel in the corner shows what each key does right now. Settings → Walking and driving switches back to on-screen controls.

| Action | Keys and mouse |
| --- | --- |
| Walk | W A S D or the arrow keys |
| Go in a door / get in or out of the car | E (F gets in the car when you're at a door too) |
| Drive | W / ▲ gas, S / ▼ brake (hold to reverse), A D / ◀ ▶ steer |
| Handbrake | Space: hold it in a turn at speed and the back end slides round |
| Park | P |
| Back / close a menu | Esc |
| Look around | Drag with the left mouse button |
| Zoom | Mouse wheel |
| Rotate camera | Drag with the right mouse button |
| Select a building | Click it |

**Bigger screens:** the interface grows with the window, so a monitor shows the same layout as a phone instead of tiny controls in the corners. Settings → Interface size makes it smaller or larger (this works on Android tablets too).

## Features

**The city**
- Procedural Manhattan-style grid: 6 neighborhoods (Garment District, Hell's Kitchen, Midtown, Murray Hill, Lower Manhattan, Industry City), about 600 buildings with addresses, parks, parking lots and gas stations.
- Buildings with personality: brick walk-ups with fire escapes and water towers, brownstones with stoops and bay windows, art deco towers with setbacks and spires, glass skyscrapers, stone offices, modern blocks with balconies and industrial sheds with sawtooth roofs. Storefronts dress up for their business (striped awnings, neon, cinema marquees, window displays, sidewalk tables) and close their shutters at night.
- Street life in detail: lamp posts that light the street, hydrants, benches, bus shelters, newspaper boxes, bike racks and planters; parks with fountains; trees that blossom in spring, turn red and gold in autumn and stand bare in winter. Cars have wheels, lights and brake lights, and taxis cruise the avenues; in HD the sedans, taxis, SUVs and sports cars near the camera are detailed low-poly models in each car's own paint.
- A day/night cycle with sun-tracking shadows, lit windows and glowing street lamps.
- Traffic with traffic lights: cars plan their turns and take smooth arcs, keep their distance and give way to pedestrians. Pedestrians cross on the walk signal, step aside for each other and walk into open shops. Buildings turn see-through when they block your view.
- Patrol cars in each city's livery cruise with the traffic, some with their lights going on a call, and a police helicopter circles over the rooftops, sweeping the streets with its searchlight after dark.
- Walk with the on-screen joystick, or drive your own car with fuel, condition and cargo space. Taxis are available from any building card.
- Car controls with a steering wheel and gas/brake pedals (or a joystick), a speedometer dashboard, and Park (pulls over to the curb) and Get out buttons.
- A minimap that turns with the camera, a full city map, and GPS navigation: a blue route line on the road and the maps, plus turn-by-turn directions with the distance and time left.
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
| Walk | Left joystick |
| Drive | Steering wheel (drag left/right) with GAS and BRAKE pedals, or arrow keys (◀ ▶ steer, ▲ gas, ▼ brake), or a joystick: pick in Settings. Hold BRAKE / ▼ when stopped to reverse. Keyboard arrow keys or W A S D also work |
| Park / get out | PARK and GET OUT buttons on the dashboard |
| Navigate | Navigate on a building card, then follow the blue GPS line |
| Look around | Drag the city |
| Zoom | Pinch |
| Rotate camera | Two-finger twist (also works inside buildings) |
| Select a building | Tap it |
| Enter a building / get in a car | Context buttons at the bottom of the screen |

Settings include HD (GPU) or classic rendering, Low/Medium/High graphics quality, a 30/60 FPS cap, music and effects volume, the radio station, an FPS counter, pausing while menus are open, and a left-handed layout.

## Building

Requirements: JDK 17 or newer and the Android SDK (API 35).

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # minified, signed with the debug key for sideloading
./gradlew testDebugUnitTest      # simulation tests + offscreen render snapshots
```

The Windows version:

```bash
./gradlew :desktop:run           # play it from source (also runs on Linux)
./gradlew :desktop:packageExe    # the installer, on Windows: desktop/build/compose/binaries/main/exe/
./gradlew :desktop:packageMsi    # the same as an .msi
./gradlew :desktop:test          # window render snapshots in desktop/build/snapshots/
```

Every push also builds both APKs and the Windows installer on GitHub Actions. Download them from the run's **Artifacts** section.

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

desktop/src/main/kotlin/
  com/hustlecity/game/desktop/  The Windows window, keyboard, and the OpenGL thread for HD graphics
  android/, androidx/           Desktop versions of the few Android APIs the game calls: Canvas and Paint on Skia,
                                GLES30 on desktop OpenGL, AudioTrack on Java Sound, settings in a file
```

The desktop module compiles `app/src/main/java` itself, so a change to the game lands in both versions. Only `MainActivity.kt`, `render/gpu/GlWorldView.kt` and `ui/GpuHost.kt` are Android-only; the desktop has its own versions of them.

## Free assets

Everything else in the game is generated in code. These came from free libraries, with their licences:

| What | From | Licence | Baked by |
| --- | --- | --- | --- |
| HD photo materials (11 surfaces) | [ambientCG](https://ambientcg.com) | CC0 (public domain) | `tools/assets/textures.py` into `app/src/main/resources/textures/` |
| Car models (sedans, taxi, police cruiser, SUV, sports cars) | [Quaternius Cars Pack](https://quaternius.com/packs/cars.html) | CC0 (public domain) | `tools/assets/cars.py` into `app/src/main/resources/models/cars.bin` |
| Trailer logo type: Anton | [Google Fonts](https://fonts.google.com/specimen/Anton) | SIL Open Font License | `tools/cinema/fonts/` (trailer tools only) |

The scripts download their sources (with each site's own download links) and can be re-run to rebake. Each resource folder has a `CREDITS.txt` listing the exact assets.

## Trailers

`tools/cinema/` cuts the trailers from shots flown through the real game (`CinematicTest`). The New York story trailer is `newyork.py` with its song `newyork_score.py`:

```bash
HC_CINEMA=1 HC_SHOTS=n ./gradlew :app:testDebugUnitTest --tests '*cinema.CinematicTest*'   # record the shots
xvfb-run -a ./gradlew :desktop:test --tests '*CinemaRender*' -PcinemaIn=app/build/cinema -PcinemaOut=frames -PcinemaShots=n
python3 tools/cinema/newyork_score.py score.wav
python3 tools/cinema/newyork.py frames app/build/cinema score.wav trailer.mp4
```

The banger trailer, `banger.py`, cuts the city, a police chase and a hero walk (shots `n01`..`n21` and `b01`..`b05`) to a 152 BPM trap beat: every cut on the beat, zoom punches, shake, whip pans, slammed titles and a letterbox that snaps open on the drops. Its music and sound effects come from [Mixkit](https://mixkit.co) under their free licences (use in videos, no redistribution of the files), so the script downloads them instead of the repository keeping them:

```bash
HC_CINEMA=1 HC_SHOTS=n,b0 ./gradlew :app:testDebugUnitTest --tests '*cinema.CinematicTest*'
python3 tools/cinema/banger.py --fetch audio
python3 tools/cinema/banger.py frames app/build/cinema audio banger.mp4 --plan plan.txt
xvfb-run -a ./gradlew :desktop:test --tests '*CinemaRender*' -PcinemaIn=app/build/cinema -PcinemaOut=frames -PcinemaPlan=plan.txt
python3 tools/cinema/banger.py frames app/build/cinema audio banger.mp4
```

`CinemaRender` draws the recordings with the game's own HD renderer through the desktop build's OpenGL; `tools/cinema/render.mjs` does the same in a headless browser.

*Hustle City is an independent fan project. It is not affiliated with Hovgaard Games or Big Ambitions. All names of in-game companies are fictional.*
