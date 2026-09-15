# Project status — AnkiDroid on Mudita Mindful Design

Handoff notes. Read [eink-design.md](eink-design.md) for the rules and
[PLAN.md](PLAN.md) for the phases. This file records what is done, what is
half-done, and what is next.

Target device: **Mudita Kompakt `MK20250408317`** (MuditaOS K 1.6.0, Android 12,
480×800 @ 213dpi). Branch: **`mmd-eink`** (from upstream `b34112c`). Pushed only to the owner's
fork, remote `fork` = `github.com/nebulion/Anki-Android`; never to `origin` (upstream ankidroid).
The fork's `.github/workflows/mmd_ci.yml` runs build + full lint + unit tests on each push and
reports failures as public job annotations (`tools/mmd/ci_report.py`).

## Real data — read before installing anything

- **AnkiDroid 2.24.1 (`com.ichi2.anki`) is installed on the Kompakt with the owner's
  real collection** at `/sdcard/AnkiDroid/collection.anki2`.
- A copy of that folder was pulled on 2026-09-14 to
  `C:\Users\Antonio\Backups\ankidroid-2.24.1-2026-09-14\` (174 MB).
- The fork is now `com.ichi2.anki.mmd` (debug: `com.ichi2.anki.mmd.debug`) and always
  uses app-private storage, so it installs beside 2.24.1 and cannot open its collection.

## Commits on `mmd-eink`

| Commit | What |
| --- | --- |
| `a99d419` | Remove ACRA crash reporting and Google Analytics |
| `5fd0506` | Add MMD fork plan, E Ink design rules and status log |
| `58576f7` | Always use app-private storage and drop `MANAGE_EXTERNAL_STORAGE` |
| `46d2cc5` | Give the fork its own identity: `com.ichi2.anki.mmd` |
| `660de76` | Remove resources orphaned by the ACRA and analytics removal |
| `9251b37` | Add `com.mudita:MMD` 1.0.2 and the MMD design kit |
| `71cc8d1` | Record Kompakt calibration and correct the confirmation pattern |
| `75f1662` | Add the OFL-1.1 licence and REUSE entry for Lato |
| `b3fb1f8` | Fix `StackOverflowError` opening any `ComposeHostFragment` (regression test first) |
| `43f59aa` | Match `ConfirmPanel` to the Kompakt's own delete confirmation |
| `1f67ac2` | Add a Kompakt device to the screenshot tests |

## Phase 0 — Safety and foundation

| Step | State |
| --- | --- |
| 1. Branch, `PLAN.md`, `eink-design.md`, `STATUS.md` | Done |
| 2. Back up real data | Done (folder copy). `.colpkg` export skipped by the owner |
| 3. Identity (`com.ichi2.anki.mmd`, "AnkiDroid MMD", `-mmd`) | Done (`46d2cc5`) |
| 4. Storage forced to app-private, `MANAGE_EXTERNAL_STORAGE` removed | Done (`58576f7`), tests written first and seen failing |
| 5. Remove upstream reporting (ACRA + Google Analytics) | Done (`a99d419`, `660de76`); lint reports no unused resources |
| 6. MMD 1.0.2 compatibility spike | **Passed at build level** (`9251b37`): material3 resolves 1.3.1 → 1.4.0, APK installs as `com.ichi2.anki.mmd.debug`. The first on-device gallery run crashed (`StackOverflowError`, fixed in `b3fb1f8`); on-panel check of the fixed gallery still open |
| 7. Calibrate against Kompakt system apps | Settings, Notes list, Notes selection mode and delete confirmation done (see `eink-design.md`); Contacts optional |
| 8. Lato + OFL licence | Done (`75f1662`). Native screens use the Lato fonts inside the MMD AAR (no copy); the annotation covers the WebView copy added in Phase 2/4 |
| 9. One XML theme | **Folded into Phase 1 step 8**, which deletes the other themes, enums and prefs; doing it twice would churn `ThemesTest` and `ScreenshotTest` for nothing |
| 10. Motion off globally | `Animations.areAnimationsEnabled` always `false`; "Remove animations" pref, `AnimationPreferences`, the provider registration and both strings (83 locales) removed (verified in the Phase 1 end build) |
| 11. Design kit `com.ichi2.compose.mmd` | In the repo (`9251b37`); `ConfirmPanel` matches the measured bottom confirmation (`43f59aa`) |
| 12. Test harness (Compose UI test deps, KOMPAKT device config) | `KOMPAKT` screenshot device (`-Pdevice=kompakt`, 360×601dp tvdpi) added; Compose UI test deps not yet |

### Step 4 — app-private storage

`selectStoragePermissions(context)` (`InitialActivity.kt`) returns `APP_PRIVATE`
unconditionally; the pure `selectStoragePermissions(canManage, legacy)` overload is
unchanged and still tested. `SelectStoragePermissionsTest` has four fork tests (no path,
public path plus `MANAGE_EXTERNAL_STORAGE`, legacy device, default directory never
`~/AnkiDroid`). They failed before the change (`EXTERNAL_MANAGER`, `LEGACY_ACCESS`, public
folder) and pass after it, alongside `StoragePolicyTest`, `ActivityStartupMetaTest`,
`ManifestThemeTest`, `DeckPickerTest` and the `InitialActivity` tests.

### Step 5 — ACRA and analytics removed

Both were deleted rather than disabled, at the owner's request.

- Deleted: `AcraCrashReporter`, `AnkiDroidUsageAnalytics`, `AnkiDroidCrashReportDialog`,
  `AcraAnalyticsInteraction`, `AnalyticsExceptionHandler`, `AnalyticsSamplePercentage`,
  `AnalyticsConstants`, `DeckPickerAnalyticsOptInDialog`, `analytic_constants.xml`,
  `docs/analytics/README.md`, and their tests (incl. androidTest `ACRATest`).
- Removed: the ACRA crash-dialog activity (`:acra` process); the error-reporting and
  analytics settings; the analytics developer option; `ACRA_URL` / `ANALYTICS_API_KEY`;
  the `acra-*`, `google-analytics-kt` and `auto-service` dependencies.
- **Kept:** the `CrashReportService` (`:common:android`) and `Analytics` (`:common`)
  facades. With no implementation registered they log and drop, so the ~60 call sites are
  unchanged.
- ACRA utilities that unrelated code borrowed were replaced: `MultimediaEditableNote.cloneField`
  deep-copies with Java serialization; `FileUtilTest` uses `File.writeText`;
  `DebugInfoService` no longer prints an ACRA UUID.
- Orphaned resources: lint (`UnusedResources` is **fatal**) reported exactly 20 — the
  `error_reporting_*`, `analytics_*` and `feedback_*` strings, two arrays, three preference
  keys and `layout/dialog_feedback.xml`. Deleted from `values/` and all 82 translated
  `values-*/` (`660de76`); lint now reports none.

## Phase 1 — Delete what the Kompakt won't use

| Step | State |
| --- | --- |
| 1. Widgets | Deleted: the four providers (small, add note, deck picker, card analysis), both config activities, `WidgetPermissionReceiver`, `WidgetRegistry`, the provider/alarm/config code in `:widgets`, their layouts, drawables and `widget_provider_*` XML, 6 widget tests, the manifest entries, the recurring-alarm restore in `AnkiDroidApp`/`BootService`, and `AnkiDroidApp`'s `ChangeManager` subscription (it only refreshed widgets). Lint then flagged 20 orphaned resources: 12 strings/plurals × 83 locales, 3 sample deck names, 5 `drawable-v31` icons — all deleted. **Kept:** `WidgetStatus` (now only the due count behind the legacy 'cards due' notification), `DayRolloverAlarm`, `MetaDB.smallWidgetStatus`, and `:widgets`' `SmallWidgetStatus`/`WidgetRepository`/`WidgetNotificationScheduler` |
| 2. Outside world | Deleted (compiles; 170 tests pass; unused resources pruned at phase end): Help and Support (`HelpDialog`, drawer items, More page rows, privacy buttons on login/logged-in), `Info` changelog screen + `ChangelogDestination` (upgrades now just record the version and show the "updated" snackbar; About loses its changelog button), shared decks (activity, download fragment, FAB button, import clean-up hook, `shared_decks` FileProvider path), AnkiWeb remove-account WebView, the app intro (`IntroductionActivity`, `SetupCollectionFragment`) and the storage-permission screens (`PermissionsActivity`, all-permissions explanation, internet/legacy/manage-storage permission fragments, `CollectionPermissionScreenLauncher`, `StartupResponse.RequestPermissions`), and Manage space. Importing a file before first launch is now gated on `CollectionHelper.storageDecision` instead of "intro shown". **Kept:** the notifications `PermissionsBottomSheet` and its two fragments, `managespace/FileUtils.kt` (backups use it), `AboutFragment` (settings are rebuilt in Phase 5) |
| 3–5. Authoring, Card Browser, legacy reviewer | **Deleted in one batch; app and unit tests compile, tests run at the phase end** (they reference each other, so deleting them separately would mean patching code about to be deleted). Deleted: note editor, instant editor, `IntentHandler2`, multimedia, image cropper, drawing, image occlusion, CSV importer; `CardBrowser` + `browser/`, previewers, note type and template editors; `Reviewer`, `AbstractFlashcardViewer`, whiteboard, voice recording/check pronunciation, `AnkiDroidJsAPI`, the 'Anki Card'/'Card Browser' system text menus; `BrowserDestination`, `NoteEditorDestination`, `CsvImporterDestination`. About 123 main files / 38k lines, 90 test files. **Kept** (the new reviewer and settings use them): `cardviewer/` gestures, media playback, type-answer and `ViewerCommand`; `previewer/CardViewerActivity`, `CardViewerFragment`, `CardViewerViewModel`, `PreviewerHelpers`, `TypeAnswer`, `PreviewerAction`; `reviewer/` bindings, `CardSide`, `AnswerTimer`, `FullScreenMode`; `browser/` column definitions (preference upgrades), `LastDeckIdRepository`, `search/SearchString`, `search/CardState`, and `IdsFile` (moved to its own file). New: `cardviewer/ViewerResult.kt` (result codes and `getMediaBaseUrl`, previously in `AbstractFlashcardViewer`). Reviewer menu loses Edit, Add note, Browse, whiteboard and voice actions; deck list loses Add/Browse/Note types; importing CSV/TSV files is refused. |
| 6. TTS and recording | Deleted (app and unit tests compile): `ReadText`, `TtsParser`, `TtsVoices`, `AndroidTtsPlayer`, the TTS voices and playback-error dialogs, the `{{tts-voices:}}` field filter, `recorder/`, the 'Read text' and 'allow templates to record audio' settings, the developer TTS shortcut, and the `RECORD_AUDIO`/`MODIFY_AUDIO_SETTINGS` permissions. `CardMediaPlayer` keeps `[sound:]` playback and skips `{{tts:}}` tags; card WebViews deny every permission request |
| 7. Drawer, bottom nav, FAB, deck long-press menu, tablet layouts | **Folded into Phase 3**: nearly all of it lives in the 2,400-line legacy `DeckPicker`, which Phase 3 replaces with `DeckListScreenMMD` and deletes (`activity_homescreen`, `DeckAdapter`, `DeckPickerContextMenu`); editing it now would be throwaway |
| 8. Themes | Done: E Ink is the only theme and night mode is forced off. `NightTheme`, `AppTheme`, the theme prefs and settings, the dark/black/plain XML and their orphan selectors are deleted. `Themes.isNightTheme` stays as a constant `false`, so its ~8 callers still compile; they go with their screens in Phases 3–5 |
| 9. Settings screens | Done (app and unit tests compile): Appearance (and with it the deck list background picker), Custom buttons, Developer options (and the About logo tap that enabled them), Switch profiles plus the whole unused `multiprofile` package and its docs, and the Controls keyboard-shortcuts row. The new study screen is now on by default (`Prefs.isNewStudyScreenEnabled` defaults to `true`). The MMD kit gallery moved to a debug-only row at the bottom of Advanced. **Deferred to Phase 5** (settings rebuilt in Compose): limiting the Controls bindings to touch gestures, since keys, gestures and gamepad share `ControlPreference` |
| 10. Tests and orphaned resources | Pruned (`045bbce`): the 845 resources lint reported unused (245 files, their strings in every locale); styles kept because lint cannot see dotted parents. Resources still referenced were restored (`faded_primary`, reviewer menu ids, `bottom_area_layout`). **Verified in the fork CI** (`MMD fork CI`, parallel since `0c43b9a`): compile, 1,284 unit tests and full lint ran; the fallout (storage tests for public storage, the removed Browser shortcut, stale translation baselines, whiteboard/voice control entries, dead instrumented tests, 2 lint errors) was fixed in `29c0280` and the follow-up commit |

## Phase 2 — Reviewer (done; device check passed with open items below)

| Step | State |
| --- | --- |
| 1. Roborazzi baseline | Not recorded: needs a Gradle run. Record `StudyScreenScreenshotTest` with `-Pscreenshot -Pdevice=kompakt` on `045bbce` (the old layout) in CI |
| 2. `ReviewerScreenMMD` | Written. `ReviewerFragment` is now a Compose host (`CardViewerFragment` gained a no-layout constructor) and the WebView sits in `WebContent`, so gestures, bindings, JS and the tag/due-date/forget dialogs are unchanged. Header: back, counts `12 · 3 · 40` with the current queue bold, Undo, Replay (only when the side has media: new `ReviewerViewModel.hasMediaFlow`), Menu. Menu `MenuPanel`: Flag (`ChoiceSheet` of named flags), Mark, Bury card/note, Suspend card/note, Card info, Deck options, Auto-advance. Flag name + mark glyph over the card. Show answer is a full-width `ButtonMMD`; ratings are 56dp with Good solid. Type-answer is `TextFieldMMD`. Timebox is a `PanelDialog`; action feedback uses `MessageHost`. Answer timer hidden (A8) |
| 3. `assets/mmd-card.css` | Written and linked after `ankidroid.css`; `stdHtml()` hardcodes white/black and drops the night-mode classes. Lato for `html, body, .card` (note-type fonts on inner elements still apply) |
| 4. Delete old views | Done: `fragment_reviewer`, `view_answer_area`, `view_study_counts`, `AnswerAreaView`, `StudyCountsView`, `AnswerTimerView`, `AnswerFeedbackView`, `AnswerButton`, `ReviewerMenuView` + `ReviewerMenu.kt`, and the toolbar-actions settings screen (`ReviewerMenuSettingsFragment`, adapter, layouts). Study-screen settings that no longer do anything are gone too: frame style, hide system bars, display cutout, answer feedback, toolbar position (with `FrameStyle`, `HideSystemBars`, `ToolbarPosition`). `ReviewerMenuRepository` stays for now (its test and `ViewerAction` defaults use it) |
| 5. `EinkRefresh.onChange` | Called on every answer. The flash was off by default, so the owner saw no refresh; it is now **on by default every 12 answers** (`Prefs.isEinkRefreshEnabled`); its setting UI comes in Phase 5 |

Known gaps: the Compose type-answer field does not set the IME hint locales the old `EditText`
did; the accessibility "answer button size" pref is ignored.

### Device check (2026-09-15, CI build of `29c0280`, owner-run)

Install note: the phone kept the earlier locally built fork's package record and data after it was
removed, and the CI APK is signed with a different debug key, so the install was refused until
`adb uninstall com.ichi2.anki.mmd.debug` cleared the record. 2.24.1 (`com.ichi2.anki`) is not
installed on the phone at the moment; `/sdcard/AnkiDroid` is intact.

Deck: Ultimate Italian Conjugation (`.apkg`).

- **Passed:** study screen layout, answering, undo, menu (flag, mark, bury, suspend, card info,
  deck options, auto-advance), study-screen settings.
- **Owner feedback:** no E Ink refresh while answering (fixed: on by default, every 12 answers);
  margins, padding and sizes want a **unified sizing pass later**, consistent across the app and
  with Mudita's apps, including the study screen's font size; "Show answer buttons" off seems
  pointless → Phase 5 settings audit.
- **Still to test** (the deck had none): an audio card (Replay icon and replay), a type-in-the-answer
  card, the timebox panel.

## Phase 3 — Home, Deck page, More (in progress)

Approach: keep `DeckPicker` as the home **activity** (it owns startup, sync, import, backups and the
error/sync dialogs, ~1,000 lines worth keeping) but make it a plain `AnkiActivity`: a fragment
container for the current tab above an MMD bottom bar. Old View-based pieces are **replaced by their
MMD equivalents where MMD has one** (owner's call, 2026-09-15: "some of these are in MMD"):
toolbar → `ScreenHeader` with header actions, bottom nav → `NavigationBottomBarMMD` (Decks /
Statistics / More). MMD has no drawer, and the owner chose no FAB, so those two go. Dialogs that are
still Views (create/rename deck, custom study, export, import, sync and database errors) stay until
Phase 6, except where this phase's pattern needs a panel.

State (2026-09-15): steps 2, 3, 4 and 6 are written and the old files deleted; app and unit tests
compile locally; committed as `258172e` on `fork/mmd-phase3`. CI run `35022472431`: compile and
Kompakt screenshots passed; lint found 46 resources left unused by the rewrite (pruned), and two tests
that had failed since before Phase 3 (`ControlsSettingsFragmentTest`, `TranslationTest`) are fixed.
**CI green on `d9e00bb`** (run `35024419219`: compile, lint, 4 test shards, Kompakt screenshots). Device
check pending.
Step 5 (splash hold) is not started. `DeckPicker` is now ~1,000 lines: tabs via
`HomeTab` (`deckpicker/HomeTab.kt`), `DeckListFragment`, `MoreTabFragment`; collection-wide actions
are public methods the More tab calls. Messages from the home screen show above the bottom bar on
every tab. Lost on purpose: create subdeck (type `Parent::Child` in Create deck), edit description,
review reminders per deck, deck search, drag-and-drop import.

| Step | Plan |
| --- | --- |
| 1. Baselines | Not recorded (needs Gradle; the old screens stay in git at the phase's starting commit) |
| 2. Deck page (`DeckPageFragment` in `SingleFragmentActivity`, over `StudyOptionsViewModel`) | Header: back, deck name, Deck options, Menu. `MenuPanel`: Custom study, Rename (panel + `TextFieldMMD`), Export, Unbury, Rebuild/Empty (filtered), Delete (`ConfirmPanel`). Body: New / Learning / To review counts, plain-text description, solid **Study**; Congrats state (Unbury / Custom study) and Empty state. `StudyOptionsDestination` opens it. Deletes `StudyOptionsActivity`/`Fragment`, their layouts and menus, `CongratsPage` and `new_congrats_screen` |
| 3. Home (`DeckListScreenMMD` in the Decks tab) | Bottom bar: `NavigationBottomBarMMD` with Decks / Statistics (the existing `Statistics` page, back arrow hidden) / More. Decks header: "Decks", Sync glyph with `BadgeMMD` dot (pending changes; one-way / not logged in as a subtitle line). `PagedList` of `DisplayDeckNode`, one row per deck: indent by depth, expand chevron, italic filtered decks, three right-aligned counts (zero blank), dashed separators. "Studied today" line. Empty state: text + `ButtonMMD` "Import file". Tap = Deck page; no long-press. Messages via `MessageHost` |
| 4. More tab | Rows: Import, Export, Create deck, Create filtered deck, Check database, Check media, Empty cards, Create backup, Restore backup, Sync account, Settings (Statistics is its own tab) |
| 5. Splash hold | Until the deck list has content, with a ceiling |
| 6. Replace / delete | Replaced by MMD versions: the Material bottom nav (`BottomNavController`, `HomeScreenNavigation`, `bottom_nav_menu`, `MoreFragment`) and the toolbar. Deleted (no MMD version, or not wanted): `NavigationDrawerActivity` + drawer layouts, the toolbar overflow menu and deck search, the FAB (`DeckPickerFloatingActionMenu`), pull-to-sync, background image, tablet split pane, deck shortcuts, `activity_homescreen`, `include_deck_picker`, `item_deck`, `DeckAdapter`, `DeckHierarchyLinesDecoration`, `DeckPickerContextMenu` + its content provider, and their tests |
| 7. E Ink refresh on answers | Done early (on by default every 12 answers, see Phase 2) |

Order: deck page → home → More → deletions and tests → CI → device check (cold start, expand/collapse,
deck page → rename → delete round trip on a throwaway deck, sync badge).

## Pre-existing issues (not caused by the fork)

- **Lint** also reports `ThreadConstraint` (23), `WrongThread` (14) and
  `ReportShortcutUsage` (1), all in upstream code: `Reviewer`, `AbstractFlashcardViewer`,
  `AnkiDroidJsAPI`, `NoteTypeFieldEditor`, `NavigationDrawerActivity`, `AutomaticAnswer`,
  `OnRenderProcessGoneDelegate`, and `DeckPicker.kt:1484` (blamed to upstream `b34112c`).
  Most sit in the legacy reviewer, which Phase 1 deletes.
- **`:lint-rules:test`**: 3 failures in `OpenInputStreamSafeDetectorTest` — lint cannot
  resolve `ContentResolver.openInputStream` in this environment. The detector is untouched.

## Known issues found on the way

- **Content provider authority mismatch.** `CardContentProvider` matches the hardcoded
  `com.ichi2.anki.flashcards` (`api/build.gradle.kts:29`) while the manifest declares
  `${applicationId}.flashcards`. Upstream `.debug` builds already have this mismatch. The
  provider only serves third-party apps adding notes (authoring), which Phase 1 removes.
- `HelpItemActionsDispatcher` still calls `CrashReportService.sendReport`; it now returns
  `false`. Help is deleted in Phase 1.
- `ComposeHostFragment` subclasses implement `ScreenContent()`, never `Content()`: inside
  `ComposeView.apply { }` that name resolves to `ComposeView.Content()` and recurses.

## Design kit (in the repo, `AnkiDroid/src/main/java/com/ichi2/compose/mmd/`)

`com.ichi2.compose.mmd`: `MmdTheme` (fills MMD's six unspecified colour roles),
`DashedDividerMMD`, `ScreenHeader`/`HeaderAction`, `Panel`/`PanelDialog`/`ConfirmPanel`
(no dim, no window animation), `MmdSheet`/`MenuPanel`/`ChoiceSheet`/`MultiChoiceSheet`,
`NavRow`/`SwitchRow`/`ValueRow`/`ActionRow`/`SectionTitle`, `PagedList`, `ProgressPanel`,
`MessageHost` (explicit snackbar colours, real 3dp rule), `WebContent`, `ComposeHostFragment`.
`com.ichi2.anki.ui.eink`: `EinkRefresh` (needs `Prefs.isEinkRefreshEnabled` and
`Prefs.einkRefreshInterval`) and the debug `MmdKitGalleryFragment`.

Every MMD signature used was checked against `mudita/MMD@0b8940c` source.

**Token profiles.** Sizes where the MMD library and Mudita's apps disagree live in
`MmdTokens`: `Library` (default — 56dp rows, 8dp corners) and `KompaktSystem` (64dp rows,
16dp corners, as measured). Swap with `MmdTheme(tokens = MmdTokens.KompaktSystem)`.

**Settings → Advanced → MMD kit gallery** (last row) opens `MmdKitGalleryFragment` (debug builds) with
every component, for checking on the panel. New preference keys: `einkRefreshEnabled`,
`einkRefreshInterval`, `mmdKitGallery`.

## Working notes

- JDK on PATH is Temurin 21; `JAVA_HOME` is unset; Gradle wrapper 9.7.1.
- Android SDK at `C:/Users/Antonio/AppData/Local/Android/Sdk`; adb at
  `C:\Users\Antonio\android-sdk\platform-tools\adb.exe`.
- Warnings are errors; unused imports fail the build. The pre-commit hook runs ktlint on
  staged Kotlin files and fixes what it can.
- First full build ~8 min; a targeted test run ~3–5 min; lint ~10 min. Run Gradle in the
  background and never edit sources while a build or lint is running.
- Deleting files trips Kotlin's incremental cache (`Incremental compilation failed …
  dirtyLookupSymbols`) and it falls back to a full recompile. Batch deletions per build.
- **Faster loops.** `tools/mmd/gradle-high.sh <args>` runs `./gradlew` with every Java process
  raised to High priority. `-PfastLint` limits lint to `UnusedResources` on main sources (can
  flag resources used only by tests); run full lint once per phase.
- **Mass deletions confuse Gradle's file watcher.** After the Phase 1 resource prune (245 files
  deleted by script), `mergePlayDebugResources` stayed UP-TO-DATE / FROM-CACHE with the deleted
  tablet layouts still merged, even after clearing `build/intermediates`. Fix: `./gradlew --stop`,
  then build with `--no-watch-fs --no-build-cache`.
- **Phase-end builds run in a Claude cloud agent** (user preference, 2026-09-15): commit locally,
  launch the remote agent to run build + full lint + unit tests and report; fix locally.
- **Machine memory** lives in `C:\Users\Antonio\.gradle\gradle.properties` (not in git):
  Gradle daemon `-Xmx5g`, Kotlin daemon `-Xmx3g`, ParallelGC. The repo keeps upstream's 3 GB.
