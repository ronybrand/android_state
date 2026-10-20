package com.ronybrand.estados.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ronybrand.estados.R
import com.ronybrand.estados.ui.AppColors

val BrandBarColors
    @Composable
    @OptIn(ExperimentalMaterial3Api::class)
    get() = TopAppBarDefaults.topAppBarColors(
        containerColor = AppColors.Brand,
        titleContentColor = androidx.compose.ui.graphics.Color.White,
        navigationIconContentColor = androidx.compose.ui.graphics.Color.White,
    )

/** Shell shared by the create, edit and login screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormPage(
    title: String,
    error: String?,
    requestId: String?,
    onBack: () -> Unit,
    apiUnavailable: Boolean = false,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            // Only the back arrow: the heading below already carries the title, and repeating it in the bar would show
            // it twice.
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = BrandBarColors,
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier.widthIn(
                    max = 480.dp,
                ).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp).semantics { heading() },
                )
                ErrorBanner(error, requestId, apiUnavailable = apiUnavailable)
                content()
            }
        }
    }
}

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.testTag("loading"))
    }
}
