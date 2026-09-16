# Converting an app to E Ink — what this project learned

Written while converting AnkiDroid to Mudita Mindful Design for the Mudita Kompakt (480×800 at
213dpi, 1-bit, MuditaOS K 1.6.0, Android 12). The numbers are this device's; the reasoning carries to
any E Ink phone or reader.

Read [eink-design.md](eink-design.md) for the rules this fork follows and
[web-tokens.md](web-tokens.md) for the backend page variables. This file is the *why*: the things
that were only obvious after they went wrong on the device.

---

## 1. The display decides the design

An E Ink panel is not a slow LCD. Four properties drive every decision:

1. **A repaint is visible.** Every change flashes or smears the affected area. Animation is not
   "less smooth", it is a strobe. Anything that repaints continuously — spinners, progress bars,
   ripples, cursors, hover states, fades — has to go or be replaced by something static.
2. **Ghosting accumulates.** Old content stays faintly behind the new until a full refresh clears
   it. Screens that change often (a study screen answering cards) need a deliberate **flash to
   clear**, and the user should control how often.
3. **There is no grey.** The panel dithers greys into noise. Two greys that differ by 10% become the
   same texture, and light grey text on white is unreadable at small sizes.
4. **Touch is slower than you think.** A mistap costs a full repaint plus the user's attention, so
   targets must be large and destructive actions must confirm.

Everything below follows from these.

---

## 2. Colour: remove it, then re-encode what it meant

Deleting colour is easy. The work is re-encoding the *information* colour carried.

| Colour carried | Replace with |
| --- | --- |
| Emphasis (blue link, red danger) | Weight, underline, or wording |
| Category (card flags, states, series in a chart) | A **name**, or a **pattern** |
| Depth (elevation, shadow, translucency) | A 1–3dp outline |
| State (disabled grey, selected highlight) | Dashed outline for disabled; inversion for selected |
| Progress fill | A bar with a border, or plain text |

Concretely in this fork:
- Card flags are shown by **name** ("Red") next to a mono glyph, never a coloured dot.
- Selection inverts: black ground, white text.
- Disabled controls keep full contrast and switch to a **dashed** border, because a faded control
  looks like a rendering fault on 1-bit.
- Graph series become **halftone patterns** (below), not shades.

**Do not simply set everything to black.** Two black areas touching with no border read as one
shape. Every surface that was distinguished by fill now needs a border or a gap.

---

## 3. Motion: what to remove and what to put in its place

Remove: transitions, animations, ripple/indication, overscroll glow and stretch, fading scrollbars,
shimmer, blur, shadows, auto-scroll, marquee, and **indeterminate spinners**.

Replacements that work:

| Instead of | Use |
| --- | --- |
| Indeterminate spinner | One line of static text ("Processing") |
| Progress spinner with percentage | A determinate bar, **updated at most once a second** |
| Animated page transition | An instant swap |
| Pull-to-refresh | An explicit action in the header |
| Toast/snackbar that slides | A static message strip that replaces itself |

Throttling matters more than it sounds: a backend that reports progress every 100ms will repaint the
panel ten times a second. This fork polls at the backend's rate but pushes to the screen at most
once per second, which is the difference between a readable bar and a flickering one.

The one deliberate exception here is a **moving progress bar while syncing**, added at the owner's
request because the platform's own apps do it. If you allow one, allow only one.

---

## 4. Ghosting: give the user a flash-to-clear

Anki's study screen changes the whole card area on every answer, which builds ghosting quickly. The
fix that worked: invert the window to solid black for one frame every *N* answers, where *N* is a
setting (default 12). The user asked for this after the first device test in exactly those words:
"every 12 clicks or something".

Rules of thumb:
- Flash on a **counted event** (answers, page turns), not a timer: the user then knows why it
  happened.
- Never flash mid-interaction, only between screens or after a committed action.
- Make it a setting with a visible interval, since tolerance for flashing is personal.

---

## 5. Typography

