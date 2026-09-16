# Settings audit

Phase 5, step 1: every key in the kept `res/xml/preferences_*.xml`, with keep or delete and the
reason. 127 keys in all — **51 kept**, **76 deleted**.

The keys themselves do not change: the MMD settings pages read and write the same
`Prefs` delegates, so existing values survive and `HardcodedPreferenceKey` stays valid.

## Added by this fork

| Key | Control | Why |
| --- | --- | --- |
| `einkRefresh` | switch | Flash the screen clear while answering; on by default (Phase 2) |
| `einkRefreshInterval` | number | How many answers between flashes; 12 by default. The owner asked for this in the Phase 2 device check, and it has had no UI until now |

## Whole screens that go

- **`preferences_previewer_controls.xml`** (14 keys): nothing opens the previewer any more —
  `previewer/` keeps only the shared card-viewer base classes. `PreviewerAction` and
  `ControlPreferenceScreen.PREVIEWER` go with it.
- **The controls tab strip** (`controlsTabLayout`): it existed to switch between the reviewer's and
  the previewer's bindings.

## Key bindings go

The owner's call (2026-09-15): "drop keybindings and such, it doesnt have a physical keyboard".
So the 33 reviewer bindings and the 9 user actions lose their editor, along with
`preferences_reviewer_controls.xml`, `ControlsSettingsFragment` and `ControlPreferenceScreen`.
The Gestures page keeps tap zones and swipes, as the plan says.

**The stored `binding_*` values are not deleted.** Each key holds every binding for its action,
gestures included, and `BindingMap` still reads them, so tap zones and swipes keep working exactly
as they do today. Only the screen that edited them goes.

### `preferences_accessibility.xml`

| Key | Control | Title | Decision | Why |
| --- | --- | --- | --- | --- |
| `accessibilityScreen` | PreferenceScreen | Accessibility | **keep** | the page itself |
| `cardZoom` | SliderPreference | Card zoom | **keep** | the only way to change card text size; the owner asked about font size |
| `imageZoom` | SliderPreference | Image zoom | **keep** | images are the other half of a card |
| `answerButtonSize` | SliderPreference | Answer button size | **delete** | the MMD answer row is a fixed 56dp; the pref was already ignored |
| `answerBtnSize` | SliderPreference | Answer button size | **delete** | legacy duplicate of answerButtonSize |
| `showLargeAnswerButtons` | SwitchPreferenceCompat | Show large answer buttons | **delete** | same fixed answer row |
| `relativeCardBrowserFontSize` | SliderPreference | Card browser font scaling | **delete** | no card browser in this fork |
| `showCardAnswerButtonTime` | SliderPreference | Show answer long-press time | **delete** | long-pressing an answer button belonged to the old reviewer |
| `doubleTapTimeout` | SliderPreference | Double tap time interval | **keep** | deck options unlocks its FSRS parameters on a triple tap |

### `preferences_advanced.xml`

| Key | Control | Title | Decision | Why |
| --- | --- | --- | --- | --- |
| `pref_screen_advanced` | PreferenceScreen | Advanced | **keep** | the page itself |
| `deckPath` | EditTextPreference | AnkiDroid directory | **delete** | storage is app-private and fixed (Phase 0) |
| `resetLanguages` | Preference | Reset languages | **delete** | resets TTS/voice languages, and there is no TTS |
| `double_scrolling` | SwitchPreferenceCompat | Double scrolling | **delete** | a workaround for the deleted reviewer |
| `category_workarounds` | PreferenceCategory | Workarounds | **keep** | groups the two workarounds below |
| `softwareRender` | SwitchPreferenceCompat | Disable card hardware render | **keep** | a WebView rendering escape hatch worth keeping on E Ink |
| `useInputTag` | SwitchPreferenceCompat | Type answer into the card | **keep** | type-answer is still in the study screen |
| `disableExtendedTextUi` | SwitchPreferenceCompat | Disable single-field edit mode | **delete** | note editor only |
| `noteEditorNewlineReplace` | SwitchPreferenceCompat | Replace newlines with HTML | **delete** | note editor only |
| `autoFocusTypeInAnswer` | SwitchPreferenceCompat | Focus ‘type in answer’ | **keep** | read by the study screen's type-answer field |
| `mediaImportAllowAllFiles` | SwitchPreferenceCompat | Allow all files in media imports | **delete** | media is imported through .apkg, not the deleted editor |
| `useFixedPort` | SwitchPreferenceCompat | localStorage in Study Screen | **keep** | cards that use localStorage need the fixed port |
| `category_plugins` | PreferenceCategory | Plugins | **delete** | with the plugin settings below |
| `providerEnabled` | SwitchPreferenceCompat | Enable AnkiDroid API | **delete** | no third-party apps talk to this phone's fork |
| `thirdpartyapps_link` | Preference | Third-party API apps | **delete** | opens a browser the Kompakt does not have |
| `allow_dangerous_js_api` | SwitchPreferenceCompat | Globally allow dangerous JavaScript APIs | **keep** | cards may call the JS API; the gate stays with it |
| `allowCardExternalLaunch` | SwitchPreferenceCompat | Allow cards to launch other apps | **delete** | no other apps to launch |
| `mmdKitGallery` | Preference | MMD kit gallery | **keep** | the fork's own component gallery |

