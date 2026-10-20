package com.ronybrand.estados.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronybrand.estados.R
import com.ronybrand.estados.core.attempt
import com.ronybrand.estados.core.formatDate
import com.ronybrand.estados.data.BackendInfo
import com.ronybrand.estados.data.InfoRepository
import com.ronybrand.estados.ui.AppColors
import java.util.Locale

/** The app version comes from the binary; the backend build is informational, so a failed lookup just hides it. */
@Composable
fun AppFooter(appVersion: String, info: InfoRepository, modifier: Modifier = Modifier) {
    val backend by produceState<BackendInfo?>(initialValue = null, info) {
        value = attempt { info.backendInfo() }.getOrNull()
    }
    val locale = Locale.forLanguageTag(stringResource(R.string.date_locale_tag))
    FlowRow(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("footer"),
        horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.CenterHorizontally),
    ) {
        Text(stringResource(R.string.app_version, appVersion), fontSize = 11.sp, color = AppColors.Muted)
        backend?.let {
            Text("|", fontSize = 11.sp, color = AppColors.Muted)
            Text(
                stringResource(R.string.backend_version, it.commit, formatDate(it.time, locale)),
                fontSize = 11.sp,
                color = AppColors.Muted,
            )
        }
    }
}
