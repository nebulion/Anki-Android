# Backend web page tokens

Phase 4, step 1. The backend's pages (deck options, statistics, card info, congrats, the import
pages) are SvelteKit apps served from the app's own assets, so their variables can be read rather
than guessed. Extracted from
`AnkiDroid/build/intermediates/assets/playDebug/mergePlayDebugAssets/backend/css/root-vars.css`
(the light `:root` block; the fork drops `:root.night-mode`), plus a scan of
`backend/sveltekit/**` for `var(--…)` usage.

- **59** variables are defined by the backend, all listed below.
- **253** distinct variables are used across the bundle; **183** of those are
  Bootstrap's own `--bs-*`, and **36** are component-local (listed at the end).

`assets/mmd-pages.css` (step 2) redefines the colour and prop variables on `:root`, so every page
inherits the values without patching the bundle.

## Colours and props

| Variable | What the backend calls it | Light value | Fork | Why |
| --- | --- | --- | --- | --- |
| `--fg` | Default text/icon color | `#020202` | `#000` | all text is black |
| `--fg-subtle` | Placeholder text, icons in idle state | `#737373` | `#000` | no grey text on E Ink |
| `--fg-disabled` | Foreground color of disabled UI elements | `#858585` | `#000` | disabled is shown by a dashed outline, not grey |
| `--fg-faint` | Foreground color that barely stands out against canvas | `#afafaf` | `#000` | — |
| `--fg-link` | Hyperlink foreground color | `#1d4ed8` | `#000` | links are underlined instead of coloured |
| `--canvas` | Window background | `#f5f5f5` | `#fff` | — |
| `--canvas-elevated` | Background of containers | `white` | `#fff` | containers are separated by outlines |
| `--canvas-inset` | Background of inputs inside containers | `white` | `#fff` | — |
| `--canvas-overlay` | Background of floating elements (menus, tooltips) | `#fcfcfc` | `#fff` | menus get a 2dp outline |
| `--canvas-code` | Background of code editors | `white` | `#fff` | — |
| `--canvas-glass` | Transparent background for surfaces containing text | `rgba(255, 255, 255, 0.4)` | `#fff` | no translucency |
| `--border` | Border color with medium contrast against window background | `#c4c4c4` | `#000` | — |
| `--border-subtle` | Border color with low contrast against window background | `#e4e4e4` | `#000` | — |
| `--border-strong` | Border color with high contrast against window background | `#858585` | `#000` | — |
| `--border-focus` | Border color of focused input elements | `#3b82f6` | `#000` | focus is a thicker outline, set in the CSS |
| `--button-bg` | Background color of buttons | `#fcfcfc` | `#fff` | outlined button |
| `--button-gradient-start` | Start value of default button gradient | `white` | `#fff` | no gradients |
| `--button-gradient-end` | End value of default button gradient | `#fcfcfc` | `#fff` | no gradients |
| `--button-hover-border` | Border color of default button in hover state | `#999999` | `#000` | no hover on a touch screen |
| `--button-disabled` | Background color of disabled button | `rgba(214, 214, 214, 0.5)` | `#fff` | dashed outline marks it disabled |
| `--button-primary-bg` | Background color of primary button | `rgb(47.9, 106.8, 236)` | `#000` | solid black button, white label |
| `--button-primary-gradient-start` | Start value of primary button gradient | `#3b82f6` | `#000` | — |
| `--button-primary-gradient-end` | End value of primary button gradient | `rgb(47.9, 106.8, 236)` | `#000` | — |
| `--button-primary-disabled` | Background color of primary button in disabled state | `#93c5fd` | `#fff` | — |
| `--scrollbar-bg` | Background of scrollbar in idle state (Win/Lin only) | `#d6d6d6` | `#000` | matches the MMD scrollbar |
| `--scrollbar-bg-hover` | Background of scrollbar in hover state (Win/Lin only) | `#c4c4c4` | `#000` | — |
| `--scrollbar-bg-active` | Background of scrollbar in pressed state (Win/Lin only) | `#afafaf` | `#000` | — |
| `--shadow` | Default box-shadow color | `#c4c4c4` | `transparent` | shadows ghost on E Ink |
| `--shadow-inset` | Inset box-shadow color | `#454545` | `transparent` | — |
| `--shadow-subtle` | Box-shadow color with lower contrast against window background | `#737373` | `transparent` | — |
| `--shadow-focus` | Box-shadow color for elements in focused state | `#6366f1` | `transparent` | — |
| `--accent-card` | Accent color for cards | `#60a5fa` | `#000` | — |
| `--accent-note` | Accent color for notes | `#22c55e` | `#000` | — |
| `--accent-danger` | Saturated accent color to grab attention | `#ef4444` | `#000` | danger reads from its wording, not colour |
| `--flag-1` | Flag 1 (red) | `#ef4444` | `#000` | flags are named in this fork, never coloured |
| `--flag-2` | Flag 2 (orange) | `#fb923c` | `#000` | — |
| `--flag-3` | Flag 3 (green) | `#4ade80` | `#000` | — |
| `--flag-4` | Flag 4 (blue) | `#3b82f6` | `#000` | — |
| `--flag-5` | Flag 5 (pink) | `#e879f9` | `#000` | — |
| `--flag-6` | Flag 6 (turquoise) | `#2dd4bf` | `#000` | — |
| `--flag-7` | Flag 7 (purple) | `#a855f7` | `#000` | — |
| `--state-new` | Accent color for new cards | `#3b82f6` | `#000` | graphs separate the states with halftone patterns (step 3) |
| `--state-learn` | Accent color for cards in learning state | `#dc2626` | `#000` | — |
| `--state-review` | Accent color for cards in review state | `#16a34a` | `#000` | — |
| `--state-buried` | Accent color for buried cards | `#f59e0b` | `#000` | — |
| `--state-suspended` | Accent color for suspended cards | `#facc15` | `#000` | — |
| `--state-marked` | Accent color for marked cards | `#6366f1` | `#000` | — |
| `--highlight-bg` | Background color of highlighted items | `rgba(37, 99, 235, 0.5)` | `transparent` | highlight is an underline |
| `--highlight-fg` | Foreground color of highlighted items | `black` | `#000` | — |
| `--selected-bg` | Background color of selected text | `rgba(214, 214, 214, 0.5)` | `#000` | selection inverts: black behind white text |
| `--selected-fg` | Foreground color of selected text | `black` | `#fff` | — |
| `--font-size` | — | `15px` | `15px` | kept; MMD's floor is 14sp |
| `--border-radius` | Used to round corners of various UI elements | `5px` | `8px` | MMD's corner (`ButtonDefaultsMMD`) |
| `--border-radius-medium` | Used for container corners | `12px` | `8px` | — |
| `--border-radius-large` | Used for pill-shaped buttons | `15px` | `8px` | — |
| `--transition` | Default duration of transitions in milliseconds | `180ms` | `0ms` | no animation on E Ink |
| `--transition-medium` | Slightly longer transition duration in milliseconds | `500ms` | `0ms` | — |
| `--transition-slow` | Long transition duration in milliseconds | `1000ms` | `0ms` | — |
| `--blur` | Default background blur value | `20px` | `0` | no blur |

