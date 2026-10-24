package org.futo.inputmethod.latin.uix.settings.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.REMOTE_WHISPER_MODEL
import org.futo.inputmethod.latin.uix.REMOTE_WHISPER_URL
import org.futo.inputmethod.latin.uix.REMOTE_WHISPER_API_KEY
import org.futo.inputmethod.latin.uix.USE_REMOTE_WHISPER
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.userSettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.voiceinput.shared.whisper.RemoteWhisperClient

@Composable
fun RemoteWhisperConfigPage() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val urlSetting = useDataStore(REMOTE_WHISPER_URL.key, REMOTE_WHISPER_URL.default)
    val modelSetting = useDataStore(REMOTE_WHISPER_MODEL.key, REMOTE_WHISPER_MODEL.default)
    val apiKeySetting = useDataStore(REMOTE_WHISPER_API_KEY.key, REMOTE_WHISPER_API_KEY.default)

    val urlState = remember { mutableStateOf(urlSetting.value) }
    val modelState = remember { mutableStateOf(modelSetting.value) }
    val apiKeyState = remember { mutableStateOf(apiKeySetting.value) }

    val modelsList = remember { mutableStateOf(emptyList<String>()) }
    val isLoading = remember { mutableStateOf(false) }
    val connectionStatus = remember { mutableStateOf("") }
    val dropdownExpanded = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val serverUrl = urlState.value.trim()
        if (serverUrl.isEmpty()) return@LaunchedEffect

        isLoading.value = true
        connectionStatus.value = ""
        try {
            val models = withContext(Dispatchers.IO) {
                RemoteWhisperClient.fetchModels(serverUrl, apiKeyState.value)
            }
            modelsList.value = models
            if (models.isNotEmpty() && !models.contains(modelState.value)) {
                modelState.value = models.first()
                modelSetting.setValue(modelState.value)
            }
            connectionStatus.value = "Connected! Found ${models.size} models."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            modelsList.value = emptyList()
            val msg = e.localizedMessage ?: e.message ?: "Unknown error"
            connectionStatus.value = "Failed to fetch models: $msg"
        } finally {
            isLoading.value = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Server URL Setting Item
        SettingItem(
            title = stringResource(R.string.voice_input_settings_remote_whisper_url),
            subcontent = {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    TextField(
                        value = urlState.value,
                        onValueChange = {
                            urlState.value = it
                            urlSetting.setValue(it)
                        },
                        placeholder = { Text("http://server:port") },
                        supportingText = { Text(stringResource(R.string.voice_input_settings_remote_whisper_url_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    TextField(
                        value = apiKeyState.value,
                        onValueChange = {
                            apiKeyState.value = it
                            apiKeySetting.setValue(it)
                        },
                        label = { Text(stringResource(R.string.voice_input_settings_remote_whisper_api_key)) },
                        supportingText = { Text(stringResource(R.string.voice_input_settings_remote_whisper_api_key_hint)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (connectionStatus.value.isNotEmpty()) {
                        Text(
                            text = connectionStatus.value,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (connectionStatus.value.startsWith("Connected")) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Button(
                        onClick = {
                            isLoading.value = true
                            connectionStatus.value = ""
                            coroutineScope.launch {
                                try {
                                    val models = withContext(Dispatchers.IO) {
                                        RemoteWhisperClient.fetchModels(urlState.value, apiKeyState.value)
                                    }
                                    modelsList.value = models
                                    if (models.isNotEmpty() && !models.contains(modelState.value)) {
                                        modelState.value = models.first()
                                        modelSetting.setValue(modelState.value)
                                    }
                                    connectionStatus.value = "Connected! Found ${models.size} models."
                                    Toast.makeText(context, "Connection Successful!", Toast.LENGTH_SHORT).show()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    modelsList.value = emptyList()
                                    val msg = e.localizedMessage ?: e.message ?: "Unknown error"
                                    connectionStatus.value = "Failed: $msg"
                                    Toast.makeText(context, "Connection Failed", Toast.LENGTH_LONG).show()
                                } finally {
                                    isLoading.value = false
                                }
                            }
                        },
                        enabled = !isLoading.value,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading.value) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing...")
                        } else {
                            Text("Test Connection & Fetch Models")
                        }
                    }
                }
            }
        ) { }

        Spacer(modifier = Modifier.height(8.dp))

        // Model ID Setting Item
        SettingItem(
            title = stringResource(R.string.voice_input_settings_remote_whisper_model),
            subcontent = {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    OutlinedButton(
                        onClick = { dropdownExpanded.value = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = modelsList.value.isNotEmpty()
                    ) {
                        Text(
                            text = if (modelsList.value.isNotEmpty()) {
                                modelState.value.ifEmpty { "Select Model..." }
                            } else {
                                "No models available (check server URL)"
                            }
                        )
                    }
                    if (modelsList.value.isNotEmpty()) {
                        DropdownMenu(
                            expanded = dropdownExpanded.value,
                            onDismissRequest = { dropdownExpanded.value = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            modelsList.value.forEach { modelName ->
                                DropdownMenuItem(
                                    text = { Text(modelName) },
                                    onClick = {
                                        modelState.value = modelName
                                        modelSetting.setValue(modelName)
                                        dropdownExpanded.value = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        ) { }
    }
}

val RemoteWhisperMenu = UserSettingsMenu(
    title = R.string.voice_input_settings_remote_whisper_title,
    navPath = "remoteWhisper", registerNavPath = true,
    settings = listOf(
        userSettingToggleDataStore(
            title = R.string.voice_input_settings_use_remote_whisper,
            subtitle = R.string.voice_input_settings_use_remote_whisper_subtitle,
            setting = USE_REMOTE_WHISPER
        ),
        UserSetting(
            name = 0,
            component = {
                RemoteWhisperConfigPage()
            }
        )
    )
)
