/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Graphs on the Mudita Kompakt. The display is 1-bit: a grey or coloured SVG fill dithers into
 * noise, and two series that differ only in hue become one shape. Every non-monochrome fill is
 * therefore replaced by a 4x4 Bayer dot pattern whose density matches the fill's darkness, so the
 * series stay apart and the page stays legible. Loaded only on the graphs page, by PageWebViewClient.
 */
(function () {
    "use strict";

    // Ordered dithering: a dot is drawn where the matrix value is below the level.
    const BAYER = [
        [0, 8, 2, 10],
        [12, 4, 14, 6],
        [3, 11, 1, 9],
        [15, 7, 13, 5],
    ];
    /** Dot counts per 4x4 cell, lightest to darkest. Six steps are as many as 4x4 can hold apart. */
    const LEVELS = [0, 3, 6, 10, 13, 16];
    const PREFIX = "mmd-halftone-";
    /** Text and marks below this size lose their shape when dithered, so they are drawn solid. */
    const MIN_DITHERED_PX = 6;

    const SVG_NS = "http://www.w3.org/2000/svg";

    function buildPatterns() {
        if (document.getElementById(PREFIX + "defs")) return;
        const svg = document.createElementNS(SVG_NS, "svg");
        svg.id = PREFIX + "defs";
        svg.setAttribute("width", "0");
        svg.setAttribute("height", "0");
        svg.setAttribute("aria-hidden", "true");
        svg.style.position = "absolute";
        const defs = document.createElementNS(SVG_NS, "defs");

        LEVELS.forEach(function (level, index) {
            const pattern = document.createElementNS(SVG_NS, "pattern");
            pattern.id = PREFIX + index;
            pattern.setAttribute("patternUnits", "userSpaceOnUse");
            pattern.setAttribute("width", "4");
            pattern.setAttribute("height", "4");
            for (let y = 0; y < 4; y++) {
                for (let x = 0; x < 4; x++) {
                    if (BAYER[y][x] >= level) continue;
                    const dot = document.createElementNS(SVG_NS, "rect");
                    dot.setAttribute("x", String(x));
                    dot.setAttribute("y", String(y));
                    dot.setAttribute("width", "1");
                    dot.setAttribute("height", "1");
                    dot.setAttribute("fill", "#000");
                    pattern.appendChild(dot);
                }
            }
            defs.appendChild(pattern);
        });
        svg.appendChild(defs);
        document.body.appendChild(svg);
    }

    /** `#abc`, `#aabbcc` or `rgb(…)` as [r, g, b], or null when it is not a colour we can read. */
    function parseColour(value) {
        if (!value) return null;
        const colour = value.trim().toLowerCase();
        if (colour === "none" || colour === "transparent" || colour.startsWith("url(")) return null;
        if (colour.startsWith("#")) {
            const hex = colour.slice(1);
            const full =
                hex.length === 3
                    ? hex.replace(/./g, function (c) {
                          return c + c;
                      })
                    : hex;
            if (full.length < 6) return null;
            return [0, 2, 4].map(function (i) {
                return parseInt(full.substr(i, 2), 16);
            });
        }
        const parts = colour.match(/rgba?\(([^)]+)\)/);
        if (!parts) return null;
        const numbers = parts[1].split(",").map(function (n) {
            return parseFloat(n);
        });
        if (numbers.length < 3 || numbers.some(isNaN)) return null;
        return numbers.slice(0, 3);
    }

    function patternFor(colour) {
        const luminance = (0.2126 * colour[0] + 0.7152 * colour[1] + 0.0722 * colour[2]) / 255;
        const darkness = 1 - luminance;
        // black and white are left alone: they already read correctly
        if (darkness <= 0.02 || darkness >= 0.98) return null;
        const index = Math.round(darkness * (LEVELS.length - 1));
        return PREFIX + Math.min(LEVELS.length - 1, Math.max(0, index));
    }

    function isSmall(element) {
        const box = element.getBBox ? element.getBBox() : null;
        if (!box) return false;
        return box.width < MIN_DITHERED_PX || box.height < MIN_DITHERED_PX;
    }

    function ditherFill(element) {
        if (element.dataset.mmdHalftone === "done") return;
        const fill = element.getAttribute("fill") || window.getComputedStyle(element).fill;
        const colour = parseColour(fill);
        if (!colour) return;
        element.dataset.mmdHalftone = "done";
        if (element.tagName === "text" || isSmall(element)) {
            element.setAttribute("fill", "#000");
            return;
        }
        const pattern = patternFor(colour);
        if (pattern === null) {
            element.setAttribute("fill", colour[0] > 127 ? "#fff" : "#000");
            return;
        }
        element.setAttribute("fill", "url(#" + pattern + ")");
        // an outline keeps a pale series visible against the page
        if (!element.getAttribute("stroke") || element.getAttribute("stroke") === "none") {
            element.setAttribute("stroke", "#000");
            element.setAttribute("stroke-width", "1");
        }
    }

    let queued = false;
    function sweep() {
        queued = false;
        buildPatterns();
        document
            .querySelectorAll("svg rect, svg path, svg circle, svg ellipse, svg polygon, svg text")
            .forEach(ditherFill);
    }

    function schedule() {
        if (queued) return;
        queued = true;
        window.requestAnimationFrame(sweep);
    }

    function start() {
        sweep();
        new MutationObserver(schedule).observe(document.body, {
            childList: true,
            subtree: true,
            attributes: true,
            attributeFilter: ["fill", "style", "d"],
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", start);
    } else {
        start();
    }
})();
