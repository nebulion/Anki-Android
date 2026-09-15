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
2. **The system font everywhere, nothing below 14sp** (owner's call, 2026-09-15: always the phone's font, never a bundled one). Scale from `TypographyMMD.kt`:

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
| **P5** | **A confirmation is a bottom panel:** 3dp full-width top rule, bold title, regular body, then full-width stacked buttons — the committing action solid black, the escape outlined. No dim. A centred bordered panel is only for non-interactive info. | Notes delete prompt (measured, see Calibration); centred: Chess `CheckInfoDialog`/`LoadingDialog` |
| **P6** | **Long-press is not a menu trigger.** | Calculator uses it only to copy text; manual mentions it only under accessibility |

## Dividers

- **3dp solid rule** under every header and on the top edge of P3 panels
  (`DividerDefaultsMMD.Thickness`).
- **Dotted hairline** between list rows and between P3 menu rows: 1px, 2dp dots,
  2dp gaps, measured on Kompakt Settings and Notes. MMD's own `DropdownMenuMMD` KDoc
  separates items with `DashedDivider()`, which the library does not ship, so the kit
  builds `DashedDividerMMD` (pixel-snapped). Inset to the label when rows have icons
  (Settings), from the edge when they do not (Notes).

## Calibration — measured on the Kompakt (MuditaOS K 1.6.0, 480×800 @ 213dpi, 1dp ≈ 1.33px)

Captured with `adb exec-out screencap` and `uiautomator dump` while the owner held each screen;
nothing was tapped. Raw files are in the session scratchpad (`calib/`).

### Settings (list screen)

| Element | Pixels | dp | Kit value |
| --- | --- | --- | --- |
| Header rule | 4px solid, full width | ≈3dp | `HorizontalDividerMMD` 3dp |
| Header (status bar bottom → rule) | ≈85px | ≈64dp | `TopAppBarMMD` 64dp |
| Title | bold, 39px line box | titleLarge 24sp | `ScreenHeader` |
| Row | 85px tall, clickable 0–425px | 64dp, 320dp wide | `RowDefaults.MinHeight` 64dp |
| Row label | regular weight, 34px box, starts x=85px | bodyLarge 20sp, inset 64dp | `RowDefaults` |
| Leading icon | left column, x≈22px | 16dp margin | optional leading icon |
| Row divider | **dotted**, 1px, dots ~2.5px, gaps ~2.5px (period ≈5.3px), from x=86px (label start) to the gutter | hairline, 2dp/2dp | `DashedDividerMMD`, `RowDivider` |
| Trailing arrow | thin line `>`, glyph 11×22px, 3px stroke, right edge x=392px | ≈8×16dp, ≈2dp stroke | `ic_baseline_chevron_right_24` at 32dp |
| Scrollbar | 11px track/thumb at x=437–447; top arrow dotted (disabled), bottom arrow filled | 8dp | `LazyColumnMMD` (matches `sliderBackgroundWidth` 8dp) |
| Scrollbar gutter | rows end at 425px | ≈40dp | `LazyColumnMMD` gutter |

- The row divider is dotted, not dashed like the Chess stats ring. The first draft of
  `DashedDividerMMD` (6dp dashes) was wrong and has been replaced.
- MMD's filled triangle is the scrollbar arrow; the row arrow is a separate line chevron.
- Settings rows are **larger than an app list needs**. The owner's direction: where the MMD
  library and Mudita's apps disagree, choose, and keep the choice swappable.

### Notes (list screen with header actions)

| Element | Pixels | dp | Note |
| --- | --- | --- | --- |
| Header actions | touch 63px each (search, pencil, `+`) | ≈48dp | matches `HeaderAction` touch target |
| Header glyphs | ink 37×36px, 3px stroke | ≈28dp, ≈2dp stroke | kit default 32dp; `KompaktSystem` should use 28dp |
| Primary header action `+` | solid black rounded rect 56×47px, corner ≈6–8px, white `+` 28×29px | ≈42×35dp, ≈5dp corner, 21dp glyph | a filled header action, not in the kit yet |
| Row (title + preview) | 107px | ≈80dp | two-line row; title bold, preview regular |
| Row title | bold, 34px box, ink 21px | bodyLarge 20sp bold | |
| Row preview | regular, 29px box, ink 23px | ≈bodyMedium 18sp | black, never grey |
| Row divider | dotted 1px, same 2–3px pattern as Settings, x=16–439px | from ≈12dp, no icon inset | `RowDivider(hasLeadingIcon = false)` |
| List background | grey value **250** (#FAFAFA); header 255 | — | system list is near-white; the kit keeps pure white (`eInkColorScheme`) |
| Scroll arrows | 75px touch targets | ≈56dp | MMD scrollbar arrows |
| Selection | pencil icon enters selection mode | — | pattern P2, confirmed |

### Notes (selection mode and delete confirmation)

Pencil → selection mode → Delete. The owner performed the taps; nothing was deleted by automation.

| Element | Pixels | dp | Kit |
| --- | --- | --- | --- |
| Selection header | `X` close (23dp ink) at left, title at 83px, outlined count action **"Delete 1"** at right | — | `SelectionHeader` (P2), when needed |
| Count action | outlined, 131×48px, 3px stroke, corner ≈7px | ≈98×36dp, 2dp stroke, ≈5dp corner | outlined header text action |
| Checkbox | 32px, 3px stroke; checked = solid black with white tick | 24dp, 2dp stroke | `CheckboxMMD` (23dp) |
| Row title in selection mode | starts at 76px | ≈57dp | checkbox column |
| Confirmation | **bottom panel**: 4px full-width rule at y=467, white to the bottom edge | 3dp rule, ≈250dp tall | `ConfirmPanel` (P5) |
| Confirmation title | "Delete 1 note?", bold, 40px box, x=16px | titleLarge 24sp bold, 12dp margin | |
| Confirmation body | "Deleting notes can't be undone", regular, 34px box | bodyLarge 20sp | |
| Primary button | "Delete", solid black, 448×75px, corner ≈12px, white bold label | full width, 56dp tall, ≈9dp corner | `ButtonMMD` |
| Secondary button | "Back", 3px outline, same size and corner | 2dp outline | `OutlinedButtonMMD` |
| Button spacing | margins 16px, gap 16px, 16px above the bottom edge | 12dp | |
| Behind the panel | list keeps its 250/255 values | — | **no dim** |

### Token profiles — how disagreements are resolved

All measurements where the sources disagree live in `MmdTokens`; components read
`LocalMmdTokens`, so the whole app switches with one argument:
`MmdTheme(tokens = MmdTokens.KompaktSystem)`.

| Token | `Library` (default) | `KompaktSystem` |
| --- | --- | --- |
| Row height | 56dp (MMD `RadioButtonMMD` sample) | 64dp (Settings, 85px) |
| Label inset with icon | 56dp | 64dp (Settings) |
| Chevron | 24dp | 32dp (≈16dp glyph, as in Settings) |
| Panel corners | 8dp (`ButtonDefaultsMMD`, `CardDefaultsMMD`) | 16dp (Chess panels) |
| Dotted divider, header glyph 32dp, border 3dp | same in both | same |

`Library` is the default because it follows MMD wherever MMD specifies a value and fits more
decks on a 601dp-tall screen. Values both sources agree on (black and white, Lato, the 3dp
header rule) are not tokens.

- The framebuffer is 8-bit greyscale and the system's own dots and glyph edges are
  anti-aliased (edge values 38–220). The panel dithers them. The kit still snaps its own
  dotted line to whole pixels.

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
