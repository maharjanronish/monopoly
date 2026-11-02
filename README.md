# 🎩 Monopoly for Android (Kotlin + Jetpack Compose)

[![Android APK Build](https://github.com/maharjanronish/monopoly/actions/workflows/android-build.yml/badge.svg)](https://github.com/maharjanronish/monopoly/actions/workflows/android-build.yml)

A complete, exact-rules Monopoly for 2–6 players on **one phone** (hot-seat /
pass-and-play), built to be as light as possible:

| | |
|---|---|
| Install size | ~2 MB release APK (~5 MB debug) |
| Images / audio files | **0** — the whole board is drawn in code |
| Permissions | **none** — no internet, no ads, no tracking |
| RAM | single Activity, one screen, minimal state |
| Randomness | `java.security.SecureRandom` (cryptographic RNG) |
| Min Android | 7.0 (API 24) |

---

## 1. Requirements

- **Android Studio** Koala (2024.1) or newer — download from
  https://developer.android.com/studio
- A phone with Android 7.0+ (or an emulator), USB debugging enabled
  (Settings → About → tap *Build number* 7× → Developer options → USB debugging)

Nothing else. The project pins its own Gradle 8.7 wrapper, AGP 8.5.2,
Kotlin 2.0.0 and Compose BOM 2024.06.00, and Android Studio ships the needed
JDK 17.

## 2. Build & run

1. Unzip this folder.
2. In Android Studio: **File → Open** → select the `Monopoly` folder
   (the one containing `settings.gradle.kts`).
3. Wait for Gradle sync (first sync downloads Gradle + libraries, ~2–5 min).
4. Connect your phone (USB), pick it in the device dropdown, press **▶ Run**.

That's it — the app installs and opens in portrait.

## 3. Get the APK without Android Studio

**Easiest:** go to the [Actions tab](https://github.com/maharjanronish/monopoly/actions) →
latest **Android APK Build** run → scroll to **Artifacts** → download
`Monopoly-release-APK` (~2 MB, R8-minified, debug-signed = directly
installable) or `Monopoly-debug-APK`.

Or build locally after one successful build, the APK is at:

```
app/build/outputs/apk/debug/app-debug.apk
```

Copy it to any phone and open it to install (allow "install from unknown
sources" once).

### Smaller release APK (~2 MB)

```
./gradlew assembleRelease        # or Build → Generate Signed App Bundle/APK in Studio
```

R8 shrinking is already enabled for release builds. For a quick unsigned
release for personal use you can also use *Build → Select Build Variant →
release* then Run.

## 4. How to play

1. **Setup** — choose 2–6 players, tap an emoji token for each player, type
   names (optional). Every player starts with **$1,500**
   (2×$500, 2×$100, 2×$50, 6×$20, 5×$10, 5×$5, 5×$1 — the official stack).
2. **Roll** — tap the big red button (or the dice). Doubles roll again;
   3 doubles in a row = straight to Jail.
3. **Land** — buy the property at the printed price, or decline and it goes
   to a **live auction** (minimum raise $10, high bidder can't pass).
4. **Manage** — the 🔨 button opens building & mortgages: build houses
   evenly on complete color sets, upgrade to hotels, mortgage/unmortgage.
   The bank really has only **32 houses and 12 hotels**.
5. **Trade** — the 🤝 button: pick a partner, tick properties, set cash and
   jail-card amounts both ways, then hand the phone over for accept/decline.
6. **Debt** — if you can't pay, the dialog lets you sell/mortgage until you
   can, or declare bankruptcy (assets go to the creditor).
7. Tap **any tile** any time to see its full title deed and current rent.
8. Tap a **player card** to inspect their holdings and net worth.

The game **auto-saves after every action**. Close the app whenever you like —
reopening offers to resume. If you close mid-animation or mid-dialog, the
game resumes at the start of that player's turn (they simply roll again).

## 5. Rules coverage

Implements the official US (Atlantic City) rules: salary $200, buy-or-auction,
doubled unimproved rent on full sets, even-build rule, house/hotel bank
stock limits, half-price sell-back, mortgages (+10% interest to lift, no
rent while mortgaged), jail (fine / card / doubles × 3), income tax choice
($200 or 10% of total worth), luxury tax $100, all 16 Chance + 16 Community
Chest cards (including repairs and per-player payments), nearest-railroad
×2 and nearest-utility ×10 card effects, trading, bankruptcy transfer with
10% mortgage fees, last-player-standing victory.

### Documented simplifications (rare corner cases)

- **Auction raise step is $10** (the rulebook has no fixed increment).
- **Bankrupt to the Bank**: properties return to the bank as unowned
  (the rulebook auctions them; this keeps the flow simple).
- **Selling a hotel** yields half the house price; if the bank has ≥4 houses
  in stock you get 4 houses back on the tile (per the FAQ), otherwise 0.
- **Mortgaged property received in a trade** transfers as-is with no
  immediate 10% fee (the fee applies when lifting the mortgage).
- **Income tax 10%** is rounded down to whole dollars.
- Closing the app during a dialog (auction/card/buy) resumes that turn from
  its start.

## 6. Project structure

```
app/src/main/java/com/local/monopoly/
├── MainActivity.kt          entry point
├── game/
│   ├── Model.kt             40 spaces, 32 cards, state data classes
│   ├── Dice.kt              SecureRandom dice
│   ├── Engine.kt            the full rules state machine
│   └── SaveStore.kt         JSON auto-save (SharedPreferences)
└── ui/
    ├── theme/Theme.kt       colors
    ├── Widgets.kt           buttons, tokens, money formatting
    ├── SetupScreen.kt       player count / names / tokens
    ├── Board.kt             the code-drawn board
    ├── Dice3D.kt            tumbling dice
    ├── PlayerStrip.kt       player cards
    ├── GameScreen.kt        orchestration + action area
    ├── Dialogs.kt           deed/buy/card/tax/debt/menu/rules/game-over
    ├── AuctionDialog.kt     live auction
    ├── ManageDialog.kt      build & mortgage manager
    └── TradeDialog.kt       trade builder + review
```

## 7. Troubleshooting

- **"Gradle sync failed / unsupported Gradle version"** → update Android
  Studio to the latest stable, then File → Sync Project with Gradle Files.
- **SDK 34 not installed** → Studio offers a one-click "Install missing
  platform" link in the sync error — click it.
- **gradle-wrapper.jar missing** (if you copied files selectively) → in
  Studio: File → Sync, or run `gradle wrapper --gradle-version 8.7` once.
- **Phone not detected** → enable USB debugging (see §1) and accept the
  RSA prompt on the phone.
- **Want to change colors/texts?** → board colors live in
  `game/Model.kt` (`Group` enum), UI colors in `ui/theme/Theme.kt`.

Have fun — and remember: the bank never breaks! 💰
