package com.harimu.app

import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.harimu.app.core.design.AppTypography
import com.harimu.app.core.design.EmptyState
import com.harimu.app.core.design.ErrorState
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.HariMuElevatedCard
import com.harimu.app.core.design.HariMuInsetPanel
import com.harimu.app.core.design.HariMuPrimaryButton
import com.harimu.app.core.design.HariMuResponsiveContainer
import com.harimu.app.core.design.HariMuSearchBar
import com.harimu.app.core.design.HariMuSectionCard
import com.harimu.app.core.design.HariMuStatusBanner
import com.harimu.app.core.design.HariMuTheme
import com.harimu.app.core.design.HariMuThemeTokens
import com.harimu.app.core.design.HariMuTopBar
import com.harimu.app.core.design.ModelDownloadDialog
import com.harimu.app.core.design.ParamDropdown
import com.harimu.app.core.design.ParamSlider
import com.harimu.app.core.design.ParamToggle
import com.harimu.app.core.design.PlusJakartaSans
import com.harimu.app.core.design.ProgressSheet
import com.harimu.app.core.design.ResultCard
import com.harimu.app.core.design.StatusTone
import com.harimu.app.core.design.ThemeMode
import com.harimu.app.core.design.ToolCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.Locale
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = HariMuApp::class)
class DesignSystemComponentTest {

    @Test
    fun typographyAndDesignTokensAreCompleteAcrossThemeModes() {
        assertEquals(PlusJakartaSans, AppTypography.displayLarge.fontFamily)
        assertEquals(PlusJakartaSans, AppTypography.headlineLarge.fontFamily)
        assertEquals(PlusJakartaSans, AppTypography.labelLarge.fontFamily)

        withMainActivity { activity ->
            var lightPrimary = Color.Unspecified
            var lightContainerLow = Color.Unspecified
            var amoledBg = Color.Unspecified
            var darkSemanticSuccess = Color.Unspecified
            var minTouchTargetDp = 0f

            setDesignContent(activity, themeMode = ThemeMode.LIGHT) {
                lightPrimary = MaterialTheme.colorScheme.primary
                lightContainerLow = MaterialTheme.colorScheme.surfaceContainerLow
                minTouchTargetDp = HariMuThemeTokens.controls.minTouchTarget.value
                Box(Modifier.fillMaxSize())
            }
            assertEquals(HariMuColors.ElectricOrangeDark, lightPrimary)
            assertEquals(HariMuColors.LightSurfaceLow, lightContainerLow)
            assertEquals(48f, minTouchTargetDp, 0.01f)

            setDesignContent(activity, themeMode = ThemeMode.AMOLED) {
                amoledBg = MaterialTheme.colorScheme.background
                darkSemanticSuccess = HariMuThemeTokens.semantic.success
                Box(Modifier.fillMaxSize())
            }
            assertEquals(HariMuColors.PureBlack, amoledBg)
            assertTrue(darkSemanticSuccess != Color.Unspecified)
        }
    }