## Component-local variables in the bundle

Not defined in `root-vars.css`; each component sets its own default, so the fork only overrides the
ones that carry colour (the graph fills and strokes, the sticky header background and borders):

- `--area-fill`
- `--area-fill-opacity`
- `--area-stroke`
- `--area-stroke-opacity`
- `--badge-color`
- `--border-color`
- `--border-left-radius`
- `--border-right-radius`
- `--button-opacity`
- `--buttons-size`
- `--buttons-wrap`
- `--col-align`
- `--col-justify`
- `--col-size`
- `--cols`
- `--container-direction`
- `--container-height`
- `--dropdown-font-size`
- `--fill-tool-colour`
- `--gutter-block`
- `--gutter-inline`
- `--height`
- `--icon-align`
- `--icon-size`
- `--min-height`
- `--padding-inline`
- `--popover-padding-block`
- `--popover-padding-inline`
- `--popover-width`
- `--sticky-bg`
- `--sticky-border`
- `--sticky-borders`
- `--sticky-top`
- `--width`
- `--width-multiplier`
- `--z-index`

The remaining 183 `--bs-*` variables come from Bootstrap. The fork does not restate them:
`mmd-pages.css` sets colour on the elements themselves, which wins over Bootstrap's defaults.

## What is not a token

- **Graph fills** are patterns, not colours: `assets/mmd-halftone.js` (step 3) swaps each non-mono
  SVG fill for a 4×4 Bayer pattern matching its luminance.
- **`#night`**: the fragment the app appends to page URLs is removed; the fork has one theme.