- **Use the system font.** A bundled font looks foreign next to the platform's own apps, and on a
  1-bit panel hinting and stroke weight matter more than the typeface's personality. This project
  started with the library's bundled Lato and moved everything (Compose typography and the WebView
  CSS) to the system font after seeing both on the device.
- **Set a floor.** Nothing below 14sp for UI, 15px for web pages. Below that, dithering eats the
  strokes.
- **Bold is the primary signal.** It survives dithering where colour, size and greys do not. In the
  deck list, a deck with cards due is simply **bold** — that replaced three columns of numbers.
- **One weight step, not three.** Regular and bold. Semi-bold and light collapse into each other.

---

## 6. Layout

- **Page, do not scroll.** Continuous scrolling smears; paging repaints once per page. Where a list
  component supports page-turn scrolling, use it. Web content is the exception — HTML cannot page —
  so it scrolls, with the overscroll effects removed.
- **Reserve fixed chrome.** A scrollbar that appears only when a list is long shifts every row when
  the list grows. Keep its width reserved and draw an **inactive** scrollbar when there is nothing to
  scroll. Users notice the shift; they do not notice the empty track.
- **Avoid layout shift in general.** Each shift is a repaint of everything below it. Fixed-width
  numeric columns, reserved icon slots, and stable headers all pay for themselves.
- **Large targets.** 48dp minimum, 56–64dp rows. The platform's own apps here use 64dp rows with the
  label inset 64dp; the library's components default to 56dp. Pick one and make it a token.
- **Bottom-anchored panels beat centred dialogs.** They are reachable one-handed and they repaint a
  smaller area. No dim behind them: a dim layer is a full-screen repaint that then has to be undone.

---

## 7. Charts and images: dither on purpose

A chart is the hardest thing to port. Anki's graphs use hue to separate series and opacity for
emphasis; both vanish on 1-bit.

What worked:
1. Build **`<pattern>` definitions** of a 4×4 Bayer matrix at six densities, from empty to solid.
2. Walk the SVG and map every non-monochrome fill to the pattern whose density matches the fill's
   **luminance** (`0.2126r + 0.7152g + 0.0722b`), so a dark series stays visually dark.
3. Give every filled shape a **1px black stroke**, so a pale series still has an edge.
4. Snap text and marks under ~6px to solid black — a dithered glyph at that size is mush.
5. Re-run on mutation: charts redraw when the data or range changes, so a `MutationObserver`
   (coalesced with `requestAnimationFrame`) keeps patterns applied.

For photographs and note images, greyscale plus a slight contrast boost beats dithering; for
diagrams and line art, threshold to pure black and white.

---

## 8. WebViews: style them before first paint

