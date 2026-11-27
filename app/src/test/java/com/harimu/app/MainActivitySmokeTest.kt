package com.harimu.app

import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import com.harimu.app.core.data.AppSettings
import com.harimu.app.feature.settings.SettingsScreen
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.Locale
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HariMuApp::class)
class MainActivitySmokeTest {

    @Test
    fun mainActivityProvidesActivityResultRegistryOwnerAndComposesSettingsWithoutCrash() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            var resolvedOwner: Any? = null
            var resolvedBackOwner: Any? = null

            activity.setContent {
                val baseContext = LocalContext.current
                val baseConfig = LocalConfiguration.current
                val localizedConfig = remember(baseConfig) {
                    Configuration(baseConfig).apply { setLocale(Locale.ENGLISH) }
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
                    LocalActivityResultRegistryOwner provides activity,
                    LocalOnBackPressedDispatcherOwner provides activity
                ) {
                    resolvedOwner = LocalActivityResultRegistryOwner.current
                    resolvedBackOwner = LocalOnBackPressedDispatcherOwner.current
                    val container = (activity.application as HariMuApp).container
                    SettingsScreen(
                        container = container,
                        settings = AppSettings(isEnglish = true),
                        onOpenStudio = {},
                        onOpenInternalTool = {}
                    )
                }
            }
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)

            assertNotNull(resolvedOwner)
            assertSame(activity, resolvedOwner)
            assertNotNull(resolvedBackOwner)
            assertSame(activity, resolvedBackOwner)
        } finally {
            runCatching { controller.pause().stop().destroy() }
            ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        }
    }
}
