package com.harimu.app

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import com.harimu.app.core.ai.AiException
import com.harimu.app.core.cloud.CloudConfig
import com.harimu.app.core.cloud.CloudConnectionState
import com.harimu.app.core.cloud.CloudException
import com.harimu.app.core.cloud.CloudProvider
import com.harimu.app.core.cloud.CloudUploader
import com.harimu.app.core.data.AppSettings
import com.harimu.app.core.data.VaultKeyEntity
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.security.SecretBox
import com.harimu.app.core.toolapi.ToolContext
import com.harimu.app.core.toolapi.ToolInput
import com.harimu.app.core.toolapi.ToolRegistry
import com.harimu.app.feature.settings.CloudConnectionsCard
import com.harimu.app.feature.settings.SettingsScreen
import com.harimu.app.feature.studios.BackupScreen
import com.harimu.app.feature.studios.KeyVaultScreen
import com.harimu.app.feature.studios.ScreenRecorderScreen
import com.harimu.app.feature.studios.VoiceRecorderScreen
import com.harimu.app.feature.toolscreen.AiToolGatewayCard
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HariMuApp::class)
class B12SettingsCloudAiSecurityTest {

    @Before
    fun installTestKeyProvider() {
        val testKey = SecretKeySpec(ByteArray(32) { (it + 7).toByte() }, "AES")
        SecretBox.keyProviderForTest = { testKey }
    }

    @After
    fun clearTestKeyProvider() {
        SecretBox.keyProviderForTest = null
    }

    @Test
    fun settingsRepository_persistsPreferencesAndImportsGatewayWithoutConsent() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as HariMuApp
        val repo = app.container.settings

        repo.setThemeMode(ThemeMode.AMOLED)
        repo.setDynamicColor(false)
        repo.setEnglish(true)
        repo.setOutputQuality(10) // clamped to 30..100
        repo.setAutoCleanTemp(false)
        repo.setAiGatewayUrl(" https://gw.example.org/ ")
        repo.setAiUploadConsent(false)

        val saved = repo.current()
        assertEquals(ThemeMode.AMOLED.name, saved.themeMode)
        assertFalse(saved.dynamicColor)
        assertTrue(saved.isEnglish)
        assertEquals(30, saved.outputQuality)
        assertFalse(saved.autoCleanTemp)
        assertEquals("https://gw.example.org", saved.aiGatewayUrl)
        assertFalse(saved.aiUploadConsent)

