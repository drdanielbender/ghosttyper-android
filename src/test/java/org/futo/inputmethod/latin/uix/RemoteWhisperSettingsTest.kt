package org.futo.inputmethod.latin.uix

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.futo.inputmethod.latin.uix.settings.DataStoreCacheProvider
import org.futo.inputmethod.latin.uix.settings.pages.RemoteWhisperConfigPage
import org.futo.inputmethod.latin.uix.settings.pages.RemoteWhisperMenu
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class RemoteWhisperSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun coldSettingsLaunchPreservesSavedFieldsAndEditsSurviveReopening() {
        val context: Context = RuntimeEnvironment.getApplication()
        val savedUrl = "http://127.0.0.1:1/v1"
        val savedKey = "test-api-key"
        val savedModel = "openai/gpt-4o-transcribe"
        runBlocking {
            context.dataStore.edit {
                it[USE_REMOTE_WHISPER.key] = true
                it[REMOTE_WHISPER_URL.key] = savedUrl
                it[REMOTE_WHISPER_API_KEY.key] = savedKey
                it[REMOTE_WHISPER_MODEL.key] = savedModel
            }
        }
        // An APK update restarts the process. Opening settings before the keyboard
        // starts leaves DataStoreHelper empty, while the saved preferences exist.
        assertEquals("", DataStoreHelper.getSetting(REMOTE_WHISPER_URL))
        val screenGeneration = mutableIntStateOf(0)
        compose.setContent {
            MaterialTheme {
                DataStoreCacheProvider {
                    key(screenGeneration.intValue) {
                        Column {
                            RemoteWhisperMenu.settings[0].component()
                            RemoteWhisperConfigPage()
                        }
                    }
                }
            }
        }
        val fields = compose.onAllNodes(hasSetTextAction())
        compose.onNode(isToggleable()).assertIsOn()
        fields[0].assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(savedUrl)))
        compose.runOnIdle {
            val prefs = runBlocking { context.dataStore.data.first() }
            assertEquals(savedUrl, prefs[REMOTE_WHISPER_URL.key])
            assertEquals(savedKey, prefs[REMOTE_WHISPER_API_KEY.key])
            assertEquals(savedModel, prefs[REMOTE_WHISPER_MODEL.key])
            assertEquals(true, prefs[USE_REMOTE_WHISPER.key])
        }

        val editedUrl = "http://127.0.0.1:2/v1"
        val editedKey = "replacement-api-key"
        fields[0].performTextReplacement(editedUrl)
        fields[1].performTextReplacement(editedKey)
        compose.waitUntil {
            val prefs = runBlocking { context.dataStore.data.first() }
            prefs[REMOTE_WHISPER_URL.key] == editedUrl &&
                prefs[REMOTE_WHISPER_API_KEY.key] == editedKey
        }
        compose.runOnIdle { screenGeneration.intValue++ }
        compose.onNode(isToggleable()).assertIsOn()
        fields[0].assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(editedUrl)))
        compose.runOnIdle {
            val prefs = runBlocking { context.dataStore.data.first() }
            assertEquals(editedKey, prefs[REMOTE_WHISPER_API_KEY.key])
            assertEquals(savedModel, prefs[REMOTE_WHISPER_MODEL.key])
        }
    }
}
