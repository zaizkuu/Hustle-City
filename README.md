# Hustle City

A business life-simulation game for Android, inspired by *Big Ambitions*. Start with a small apartment, a beat-up car and $5,000. Rent a storefront, furnish it, stock it, hire staff, and grow a business empire in a living 3D city.

Native Kotlin + Jetpack Compose with a custom 3D engine. In HD mode the world is drawn by an OpenGL ES 3 renderer with per-pixel lighting, real-time sun shadows, procedural materials (brick, stone, glass that reflects the sky, asphalt, sidewalk slabs, grass, water, wood) and 4x anti-aliasing; a lightweight Canvas renderer is the fallback for older devices.

## Features

**Five cities**
- **Hustle City**, the original Manhattan-style grid (or a random one): 6 neighborhoods, about 600 buildings with addresses, parks, parking lots and gas stations.
- **New York**: Manhattan and Brooklyn across the river, Central Park, Times Square's screens and yellow cabs.
- **Manila**: the bay and Roxas Boulevard, the Pasig and its bridges, walled Intramuros, Rizal Park, Binondo and Makati. Jeepneys old and new, tricycles and mopeds; spaghetti wires, banderitas and basketball hoops; the Manila Cathedral, Quiapo and Binondo churches, the Rizal Monument, City Hall's clock tower, the Chinatown arch and the MOA Eye; LRT-1 overhead.
- **Tokyo**: keep left. The Yamanote loop and Chuo line, Tokyo Tower, the Shibuya scramble where every car waits while the crowd crosses, lantern-lit alleys and vending machines.
- **Seoul**: the Han River, Gwanghwamun, N Seoul Tower, hanok roofs and Gangnam's boulevards.
- Each city has its own street names, people's names, architecture, trees, traffic mix and money: pesos, yen and won (or dollars everywhere, in Settings).
- You can ride the trains: walk to a station on LRT-1, the Yamanote or the Chuo line, pick a stop and pay the fare.

**The city**
- Buildings with personality: brick walk-ups with fire escapes and water towers, brownstones with stoops and bay windows, art deco towers with setbacks and spires, glass skyscrapers, stone offices, modern blocks with balconies and industrial sheds with sawtooth roofs. Storefronts dress up for their business (striped awnings, neon, cinema marquees, window displays, sidewalk tables) and close their shutters at night.
- Street life in detail: lamp posts that light the street, hydrants, benches, bus shelters, newspaper boxes, bike racks and planters; parks with fountains; trees that blossom in spring, turn red and gold in autumn and stand bare in winter. Cars have wheels, lights and brake lights, and taxis cruise the avenues.
- A day/night cycle with sun-tracking shadows, lit windows and glowing street lamps.
- Traffic with traffic lights: cars plan their turns and take smooth arcs, keep their distance and give way to pedestrians. Pedestrians cross on the walk signal, step aside for each other and walk into open shops. Buildings turn see-through when they block your view.
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
- **Fleets**: vans with drivers run delivery routes from wholesalers and warehouses to your stores, and stock reorders itself when it runs low.
- **Staff in depth**: shifts and fatigue, perks, training courses, managers, and rivals who try to poach your best people.
- **Property**: leases renew (and rents rise) every 60 days. Take Real Estate Investing to buy buildings, let the ones you aren't using to tenants, or sell them.
- **Deals and markets**: buy out a rival's store, trade shares in the city's companies, take over a rival you own more than half of, and park cash in city bonds.
- **Achievements**: 25 milestones from your first sale to the top of the rankings, listed in Persona.
- **Three save slots**, so a game in Manila and another in Tokyo sit side by side.
- Bankruptcy after three midnights deep in debt (or keep playing in sandbox mode), and a victory screen when you're #1 with a $5M net worth.

**Sound**
- A phone radio with four procedurally generated stations (Lo-fi, Pop, Jazz, Electro), plus effects for purchases, the cash register, doors, notifications and the car engine. No audio files are shipped; everything is synthesized at runtime.

**Management** (on the in-game HustlePhone)
- **BizHub** has the tabs Insight, Inventory & Pricing, Schedule, Deliveries, Marketing and Settings.
- **Fleet** plans van routes and drivers; **Markets** is the stock exchange and bond desk; **Realty** lists what's for rent and manages what you hold.
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

Every push also builds both APKs on GitHub Actions. Download them from the run's **Artifacts** section.

The unit tests use Robolectric's native graphics to render the city, interiors and every main UI screen to PNG files in `app/build/snapshots/`. `AudioTest` renders each radio station to WAV files in `app/build/audio/`. `BalanceReportTest` writes a per-business-type profit report to `app/build/balance.txt`.

## Project layout

```
app/src/main/java/com/hustlecity/game/
  data/      Catalog: products, furniture models, business types, vehicles, courses, places
  model/     Serializable save state
  world/     City generators (the classic grid and the planned maps in world/maps), roads, traffic and pedestrians
  render/    Projector, triangle batcher, city / interior renderers, landmarks, railways, 3D thumbnails; render/gpu is the OpenGL ES 3 path
  audio/     Procedural music stations, sound effects and the audio mixer
  engine/    Game loop, economy simulation, interiors, actions, objectives, saving
  ui/        Compose HUD, phone apps, BizHub, shops, menus
```

