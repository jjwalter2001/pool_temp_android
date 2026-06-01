package com.jjwalter.pooltemp.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.jjwalter.pooltemp.data.Settings
import kotlinx.coroutines.launch

/**
 * First-launch configuration screen. Captures everything the API client needs
 * (base URL, Cloudflare Access service-token client id + secret, optional
 * Flask API_TOKEN, the user's name) and persists via [Settings.save].
 *
 * Also rendered as the editable form when invoked from Settings (Phase 6).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    settings: Settings,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var baseUrl by remember { mutableStateOf("") }
    var cfClientId by remember { mutableStateOf("") }
    var cfClientSecret by remember { mutableStateOf("") }
    var apiToken by remember { mutableStateOf("") }
    var userName by remember { mutableStateOf("") }

    // Pre-fill when editing an existing config (Settings -> "Edit").
    LaunchedEffect(Unit) {
        val cfg = settings.current()
        if (cfg.baseUrl.isNotBlank()) baseUrl = cfg.baseUrl
        if (cfg.cfClientId.isNotBlank()) cfClientId = cfg.cfClientId
        if (cfg.cfClientSecret.isNotBlank()) cfClientSecret = cfg.cfClientSecret
        if (cfg.apiToken.isNotBlank()) apiToken = cfg.apiToken
        if (cfg.userName.isNotBlank()) userName = cfg.userName
    }

    val canSave =
        baseUrl.isNotBlank() &&
        cfClientId.isNotBlank() &&
        cfClientSecret.isNotBlank() &&
        userName.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect Pool Temp") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Tell the app how to reach your pool_temp instance. " +
                    "Cloudflare Access service-token credentials come from the Zero Trust dashboard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text("https://pool.example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = cfClientId,
                onValueChange = { cfClientId = it },
                label = { Text("CF Access Client ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = cfClientSecret,
                onValueChange = { cfClientSecret = it },
                label = { Text("CF Access Client Secret") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = apiToken,
                onValueChange = { apiToken = it },
                label = { Text("Flask API Token (optional)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = userName,
                onValueChange = { userName = it },
                label = { Text("Your name (for heater event log)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        settings.save(
                            Settings.Config(
                                baseUrl = baseUrl,
                                cfClientId = cfClientId,
                                cfClientSecret = cfClientSecret,
                                apiToken = apiToken,
                                userName = userName,
                            )
                        )
                        onDone()
                    }
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save & continue")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
