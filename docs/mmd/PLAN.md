# AnkiDroid MMD — plan

A personal fork of AnkiDroid rebuilt as a **review-only flashcard app for the Mudita Kompakt**,
on Mudita's own Compose design system `com.mudita:MMD`.

## Context

AnkiDroid is a large Material 3 View app:
- 214 layouts, 34 menus, 55 DialogFragments and 107 AlertDialog builders
- five themes and colour-coded answer buttons
- animation throughout

On the Kompakt's E Ink panel that design fights the hardware. Ripples and fades leave ghosts, greys
dither into smears, and colour carries no information.

The same job was already done on Loop Habit Tracker
(`C:\Users\Antonio\loop-habit-tracker-fork\docs\eink-design.md` + `PROJECT-STATUS.md`). Its rules
and its hard-won MMD lessons carry over directly.

**The most important part is MMD in Compose:** import MMD components and never imitate them. The
end state is Compose-only chrome.

AnkiDroid 2.24.1 is on the device today with the real collection. The fork **replaces it** at the end.

## Decisions (from the user)

| Topic | Decision |
|---|---|
| Design system | `com.mudita:MMD` in Compose. Compose-only end state. No XML reskin phase. |
| Relationship to 2.24.1 | Fork replaces it after a verified migration (Phase 7). |
| Card CSS | Forced monochrome, **no exceptions**. |
| Features kept | **None** of: whiteboard, audio recording/TTS, image occlusion, note adding/editing. The Kompakt is review-only. |
| Card Browser | **Deleted.** |
| New cards arrive by | **Both** AnkiWeb sync and `.apkg`/`.colpkg` file import. |
| Card audio (`[sound:]`) | **Playback kept**, with one replay action. |
| Navigation | **MuditaOS style.** The deck list is home, actions sit in the header, everything else is drill-down pages. No drawer, no bottom bar. |

## Design rules (eink-design.md, applied to AnkiDroid)

1. **Black and white only.** Emphasis is inversion, never grey or tint. Override Material's grey
   *roles* (secondary text, hint, highlight), not just layout colours.
2. **Lato everywhere, nothing below 14sp.** The MMD scale is 28/24/20/18/16/15/14.
3. **Structure:** bold title, then a 3dp rule, then body. Actions are always visible in the header.
   Lists page, they don't scroll.
4. **Nothing animates:**
   - no ripples, transitions, fades, fling momentum, overscroll or item animators
   - no indeterminate spinners
   - no text that ticks every second
   Dragging still tracks the finger; motion that continues after the finger lifts goes.
5. **Touch targets:** 48dp minimum with 32dp glyphs. Answer buttons are 56dp.
6. **Buttons have three levels:** solid (the one committing action), outlined, bare text.
7. **Check whether a screen should exist before redesigning it.**

### Interaction patterns (researched, replaces the earlier "action sheets" exception)
Taken from how Mudita's own Kompakt apps behave. No generic Android habits.