`tools/cinema/` holds the trailer pipeline: `CinematicTest` records camera shots from the real game, `render.mjs` draws them with the game's shaders in headless Chromium, and `edit.py` / `manila.py` with `score.py` / `manila_score.py` cut and score the two trailers.

`tools/blender/` builds the 3D art in Blender (driven over the Blender MCP add-on's socket on localhost:9876 by `bl.py`): 229 models of furniture (82), goods (50), street props and trees (36), vehicles (16), buildings in every architecture style (12), the cities' landmarks (8), and the parts people are posed from (25). Every model is a script; nothing is modelled by hand in the file.

- **The library.** `kit.py` is the modelling kit, `furniture.py` / `furniture2.py`, `items.py`, `props.py`, `vehicles.py` / `vehicles2.py`, `buildings.py`, `landmarks.py` and `people.py` hold the models, `build_all.py` builds and lays them out, and `export.py` saves `art/hustle_city_assets.blend`, one `.glb` per model and preview sheets in `art/previews/`.
- **People.** A person is rigid parts on a 15-bone skeleton (`rig.py`): every limb ends in a ball on its joint, every outline is a smooth curve through its cross-sections, and each part is modelled at four levels of detail rather than decimated. `rig.py` also writes the 22 animation clips (walking, running, three ways of standing about, sitting, eating, two dances, carrying, pushing, working a counter, reaching for a shelf, the gym machines, waving, talking) and bakes them into Blender Actions on the `HC Rig` armature.
- **Into the game.** `export_game.py` packs every model of the library into `app/src/main/assets/models.hcm` (with lighter copies for distance, and a few variants: trees bare for winter, strings across the street without their posts, the Ferris wheel split so its rotor turns) and the clips into `anims.hca`; `render/Meshes.kt` reads it. In HD mode at Medium and High quality:
  - **landmarks** (`render/Landmarks.kt`): Tokyo's lattice tower (lit at night), the torii pair, the monument (flying each city's own flag), the palace gate, the cathedral, the Ferris wheel (its rotor turning, the gondolas hanging level), the Chinatown arch stretched to the street it spans, and city hall;
  - **buildings** (`render/BuildingPainter.kt`): a plain lot whose size is near its style's model is that model, stretched to fit, in its own facade color, its windows lighting up in groups at night; storefronts, FOR RENT signs and named places stay painted;
  - **trees** (`render/TreePainter.kt`): oak, park oak, pine, cherry, palm and ginkgo, the leaves colored by the season and bare in winter;
  - **street props** (`render/StreetProps.kt`, `WorldRenderer.kt`): every prop is a model, including the traffic lights (the current lamp lit), Manila's power poles (the wires still run pole to pole), food carts and market stalls, the fountain (its water still plays) and the lantern and bunting strings; plus bollards, parking meters, street-name signs, cash machines, phone booths, café tables, dumpsters, sidewalk works, bushes, billboards and izakaya lanterns, placed in a pass of their own;
  - **vehicles** (`render/CarPainter.kt`, `render/Railways.kt`): every traffic style (the short sedans are the old hatchback), and the elevated trains;
  - **furniture** (`render/PartSorter.kt`): every piece; displays with live stock in your own store keep their boxes so each product sits in its slot, but the products on them are the goods models (`render/StockPainter.kt`, `render/Goods.kt`); managers get an office chair, and the new Office Safe has its cash bag and takings;
  - **people** (`render/People.kt`): hold what they picked off the shelf as its model, carry paper bags, boxes, baskets and shopping carts, eat what the place serves, lift dumbbells, and out on the street carry briefcases, handbags, phones and cameras, some in hats.

  Low quality and the classic renderer keep the box models (every new prop has boxes of its own). `MeshModelsTest` checks the pack, that every library model is drawn somewhere (`everyModelIsUsed`), the clips and the triangle budgets, and dumps frames that `view_frames.py` renders in Blender.
- **The cinematic trailer.** `tools/blender/trailer/` stages and shoots "Every empire starts with one door" entirely in Blender: `set.py` builds an avenue out of the library (and checks that nothing stands in a road and every crossing runs kerb to kerb), `light.py` is the sky, sun, clouds, haze and the city's lights from blue hour to night, `cast.py` puts people, traffic, rain and blossom into shots, `shots.py` / `shots2.py` are the 17 shots, `score.py` synthesises the music, `render.py` renders the frames in a background Blender, `edit.py` cuts the film in the sequencer, and `viewport.py` / `breakdown.py` set the raw viewport beside the finished frames. Output goes to `art/trailer/`.

`MapAuditTest` walks every map for things out of place (a crossing painted across a second road or into the river, a tree or a prop in a road or a building, two props on one spot) and fails on any; `world/Crossings.kt` is the one rule the renderer paints crossings by.

*Hustle City is an independent fan project. It is not affiliated with Hovgaard Games or Big Ambitions. All names of in-game companies are fictional.*
