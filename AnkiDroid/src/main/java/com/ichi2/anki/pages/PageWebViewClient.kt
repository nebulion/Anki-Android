/*
 *  Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.pages

import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.core.view.isVisible
import com.ichi2.anki.OnPageFinishedCallback
import com.ichi2.anki.workarounds.SafeWebViewClient
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.utils.AssetHelper.guessMimeType
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Base WebViewClient to be used on [PageFragment]
 */
open class PageWebViewClient : SafeWebViewClient() {
    val onPageFinishedCallbacks: MutableList<OnPageFinishedCallback> = mutableListOf()

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val path = request.url.path
        if (request.method != "GET" || path == null) return null
        if (path == "/favicon.png") {
            return WebResourceResponse("image/x-icon", null, ByteArrayInputStream(byteArrayOf()))
        }
        if (path == "/$MMD_PAGES_CSS") {
            return WebResourceResponse("text/css", "utf-8", view.context.assets.open(MMD_PAGES_CSS))
        }
        if (path == "/$MMD_HALFTONE_JS") {
            return WebResourceResponse("text/javascript", "utf-8", view.context.assets.open(MMD_HALFTONE_JS))
        }

        val assetPath =
            if (path.startsWith("/_app/")) {
                "backend/sveltekit/app/${path.substring(6)}"
            } else if (isSvelteKitPage(path.substring(1))) {
                SVELTEKIT_INDEX
            } else {
                return null
            }

        try {
            if (assetPath == SVELTEKIT_INDEX) return sveltekitShell(view, path)
            val mimeType = guessMimeType(assetPath)
            val inputStream = view.context.assets.open(assetPath)
            val response = WebResourceResponse(mimeType, null, inputStream)
            if ("immutable" in path) {
                response.responseHeaders = mapOf("Cache-Control" to "max-age=31536000")
            }
            return response
        } catch (_: IOException) {
            Timber.w("Not found %s", assetPath)
        }
        return null
    }

    /**
     * The SvelteKit shell with [MMD_PAGES_CSS] linked last in its head: the fork's black and white
     * then wins over the bundle's own stylesheet, and the page never flashes in Anki's colours.
     * The graphs page also gets [MMD_HALFTONE_JS], which dithers its fills.
     */
    private fun sveltekitShell(
        view: WebView,
        path: String,
    ): WebResourceResponse {
        val html =
            view.context.assets
                .open(SVELTEKIT_INDEX)
                .use { it.readBytes().decodeToString() }
        val head =
            buildString {
                append("""<link rel="stylesheet" href="/$MMD_PAGES_CSS">""")
                if (path.removePrefix("/").substringBefore("/") == "graphs") {
                    append("""<script src="/$MMD_HALFTONE_JS" defer></script>""")
                }
                append("</head>")
            }
        return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.replace("</head>", head).toByteArray()))
    }

    /**
     * Shows the WebView after the page is loaded
     *
     * This may be overridden if additional 'screen ready' logic is provided by the backend
     * @see DeckOptions
     */
    open fun onShowWebView(webView: WebView) {
        Timber.v("Displaying WebView")
        webView.isVisible = true
        (webView.parent as? SafeWebViewLayout)?.isVisible = true
    }

    override fun onPageFinished(
        view: WebView?,
        url: String?,
    ) {
        super.onPageFinished(view, url)
        if (view == null) return
        onPageFinishedCallbacks.map { callback -> callback.onPageFinished(view) }
        /* webView is invisible by default to avoid flashes while
         * the page is loaded, and can be made visible again after it finishes loading */
        onShowWebView(view)
    }
}

/** The fork's stylesheet for backend pages, in `assets/`. */
private const val MMD_PAGES_CSS = "mmd-pages.css"

/** Dithers the graphs' fills into 4x4 dot patterns; the graphs page only. */
private const val MMD_HALFTONE_JS = "mmd-halftone.js"

private const val SVELTEKIT_INDEX = "backend/sveltekit/index.html"

fun isSvelteKitPage(path: String): Boolean {
    val pageName = path.substringBefore("/")
    return when (pageName) {
        "graphs",
        "congrats",
        "card-info",
        "change-notetype",
        "deck-options",
        "import-anki-package",
        "import-csv",
        "import-page",
        "image-occlusion",
        -> true
        else -> false
    }
}

fun WebView.evaluateAfterDOMContentLoaded(
    script: String,
    resultCallback: ValueCallback<String>? = null,
) {
    evaluateJavascript(
        """
        var codeToRun = function() { 
            $script
        }
        
        if (document.readyState === "loading") {
          document.addEventListener("DOMContentLoaded", codeToRun);
        } else {
          codeToRun();
        }
        """.trimIndent(),
        resultCallback,
    )
}