Any app with web content (Anki's deck options, statistics, card info) has a second design system
inside it. Three lessons:

1. **Find the real variable names.** The backend here defines 59 CSS custom properties in its own
   `root-vars.css`, with a comment describing each. Extract them mechanically into a table with the
   mapping you choose; never guess names from the rendered page. The same bundle also used 183
   Bootstrap `--bs-*` variables — those are best left alone and overridden at the element level.
2. **Inject the stylesheet into the served HTML**, not after `DOMContentLoaded`. Rewriting the
   shell's `<head>` (the app serves the page from its own assets, so this is a string replace) means
   the page never paints in the original colours first. A JS-injected stylesheet always flashes.
3. **Kill the platform's dark mode path.** If the app appends a `#night` fragment or a `night-mode`
   class, remove it rather than styling both. One theme is less code and fewer surprises.

Also: define the overrides on `:root` *and* on the night-mode selector, so a page that flips the
class still lands on the same values.

---

## 9. Compose on E Ink (framework specifics)

- **Ripple is the default indication.** Disabling it once, at the theme, is not enough if you nest
  another `MaterialTheme` inside — the inner one re-provides the default indication. Prefer passing
  a typography/colour scheme into the platform theme over nesting themes.
- **Alpha is not a dimming tool.** `alpha(0.4f)` on 1-bit produces a dither pattern, not grey. Use
  borders, dashes, or omission.
- **`derivedStateOf` for "can this scroll".** Reading `canScrollForward` directly in composition
  re-runs it every frame; derived state recomputes only when it changes — fewer recompositions,
  fewer repaints.
- **Know your list component's contract.** The one here pages by *item*, so a single tall item cannot
  scroll at all, hidden items still count toward the page step, and the list renders at `alpha = 0`
  until it has counted its children. All three shaped how screens were built.
- **When the library has no public docs, read the bytecode.** `javap` on the AAR settled the exact
  scrollbar width (8dp track, 24dp arrows, 8dp padding = a 40dp column) and the fact that the top
  app bar applied its own window insets — which was the cause of a mysterious extra gap under the
  status bar on one screen.

---

## 10. The platform's own apps are the spec

The most useful hour of this project was capturing the device's built-in apps with
`adb exec-out screencap` plus `uiautomator dump`, and measuring them in pixels: row heights, divider
style (dotted, 1px, ~2.5px dashes), header rule thickness (4px), button corner radius, the exact
inset where labels start.

Two things came out of that:
- A **token table** with two profiles — "what the component library says" and "what the system apps
  do" — and one switch to move the whole app between them. They disagree more than you expect.
- Confidence when the library and the platform conflict. Written-down measurements beat opinion in
  every later argument with yourself.

---

## 11. Device behaviour will surprise you — check the logs

Two non-obvious findings, both from `adb logcat` and `adb shell settings get`:

- The phone had **"Don't keep activities" enabled**, so every screen change destroyed the previous
  activity. Anything done in `onCreate` ran on every return: re-running a start-up sync, re-reading
  a one-shot intent extra, re-showing prompts. The fix is not to fight it: consume one-shot extras
  once, gate start-up work behind "was this a fresh start or a restore", and keep state that must
  survive in a `ViewModel` or saved state.
- A "sync on every return" bug that looked like a design problem was this exact mechanism. **Read
  the device log before redesigning anything**; the phone tells you what actually happened.

---

## 12. Reduce the app, not just the theme

Converting an app to E Ink is a good moment to ask what the device is *for*. This fork deleted the
note editor, card browser, image occlusion, whiteboard, TTS, widgets, themes, crash reporting and
analytics — not to save work, but because each one assumed colour, animation, a camera, or a
browser. The result is fewer screens to convert and a faster app on weak hardware.

A useful test for each feature: *if this screen can only be black, white, static and slow, is it
still worth opening?* If not, cut it and say so in the plan.

---

## 13. Process notes

- **Test on the device early and often, in small rounds.** Every round of feedback in this project
  changed a decision that looked settled on paper: the bottom bar became header icons, counts became
  bold names, indentation went away, a reserved gutter became a drawn inactive scrollbar.
- **Give the owner mockups, not adjectives.** ASCII sketches of two or three layouts settled
  navigation in one exchange.
- **Screenshot tests with a device profile** (here: a 360×601dp tvdpi "Kompakt" device in Roborazzi)
  catch layout regressions without the phone, but they do not catch ghosting, contrast or touch
  size. Those need the device.
- **Build once per phase, not per step**, when builds are slow; push phase-end work to CI and keep
  local runs to incremental compiles.
- **Write the failing test first for bugs**, even UI ones: the "sync on every return" fix has a test
  that recreates the activity and asserts one sync, which is the only way that regression stays
  fixed.

---

## 14. Checklist for the next screen

1. Every colour resolved to black, white or transparent, with meaning re-encoded.
2. No animation, transition, spinner, ripple, shadow or blur left.
3. Text ≥ 14sp, one regular and one bold weight, system font.
4. Rows ≥ 56dp, touch targets ≥ 48dp, destructive actions confirmed in a bottom panel.
5. Fixed chrome reserved (scrollbar, icon slots, numeric columns) so nothing shifts.
6. Lists page rather than scroll; web content scrolls without overscroll effects.
7. Charts dithered to patterns with strokes; small marks solid.
8. Progress shown statically, or as a bar updated at most once a second.
9. State that must survive activity recreation is saved; one-shot work runs once.
10. Compared side by side with the platform's own app for the same job.