| # | Pattern | Evidence |
|---|---|---|
| **P1** | **An item's actions live on the item's own page.** Tap the item → its page → actions as header icons. Destructive ones confirm with a pop-up. | Contacts: "select contact → Pencil icon → Bin icon → Delete contact", with a confirmation pop-up ([support](https://support.mudita.com/en/support/solutions/articles/77000579753-how-to-manage-contacts-on-mudita-kompakt)) |
| **P2** | **Bulk actions use selection mode.** Header Edit (pencil) icon → checkboxes → Delete → confirm. Built only if a screen needs it; none currently does. | Notes: "Edit icon → Check box → Delete → Delete" ([quick start](https://mudita.com/products/phones/mudita-kompakt/quick-start/notes-app/delete-notes/)). Recorder: "Pencil (Edit) icon → check boxes → Delete → Delete again to confirm" ([support](https://support.mudita.com/en/support/solutions/articles/77000596772-how-to-delete-voice-recordings-)) |
| **P3** | **An in-screen menu is a bottom-anchored panel opened from a header icon.** Title, a 3dp top rule, rows or full-width buttons, no dim; a tap outside or back dismisses it. | `MuditaOS-K-Chess-opensource`: `GameMenuDialog.kt` (3dp top rule, title, stacked Primary/Secondary buttons + switch), bottom-aligned by `GameplayDialog.kt` inside `DialogHost.kt` (no scrim, click-outside + `BackHandler` dismiss) |
| **P4** | **A choice from a list is a sheet** of radio rows. | Kompakt Sound/Bluetooth (Loop screenshots); Loop's shipped `ModalBottomSheetMMD` sheets |
| **P5** | **Confirmation or info is a centred panel:** white, 3dp black border, no elevation, no dim. | Chess `CheckInfoDialog`/`LoadingDialog` (3dp border); Loop `PanelMMD` |
| **P6** | **Long-press is not a menu trigger.** Kompakt only uses it for a copy affordance on text (Calculator `attachLongClickCopyPopup`) and to select messages. This app uses no long-press. | Calculator `MainActivity.kt:720-763`; MuditaOS K manual mentions long press only under accessibility |

**Corner radius** is 8dp by default: MMD `ButtonDefaultsMMD`/`CardDefaultsMMD`. Chess's pre-MMD
`kompakt-ui` panels use 16dp; measure on device in Phase 0 and pick one.

### Dividers (researched)
- **3dp solid rule:** under every header and on the top edge of P3 panels
  (`DividerDefaultsMMD.Thickness`, Chess `GameMenuDialog`).
- **Dashed hairline:** between list rows (indented to the text) and between P3 menu rows.
  - MMD's own `DropdownMenuMMD` KDoc separates items with `DashedDivider()`
    (`mmd-core/.../menus/MenuMMD.kt:113-127`). That component isn't shipped, so it is built once in
    the kit as `DashedDividerMMD`.
  - Chess draws dashes as `Stroke(1.5f, PathEffect.dashPathEffect([8f, 10f]))`
    (`PlayerColorResult.kt:62-70`).
  - MMD draws disabled chevrons dotted (`chevron_dotted_*`).
  - Starting values: 1dp stroke, 6dp dash, 7.5dp gap (Chess's px at 213dpi). **Calibrated on device
    in Phase 0.**

### Remaining exceptions
- **E1 WebView content scrolls continuously**, because paging can't be imposed on HTML. This covers
  the card, deck options, statistics and card info. CSS sets `scroll-behavior:auto`.
- **E2 Halftone appears only in the statistics graphs.** Text and small marks (<6px) snap to pure
  black and are never dithered.

## Verified facts that shape the plan

### Device and data
- **Kompakt `MK20250408317`:**
  - MuditaOS K 1.6.0, Android 12 (API 31)
  - 480×800 @ 213dpi, exactly **tvdpi → 360×601dp**
  - WebView 149
  - InkOS launcher with **0 widget hosts**
  - `Log.d` does not reach logcat on this device (Loop), so verify via `uiautomator dump` and the DB.
- **AnkiDroid 2.24.1 (`com.ichi2.anki`) holds the real collection at `/sdcard/AnkiDroid/`**
  (`collection.anki2` 8.8MB, plus media). That is PUBLIC shared storage.
- Manifest authorities and the custom permission all use `${applicationId}`
  (`AnkiDroid/src/main/AndroidManifest.xml:44,710,719`). A new `applicationId` therefore installs
  side by side with no conflicts.
- `selectStoragePermissions()` (`AnkiDroid/src/main/java/com/ichi2/anki/InitialActivity.kt:280-315`)
  returns `APP_PRIVATE` on API 30+ unless `MANAGE_EXTERNAL_STORAGE` is granted.

### MMD 1.0.2 (source read at `mudita/MMD@0b8940c`, cloned in the session scratchpad)
- `ThemeMMD` wraps `MaterialTheme` with `eInkColorScheme` and `eInkTypography` (Lato), and sets
  `LocalRippleConfiguration provides null`.
- It is built on Compose 1.7.3 / material3 1.3.1 / Kotlin 2.0.20, minSdk 23.
- AnkiDroid is on **BOM 2026.06.01, Kotlin 2.3.21, AGP 9.0.1**. Gradle resolves material3 upward, so
  **binary compatibility is unproven** until the Phase 0 spike.
- **Components it has:** Badge, ModalBottomSheet, Button/OutlinedButton, FAB, Card, Checkbox, Chips,
  Divider, LazyColumn/Row, DropdownMenu, NavigationBar, Circular/Linear progress, RadioButton,
  SearchBar, Slider, Snackbar, Switch, Tabs, Text, TextField, DatePicker, TimeInput, Tooltip,
  TopAppBar.
- **It has no:** dialog, IconButton, TextButton, ListItem, Scaffold, drawer, dashed divider, or icon
  set (only `chevron_filled_*`, `chevron_dotted_*`).
- **Known MMD bugs, handled once in our kit and never rediscovered:**

  | Bug | Consequence | Fix in the kit |
  |---|---|---|
  | `eInkColorScheme` leaves `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`, `surfaceContainerLowest`, `surfaceBright` and `surfaceDim` as `Unspecified` | TextField, TimeInput, Card and SearchBar render with no fill | Fill those roles with white |
  | `TopAppBarMMD(showDivider=true)` draws **1dp** | No heavy rule under the header | `showDivider=false` + `HorizontalDividerMMD()` (3dp) |
  | `SnackbarMMD` action colour is `inversePrimary` = white on white, and its divider is 1dp | Invisible action, thin rule | Explicit colours and a real 3dp rule |
  | `LazyColumnMMD` pages **by item** (step 4) | One tall item can't scroll; hidden items still count; thumb sized by item count; `alpha=0` until counted; ~40dp gutter only while scrollable | Emit real items, drop hidden ones, account for the conditional gutter |
  | `TextFieldMMD` minimum is 280×56dp | No dense variant | Design forms around it |
  | Inert `RadioButtonMMD` measures 24dp | Row label inset is 24+16dp | Bake the inset into the row components |

### Repo (HEAD `b34112c`, `2.25.0alpha4`)
- **Build.** `AnkiDroid/build.gradle` is **Groovy**.
  - `applicationId` is set at l.84, `app_name` at l.93 and `versionCode` at l.116.
  - Flavors are `play`/`amazon`/`full`. Build types are `debug` (`.debug` suffix), `release` and
    `benchmark` (release signed with the debug key).
  - Warnings are errors.
  - The `ExperimentalMaterial3Api` opt-in is already set (`build.gradle.kts:153`).
- **Compose.** It is enabled only in `:AnkiDroid`, and the code lives in `com.ichi2.compose.*`:
  - `theme/Theme.kt` `AnkiDroidTheme` bridges the XML theme into M3 colours only.
  - The one Compose screen is Switch Profiles.
  - There are **no compose-ui-test deps**.
- **Tests.**
  - Roborazzi 1.73 with the `ScreenshotTest.kt` base; `-Pscreenshot -Ptheme=eink -Pdevice=…`.
  - Robolectric in 228 of the 380 unit-test files, plus 48 androidTest files.
- **E Ink theme.** `theme_eink.xml` exists but is shallow:
  - 44 attributes are inherited from Light, some coloured.
  - It uses ripples and sets its own greys (`#E0E0E0`, `#CCCCCC`, grey counts).
  - Card HTML and web pages ignore it.
- **Global animation switch.** `common/android/.../Animations.kt:45-65` `animationEnabled()` already
  gates most View animations (drawer, FAB, show-answer fade, activity transitions).
- **Web pages.** The Rust-backend pages (`PageFragment` + `AnkiServer` + `PageWebViewClient`) are
  deck-options, graphs, card-info, congrats, import-anki-package, import-csv and image-occlusion.
  - Their CSS/JS ships **inside the backend AAR**, not in the repo.
  - The only native styling is the body background colour plus a `#night` URL fragment.
- **New study screen.** `ReviewerFragment` + `ReviewerViewModel` (StateFlows) is off by default
  (`Prefs.isNewStudyScreenEnabled`). The card HTML comes from `stdHtml()` in
  `previewer/PreviewerHelpers.kt`, which loads `assets/ankidroid.css` last.
- **Legacy study screen.** `Reviewer`/`AbstractFlashcardViewer`: ~5,000 lines, no ViewModel.
- **Widgets.**
  - Keep `WidgetStatus`: `fetchDue()` drives the due-cards notification
    (`services/NotificationService.kt:352`).
  - Keep `DayRolloverAlarm`: it broadcasts `studyQueues` at day cutoff.
  - Keep `MetaDbWidgetRepository` and MetaDB `smallWidgetStatus`.
- **Lint rules to respect:** SPDX header on new files (`LicenseHeaderExists`),
  `AvoidAlertDialogUsage`, `DirectSnackbarMakeUsage`, `DirectToastMakeTextUsage`,
  `HardcodedPreferenceKey`, and layout prefixes.
- **Licensing.**
  - AnkiDroid is GPL-3.0 with a REUSE layout.
  - MMD is Apache-2.0.
  - The Lato files ship **without OFL text**, so the fork adds `LICENSES/OFL-1.1.txt`.
  - The Loop fork is GPLv3, so porting its files is compatible.
  - Mudita's Chess and Calculator apps are reference only; no code is copied.

## Target app — what survives

### Kept (every one becomes Compose MMD chrome)
| Screen | Today | Becomes |
|---|---|---|
| **Home: deck list** | `DeckPicker` (NavigationDrawerActivity, RecyclerView, FAB, drawer) + `DeckPickerViewModel` | `DeckListScreenMMD` in DeckPicker's own ComposeView; ViewModel flows reused. Tap deck → Deck page (P1) |
| **Deck page** (study options / congrats) | `StudyOptionsActivity/Fragment` + `StudyOptionsViewModel` (states StudyOptions/Congrats/Empty), `DeckPickerContextMenu`, Rename/Delete dialogs | One page:<br>• header: Deck options + Menu (P3 panel: Custom study, Rename, Export, Unbury, Rebuild/Empty for filtered decks, Delete → P5 confirm)<br>• body: counts, description, solid **Study** button<br>The web `CongratsPage` is deleted |
| "More" page | deck_picker.xml overflow + drawer items | Drill-down page: Create deck, Create filtered deck, Import file, Export, Backups, Check database, Check media, Empty cards |
| **Reviewer** | `ReviewerFragment` XML (menu view, counts, timer, answer area) + card WebView | `ReviewerScreenMMD`: header + `AndroidView` card + answer row; `ReviewerViewModel` reused |
| Custom study, filtered deck options | `CustomStudyDialog`, `TagLimitFragment`, `FilteredDeckOptionsFragment` (+ViewModels) | Pages (forms) + multi-choice sheet |
| Deck options | Web `deck-options` | Compose header + WebView + mono CSS |
| Statistics | Web `graphs` + native deck selector | Compose header (deck = ChoiceSheet) + WebView + mono CSS + halftone JS |
| Card info | Web `card-info` | Compose header + WebView + mono CSS |
| Import `.apkg` / `.colpkg` | `IntentHandler` + `ImportDialog` + web `import-anki-package` | Intent kept; Panels; WebView + mono CSS |
| Export | `ExportDialogFragment`, `ExportReadyDialog` | Page + Panel; save via system file picker |
| Sync + account | `LoginFragment`, `LoggedInFragment`, `SyncErrorDialog`, sync progress | Pages/Panels; conflict choice = ChoiceSheet; header sync action with badge |
| Settings (subset, see A6) | `PreferencesActivity` + 14 `SettingsFragment`s + SearchPreference | `SettingsScreenMMD` + section pages using existing `Prefs` keys |
| Review reminders | `ScheduleRemindersFragment`, `AddEditReminderDialog` | List page → reminder page (P1) with `TimeInputMMD`; delete via header bin + P5 confirm |
| Maintenance | `MediaCheckFragment`, `EmptyCardsDialogFragment`, `DatabaseErrorDialog`, backups | Pages/Panels/ChoiceSheets |
| System plumbing | `withProgress`/`LoadingDialogFragment`, `showSnackbar` (186), `showThemedToast` (72), timebox dialog | `ProgressPanel` (static text, determinate bar ≤1 update/s), `MessageBar` |

### Deleted
- **Authoring:**
  - `NoteEditorActivity`/`Fragment`, `FieldEditLine`/`FieldEditText`, `noteeditor/*`,
    `InstantNoteEditorActivity`, `IntentHandler2` + `CREATE_FLASHCARD`/SEND filters, CSV/TSV/TXT/image
    intent filters, `CsvImporter`
  - `CardBrowser` + all of `browser/*` (search, columns, bottom sheets, find & replace, reposition),
    `ChangeNoteTypeDialog`, `SetDueDateDialog`, `ForgetCardsDialog`, `GradeNowDialog`, `TagsDialog`
  - `CardTemplateEditor`, `CardTemplateBrowserAppearanceEditor`, `ManageNotetypes`,
    `NoteTypeFieldEditor`, `InsertFieldDialog`, `PreviewerFragment`, `TemplatePreviewer*`
  - `ImageOcclusion` page, `multimedia/*`, `imagecropper/*`, `DrawingFragment`
- **Study extras:**
  - legacy `Reviewer`/`AbstractFlashcardViewer` and their layouts/menu, `Whiteboard.kt` + `whiteboard/*`
  - `CheckPronunciationFragment` + audiorecord, `recorder/*`, TTS (`AndroidTtsPlayer`, `TTS`,
    `TtsVoices*`, `ReadText`, `JavaScriptTTS`, `JavaScriptSTT`)
  - `AnswerFeedbackView`, `ReviewerMenuSettingsFragment` + `MenuDisplayType` + `ReviewerMenuRepository`
- **Chrome and navigation:** `NavigationDrawerActivity` + drawer layouts/menu + app shortcuts,
  `BottomNavController`, `HomeScreenNavigation`, `MoreFragment`, `DeckPickerFloatingActionMenu`,
  `DeckPickerContextMenu` (long-press), `BackgroundImage`, pull-to-sync, all
  `layout-sw600dp`/`layout-land`/`menu-xlarge`, `ResizablePaneManager`/`ResizingDivider`.
- **Themes:** `theme_light/plain/dark/black.xml`, `values-night`, `values-v29` theme files, the
  `AppTheme`/`DayTheme`/`NightTheme` enums + appearance prefs, the night branches in
  `CardAppearance`/`stdHtml`/`PageFragment`.
- **Outside world:**
  - `HelpDialog`, `Info` (changelog), `AboutFragment`, donate/rate/feedback/social links
  - `SharedDecksActivity` + download, `RemoveAccountFragment`
  - `IntroductionActivity`/`SetupCollectionFragment`
  - storage-permission fragments, `ManageSpaceActivity`
  - ACRA upload + analytics opt-in (a fork must not report into upstream's servers)
- **Widgets:** the 4 providers, 2 config activities, `WidgetPermissionReceiver`,
  `res/xml/widget_provider_*`, widget layouts, `:widgets` `WidgetConfigScreenAdapter`.
  Keep `WidgetStatus` and `DayRolloverAlarm`.
- **Settings screens:** Appearance, Custom buttons, "New study screen" header (it becomes the only
  one), keyboard/gamepad controls, Developer options, Switch profiles.

## Architecture of the fork

### Home and navigation (MuditaOS style)
```
Deck list (home) ── header: [Sync•] [Stats] [Settings] [More]
 ├─ tap deck ───────────── Deck page ── header: [Deck options] [Menu ▸ P3 panel]
 │                            │           panel: Custom study, Rename, Export, Unbury,
 │                            │                  Rebuild/Empty (filtered), Delete → P5 confirm
 │                            └─ [Study] ── Reviewer ── header: [Undo] [Replay♪] [Menu ▸ P3 panel]
 │                                             └─ deck finished → Deck page (Congrats state)
 ├─ Stats ──────────────── Statistics page (deck = P4 ChoiceSheet)
 ├─ Settings ───────────── Settings root → section pages
 └─ More ───────────────── More page → Create deck / filtered deck, Import, Export, Backups,
                            Check database, Check media, Empty cards
```

### Three rendering worlds
1. **Compose chrome** — every native screen, under `AnkiDroidTheme` rewritten to wrap `ThemeMMD`.
   Activities stay. Fragments return `ComposeView`; DeckPicker sets a ComposeView as its content.
   Navigation (`Destination` + `AnkiDroidNavigator`) is untouched.
2. **Card content** — `ReviewerFragment`'s WebView inside `AndroidView`, with `assets/mmd-card.css`
   loaded after `ankidroid.css` in `stdHtml()`.
3. **Backend pages** — `PageFragment` chrome rebuilt once in Compose. `PageWebViewClient` injects
   `assets/mmd-pages.css`, plus `assets/mmd-halftone.js` on `graphs` only.

### Design kit — `AnkiDroid/src/main/java/com/ichi2/compose/mmd/`
This is the only package allowed to know MMD's gaps. Every file carries an SPDX header.

| File | Contents |
|---|---|
| `Theme` | `AnkiDroidTheme` → `ThemeMMD(colorScheme = eInkColorScheme` with the six `Unspecified` roles set to white`)`. The name is kept so existing callers compile. |
| `ScreenHeader` | `TopAppBarMMD(showDivider=false)` + `HorizontalDividerMMD()`; `HeaderAction` (48dp button, 32dp black glyph from existing drawables via `painterResource`; no material-icons dep). |
| `DashedDividerMMD` | Canvas line with `PathEffect.dashPathEffect`, black, `startIndent` param; constants calibrated in Phase 0. |
| `Panel` (P5) | Port of Loop `PanelMMD.kt` (white, 3dp border, calibrated corners, no elevation, **no dim**) + `PanelDialogFragment` (clears `dimAmount` and window animations) + `panelTextFieldColors()` + `ConfirmPanel(title, body, confirmLabel, onConfirm)`. |
| `MenuPanel` (P3) | `ModalBottomSheetMMD(skipPartiallyExpanded=true, dragHandle=null, scrim transparent)`. Content modelled on Chess `GameMenuDialog`: 3dp top rule, centred bold title, 56dp rows (label, optional trailing chevron or value) separated by `DashedDividerMMD`; one optional solid primary button. |
| `ChoiceSheet` / `MultiChoiceSheet` (P4) | Same sheet base, 56dp rows with inert `RadioButtonMMD`/`CheckboxMMD`, dashed separators, paged when long. |
| `PagedList` | `LazyColumnMMD`, `scrollStep = 6`, exposes the conditional scrollbar gutter from its `LazyListState`; `DashedDividerMMD` between rows. |
| `Rows` | `NavRow` (`chevron_filled_right`), `SwitchRow` (`SwitchMMD`), `ValueRow` (opens ChoiceSheet), `ActionRow`, `SectionTitle`. |
| `MessageBar` + `MessageHost` | `SnackbarMMD` with explicit colours and a real 3dp rule. |
| `ProgressPanel` | Static "Working…" text; an optional `LinearProgressIndicatorMMD` that is determinate only and throttled to ≤1 update/s. |
| `WebContent` | `AndroidView(SafeWebViewLayout)`, `OVER_SCROLL_NEVER`, `fadeScrollbars=false`. |
| `EinkRefresh` | Port of Loop's: a 100ms black overlay, counting answered cards; off by default; toggle + interval in Settings → E Ink. |
| `ComposeHostFragment` | Base Fragment returning `ComposeView { AnkiDroidTheme { … } }`. |
| *(not built yet)* `SelectionHeader` (P2) | Only if a future screen needs bulk actions. |

## Phases

Each phase ends with an on-device check. Commits are one purpose each, and the build stays green
per commit.

### Phase 0 — Safety and foundation (~1 week)
1. **Branch** `mmd-eink` from `main`. Commit this plan as `docs/mmd/PLAN.md`. Start
   `docs/mmd/eink-design.md` (rules + patterns P1–P6 with their sources) and `docs/mmd/STATUS.md`
   (the Loop-style handoff log).
2. **Back up real data.**
   - `adb pull /sdcard/AnkiDroid` to `C:\Users\Antonio\Backups\ankidroid-2.24.1-<date>\`.
   - Export a `.colpkg` with media from 2.24.1 (you do this on the device) and pull it too.
3. **Identity** in `AnkiDroid/build.gradle`:
   - `applicationId "com.ichi2.anki.mmd"` (l.84); debug becomes `com.ichi2.anki.mmd.debug`.
   - `app_name` "AnkiDroid MMD" (l.93); `versionName` suffix `-mmd`.
4. **Storage.**
   - Remove `MANAGE_EXTERNAL_STORAGE` from the manifest.
   - Make `selectStoragePermissions()` return `APP_PRIVATE` unconditionally.
   - Unit-test that `getDefaultAnkiDroidDirectory()` never resolves under `/sdcard/AnkiDroid`.
5. **Silence upstream reporting.** Disable ACRA upload (`ACRA_URL`) and usage analytics; remove the
   opt-in dialog.
6. **MMD spike (go/no-go, 1–2 days).**
   - Add `mmd = "1.0.2"` to `gradle/libs.versions.toml` and `implementation libs.mmd` to the app.
   - Build a throwaway debug screen exercising `ThemeMMD`, `ModalBottomSheetMMD`, `LazyColumnMMD`,
     `TextFieldMMD`, `TimeInputMMD`, `SnackbarHostMMD`.
   - Install on the Kompakt and use each component.
   - **If it fails against material3 2026:** vendor `mmd-core` as a `:mmd` module (Apache-2.0
     headers kept, REUSE annotation, compiled against our BOM). Imports stay identical.
7. **Calibrate against the Kompakt.**
   - You open Kompakt Settings (a long list), a Notes delete confirmation pop-up and a Contacts
     details page. I take `screencap` + `uiautomator dump` only; the phone is not driven.
   - Measure the row dash length, gap and stroke, the panel corner radius and border, row height and
     header icon size.
   - Set the kit constants and record the measurements with the screenshots in
     `docs/mmd/eink-design.md`.
8. **Lato and tokens.**
   - Copy `lato_*.ttf` from `mmd-core/src/androidMain/res/font/` into `res/font/`.
   - Add `LICENSES/OFL-1.1.txt` and a REUSE annotation.
   - Copy the same files to `assets/fonts/lato/` for the WebViews.
9. **One XML theme.** `theme_eink.xml` becomes the app theme (transition-period windows, splash,
   system dialogs):
   - Lato `fontFamily`, every grey role → black/white, `colorControlHighlight` transparent,
     `windowAnimationStyle` null, elevation 0.
   - The launcher theme is white with an empty splash icon.
10. **Motion off globally.** `animationEnabled()` returns `false`, and the "safe display" pref is
    removed.
11. **Design kit** (the table above), with a debug-only gallery screen showing every kit component,
    screenshotted on device next to the calibration captures.
12. **Test harness.**
    - Add `androidx.compose.ui:ui-test-junit4` + `ui-test-manifest`.
    - Add a `DeviceConfig.KOMPAKT` = `w360dp-h601dp-port-tvdpi` in `ScreenshotTest.kt`.
    - `ThemeConfig` reduces to EINK.
    - Add `tools/mmd/device.sh`: install, force-stop, `uiautomator dump`, screencap; never chained taps.

**Device check:** the fork installs next to 2.24.1, imports the `.colpkg` copy, and shows the old
deck list in the flattened E Ink theme. The real `/sdcard/AnkiDroid` modification time is unchanged.
The kit gallery sits side by side with the Kompakt calibration captures.

### Phase 1 — Delete what the Kompakt won't use (~1.5–2 weeks)
Deleting first roughly halves the surface to redesign. It also removes the drawer and the legacy
reviewer, which every later rewrite would otherwise work around.

Order: leaves first, so each commit compiles.

1. **Widgets.** Providers, configs and layouts go; `WidgetStatus`/`DayRolloverAlarm` stay. Run the
   notification test.
2. **Outside world.** Help, Info, About, links, shared decks, remove-account, intro, storage
   permissions, manage space.
3. **Authoring.** Note editor, instant editor, `IntentHandler2`, CSV import, image occlusion,
   multimedia, cropper, drawing.
4. **Card Browser and its dialogs.** Previewers and template/note-type editors.
5. **Legacy reviewer.**
   - Force `isNewStudyScreenEnabled` true, then remove the pref.
   - Re-point `Reviewer.getIntent()` and `IntentHandler.kt:184` to `ReviewerFragment`.
   - Delete `Reviewer`, `AbstractFlashcardViewer`, `Whiteboard`, `ViewerCommand` users, and the
     whiteboard + check-pronunciation code in the new reviewer.
6. **TTS and recording.** Card `[sound:]` playback stays (`CardMediaPlayer`).
7. **Drawer, bottom nav, FAB, deck long-press context menu, tablet/landscape layouts.** DeckPicker
   extends `AnkiActivity`.
8. **Themes.** Four theme files, the enums and prefs, and the night branches.
   `Themes.isNightTheme` → removed.
9. **Settings screens:** Appearance, Custom buttons, Controls keys/gamepad, Developer options,
   Profiles.
10. **Tests.** Delete tests belonging to deleted code in the same commit as the code. If
    `UnusedResources` is fatal, strip orphaned strings from `values/` and all `values-*/` with one
    scripted commit (`tools/mmd/prune-strings`).

**Device check:** study 20 cards on the new reviewer, sync off; deck list, deck options and stats
work. Record a list of anything upstream's new study screen lacks versus 2.24.1's legacy reviewer
in `STATUS.md` before building on it.

### Phase 2 — Reviewer (~2 weeks)
1. **Roborazzi baseline** of the current `fragment_reviewer` on KOMPAKT.
2. **`ReviewerScreenMMD`**, fed by `ReviewerViewModel` flows (counts, `showingAnswer`, next times,
   flag/mark, `typeAnswerFlow`, `timeBoxReachedFlow`):
   - **Header:** back, counts as plain text `12 · 3 · 40` (current queue bold), actions Undo,
     Replay (only when the card has audio), Menu.
   - **Menu → `MenuPanel` (P3):**
     - Flag → P4 ChoiceSheet of named flags, no colour
     - Mark
     - Bury card, Bury note
     - Suspend card, Suspend note
     - Card info
     - Deck options
     - Auto-advance
   - **Card:** `WebContent` hosting the existing `SafeWebViewLayout`. JS gesture bridge
     (`GestureParser`, tap zones, swipes) unchanged.
   - **Answer row:** "Show answer" is a full-width `ButtonMMD`. After flipping:
     - four 56dp buttons, each label + interval in regular weight
     - **Good** is solid `ButtonMMD`; Again, Hard and Easy are `OutlinedButtonMMD`
     - hide Hard/Easy per the existing pref
   - **Type-answer:** `TextFieldMMD` with `panelTextFieldColors()`.
   - **Flag/mark indicators:** small black glyph + flag name over the card; never colour.
   - **Answer timer:** hidden (A8). Timebox → P5 Panel.
3. **`assets/mmd-card.css`**, forced mono with no exceptions, appended after `ankidroid.css` in
   `stdHtml()`:
   - `*` gets black text, transparent backgrounds, black borders, no shadows/transitions/animations.
   - `html, body` are white.
   - `font-family: Lato, sans-serif` via `@font-face` from `assets/fonts/lato/`. Scripts Lato lacks
     fall back to system sans.
   - Cloze and highlights become bold + 3px underline, not colour.
   - `.typeGood` = bold, `.typeBad` = strike-through, `.typeMissed` = underline.
   - `hr` = dashed 1px black.
   - Images `filter: grayscale(1)`. MathJax is black. `scroll-behavior:auto`.
   - Remove `--canvas`/`--fg` theme reading and night classes; hardcode white/black.
4. **Delete `fragment_reviewer.xml`**, `view_answer_area`, `view_study_counts`, `view_reviewer_menu`,
   `AnswerAreaView`, `StudyCountsView`, `AnswerTimerView`, `ReviewerMenuView`.
5. **Hook `EinkRefresh.onChange`** on each answer.

**Device check:** a 50-card session on the imported copy.
- Ghosting after answers (flash off and on)
- Audio replay
- Undo; flag and bury from the Menu panel, confirmed in the pulled DB
- A heavily styled note type renders black-on-white Lato

### Phase 3 — Home, Deck page, More (~1.5 weeks)
1. **Baselines** of DeckPicker and StudyOptions.
2. **`DeckListScreenMMD`:**
   - `PagedList` of `FlattenedDeckList`, **one item per deck**, dashed row separators
   - indent by depth, expand chevron
   - counts are three right-aligned numbers (zero shown blank)
   - filtered decks marked by italic name
   - "studied today" summary line
   - empty state: text + `ButtonMMD` "Import file"
   - **Tap = open the Deck page (P1).** No long-press.
3. **Sync in the header.** A glyph with a `BadgeMMD` dot for `PendingChanges`; the `OneWay` /
   `NotLoggedIn` states are a subtitle line. The full-sync conflict is a P4 ChoiceSheet. Progress uses
   `ProgressPanel`.
4. **Deck page** (replaces StudyOptions and the deck context menu):
   - **Header:** Deck options + Menu. The `MenuPanel` holds Custom study, Rename (P5 panel with
     `TextFieldMMD`), Export, Unbury, Rebuild/Empty (filtered decks), Delete (P5 `ConfirmPanel`).
   - **Body:** counts, description, and a solid **Study** button, or the Congrats state (Unbury /
     Custom study buttons) or the Empty state.
   - Delete the `CongratsPage` web page and the `new_congrats_screen` pref.
5. **More page.** Rows open existing flows. Create deck is a P5 panel. Check database/media and Empty
   cards use `ProgressPanel` + a result Panel.
6. **Splash hold** until the deck list has content (Loop `holdSplashUntilDrawn` pattern), with a
   ceiling.
7. **Delete** `activity_homescreen`, `include_deck_picker`, `item_deck`, `DeckAdapter`,
   `DeckHierarchyLinesDecoration`, `DeckPickerContextMenu`, `activity_study_options`,
   `fragment_study_options` and their menus.

**Device check:** cold start (benchmark build), deck tree expand/collapse, deck page → rename →
delete-confirm round trip on a throwaway deck, sync badge state.

### Phase 4 — Backend web pages (~1–1.5 weeks)
1. **Extract the backend CSS variable list** from the backend AAR's `backend/css/root-vars.css` and
   the sveltekit bundle. Write the table into `docs/mmd/web-tokens.md`. No guessing variable names.
2. **`assets/mmd-pages.css`:**
   - every colour variable → black/white
   - Lato `@font-face`
   - no shadows, transitions or animations
   - 2dp black outlines on inputs/toggles/sliders
   - `hr` and list separators dashed
   - `scroll-behavior:auto`
   Served through `AnkiServer`, injected by `PageWebViewClient` on every page; the `#night` fragment
   is removed.
3. **`assets/mmd-halftone.js`** (graphs only). A JS port of Loop's `Halftone`:
   - injects `<pattern>` defs of 4×4 Bayer dots at 1px
   - a `MutationObserver` maps each non-mono SVG `fill` to the pattern matching its luminance
   - text and marks under 6px snap to black (E2)
4. **`PageFragment` chrome in Compose.** `ScreenHeader` + `WebContent`, loading state = static text
   (no `page_loading` spinner). Statistics' deck selector becomes a header action → ChoiceSheet; PDF
   export is deleted.
5. **Delete** `fragment_page.xml`, `page_statistics.xml`, `menu/statistics.xml`.

**Device check:** deck options (edit and save a limit), statistics for one deck and for the whole
collection, card info from the reviewer, `.apkg` import.

### Phase 5 — Settings, account, reminders (~1.5 weeks)
1. **Audit every key** in the kept `preferences_*.xml`. Output `docs/mmd/settings-audit.md` with keep
   or delete per key, plus the reason.
2. **`SettingsScreenMMD` root** (`NavRow`s): Reviewing, Sync, Notifications & reminders, Backups,
   Gestures (tap zones + swipes only), Accessibility (card zoom, answer button size), **E Ink**
   (flash-to-clear + interval), Advanced (kept subset). Version line at the foot.
3. **Section pages** use `SwitchRow`/`ValueRow` + ChoiceSheets over the **existing** `Prefs`
   delegates and keys, so values and `HardcodedPreferenceKey` both survive. Number inputs are a
   Panel with `TextFieldMMD` and `toDoubleOrNull`/`toIntOrNull` validation (Loop crash lesson).
4. **Login page** (`TextFieldMMD` email/password, `ButtonMMD` Log in); logged-in page (Log out →
   P5 confirm); custom sync server fields. Register and reset-password links are deleted (no browser).
5. **Review reminders.** List page → reminder page (P1: `TimeInputMMD`, deck ChoiceSheet, header
   bin → P5 confirm).
6. **Delete** `PreferencesActivity`'s Fragment tree, the SearchPreference dependency, the custom
   `com.ichi2.preferences.*` widgets no longer used, `fragment_preferences`, `fragment_settings`,
   `fragment_my_account*`, and reminder layouts.

**Device check:** toggle each kept setting and confirm the effect in the reviewer; log in with a
**throwaway** AnkiWeb account only.

### Phase 6 — Remaining dialogs, messages, polish (~1.5 weeks)
1. **Custom study page + tags MultiChoiceSheet**; filtered deck options page; export page + ready
   Panel (system file picker, not the share sheet; persist the pending path, because the Kompakt
   kills the process while the picker is open); `ImportDialog` → Panels.
2. **`DatabaseErrorDialog`, `SyncErrorDialog`, media check page, backup restore** → Panels,
   ChoiceSheets and pages.
3. **Messages.** `Snackbars.kt` routes to the nearest `MessageHost`; `showThemedToast` routes to
   `MessageBar` where a host exists. `withProgress`/`LoadingDialogFragment` → `ProgressPanel`.
4. **Launcher icon.** A monochrome outline mark in MuditaOS's rounded-square frame. Stroke weight
   measured against the system icons (Loop's open problem).
5. **Final sweep:** every remaining `AlertDialog`, `MaterialAlertDialogBuilder`, `PopupMenu`,
   `onCreateOptionsMenu`, `setOnLongClickListener`/`onLongClick`, `R.anim`, and indeterminate
   progress.

**Device check:** walk every page reachable from home and record each in `STATUS.md` with its dump.

### Phase 7 — Cutover and finish line (~2–3 days)
1. **Finish-line gates reach zero:**
   - `res/layout*` files (widget-free)
   - `res/menu*`
   - `AlertDialog.Builder`/`MaterialAlertDialogBuilder`, `PopupMenu`, `onCreateOptionsMenu`,
     long-press handlers
   - `R.anim.`
   - hex colours other than black/white/transparent in `res/` and `assets/mmd-*`
   - `sp` values below 14
   - `indeterminate="true"`

   Then set `buildFeatures.viewBinding = false`. `:vbpd` is deleted when unused.
2. **Migrate.**
   1. On 2.24.1: sync (if you use AnkiWeb), then export a `.colpkg` with media.
   2. Pull both to the PC.
   3. Install the fork's `benchmark` build (release performance, debug key).
   4. Log in → full download from AnkiWeb, **or** import the `.colpkg`.
3. **Verify in the DB, not on screen.** Pull the fork's `collection.anki2` and the 2.24.1 copy;
   compare `count(*)` of `notes`, `cards`, `revlog` and `decks`, and today's due counts. Study 5
   cards, sync, confirm on desktop.
4. **Re-enter per-app settings** that a `.colpkg` does not carry (checklist from
   `settings-audit.md`: tap zones, reminders, E Ink flash).
5. **You** uninstall 2.24.1. `/sdcard/AnkiDroid` stays until you delete it yourself.

## Per-screen workflow (every screen in Phases 2–6)
1. Confirm the screen still exists after Phase 1; note the decision in `STATUS.md`.
2. Record a Roborazzi baseline of the current screen (KOMPAKT, EINK).
3. Read the MMD source of every component used.
4. Build `XxxScreenMMD.kt` against the existing ViewModel/StateFlows. Host it in the existing
   Activity/Fragment. **One change per commit:** no state or navigation restructuring alongside.
5. Apply the interaction patterns:
   - item actions → the item's page (P1)
   - screen menus → header icon + `MenuPanel` (P3)
   - list choices → `ChoiceSheet` (P4)
   - forms → pages
   - destructive actions → `ConfirmPanel` (P5)
   - no long-press (P6)
6. Delete the old layout, menu, drawables, styles and tests.
7. On device: force-stop, open, `uiautomator dump` for truth, then screencap. Never chain taps.
   Confirm writes in the pulled DB.

## Verification

Run from Git Bash in the repo root; arm64 only speeds builds:
```bash
./gradlew :AnkiDroid:assemblePlayDebug -PabiFilter=arm64-v8a
```
```bash
./gradlew :AnkiDroid:testPlayDebugUnitTest
```
```bash
./gradlew :AnkiDroid:lintPlayDebug ktlintCheck
```
```bash
./gradlew :AnkiDroid:compareRoborazziPlayDebug -Pscreenshot -Ptheme=eink -Pdevice=kompakt
```
```bash
./gradlew :AnkiDroid:assemblePlayBenchmark -PabiFilter=arm64-v8a
```
```bash
C:/Users/Antonio/android-sdk/platform-tools/adb.exe -s MK20250408317 install -r AnkiDroid/build/outputs/apk/play/debug/AnkiDroid-play-arm64-v8a-debug.apk
```

- **Roborazzi:** record deliberately (`recordRoborazziPlayDebug`), never to silence a diff. Compose
  UI tests use `createAndroidComposeRule` for the reviewer answer flow, the deck list → deck page →
  study path, and Settings toggles.
- **Performance:** judge it only on the benchmark build. Debuggable builds were ~4× slower on this
  device.
- **Grey audit after every phase:** the gates above, plus a manual on-device look, because Material
  supplies greys no file contains.
- **Pattern audit after every phase:** every screen is checked against P1–P6 and the divider rules,
  next to the Phase 0 calibration captures.
- **Data safety check after every install during development:** the modification time of
  `/sdcard/AnkiDroid/collection.anki2` is unchanged.

## Assumptions made without asking (veto any during review)
| # | Assumption |
|---|---|
| A1 | Tapping a deck opens its Deck page (P1, like Contacts), not review directly. Studying is one more tap, on a solid Study button. |
| A2 | The reviewer Menu panel keeps bury, suspend, flag, mark, card info, deck options and auto-advance. Edit/add/delete note, reschedule, reset progress and set due date are gone. |
| A3 | Deck management stays on device: create, rename, delete, filtered decks, custom study. Editing deck descriptions is deleted. |
| A4 | The native Congrats state on the Deck page replaces the web congrats page. |
| A5 | No in-app AnkiWeb shared-deck browsing. Shared decks arrive as files or via sync. |
| A6 | Settings kept: Reviewing, Sync, Notifications & reminders, Backups, Gestures (touch only), Accessibility (zoom, button size), E Ink, Advanced subset, General (language). Deleted: Appearance, Custom buttons, keyboard/gamepad, Developer options, Profiles. |
| A7 | Export stays, for backups and the migration. |
| A8 | The running answer timer is hidden (it repaints every second). The deck-config timer limit still works. |
| A9 | Pull-to-sync is deleted; sync is the header action plus auto-sync. |
| A10 | During development the fork never logs into your real AnkiWeb account, only a throwaway. Real sync starts at cutover. |
| A11 | The fork freezes on upstream `b34112c`. Upstream is not rebased in; backend bumps are cherry-picked only if needed. |
| A12 | ACRA crash upload and analytics are disabled in the fork. |
| A13 | Dash, corner and row measurements come from the Phase 0 captures of Kompakt system apps. Until then the kit uses MMD's 8dp corners and Chess-derived dash values. |

## Risks
1. **MMD vs material3 2026 binary skew.** Mitigation: Phase 0 spike, with vendoring as the fallback.
2. **The new study screen lacks something the legacy reviewer had.** Mitigation: the parity list is
   recorded in Phase 1 before anything is deleted that can't be restored from git.
3. **Unknown backend CSS variables and SVG structure.** Mitigation: Phase 4 step 1 extracts them
   first; the halftone JS only touches `fill` attributes.
4. **Lato lacks some scripts** (CJK etc.). Mitigation: the system sans fallback in the font stack.
5. **Mass deletion breaks many Robolectric tests.** Mitigation: tests are deleted with their feature;
   the suite stays green per commit.
6. **Kompakt quirks:** silent `Log.d`, slow repaint making screenshots stale, the process killed
   during the file picker. Mitigation: verify through dumps and the DB; persist pending state.
7. **Pattern evidence is partial.** Mudita's `kompakt-ui` library is private, Zeroheight doesn't
   render outside a browser, and the manual barely covers interactions. Mitigation: the Phase 0
   on-device calibration; P1–P6 are revised there if the system apps disagree.

## Estimate
About **10–12 weeks** of steady work:

| Phase | Time |
|---|---|
| 0 | 1w |
| 1 | 2w |
| 2 | 2w |
| 3 | 1.5w |
| 4 | 1.5w |
| 5 | 1.5w |
| 6 | 1.5w |
| 7 | 0.5w |

Phase 0's spike is the only step that can move the whole schedule: +2 days if `mmd-core` has to be
vendored.

## Research sources (contextual actions and dividers)
- `mudita/MMD` `mmd-core/.../menus/MenuMMD.kt:113-127` (the `DashedDivider()` sample),
  `.../lazy/LazyMMD.kt:393-581` (dotted chevrons for disabled state)
- `mudita/MuditaOS-K-Chess-opensource`:
  - `features/gameplay/.../design/GameMenuDialog.kt`, `GameplayDialog.kt`
  - `library/ui/.../compontent/DialogHost.kt`
  - `features/statistics/.../design/PlayerColorResult.kt`
- `mudita/MuditaOS-K-Calculator-opensource` `app/.../MainActivity.kt:720-763` (long-press copy popup)
- [Kompakt quick start: delete notes](https://mudita.com/products/phones/mudita-kompakt/quick-start/notes-app/delete-notes/)
- [How to manage contacts on Kompakt](https://support.mudita.com/en/support/solutions/articles/77000579753-how-to-manage-contacts-on-mudita-kompakt)
- [How to delete voice recordings](https://support.mudita.com/en/support/solutions/articles/77000596772-how-to-delete-voice-recordings-)
- [Mudita Kompakt user manual (PDF)](https://assets.ctfassets.net/isxmxtc67n72/3biBEzYwk8UjSapgvMB461/b6ade0485fba2625382f44aca0fd0b51/ENG_Mudita_Kompakt_Manual_Online_04.pdf) (long press only under accessibility)