    @Test
    fun searchBarTopBarAndParameterControlsMeetTouchTargetsAndInvokeCallbacks() {
        withMainActivity { activity ->
            var cleared = false
            var backClicked = false
            var toggledTo: Boolean? = null
            var selectedOption = "PNG"
            var primaryClicked = false

            setDesignContent(activity, themeMode = ThemeMode.DARK) {
                Column(
                    modifier = Modifier.padding(HariMuThemeTokens.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(HariMuThemeTokens.spacing.sm)
                ) {
                    HariMuTopBar(
                        title = "Design Test",
                        onBack = { backClicked = true },
                        backTestTag = "ds_back_btn"
                    )
                    HariMuSearchBar(
                        query = "pdf",
                        onQueryChange = { if (it.isEmpty()) cleared = true },
                        inputTestTag = "ds_search_input",
                        clearTestTag = "ds_search_clear"
                    )
                    ParamDropdown(
                        label = "Format",
                        options = listOf("PNG", "WEBP"),
                        selectedOption = selectedOption,
                        onOptionSelected = { selectedOption = it }
                    )
                    ParamToggle(
                        label = "Strip EXIF",
                        checked = false,
                        onCheckedChange = { toggledTo = it },
                        modifier = Modifier.testTag("ds_toggle")
                    )
                    ParamSlider(
                        label = "Quality",
                        value = 80f,
                        min = 10f,
                        max = 100f,
                        unit = "%",
                        onValueChange = {}
                    )
                    HariMuPrimaryButton(
                        text = "Execute",
                        onClick = { primaryClicked = true },
                        modifier = Modifier.testTag("ds_primary_btn")
                    )
                }
            }

            val nodes = collectSemanticsNodes(activity.window.decorView)
            val density = activity.resources.displayMetrics.density

            val backNode = findByTag(nodes, "ds_back_btn")
            val clearNode = findByTag(nodes, "ds_search_clear")
            val toggleNode = findByTag(nodes, "ds_toggle")
            val primaryNode = findByTag(nodes, "ds_primary_btn")

            assertNotNull(backNode)
            assertNotNull(clearNode)
            assertNotNull(toggleNode)
            assertNotNull(primaryNode)

            assertTrue(
                "Back button touch height should be >= 48dp, was ${backNode!!.touchBoundsInRoot.height / density}",
                backNode.touchBoundsInRoot.height / density >= 47.9f
            )
            assertTrue(
                "Clear button touch height should be >= 48dp, was ${clearNode!!.touchBoundsInRoot.height / density}",
                clearNode.touchBoundsInRoot.height / density >= 47.9f
            )
            assertTrue(
                "Toggle touch height should be >= 48dp, was ${toggleNode!!.touchBoundsInRoot.height / density}",
                toggleNode.touchBoundsInRoot.height / density >= 47.9f
            )
            assertTrue(
                "Primary button touch height should be >= 48dp, was ${primaryNode!!.touchBoundsInRoot.height / density}",
                primaryNode.touchBoundsInRoot.height / density >= 47.9f
            )
            assertEquals(Role.Switch, toggleNode.config.getOrNull(SemanticsProperties.Role))

            clickNode(backNode)
            clickNode(clearNode)
            clickNode(toggleNode)
            clickNode(primaryNode)
            flushMainUi(activity)

            assertTrue(backClicked)
            assertTrue(cleared)
            assertEquals(true, toggledTo)
            assertTrue(primaryClicked)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h800dp")
    fun statusCardsDialogsAndLargeFontLayoutRenderWithoutCrashing() {
        withMainActivity { activity ->
            var retried = false
            var uploaded = false
            var downloadConfirmed = false

            setDesignContent(activity, themeMode = ThemeMode.LIGHT, fontScale = 1.5f) {
                HariMuResponsiveContainer(
                    maxWidth = HariMuThemeTokens.layout.formMaxWidth,
                    scrollable = true
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(HariMuThemeTokens.spacing.sm)) {
                        HariMuSectionCard {
                            HariMuInsetPanel {
                                Text("Inset Content")
                            }
                        }
                        HariMuElevatedCard {
                            Text("Elevated Content")
                        }
                        HariMuStatusBanner(
                            message = "Processing stays local on device.",
                            tone = StatusTone.INFO
                        )
                        ToolCard(
                            id = "VID-01",
                            title = "Trim Video",
                            description = "Fast MP4 trim",
                            engineLabel = "M3/NAT",
                            icon = Icons.Filled.ContentCut,
                            accent = HariMuColors.GroupVideo,
                            isFavorite = false,
                            onClick = {},
                            onToggleFavorite = {}
                        )
                        ProgressSheet(
                            percent = 45,
                            message = "Encoding...",
                            onCancel = {}
                        )
                        ResultCard(
                            summary = "Done (1.4 MB)",
                            onOpen = {},
                            onShare = {},
                            onSave = {},
                            onUploadToCloud = { uploaded = true }
                        )
                        EmptyState(
                            icon = Icons.Filled.History,
                            title = "Empty",
                            subtitle = "Nothing here yet"
                        )
                        ErrorState(
                            message = "Failed to decode",
                            onRetry = { retried = true }
                        )
                        ModelDownloadDialog(
                            modelName = "U2-Net",
                            modelSizeLabel = "42 MB",
                            onConfirmDownload = { downloadConfirmed = true },
                            onDismiss = {}
                        )
                    }
                }
            }

            val mergedNodes = collectSemanticsNodes(activity.window.decorView, mergingEnabled = true)
            assertNotNull(findByTag(mergedNodes, "tool_card_VID-01"))
            val uploadBtn = findByTag(mergedNodes, "upload_to_cloud_button")
            assertNotNull(uploadBtn)
            clickNode(uploadBtn!!)

            val retryBtn = mergedNodes.firstOrNull { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null &&
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Retry" } == true
            }
            assertNotNull("Expected Retry button in merged semantics", retryBtn)
            clickNode(retryBtn!!)

            val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertNotNull("Expected ModelDownloadDialog to be shown", dialog)
            val dialogDecor = dialog!!.window!!.decorView
            layoutView(dialogDecor, activity.resources.displayMetrics.widthPixels, activity.resources.displayMetrics.heightPixels)
            val dialogNodes = collectSemanticsNodes(dialogDecor, mergingEnabled = true)
            val downloadBtn = dialogNodes.firstOrNull { node ->
                node.config.getOrNull(SemanticsActions.OnClick) != null &&
                    node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == "Download" } == true
            }
            assertNotNull("Expected Download button inside ModelDownloadDialog", downloadBtn)
            clickNode(downloadBtn!!)
            flushMainUi(activity)

            assertTrue(uploaded)
            assertTrue(retried)
            assertTrue(downloadConfirmed)
        }
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

    private fun setDesignContent(
        activity: MainActivity,
        themeMode: ThemeMode,
        fontScale: Float = 1.0f,
        content: @Composable () -> Unit
    ) {
        activity.setContent {
            val baseContext = LocalContext.current
            val baseConfig = LocalConfiguration.current
            val localizedConfig = remember(baseConfig, fontScale) {
                Configuration(baseConfig).apply {
                    setLocale(Locale.ENGLISH)
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
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalConfiguration provides localizedConfig,
                LocalContext provides localizedContext,
                LocalResources provides localizedResources,
                LocalDensity provides Density(currentDensity.density, fontScale = fontScale),
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

    private fun layoutView(view: View, widthPx: Int, heightPx: Int) {
        fun forceLayoutTree(node: View) {
            node.forceLayout()
            if (node is ViewGroup) {
                for (i in 0 until node.childCount) {
                    forceLayoutTree(node.getChildAt(i))
                }
            }
        }
        forceLayoutTree(view)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, widthPx, heightPx)
        view.viewTreeObserver.dispatchOnPreDraw()
    }

    private fun flushMainUi(activity: MainActivity? = null, frames: Int = 4) {
        repeat(frames) {
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            activity?.window?.decorView?.let { decor ->
                val dm = activity.resources.displayMetrics
                layoutView(decor, dm.widthPixels, dm.heightPixels)
            }
        }
    }

    private fun collectSemanticsNodes(
        rootView: View,
        mergingEnabled: Boolean = false
    ): List<SemanticsNode> {
        val roots = mutableListOf<androidx.compose.ui.node.RootForTest>()
        fun walk(view: View) {
            if (view is androidx.compose.ui.node.RootForTest) roots += view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    walk(view.getChildAt(i))
                }
            }
        }
        walk(rootView)
        return roots.flatMap { it.semanticsOwner.getAllSemanticsNodes(mergingEnabled = mergingEnabled) }
    }

    private fun findByTag(nodes: List<SemanticsNode>, tag: String): SemanticsNode? =
        nodes.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }

    private fun clickNode(node: SemanticsNode) {
        val action = node.config.getOrNull(SemanticsActions.OnClick)?.action
            ?: error("Semantics node ${node.config.getOrNull(SemanticsProperties.TestTag)} has no OnClick action")
        action.invoke()
    }
}