        repo.applyImported(
            AppSettings(
                themeMode = ThemeMode.LIGHT.name,
                dynamicColor = true,
                isEnglish = false,
                outputQuality = 150,
                autoCleanTemp = true,
                aiGatewayUrl = " https://restored.worker.dev/ ",
                aiUploadConsent = true // consent must stay device-local!
            )
        )
        val afterImport = repo.current()
        assertEquals(ThemeMode.LIGHT.name, afterImport.themeMode)
        assertEquals(100, afterImport.outputQuality)
        assertEquals("https://restored.worker.dev", afterImport.aiGatewayUrl)
        assertFalse("aiUploadConsent must remain device-local on import", afterImport.aiUploadConsent)
    }

    @Test
    fun cloudModels_connectionStateValidationAndRedactionAreHonest() {
        val unconfigured = CloudConfig(provider = CloudProvider.S3_R2)
        assertEquals(CloudConnectionState.NOT_CONFIGURED, unconfigured.connectionState())

        val expiredDrive = CloudConfig(
            provider = CloudProvider.GOOGLE_DRIVE,
            driveClientId = "cid-1",
            driveAccessToken = "old-access",
            driveRefreshToken = "refresh-1",
            driveTokenExpiresAt = 1_000L
        )
        assertEquals(CloudConnectionState.TOKEN_EXPIRED, expiredDrive.connectionState(nowMs = 5_000L))
        assertFalse(expiredDrive.redacted().contains("old-access"))
        assertFalse(expiredDrive.redacted().contains("refresh-1"))
        assertTrue(expiredDrive.redacted().contains("TOKEN_EXPIRED"))

        val validRest = CloudConfig(
            provider = CloudProvider.CUSTOM_REST,
            customUrl = "http://upload.local/hook",
            customHeadersJson = """{"Authorization":"Bearer secret-token"}"""
        )
        assertEquals(CloudConnectionState.SAVED_UNVERIFIED, validRest.connectionState())
        assertNull(validRest.validationError(isEnglish = true))
        assertTrue(validRest.isInsecureHttp())
        assertFalse(validRest.redacted().contains("secret-token"))

        val badRest = validRest.copy(customUrl = "ftp://bad.host", customHeadersJson = "not-json")
        assertNotNull(badRest.validationError(isEnglish = true))
        val badHeadersRest = validRest.copy(customHeadersJson = """{"X-Num": 42}""")
        assertNotNull(badHeadersRest.validationError(isEnglish = true))
    }

    @Test
    fun cloudUploader_refreshesExpiredDriveTokenAndFailsHonestlyOn401() = runBlocking {
        var capturedAuthHeader: String? = null
        var probeStatus = 200
        val interceptor = Interceptor { chain ->
            val req = chain.request()
            val url = req.url.toString()
            when {
                url.contains("oauth.local/token") -> Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("""{"access_token":"fresh-access-123","expires_in":3600}""".toResponseBody("application/json".toMediaType()))
                    .build()
                url.contains("/drive/v3/about") -> {
                    capturedAuthHeader = req.header("Authorization")
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(probeStatus)
                        .message(if (probeStatus == 200) "OK" else "Unauthorized")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                url.contains("rest.local/upload") -> {
                    capturedAuthHeader = req.header("Authorization")
                    Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(401)
                        .message("Unauthorized")
                        .body("{}".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                else -> error("Unexpected URL $url")
            }
        }
        val uploader = CloudUploader(
            client = OkHttpClient.Builder().addInterceptor(interceptor).build(),
            driveApiBase = "https://drive.local",
            tokenEndpointProvider = { "https://oauth.local/token" }
        )

        var persisted: CloudConfig? = null
        val expiredConfig = CloudConfig(
            provider = CloudProvider.GOOGLE_DRIVE,
            enabled = true,
            driveClientId = "cid",
            driveAccessToken = "stale-token",
            driveRefreshToken = "refresh-xyz",
            driveTokenExpiresAt = 100L
        )
        val outcome = uploader.testConnectionDetailed(expiredConfig) { persisted = it }
        assertEquals("Bearer fresh-access-123", capturedAuthHeader)
        assertEquals("fresh-access-123", outcome.updatedConfig.driveAccessToken)
        assertEquals("fresh-access-123", persisted?.driveAccessToken)

        val failingRest = CloudConfig(
            provider = CloudProvider.CUSTOM_REST,
            customUrl = "https://rest.local/upload",
            customHeadersJson = """{"Authorization":"Bearer custom-abc"}"""
        )
        val restErr = runCatching { uploader.testConnectionDetailed(failingRest) }.exceptionOrNull()
        assertTrue(restErr is CloudException)
        assertEquals("Bearer custom-abc", capturedAuthHeader)
    }

    @Test
    fun aiClient_andChatTool_enforceValidGatewayAndUploadConsent() = runBlocking {
        val app = RuntimeEnvironment.getApplication() as HariMuApp
        val settings = app.container.settings
        val aiClient = app.container.aiClient

        settings.setAiGatewayUrl("")
        val blankErr = runCatching { aiClient.ensureToken() }.exceptionOrNull() as? AiException
        assertNotNull(blankErr)
        assertEquals("ai_not_configured", blankErr!!.code)
        assertTrue(AiException("quota_exceeded", "quota").userMessage(isEnglish = true).contains("Daily AI quota"))

        settings.setAiGatewayUrl("http://insecure.example.com")
        val insecureErr = runCatching { aiClient.ensureToken() }.exceptionOrNull() as? AiException
        assertEquals("ai_not_configured", insecureErr?.code)

        settings.setAiGatewayUrl("https://worker.example.dev")
        settings.setAiUploadConsent(false)
        val chatTool = ToolRegistry.allTools.first { it.id == "CORE-10" }
        val ctx = ToolContext(appContext = app, storage = app.container.storage, isEnglish = true, ai = aiClient)
        val consentErr = runCatching { chatTool.run(ctx, ToolInput(textInput = "Hello")).toList() }
            .exceptionOrNull() as? AiException
        assertNotNull("Expected CORE-10 to fail with AiException without upload consent", consentErr)
        assertEquals("upload_consent_required", consentErr!!.code)
        assertTrue(consentErr.message.contains("confirmation", ignoreCase = true))
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun settingsScreen_validatesAiGatewayUrlAndExposesSecurityButtons() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            runBlocking {
                container.settings.setThemeMode(ThemeMode.AMOLED)
                container.settings.setAiGatewayUrl("https://valid.worker.dev")
                container.settings.setAiUploadConsent(false)
            }
            val currentSettings = runBlocking { container.settings.current() }
            setTestContent(activity, isEnglish = true, themeMode = ThemeMode.AMOLED) {
                SettingsScreen(
                    container = container,
                    settings = currentSettings,
                    onOpenStudio = {}
                )
            }

            val urlField = scrollUntilTag(activity, "ai_gateway_url_input")
            assertNotNull("Expected ai_gateway_url_input", urlField)
            urlField!!.config.getOrNull(SemanticsActions.SetText)?.action?.invoke(
                AnnotatedString("http://not-https.example.com")
            )
            flushMainUi(activity, frames = 5)

            val nodesAfterEdit = collectSemanticsNodes(activity.window.decorView)
            assertTrue(
                "Expected invalid AI Gateway URL message",
                collectAllText(nodesAfterEdit).any { it.contains("https://", ignoreCase = true) }
            )
            val testBtn = findByTag(nodesAfterEdit, "ai_gateway_test_button")
            assertNotNull(testBtn)
            assertNotNull("Expected test button disabled on invalid URL", testBtn!!.config.getOrNull(SemanticsProperties.Disabled))

            val keyVaultBtn = scrollUntilTag(activity, "settings_key_vault_button")
            assertNotNull("Expected settings_key_vault_button", keyVaultBtn)
            val backupBtn = scrollUntilTag(activity, "settings_backup_button")
            assertNotNull("Expected settings_backup_button", backupBtn)
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun cloudConnectionsCard_masksSecretsAndDriveDisconnectPersists() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            runBlocking {
                container.cloudStore.save(
                    CloudConfig(
                        provider = CloudProvider.GOOGLE_DRIVE,
                        enabled = true,
                        driveClientId = "cid-drive",
                        driveAccessToken = "secret-access",
                        driveRefreshToken = "secret-refresh",
                        driveTokenExpiresAt = System.currentTimeMillis() + 3_600_000L
                    )
                )
            }

            setTestContent(activity, isEnglish = true) {
                CloudConnectionsCard(container = container, isEnglish = true)
            }
            waitUntilNodes(activity) { findByTag(it, "cloud_drive_disconnect_button") != null }

            // Disconnect Drive persists cleared tokens
            clickNode(findByTag(collectSemanticsNodes(activity.window.decorView), "cloud_drive_disconnect_button")!!)
            Thread.sleep(60)
            flushMainUi(activity, frames = 5)
            val afterDisconnect = runBlocking { container.cloudStore.load() }
            assertEquals("", afterDisconnect.driveAccessToken)
            assertEquals("", afterDisconnect.driveRefreshToken)
            assertFalse(afterDisconnect.enabled)

            // S3 Secret Key is masked by default and revealed on toggle
            clickNode(findByTag(collectSemanticsNodes(activity.window.decorView), "cloud_provider_chip_S3_R2")!!)
            flushMainUi(activity, frames = 4)
            val s3SecretNode = findByTag(collectSemanticsNodes(activity.window.decorView), "cloud_s3_secret_input")
            assertNotNull(s3SecretNode)
            assertNotNull(
                "S3 secret input should have Password semantics by default",
                s3SecretNode!!.config.getOrNull(SemanticsProperties.Password)
            )
            clickNode(findByTag(collectSemanticsNodes(activity.window.decorView), "cloud_secret_visibility_toggle")!!)
            flushMainUi(activity, frames = 4)
            val s3ToggleAfter = findByTag(collectSemanticsNodes(activity.window.decorView, mergingEnabled = true), "cloud_secret_visibility_toggle")
            assertTrue(
                "S3 secret toggle should switch to Hide secret",
                s3ToggleAfter!!.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it == "Hide secret" } == true
            )

            // Custom REST headers are masked by default and revealed on toggle
            clickNode(findByTag(collectSemanticsNodes(activity.window.decorView), "cloud_provider_chip_CUSTOM_REST")!!)
            flushMainUi(activity, frames = 4)
            val restHeadersNode = findByTag(collectSemanticsNodes(activity.window.decorView), "cloud_rest_headers_input")
            assertNotNull(restHeadersNode)
            assertNotNull(
                "REST headers input should have Password semantics by default",
                restHeadersNode!!.config.getOrNull(SemanticsProperties.Password)
            )
            clickNode(findByTag(collectSemanticsNodes(activity.window.decorView), "cloud_rest_headers_visibility_toggle")!!)
            flushMainUi(activity, frames = 4)
            val restToggleAfter = findByTag(collectSemanticsNodes(activity.window.decorView, mergingEnabled = true), "cloud_rest_headers_visibility_toggle")
            assertTrue(
                "REST headers toggle should switch to Hide secret",
                restToggleAfter!!.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it == "Hide secret" } == true
            )
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun backupScreen_masksPasswordAndUpdatesEncryptionNoticeHonestly() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            setTestContent(activity, isEnglish = true) {
                BackupScreen(container = container, isEnglish = true, onBack = {})
            }

            val nodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull("Expected scope exclusion note", findByTag(nodes, "backup_scope_exclusion_note"))
            val passwordField = findByTag(nodes, "backup_password_input")
            assertNotNull(passwordField)
            assertNotNull("Expected Password semantics on backup_password_input", passwordField!!.config.getOrNull(SemanticsProperties.Password))

            // Blank password -> unencrypted warning
            assertTrue(
                collectAllText(nodes).any { it.contains("plain unencrypted ZIP", ignoreCase = true) }
            )

            // Short password -> weak warning
            passwordField.config.getOrNull(SemanticsActions.SetText)?.action?.invoke(AnnotatedString("12345"))
            flushMainUi(activity, frames = 4)
            assertTrue(
                collectAllText(collectSemanticsNodes(activity.window.decorView))
                    .any { it.contains("8+ characters", ignoreCase = true) }
            )

            // Strong password + toggle visibility
            findByTag(collectSemanticsNodes(activity.window.decorView), "backup_password_input")!!
                .config.getOrNull(SemanticsActions.SetText)?.action?.invoke(AnnotatedString("strong-pass-123"))
            flushMainUi(activity, frames = 4)
            val toggleBefore = findByTag(collectSemanticsNodes(activity.window.decorView, mergingEnabled = true), "backup_password_visibility_toggle")
            assertTrue(
                "Password toggle should start as Show password",
                toggleBefore!!.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it == "Show password" } == true
            )
            clickNode(toggleBefore)
            flushMainUi(activity, frames = 6)

            val updatedNodes = collectSemanticsNodes(activity.window.decorView, mergingEnabled = true)
            assertTrue(
                collectAllText(updatedNodes).any { it.contains("AES-256-GCM", ignoreCase = true) }
            )
            val toggleAfter = findByTag(updatedNodes, "backup_password_visibility_toggle")
            assertTrue(
                "Password toggle should switch to Hide password",
                toggleAfter!!.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it == "Hide password" } == true
            )
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun keyVaultScreen_requiresConfirmationBeforeExportOrDelete() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            runBlocking {
                container.workspace.saveVaultKey(
                    VaultKeyEntity(
                        alias = "vault-test-ec",
                        algorithm = "EC P-256",
                        keySizeBits = 256,
                        publicKeyPem = "-----BEGIN PUBLIC KEY-----\nTEST\n-----END PUBLIC KEY-----",
                        privateKeyPem = "sealed",
                        fingerprintSha256 = "abcd1234abcd1234abcd1234abcd1234abcd1234",
                        keystoreBacked = false
                    )
                )
            }

            setTestContent(activity, isEnglish = true) {
                KeyVaultScreen(container = container, isEnglish = true, onBack = {})
            }
            val nodesWithKey = waitUntilNodes(activity) { current ->
                collectAllText(current).any { it == "vault-test-ec" }
            }
            assertTrue(collectAllText(nodesWithKey).any { it.contains("Keystore master key", ignoreCase = true) })

            // Export PEM opens warning dialog instead of copying immediately
            val mergedNodesWithKey = collectSemanticsNodes(activity.window.decorView, mergingEnabled = true)
            val exportBtn = mergedNodesWithKey.first { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Export PEM" } == true &&
                    node.config.getOrNull(SemanticsActions.OnClick) != null
            }
            clickNode(exportBtn)
            flushMainUi(activity, frames = 6)
            val exportDialogNodes = collectAllWindowSemanticsNodes(activity, mergingEnabled = true)
            assertTrue(
                "Expected Export PEM confirmation dialog",
                collectAllText(exportDialogNodes).any { it.contains("Export Private PEM Key?", ignoreCase = true) }
            )
            val cancelExport = exportDialogNodes.last { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Cancel" } == true &&
                    node.config.getOrNull(SemanticsActions.OnClick) != null
            }
            clickNode(cancelExport)
            flushMainUi(activity, frames = 6)

            // Delete opens confirmation dialog first without deleting immediately
            val deleteCd = activity.getString(R.string.key_vault_delete_cd)
            val deleteBtn = collectSemanticsNodes(activity.window.decorView, mergingEnabled = true).first { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it == deleteCd } == true &&
                    node.config.getOrNull(SemanticsActions.OnClick) != null
            }
            clickNode(deleteBtn)
            flushMainUi(activity, frames = 6)
            val deleteDialogNodes = collectAllWindowSemanticsNodes(activity, mergingEnabled = true)
            assertTrue(
                "Expected delete confirmation dialog",
                collectAllText(deleteDialogNodes).any { it.contains("vault-test-ec") && it.contains("Delete", ignoreCase = true) }
            )

            val confirmDeleteBtn = deleteDialogNodes.last { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Delete" } == true &&
                    node.config.getOrNull(SemanticsActions.OnClick) != null
            }
            clickNode(confirmDeleteBtn)
            Thread.sleep(150)
            flushMainUi(activity, frames = 6)
            val remaining = collectAllText(collectSemanticsNodes(activity.window.decorView))
            assertFalse("Key should be deleted after confirmation", remaining.contains("vault-test-ec"))
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun aiToolGatewayCard_andRecorders_exposeNetworkPrivacyAndMicRecovery() {
        withMainActivity { activity ->
            val container = (activity.application as HariMuApp).container
            val chatTool = ToolRegistry.allTools.first { it.id == "CORE-10" }

            setTestContent(activity, isEnglish = true) {
                AiToolGatewayCard(
                    tool = chatTool,
                    gatewayUrl = "",
                    hasUploadConsent = false,
                    onOpenSettings = {}
                )
            }
            val aiNodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(aiNodes, "ai_gateway_tool_info_card"))
            assertNotNull(findByTag(aiNodes, "ai_tool_open_settings_button"))
            assertTrue(collectAllText(aiNodes).any { it.contains("quota", ignoreCase = true) })

            Shadows.shadowOf(activity.application).denyPermissions(Manifest.permission.RECORD_AUDIO)
            setTestContent(activity, isEnglish = true) {
                ScreenRecorderScreen(container = container, isEnglish = true, onBack = {})
            }
            val srNodes = collectSemanticsNodes(activity.window.decorView)
            assertNotNull(findByTag(srNodes, "screen_recorder_mic_rationale_card"))
            assertNotNull(findByTag(srNodes, "screen_recorder_grant_mic_button"))

            setTestContent(activity, isEnglish = true) {
                VoiceRecorderScreen(container = container, isEnglish = true, onBack = {})
            }
            val vrBtn = scrollUntilTag(activity, "voice_grant_mic_button")
            assertNotNull(vrBtn)
            assertEquals(PackageManager.PERMISSION_DENIED, activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        }
    }

    private fun collectAllText(nodes: List<SemanticsNode>): List<String> =
        nodes.flatMap { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
                node.config.getOrNull(SemanticsProperties.EditableText)?.text?.let(::listOf).orEmpty()
        }

    private fun withMainActivity(block: (MainActivity) -> Unit) {
        val controller: ActivityController<MainActivity> = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            flushMainUi(activity)
            block(activity)
        } finally {
            runCatching { controller.pause().stop().destroy() }
            flushMainUi()
        }
    }

    private fun setTestContent(
        activity: MainActivity,
        isEnglish: Boolean,
        themeMode: ThemeMode = ThemeMode.SYSTEM,
        fontScale: Float = 1.0f,
        content: @Composable () -> Unit
    ) {
        activity.setContent {
            val baseContext = LocalContext.current
            val baseConfig = LocalConfiguration.current
            val baseDensity = LocalDensity.current
            val targetLocale = if (isEnglish) Locale.ENGLISH else Locale.forLanguageTag("id")
            val localizedConfig = remember(baseConfig, targetLocale, fontScale) {
                Configuration(baseConfig).apply {
                    setLocale(targetLocale)
                    this.fontScale = fontScale
                }
            }
            val localizedResources = remember(baseContext, localizedConfig) {
                baseContext.createConfigurationContext(localizedConfig).resources
            }
            val localizedContext = remember(baseContext, localizedResources) {
                object : ContextWrapper(baseContext) {
                    override fun getResources(): Resources = localizedResources
                }
            }
            CompositionLocalProvider(
                LocalConfiguration provides localizedConfig,
                LocalContext provides localizedContext,
                LocalResources provides localizedResources,
                LocalDensity provides Density(baseDensity.density, fontScale),
                LocalActivityResultRegistryOwner provides activity,
                LocalOnBackPressedDispatcherOwner provides activity
            ) {
                HariMuTheme(
                    themeMode = themeMode,
                    isSystemDark = themeMode == ThemeMode.DARK || themeMode == ThemeMode.AMOLED,
                    dynamicColor = false,
                    content = content
                )
            }
        }
        flushMainUi(activity)
    }

    private fun flushMainUi(activity: MainActivity? = null, frames: Int = 4) {
        fun forceLayoutTree(view: View) {
            view.forceLayout()
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    forceLayoutTree(view.getChildAt(i))
                }
            }
        }
        repeat(frames) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            activity?.let { act ->
                val dm = act.resources.displayMetrics
                act.window?.decorView?.let { decor ->
                    forceLayoutTree(decor)
                    decor.measure(
                        View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY)
                    )
                    decor.layout(0, 0, dm.widthPixels, dm.heightPixels)
                    decor.viewTreeObserver.dispatchOnPreDraw()
                }
                org.robolectric.shadows.ShadowDialog.getShownDialogs().forEach { dialog ->
                    dialog.window?.decorView?.let { dialogDecor ->
                        forceLayoutTree(dialogDecor)
                        dialogDecor.measure(
                            View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY)
                        )
                        dialogDecor.layout(0, 0, dm.widthPixels, dm.heightPixels)
                        dialogDecor.viewTreeObserver.dispatchOnPreDraw()
                    }
                }
            }
        }
    }

    private fun collectAllWindowSemanticsNodes(
        activity: MainActivity,
        mergingEnabled: Boolean = false
    ): List<SemanticsNode> {
        val dm = activity.resources.displayMetrics
        val globalViews = runCatching {
            android.view.inspector.WindowInspector.getGlobalWindowViews()
        }.getOrNull().orEmpty()
        val shownDialogs = org.robolectric.shadows.ShadowDialog.getShownDialogs() +
            listOfNotNull(org.robolectric.shadows.ShadowDialog.getLatestDialog())
        val dialogViews = shownDialogs.mapNotNull { it.window?.decorView }
        val windowRoots = (dialogViews + globalViews + listOfNotNull(activity.window?.decorView)).distinct()
        windowRoots.forEach { decor ->
            decor.measure(
                View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY)
            )
            decor.layout(0, 0, dm.widthPixels, dm.heightPixels)
            decor.viewTreeObserver.dispatchOnPreDraw()
        }
        return windowRoots.flatMap { view -> collectSemanticsNodes(view, mergingEnabled) }
    }

    private fun waitUntilNodes(
        activity: MainActivity,
        predicate: (List<SemanticsNode>) -> Boolean
    ): List<SemanticsNode> {
        repeat(50) {
            flushMainUi(activity, frames = 2)
            val nodes = collectSemanticsNodes(activity.window.decorView)
            if (predicate(nodes)) return nodes
            Thread.sleep(20)
        }
        return collectSemanticsNodes(activity.window.decorView)
    }

    private fun scrollUntilTag(activity: MainActivity, tag: String): SemanticsNode? {
        val nodes = scrollUntilPredicate(activity) { current ->
            val node = findByTag(current, tag) ?: return@scrollUntilPredicate false
            node.size.height > 0 && (node.boundsInRoot.bottom - node.boundsInRoot.top) >= node.size.height - 1f
        }
        return findByTag(nodes, tag)
    }

    private fun scrollUntilPredicate(
        activity: MainActivity,
        predicate: (List<SemanticsNode>) -> Boolean
    ): List<SemanticsNode> {
        repeat(25) {
            flushMainUi(activity, frames = 2)
            val nodes = collectSemanticsNodes(activity.window.decorView)
            if (predicate(nodes)) return nodes
            val scrollable = nodes.lastOrNull { node ->
                node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null &&
                    node.config.getOrNull(SemanticsActions.ScrollBy) != null
            } ?: nodes.lastOrNull { it.config.getOrNull(SemanticsActions.ScrollBy) != null }
                ?: return nodes
            scrollable.config.getOrNull(SemanticsActions.ScrollBy)?.action?.invoke(0f, 520f)
            flushMainUi(activity, frames = 2)
        }
        return collectSemanticsNodes(activity.window.decorView)
    }

    private fun collectSemanticsNodes(rootView: View, mergingEnabled: Boolean = false): List<SemanticsNode> {
        val roots = mutableListOf<RootForTest>()
        fun walk(view: View) {
            if (view is RootForTest) roots += view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    walk(view.getChildAt(i))
                }
            }
        }
        walk(rootView)
        return roots.flatMap { root ->
            root.semanticsOwner.getAllSemanticsNodes(mergingEnabled = mergingEnabled)
        }
    }

    private fun findByTag(nodes: List<SemanticsNode>, tag: String): SemanticsNode? =
        nodes.firstOrNull { node ->
            node.config.getOrNull(SemanticsProperties.TestTag) == tag
        }

    private fun clickNode(node: SemanticsNode) {
        val action = node.config.getOrNull(SemanticsActions.OnClick)?.action
            ?: error("Semantics node ${node.config.getOrNull(SemanticsProperties.TestTag)} has no OnClick action")
        action.invoke()
    }
}