### `preferences_backup_limits.xml`

| Key | Control | Title | Decision | Why |
| --- | --- | --- | --- | --- |
| `backupsScreen` | PreferenceScreen | Backup | **keep** | the page itself |
| `backups_help` | HtmlHelpPreference | — | **keep** | explains the four numbers; becomes plain text |
| `minutes_between_automatic_backups` | IncrementerNumberRangePreferenceCompat | Minutes between automatic backups | **keep** | backups are the safety net for a single-device collection |
| `daily_backups_to_keep` | IncrementerNumberRangePreferenceCompat | Daily backups to keep | **keep** | as above |
| `weekly_backups_to_keep` | IncrementerNumberRangePreferenceCompat | Weekly backups to keep | **keep** | as above |
| `monthly_backups_to_keep` | IncrementerNumberRangePreferenceCompat | Monthly backups to keep | **keep** | as above |

### `preferences_controls.xml`

| Key | Control | Title | Decision | Why |
| --- | --- | --- | --- | --- |
| `controlsScreen` | PreferenceScreen | Controls | **keep** | becomes Gestures: tap zones and swipes |
| `gestures` | SwitchPreferenceCompat | Enable gestures | **keep** | tap zones and swipes drive the study screen |
| `gestureCornerTouch` | SwitchPreferenceCompat | 9-point touch | **keep** | 9-point touch splits the card into tap zones |
| `swipeSensitivity` | SliderPreference | Swipe sensitivity | **keep** | swipes need tuning on a small screen |
| `controlsTabLayout` | ControlsTabPreference | — | **delete** | the tab strip only existed to switch to the previewer's bindings |

### `preferences_custom_sync_server.xml`

| Key | Control | Title | Decision | Why |
| --- | --- | --- | --- | --- |
| `customSyncServerScreen` | PreferenceScreen | Custom sync server | **keep** | the page itself |
| `syncBaseUrl` | VersatileTextWithASwitchPreference | Sync URL | **keep** | self-hosted sync servers stay supported |
| `customSyncCertificate` | VersatileTextPreference | Custom root certificate (PEM) | **keep** | a self-hosted server may need its certificate |

### `preferences_general.xml`

| Key | Control | Title | Decision | Why |
| --- | --- | --- | --- | --- |
| `generalScreen` | PreferenceScreen | General | **keep** | the page itself |
| `language` | ListPreference | Language | **keep** | app language |
| `pastePNG` | SwitchPreferenceCompat | — | **delete** | pasting images belonged to the note editor |
| `useCurrent` | ListPreference | Deck for new cards | **delete** | chooses the deck for new cards, and cards are no longer added here |
| `exitViaDoubleTapBack` | SwitchPreferenceCompat | Press back twice to go back/exit | **keep** | the home screen still uses it |

### `preferences_notifications.xml`

| Key | Control | Title | Decision | Why |
| --- | --- | --- | --- | --- |
| `notificationsScreen` | PreferenceScreen | Notifications | **keep** | the page itself |
| `minimumCardsDueForNotification` | ListPreference | Notify when | **keep** | the due-cards notification survives |
| `widgetVibrate` | SwitchPreferenceCompat | Vibrate | **keep** | applies to that notification, not to the deleted widgets |
| `widgetBlink` | SwitchPreferenceCompat | Blink light | **keep** | as above |

