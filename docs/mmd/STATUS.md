# Project status — AnkiDroid on Mudita Mindful Design

Handoff notes. Read [eink-design.md](eink-design.md) for the rules and
[PLAN.md](PLAN.md) for the phases. This file records what is done, what is
half-done, and what is next.

Target device: **Mudita Kompakt `MK20250408317`** (MuditaOS K 1.6.0, Android 12,
480×800 @ 213dpi). Branch: **`mmd-eink`** (from upstream `b34112c`, nothing pushed).

## Real data — read before installing anything

- **AnkiDroid 2.24.1 (`com.ichi2.anki`) is installed on the Kompakt with the owner's
  real collection** at `/sdcard/AnkiDroid/collection.anki2`.
- A copy of that folder was pulled on 2026-09-14 to
  `C:\Users\Antonio\Backups\ankidroid-2.24.1-2026-09-14\` (174 MB).
- The fork must never open `/sdcard/AnkiDroid`. See Phase 0 storage below.

## Phase 0 — Safety and foundation

| Step | State |
| --- | --- |
| 1. Branch, `PLAN.md`, `eink-design.md`, `STATUS.md` | Done |
| 2. Back up real data | Done (folder copy). `.colpkg` export skipped by the owner |
| 3. Identity (`com.ichi2.anki.mmd`, "AnkiDroid MMD", `-mmd`) | Next |
| 4. Storage forced to app-private, `MANAGE_EXTERNAL_STORAGE` removed | Next (tests first) |
| 5. Remove upstream reporting (ACRA + Google Analytics) | Code done, **build verification running** |
| 6. MMD 1.0.2 compatibility spike | Not started |
| 7. Calibrate against Kompakt system apps | Not started — needs the owner to open screens |
| 8. Lato + OFL licence | Not started |
| 9. One XML theme | Not started |
| 10. Motion off globally | Not started |
| 11. Design kit `com.ichi2.compose.mmd` | Not started |
| 12. Test harness (Compose UI test deps, KOMPAKT device config) | Not started |

### Step 5 — ACRA and analytics removed

Both were deleted rather than disabled, at the owner's request.

- Deleted: `AcraCrashReporter`, `AnkiDroidUsageAnalytics`, `AnkiDroidCrashReportDialog`,
  `AcraAnalyticsInteraction`, `AnalyticsExceptionHandler`, `AnalyticsSamplePercentage`,
  `AnalyticsConstants`, `DeckPickerAnalyticsOptInDialog`, `analytic_constants.xml`,
  `docs/analytics/README.md`, and their tests (incl. androidTest `ACRATest`).
- Removed: the ACRA crash-dialog activity (`:acra` process) from the manifest; the
  error-reporting and analytics settings; the analytics developer option;
  `ACRA_URL` / `ANALYTICS_API_KEY` build config; the `acra-*`, `google-analytics-kt`
  and `auto-service` dependencies.
- **Kept:** the `CrashReportService` (`:common:android`) and `Analytics` (`:common`)
  facades. With no implementation registered they log and drop, so the ~60 call
  sites are unchanged.
- ACRA utilities that unrelated code borrowed were replaced:
  `MultimediaEditableNote.cloneField` now deep-copies with Java serialization;
  `FileUtilTest` uses `File.writeText`; `DebugInfoService` no longer prints an ACRA UUID.
- **Still to do:** unused strings/arrays (`error_reporting_*`, `analytics_*`,
  `pref_analytics_debug_key`, feedback strings). `UnusedResources` is **fatal** in
  `lint-release.xml`, so run lint and delete exactly what it reports.

## Known issues found on the way

- **Content provider authority mismatch after the id change.** `CardContentProvider`
  matches the hardcoded `com.ichi2.anki.flashcards` (`api/build.gradle.kts:29`),
  while the manifest declares `${applicationId}.flashcards`. Upstream `.debug`
  builds already have this mismatch. The provider only serves third-party apps adding
  notes (authoring), which Phase 1 removes.
- **Developer option "Set Database to pre-Scoped Storage default"** points the
  collection at `/storage/emulated/0/AnkiDroid` — the real collection's folder.
  Without storage permission on Android 12 the fork cannot read it, but delete the
  option in Phase 1 regardless.
- `HelpItemActionsDispatcher` still calls `CrashReportService.sendReport`; it now
  returns `false`. Help is deleted in Phase 1.

## Working notes

- JDK on PATH is Temurin 21; `JAVA_HOME` is unset; Gradle wrapper 9.7.1.
- Android SDK at `C:/Users/Antonio/AppData/Local/Android/Sdk`; adb at
  `C:\Users\Antonio\android-sdk\platform-tools\adb.exe`.
- Warnings are errors (`build.gradle.kts`); unused imports fail the build.
- A first full build of this project takes a long time; run Gradle in the background.
