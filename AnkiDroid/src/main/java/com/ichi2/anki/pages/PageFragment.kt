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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.CallSuper
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.ichi2.anki.R
import com.ichi2.anki.workarounds.OnWebViewRecreatedListener
import com.ichi2.anki.workarounds.SafeWebViewLayout
import com.ichi2.compose.mmd.HeaderAction
import com.ichi2.compose.mmd.MmdTheme
import com.ichi2.compose.mmd.ScreenHeader
import com.ichi2.compose.mmd.WebContent
import com.ichi2.utils.WebViewVersion
import com.ichi2.utils.showDialogIfWebViewOutdated
import com.mudita.mmd.components.text.TextMMD
import timber.log.Timber

/**
 * Base class for displaying Anki HTML pages
 */
abstract class PageFragment :
    Fragment(),
    PostRequestHandler,
    OnWebViewRecreatedListener {
    lateinit var webViewLayout: SafeWebViewLayout
    private lateinit var server: AnkiServer
    protected abstract val pagePath: String

    /** The header's title. Subclasses set it as soon as they know what the page shows. */
    @VisibleForTesting
    internal var title: String by mutableStateOf("")

    /**
     * Whether the page is still preparing itself, shown as a line of text: a spinner repaints
     * continuously, which ghosts on E Ink.
     */
    protected var isLoading: Boolean by mutableStateOf(false)

    /** Extra actions for the header, to the right of the title. */
    @Composable
    protected open fun RowScope.HeaderActions() = Unit

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        // invisible until the page has loaded, so a half-drawn page never reaches the screen
        webViewLayout = SafeWebViewLayout(requireContext()).apply { isVisible = false }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { MmdTheme { PageScreen() } }
        }
    }

    @Composable
    private fun PageScreen() {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = title,
                navigationIcon = {
                    HeaderAction(
                        icon = R.drawable.ic_baseline_arrow_back_24,
                        contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                        onClick = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                    )
                },
                actions = { HeaderActions() },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                WebContent(factory = { webViewLayout }, modifier = Modifier.fillMaxSize())
                if (isLoading) {
                    TextMMD(
                        text = stringResource(R.string.dialog_processing),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }

    /**
     * Override this to set a custom [WebViewClient] to the page.
     * This is called in [onViewCreated].
     *
     * @param savedInstanceState If non-null, this fragment is being re-constructed
     * from a previous saved state as given here.
     */
    protected open fun onCreateWebViewClient(savedInstanceState: Bundle?) = PageWebViewClient()

    protected open fun onWebViewCreated() { }

    protected open val minimumWebViewVersion: WebViewVersion? = null

    /**
     * When the webview calls `BridgeCommand("foo")`, the PageFragment execute `bridgeCommands["foo"]`.
     * By default, only bridge command is allowed, subclasses must redefine it if they expect bridge commands.
     */
    open val bridgeCommands: Map<String, () -> Unit> = mapOf()

    /**
     * Ensures that [pageWebViewClient] can receive `bridgeCommand` requests and execute the command from [bridgeCommands].
     */
    private fun setupBridgeCommand(pageWebViewClient: PageWebViewClient) {
        if (bridgeCommands.isEmpty()) {
            return
        }
        webViewLayout.addJavascriptInterface(
            object : Any() {
                @JavascriptInterface
                fun bridgeCommandImpl(request: String) {
                    bridgeCommands.getOrDefault(request) {
                        Timber.d("Unknown request received %s", request)
                    }()
                }
            },
            "bridgeCommandInterface",
        )
        pageWebViewClient.onPageFinishedCallbacks.add { webView ->
            webView.evaluateJavascript(
                "bridgeCommand = function(request){ bridgeCommandInterface.bridgeCommandImpl(request); };",
            ) {}
        }
    }

    @CallSuper
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        server = AnkiServer(this).also { it.start() }

        minimumWebViewVersion?.let { minVersion ->
            val isOutdated =
                with(requireContext()) {
                    showDialogIfWebViewOutdated(minVersion) {
                        requireActivity().finish()
                    }
                }
            if (isOutdated) {
                Timber.w("${this::class.simpleName} requires modern WebView version, aborting load")
                return
            }
        }
        setupWebView(savedInstanceState)
    }

    private fun setupWebView(savedInstanceState: Bundle?) {
        val pageWebViewClient = onCreateWebViewClient(savedInstanceState)
        webViewLayout.apply {
            setAcceptThirdPartyCookies(true)
            with(settings) {
                javaScriptEnabled = true
                displayZoomControls = false
                builtInZoomControls = true
                setSupportZoom(true)
            }
            setWebViewClient(pageWebViewClient)
            setWebChromeClient(PageChromeClient())
            setupBridgeCommand(pageWebViewClient)
            onWebViewCreated()
        }
        // one theme in this fork: the backend's #night fragment is never appended
        val url = "${server.baseUrl()}$pagePath".toUri()
        Timber.i("Loading $url")
        webViewLayout.loadUrl(url.toString())
    }

    override suspend fun handlePostRequest(
        uri: PostRequestUri,
        bytes: ByteArray,
    ): ByteArray {
        val methodName = uri.backendMethodName ?: throw IllegalArgumentException("unhandled request: $uri")

        val resolvedUiMethod =
            when (val uiResponse = activity.handleUiPostRequest(methodName, bytes)) {
                is UiPostRequestResponse.Handled -> return uiResponse.data
                is UiPostRequestResponse.UnknownMethod -> false
                is UiPostRequestResponse.Ignored -> true
            }

        return handleCollectionPostRequest(methodName, bytes) ?: run {
            if (!resolvedUiMethod) {
                Timber.w("Unknown TS method called.")
                Timber.d("No handlers resolve TS method %s", methodName)
            }
            throw IllegalArgumentException("unhandled method: $methodName")
        }
    }

    @CallSuper
    override fun onDestroyView() {
        server.stop()
        webViewLayout.safeDestroy()
        super.onDestroyView()
    }

    override fun onWebViewRecreated(webView: WebView) {
        setupWebView(null)
    }
}