### `preferences_previewer_controls.xml`

| Key | Control | Title | Decision | Why |
| --- | --- | --- | --- | --- |
| `previewer_BACK` | ControlPreference | Back | **delete** | nothing opens the previewer in this fork |
| `previewer_NEXT` | ControlPreference | Next | **delete** | nothing opens the previewer in this fork |
| `previewer_MARK` | ControlPreference | — | **delete** | nothing opens the previewer in this fork |
| `previewer_EDIT` | ControlPreference | Edit note | **delete** | nothing opens the previewer in this fork |
| `previewer_REPLAY_AUDIO` | ControlPreference | Replay media | **delete** | nothing opens the previewer in this fork |
| `previewer_TOGGLE_BACKSIDE_ONLY` | ControlPreference | Toggle backside only | **delete** | nothing opens the previewer in this fork |
| `previewer_TOGGLE_FLAG_RED` | ControlPreference | Toggle red flag | **delete** | nothing opens the previewer in this fork |
| `previewer_TOGGLE_FLAG_ORANGE` | ControlPreference | Toggle orange flag | **delete** | nothing opens the previewer in this fork |
| `previewer_TOGGLE_FLAG_GREEN` | ControlPreference | Toggle green flag | **delete** | nothing opens the previewer in this fork |
| `previewer_TOGGLE_FLAG_BLUE` | ControlPreference | Toggle blue flag | **delete** | nothing opens the previewer in this fork |
| `previewer_TOGGLE_FLAG_PINK` | ControlPreference | Toggle pink flag | **delete** | nothing opens the previewer in this fork |
| `previewer_TOGGLE_FLAG_TURQUOISE` | ControlPreference | Toggle turquoise flag | **delete** | nothing opens the previewer in this fork |
| `previewer_TOGGLE_FLAG_PURPLE` | ControlPreference | Toggle purple flag | **delete** | nothing opens the previewer in this fork |
| `previewer_UNSET_FLAG` | ControlPreference | Remove flag | **delete** | nothing opens the previewer in this fork |

### `preferences_reviewer.xml`

| Key | Control | Title | Decision | Why |
| --- | --- | --- | --- | --- |
| `reviewCategory` | PreferenceCategory | — | **keep** | groups the study screen's switches |
| `showProgress` | SwitchPreferenceCompat | — | **keep** | the counts in the study screen's header |
| `showAudioPlayButtons` | SwitchPreferenceCompat | — | **keep** | the card's own play buttons, separate from the header's Replay |
| `showAnswerButtons` | SwitchPreferenceCompat | Show answer buttons | **delete** | the owner: turning it off 'seems pointless'; the study screen needs its answer row |
| `showEstimates` | SwitchPreferenceCompat | — | **keep** | the next interval under each rating |
| `hideHardAndEasy` | SwitchPreferenceCompat | Hide ‘Hard’ and ‘Easy’ buttons | **keep** | a two-button study screen is a real preference; already read by the UI |

### `preferences_reviewer_controls.xml`

