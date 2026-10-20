package com.ronybrand.estados.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronybrand.estados.R
import com.ronybrand.estados.core.startApiUrl
import com.ronybrand.estados.ui.AppColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val DEFAULT_DURATION_MS = 5_000L

// The error only arrives after the timeout and retries (~45s) with an empty screen, so the usual 5s would vanish before
// being read.
private const val API_UNAVAILABLE_DURATION_MS = 60_000L

/** A transient error banner's state: set it, and it clears itself. */
@Stable
class ErrorMessageState(private val scope: CoroutineScope, private val durationMs: Long = DEFAULT_DURATION_MS) {
    var error: String? by mutableStateOf(null)
        private set
    var requestId: String? by mutableStateOf(null)
        private set
    var apiUnavailable: Boolean by mutableStateOf(false)
        private set
    private var timer: Job? = null

    fun show(message: String, requestId: String? = null, apiUnavailable: Boolean = false) {
        timer?.cancel()
        error = message
        this.requestId = requestId
        this.apiUnavailable = apiUnavailable
        timer = scope.launch {
            delay(if (apiUnavailable) API_UNAVAILABLE_DURATION_MS else durationMs)
            clear()
        }
    }

    fun clear() {
        timer?.cancel()
        timer = null
        error = null
        requestId = null
        apiUnavailable = false
    }
}

@Composable
fun rememberErrorMessage(): ErrorMessageState {
    val scope = rememberCoroutineScope()
    return remember { ErrorMessageState(scope) }
}

@Composable
fun ErrorBanner(error: String?, requestId: String?, modifier: Modifier = Modifier, apiUnavailable: Boolean = false) {
    if (error == null) return
    // A live region makes TalkBack announce the text as soon as it appears, without the user having to find it.
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .testTag("error-banner")
            .semantics { liveRegion = LiveRegionMode.Polite },
        color = AppColors.DangerSoft,
        border = BorderStroke(1.dp, AppColors.Danger),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(error, color = AppColors.Danger)
            if (apiUnavailable) {
                val uriHandler = LocalUriHandler.current
                val locale = Locale.forLanguageTag(stringResource(R.string.date_locale_tag))
                TextButton(
                    onClick = { uriHandler.openUri(startApiUrl(locale)) },
                    modifier = Modifier.testTag("turn-on-api"),
                ) {
                    Text(stringResource(R.string.start_api), color = AppColors.Danger)
                }
            }
            if (requestId != null) {
                Text(
                    stringResource(R.string.reference_id, requestId),
                    color = AppColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
