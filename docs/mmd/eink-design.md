# E Ink design notes — AnkiDroid MMD

This fork restyles AnkiDroid for the E Ink panel of a Mudita Kompakt
(MuditaOS K 1.6.0, Android 12, 480×800 @ 213dpi → 360×601dp). These are the
rules it follows, where each came from, and what is deliberately not done.
Check every change against this document. The full plan is [PLAN.md](PLAN.md);
progress is logged in [STATUS.md](STATUS.md).

## Sources, in order of authority

1. **`mudita/MMD` — Mudita Mindful Design** (Apache 2.0, `com.mudita:MMD:1.0.2`).
   Mudita's own Compose component library. **Read a component's source before
   using it** — `git clone --depth 1 https://github.com/mudita/MMD.git`.
2. **`mudita/mmd-migrator`** — Material 3 → MMD component mappings.
3. **The Kompakt itself** — system apps captured over adb (calibration in Phase 0).
4. **Mudita's open-source Kompakt apps** — `MuditaOS-K-Chess-opensource`,
   `MuditaOS-K-Calculator-opensource`.
5. **Mudita support docs** for Kompakt (Notes, Contacts, Recorder).
6. **`mudita/MuditaOS`** — the older Pure. Historical only.
7. **The Loop Habit Tracker MMD fork** (`loop-habit-tracker-fork/docs/`) — lessons
   already paid for on this exact device.

## Rules

1. **Black and white only.** Emphasis is inversion, never grey or tint.
   Override Material's grey *roles* (secondary text, hint, highlight) — most
   greys never appear in a layout file.
2. **Lato everywhere, nothing below 14sp.** Scale from `TypographyMMD.kt`:

   | Role | Size |
   | --- | --- |
   | headlineLarge | 28sp |
   | titleLarge | 24sp |
   | titleMedium / bodyLarge | 20sp |
   | bodyMedium / labelLarge | 18sp |
   | titleSmall | 16sp |
   | bodySmall / labelMedium | 15sp |
   | labelSmall | 14sp |

3. **Structure:** bold title → 3dp rule → body. Actions always visible in the
   header. Lists page, not scroll.
4. **Nothing animates.** No ripples, transitions, fades, fling momentum,
   overscroll, item animators, indeterminate spinners, or text that ticks every
   second. Dragging tracks the finger; nothing moves after the finger lifts.
5. **Touch targets** 48dp minimum with 32dp glyphs; answer buttons 56dp.
6. **Buttons, three levels:** solid (the one committing action), outlined, bare text.
7. **Check whether a screen should exist before redesigning it.**

## Interaction patterns

Taken from how Mudita's own Kompakt apps behave.

| # | Pattern | Evidence |
| --- | --- | --- |
| **P1** | An item's actions live on **the item's own page**: tap item → page → header icons. Destructive actions confirm. | Contacts: select contact → Pencil → Bin → confirm |
| **P2** | **Bulk actions use selection mode:** header Edit icon → checkboxes → Delete → confirm. Built only when a screen needs it. | Notes; Recorder |
| **P3** | An **in-screen menu is a bottom-anchored panel** opened from a header icon: 3dp top rule, title, rows or full-width buttons, no dim, tap outside or back dismisses. | Chess `GameMenuDialog.kt`, `GameplayDialog.kt`, `DialogHost.kt` |
| **P4** | **A choice from a list is a sheet** of radio rows. | Kompakt Sound/Bluetooth; Loop fork |
| **P5** | **Confirmation/info is a centred panel:** white, 3dp black border, no elevation, no dim. | Chess `CheckInfoDialog`/`LoadingDialog`; Loop `PanelMMD` |
| **P6** | **Long-press is not a menu trigger.** | Calculator uses it only to copy text; manual mentions it only under accessibility |

## Dividers

- **3dp solid rule** under every header and on the top edge of P3 panels
  (`DividerDefaultsMMD.Thickness`).
- **Dashed hairline** between list rows (indented to the text) and between P3 menu
  rows. MMD's own `DropdownMenuMMD` KDoc separates items with `DashedDivider()`,
  which the library does not ship, so the kit builds `DashedDividerMMD`. Chess
  draws dashes as `Stroke(1.5f, dashPathEffect([8f, 10f]))`. Starting values
  1dp stroke / 6dp dash / 7.5dp gap — **calibrated on device in Phase 0**.

## Exceptions

- **E1** WebView content (card, deck options, statistics, card info) scrolls
  continuously. CSS sets `scroll-behavior: auto`.
- **E2** Halftone (4×4 Bayer, 1px dot) appears only in the statistics graphs.
  Text and marks under 6px snap to black.

## MMD 1.0.2 gaps — handled once in `com.ichi2.compose.mmd`, never rediscovered

| Gap | Consequence | Fix |
| --- | --- | --- |
| `eInkColorScheme` leaves six `surfaceContainer*`/`Bright`/`Dim` roles `Unspecified` | TextField, TimeInput, Card, SearchBar render unfilled | Fill with white in our theme |
| `TopAppBarMMD(showDivider=true)` draws 1dp | No heavy rule | `showDivider=false` + `HorizontalDividerMMD()` |
| `SnackbarMMD` action is `inversePrimary` (white on white); divider 1dp | Invisible action | Explicit colours + real 3dp rule |
| `LazyColumnMMD` pages by item (step 4) | One tall item can't scroll; hidden items count; `alpha=0` until counted; gutter only while scrollable | Emit real items; handle the gutter |
| `TextFieldMMD` min 280×56dp | No dense variant | Design forms around it |
| Inert `RadioButtonMMD` measures 24dp | Label inset is 24+16dp | Bake into row components |
| No dialog, IconButton, ListItem, Scaffold, dashed divider, icon set | — | Kit components; existing drawables via `painterResource` |

## What this app does as a result

- **Review-only.** No note editor, card browser, template/note-type editors,
  image occlusion, whiteboard, audio recording, or TTS. Card audio playback stays.
- **One theme.** No dark/black/plain themes, no night resources.
- **No reporting to upstream.** ACRA crash reports and Google Analytics are removed.
- **No home-screen widgets** (the Kompakt launcher has no widget host);
  `WidgetStatus` and `DayRolloverAlarm` stay because notifications depend on them.
- **Separate identity and storage** (`com.ichi2.anki.mmd`, app-private
  collection) until the verified cutover from AnkiDroid 2.24.1.