| Key | Control | Title | Decision | Why |
| --- | --- | --- | --- | --- |
| `binding_SHOW_ANSWER` | ReviewerControlPreference | Show answer | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_ANSWER_AGAIN` | ReviewerControlPreference | — | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_ANSWER_HARD` | ReviewerControlPreference | — | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_ANSWER_GOOD` | ReviewerControlPreference | — | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_ANSWER_EASY` | ReviewerControlPreference | Answer easy | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_TOGGLE_FLAG_RED` | ReviewerControlPreference | Toggle red flag | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_TOGGLE_FLAG_ORANGE` | ReviewerControlPreference | Toggle orange flag | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_TOGGLE_FLAG_GREEN` | ReviewerControlPreference | Toggle green flag | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_TOGGLE_FLAG_BLUE` | ReviewerControlPreference | Toggle blue flag | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_TOGGLE_FLAG_PINK` | ReviewerControlPreference | Toggle pink flag | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_TOGGLE_FLAG_TURQUOISE` | ReviewerControlPreference | Toggle turquoise flag | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_TOGGLE_FLAG_PURPLE` | ReviewerControlPreference | Toggle purple flag | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_UNSET_FLAG` | ReviewerControlPreference | Remove flag | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_BURY_CARD` | ReviewerControlPreference | — | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_SUSPEND_CARD` | ReviewerControlPreference | — | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_RESCHEDULE_NOTE` | ReviewerControlPreference | — | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_CARD_INFO` | ReviewerControlPreference | — | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_PREVIOUS_CARD_INFO` | ReviewerControlPreference | — | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_BURY_NOTE` | ReviewerControlPreference | — | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_SUSPEND_NOTE` | ReviewerControlPreference | — | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_DELETE` | ReviewerControlPreference | — | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_MARK` | ReviewerControlPreference | — | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_TAG` | ReviewerControlPreference | Edit tags | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_PAGE_UP` | ReviewerControlPreference | Page up | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_PAGE_DOWN` | ReviewerControlPreference | Page down | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_STATISTICS` | ReviewerControlPreference | — | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_EXIT` | ReviewerControlPreference | Close study screen | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_PLAY_MEDIA` | ReviewerControlPreference | Replay media | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_UNDO` | ReviewerControlPreference | Undo | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_REDO` | ReviewerControlPreference | Redo | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_TOGGLE_AUTO_ADVANCE` | ReviewerControlPreference | Toggle auto advance | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_SHOW_HINT` | ReviewerControlPreference | Show hint | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_SHOW_ALL_HINTS` | ReviewerControlPreference | Show all hints | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `userActionsControls` | ExtendedPreferenceCategory | User actions | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_USER_ACTION_1` | ReviewerControlPreference | User action 1 | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_USER_ACTION_2` | ReviewerControlPreference | User action 2 | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_USER_ACTION_3` | ReviewerControlPreference | User action 3 | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_USER_ACTION_4` | ReviewerControlPreference | User action 4 | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_USER_ACTION_5` | ReviewerControlPreference | User action 5 | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_USER_ACTION_6` | ReviewerControlPreference | User action 6 | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_USER_ACTION_7` | ReviewerControlPreference | User action 7 | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_USER_ACTION_8` | ReviewerControlPreference | User action 8 | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |
| `binding_USER_ACTION_9` | ReviewerControlPreference | User action 9 | **delete** | key bindings: the Kompakt has no physical keyboard (owner, 2026-09-15) |

### `preferences_reviewing.xml`

| Key | Control | Title | Decision | Why |
| --- | --- | --- | --- | --- |
| `reviewingScreen` | PreferenceScreen | Reviewing | **keep** | the page itself |
| `dayOffset` | SliderPreference | Start of next day | **keep** | start of the next day |
| `learnCutoff` | IncrementerNumberRangePreferenceCompat | Learn ahead limit | **keep** | learn-ahead limit |
| `timeLimit` | IncrementerNumberRangePreferenceCompat | Timebox time limit | **keep** | timebox, which the study screen shows |
| `keepScreenOn` | SwitchPreferenceCompat | Keep screen on | **keep** | useful while studying |

### `preferences_sync.xml`

| Key | Control | Title | Decision | Why |
| --- | --- | --- | --- | --- |
| `syncScreen` | PreferenceScreen | Sync | **keep** | the page itself |
| `syncAccount` | Preference | — | **keep** | opens the login or logged-in page |
| `syncFetchMedia` | ListPreference | Fetch media on sync | **keep** | media costs bandwidth and space |
| `automaticSyncMode` | SwitchPreferenceCompat | Automatic synchronization | **keep** | gates every automatic sync |
| `showSyncStatusBadge` | SwitchPreferenceCompat | Display synchronization status | **keep** | the header's sync dot |
| `allowMetered` | SwitchPreferenceCompat | Allow sync on metered connections | **keep** | the phone is often on mobile data |
| `syncIoTimeoutSecs` | IncrementerNumberRangePreferenceCompat | — | **keep** | slow connections need it |
| `one_way_sync` | Preference | One-way sync | **keep** | the way out of a sync conflict |
| `custom_sync_server_link` | Preference | Custom sync server | **keep** | opens the page above |
