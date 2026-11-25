package com.harimu.app.feature.toolscreen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.harimu.app.AppContainer
import com.harimu.app.R
import com.harimu.app.core.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.harimu.app.core.design.EngineBadge
import com.harimu.app.core.design.ErrorState
import com.harimu.app.core.design.FilePickerField
import com.harimu.app.core.design.HariMuColors
import com.harimu.app.core.design.MonoTextStyle
import com.harimu.app.core.design.ParamDropdown
import com.harimu.app.core.design.ParamSlider
import com.harimu.app.core.design.ParamToggle
import com.harimu.app.core.design.ProgressSheet
import com.harimu.app.core.design.ResultCard
import com.harimu.app.core.design.ToolIdBadge
import com.harimu.app.core.design.accentColor
import com.harimu.app.core.media.Id3TagHelper
import com.harimu.app.core.pdf.PdfVaultEngine
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolOutputFile
import com.harimu.app.core.toolapi.ToolParam
import com.harimu.app.core.toolapi.ToolProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScreen(
    toolId: String,
    container: AppContainer,
    isEnglish: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showUploadConsent by remember { mutableStateOf(false) }
    val vm: ToolViewModel = viewModel(factory = ToolViewModel.Factory(toolId, container))
    LaunchedEffect(context) { vm.bindContext(context) }

    val state by vm.state.collectAsStateWithLifecycle()
    val settings by container.settings.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val tool = state.tool ?: return

    var pendingExportFile by remember { mutableStateOf<ToolOutputFile?>(null) }
    var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlayingAudio by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { runCatching { audioPlayer?.release() } }
    }

    LaunchedEffect(tool.id, state.selectedUris) {
        if (tool.id == "AUD-18" && state.selectedUris.isNotEmpty()) {
            val existing = withContext(Dispatchers.IO) {
                Id3TagHelper.readTags(context, state.selectedUris.first())
            }
            if ((state.paramValues["title"]?.toString()).isNullOrBlank() && existing.title.isNotBlank()) {
                vm.setParam("title", existing.title)
            }
            if ((state.paramValues["artist"]?.toString()).isNullOrBlank() && existing.artist.isNotBlank()) {
                vm.setParam("artist", existing.artist)
            }
            if ((state.paramValues["album"]?.toString()).isNullOrBlank() && existing.album.isNotBlank()) {
                vm.setParam("album", existing.album)
            }
            if ((state.paramValues["year"]?.toString()).isNullOrBlank() && existing.year.isNotBlank()) {
                vm.setParam("year", existing.year)
            }
            if ((state.paramValues["genre"]?.toString()).isNullOrBlank() && existing.genre.isNotBlank()) {
                vm.setParam("genre", existing.genre)
            }
        }
    }

    val singleImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.setUris(listOf(it)) }
    }
    val multiImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
        if (uris.isNotEmpty()) vm.setUris(uris)
    }
    val singleVideoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.setUris(listOf(it)) }
    }
    val multiVideoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(8)) { uris ->
        if (uris.isNotEmpty()) vm.setUris(uris)
    }
    val singleDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setUris(listOf(it)) }
    }
    val multiDocPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.setUris(uris)
    }
    val secondaryAudioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setSecondaryUri(it) }
    }
    val secondaryImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.setSecondaryUri(it) }
    }
    val secondarySubtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setSecondaryUri(it) }
    }
    val secondaryBgMediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.setSecondaryUri(it) }
    }

    val savedToast = stringResource(R.string.toast_saved)
    val saveFailedToast = stringResource(R.string.toast_save_failed)
    val copiedToast = stringResource(R.string.toast_copied)

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        vm.execute(isEnglish)
    }

    fun triggerExecution() {
        if (tool.isStub && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        vm.execute(isEnglish)
    }

    val safSaveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destUri ->
        val src = pendingExportFile
        if (destUri != null && src != null) {
            val ok = container.storage.copyFileToUri(src.file, destUri)
            Toast.makeText(context, if (ok) savedToast else saveFailedToast, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tool.title(isEnglish), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("tool_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = stringResource(R.string.cd_toggle_favorite),
                            tint = if (isFavorite) HariMuColors.ElectricOrange else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ToolIdBadge(id = tool.id, accent = tool.group.accentColor())
                            EngineBadge(engine = tool.engineLabel)
                            if (tool.isStub) {
                                EngineBadge(engine = stringResource(R.string.badge_coming_soon))
                            }
                            if (tool.requiresNetwork) {
                                EngineBadge(engine = stringResource(R.string.badge_needs_internet))
                            }
                            tool.requiresModel?.let { model ->
                                EngineBadge(engine = stringResource(R.string.badge_needs_model, model.sizeLabel))
                            }
                        }
                        Text(
                            text = tool.description(isEnglish),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (tool.isStub) {
                    Card(
                        modifier = Modifier.testTag("stub_notice_card"),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = stringResource(R.string.stub_notice_title, tool.plannedPhase.ifBlank { "-" }),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                text = stringResource(R.string.stub_notice_body, tool.plannedPhase.ifBlank { "-" }),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                when (val spec = tool.inputSpec) {
                    InputSpec.None -> Unit
                    is InputSpec.TextInput -> {
                        OutlinedTextField(
                            value = state.textInput,
                            onValueChange = vm::setTextInput,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .testTag("tool_text_input"),
                            label = { Text(if (isEnglish) spec.hintEn else spec.hintId) },
                            textStyle = MonoTextStyle
                        )
                    }
                    else -> {
                        val isMulti = spec == InputSpec.MultipleImages || spec == InputSpec.MultipleVideos ||
                            spec == InputSpec.MultipleAudios || spec == InputSpec.MultiplePdfs || spec == InputSpec.AnyFile
                        val fallbackAudio = stringResource(R.string.fallback_audio_name)
                        val fallbackImage = stringResource(R.string.fallback_image_name)
                        val fallbackSubtitle = stringResource(R.string.fallback_subtitle_name)
                        val fallbackMedia = stringResource(R.string.fallback_media_name)
                        val fileNames = state.selectedUris.mapIndexed { idx, uri ->
                            container.storage.getDisplayName(uri) ?: stringResource(R.string.fallback_file_name, idx + 1)
                        }
                        FilePickerField(
                            title = stringResource(R.string.label_input_files),
                            buttonLabel = if (isMulti) stringResource(R.string.action_pick_files) else stringResource(R.string.action_pick_file),
                            selectedFiles = fileNames,
                            onPickClick = {
                                when (spec) {
                                    InputSpec.SingleImage -> singleImagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                    InputSpec.MultipleImages -> multiImagePicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                    InputSpec.SingleVideo,
                                    InputSpec.VideoAndAudio,
                                    InputSpec.VideoAndImage,
                                    InputSpec.VideoAndSubtitle,
                                    InputSpec.VideoAndMedia -> singleVideoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                    InputSpec.MultipleVideos -> multiVideoPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                    )
                                    InputSpec.SingleAudio,
                                    InputSpec.AudioAndImage,
                                    InputSpec.AudioAndWatermarkAudio -> singleDocPicker.launch(arrayOf("audio/*"))
                                    InputSpec.MultipleAudios -> multiDocPicker.launch(arrayOf("audio/*"))
                                    InputSpec.SinglePdf,
                                    InputSpec.PdfAndImage -> singleDocPicker.launch(arrayOf("application/pdf"))
                                    InputSpec.MultiplePdfs -> multiDocPicker.launch(arrayOf("application/pdf"))
                                    InputSpec.ZipFile -> singleDocPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                                    InputSpec.AnyFile -> multiDocPicker.launch(arrayOf("*/*"))
                                    else -> Unit
                                }
                            },
                            secondaryContent = when (spec) {
                                InputSpec.VideoAndAudio -> {
                                    {
                                        OutlinedButton(
                                            onClick = { secondaryAudioPicker.launch(arrayOf("audio/*")) },
                                            modifier = Modifier.fillMaxWidth().testTag("pick_secondary_audio_btn")
                                        ) {
                                            Text(stringResource(R.string.action_pick_audio_bg))
                                        }
                                        state.secondaryUri?.let { aUri ->
                                            Text(
                                                text = container.storage.getDisplayName(aUri) ?: fallbackAudio,
                                                style = MonoTextStyle,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                                InputSpec.VideoAndImage -> {
                                    {
                                        OutlinedButton(
                                            onClick = {
                                                secondaryImagePicker.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth().testTag("pick_secondary_image_btn")
                                        ) {
                                            Text(stringResource(R.string.action_pick_image_overlay))
                                        }
                                        state.secondaryUri?.let { iUri ->
                                            Text(
                                                text = container.storage.getDisplayName(iUri) ?: fallbackImage,
                                                style = MonoTextStyle,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                                InputSpec.VideoAndSubtitle -> {
                                    {
                                        OutlinedButton(
                                            onClick = {
                                                secondarySubtitlePicker.launch(
                                                    arrayOf("application/x-subrip", "text/vtt", "text/plain", "*/*")
                                                )
                                            },
                                            modifier = Modifier.fillMaxWidth().testTag("pick_secondary_subtitle_btn")
                                        ) {
                                            Text(stringResource(R.string.action_pick_subtitle_file))
                                        }
                                        state.secondaryUri?.let { sUri ->
                                            Text(
                                                text = container.storage.getDisplayName(sUri) ?: fallbackSubtitle,
                                                style = MonoTextStyle,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }
                                    }
                                }
                                InputSpec.VideoAndMedia -> {
                                    {
                                        OutlinedButton(
                                            onClick = { secondaryBgMediaPicker.launch(arrayOf("image/*", "video/*")) },
                                            modifier = Modifier.fillMaxWidth().testTag("pick_secondary_media_btn")
                                        ) {
                                            Text(stringResource(R.string.action_pick_bg_media))
                                        }
                                        state.secondaryUri?.let { mUri ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = container.storage.getDisplayName(mUri) ?: fallbackMedia,
                                                    style = MonoTextStyle,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(onClick = { vm.setSecondaryUri(null) }) {
                                                    Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.action_clear_secondary))
                                                }
                                            }
                                        }
                                    }
                                }
                                InputSpec.AudioAndImage -> {
                                    {
                                        val fallbackCover = stringResource(R.string.fallback_cover_name)
                                        OutlinedButton(
                                            onClick = {
                                                secondaryImagePicker.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("pick_cover_image_btn")
                                        ) {
                                            Text(stringResource(R.string.action_pick_cover_image))
                                        }
                                        state.secondaryUri?.let { imgUri ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = container.storage.getDisplayName(imgUri) ?: fallbackCover,
                                                    style = MonoTextStyle,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(onClick = { vm.setSecondaryUri(null) }) {
                                                    Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.action_clear_secondary))
                                                }
                                            }
                                        }
                                    }
                                }
                                InputSpec.AudioAndWatermarkAudio -> {
                                    {
                                        OutlinedButton(
                                            onClick = { secondaryAudioPicker.launch(arrayOf("audio/*")) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("pick_watermark_audio_btn")
                                        ) {
                                            Text(stringResource(R.string.action_pick_watermark_audio))
                                        }
                                        state.secondaryUri?.let { wmUri ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = container.storage.getDisplayName(wmUri) ?: fallbackAudio,
                                                    style = MonoTextStyle,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(onClick = { vm.setSecondaryUri(null) }) {
                                                    Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.action_clear_secondary))
                                                }
                                            }
                                        }
                                    }
                                }
                                InputSpec.PdfAndImage -> {
                                    {
                                        OutlinedButton(
                                            onClick = {
                                                secondaryImagePicker.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("pick_pdf_watermark_image_btn")
                                        ) {
                                            Text(if (isEnglish) "Pick watermark image" else "Pilih gambar watermark")
                                        }
                                        state.secondaryUri?.let { wmImage ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = container.storage.getDisplayName(wmImage) ?: fallbackImage,
                                                    style = MonoTextStyle,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(onClick = { vm.setSecondaryUri(null) }) {
                                                    Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.action_clear_secondary))
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> null
                            }
                        )
                    }
                }

                if (tool.id == "VID-12" || tool.id == "VID-19") {
                    VideoSafetyWarningCard(isReverse = tool.id == "VID-19")
                }

                val isSingleVideoSpec = tool.inputSpec == InputSpec.SingleVideo ||
                    tool.inputSpec == InputSpec.VideoAndAudio ||
                    tool.inputSpec == InputSpec.VideoAndImage ||
                    tool.inputSpec == InputSpec.VideoAndSubtitle ||
                    tool.inputSpec == InputSpec.VideoAndMedia

                if (isSingleVideoSpec && state.selectedUris.isNotEmpty()) {
                    if (tool.id == "VID-14") {
                        RoiLogoPreviewCard(
                            videoUri = state.selectedUris.first(),
                            xPct = (state.paramValues["xPct"] as? Number)?.toFloat() ?: 70f,
                            yPct = (state.paramValues["yPct"] as? Number)?.toFloat() ?: 5f,
                            wPct = (state.paramValues["wPct"] as? Number)?.toFloat() ?: 22f,
                            hPct = (state.paramValues["hPct"] as? Number)?.toFloat() ?: 14f
                        )
                    } else if (tool.id == "VID-25") {
                        FaceBlurPreviewCard(videoUri = state.selectedUris.first())
                    } else {
                        val showTrim = tool.id == "VID-01" || tool.id == "VID-21"
                        val startVal = (state.paramValues["startSec"] as? Number)?.toFloat() ?: 0f
                        val endVal = (state.paramValues["endSec"] as? Number)?.toFloat() ?: 0f
                        VideoTrimPreviewCard(
                            videoUri = state.selectedUris.first(),
                            showTrimStrip = showTrim,
                            startSec = startVal,
                            endSec = endVal,
                            onRangeChange = { newStart, newEnd ->
                                vm.setParam("startSec", newStart)
                                vm.setParam("endSec", newEnd)
                                if (tool.id == "VID-21") {
                                    vm.setParam("mode", "Rentang Pilihan")
                                }
                            }
                        )
                    }
                }

                if (tool.id == "IMG-06" && state.selectedUris.isNotEmpty()) {
                    val keys = listOf("tlX", "tlY", "trX", "trY", "brX", "brY", "blX", "blY")
                    val defaults = listOf(8f, 8f, 92f, 8f, 92f, 92f, 8f, 92f)
                    PerspectivePointsCard(
                        imageUri = state.selectedUris.first(),
                        points = keys.mapIndexed { index, key ->
                            (state.paramValues[key] as? Number)?.toFloat() ?: defaults[index]
                        },
                        onPointChange = { index, value -> vm.setParam(keys[index], value) }
                    )
                }

                if (tool.id == "IMG-33" && state.selectedUris.isNotEmpty()) {
                    FilterPreviewCard(
                        imageUri = state.selectedUris.first(),
                        preset = state.paramValues["preset"]?.toString() ?: "Vintage",
                        brightness = (state.paramValues["brightness"] as? Number)?.toFloat() ?: 0f,
                        contrast = (state.paramValues["contrast"] as? Number)?.toFloat() ?: 1.05f,
                        saturation = (state.paramValues["saturation"] as? Number)?.toFloat() ?: 1f,
                        temperature = (state.paramValues["temperature"] as? Number)?.toFloat() ?: 0f,
                        blurRadius = (state.paramValues["blur"] as? Number)?.toInt() ?: 0,
                        vignette = (state.paramValues["vignette"] as? Number)?.toInt() ?: 0
                    )
                }

                if (tool.group == ToolGroup.PDF && !tool.isStub &&
                    state.selectedUris.isNotEmpty() &&
                    tool.inputSpec != InputSpec.MultiplePdfs &&
                    tool.inputSpec != InputSpec.MultipleImages
                ) {
                    val pdfUri = state.selectedUris.first()
                    if (tool.id == "PDF-04") {
                        PdfPagePickerCard(
                            pdfUri = pdfUri,
                            storage = container.storage,
                            isEnglish = isEnglish,
                            reorderMode = true,
                            pages = PdfVaultEngine.parsePagesLoose(state.paramValues["order"]?.toString() ?: ""),
                            onPagesChange = { order -> vm.setParam("order", order.joinToString(",")) }
                        )
                    } else if (tool.params.any { it.key == "pages" }) {
                        PdfPagePickerCard(
                            pdfUri = pdfUri,
                            storage = container.storage,
                            isEnglish = isEnglish,
                            reorderMode = false,
                            pages = PdfVaultEngine.parsePagesLoose(state.paramValues["pages"]?.toString() ?: ""),
                            onPagesChange = { pages ->
                                vm.setParam("pages", PdfVaultEngine.formatPageRange(pages))
                            }
                        )
                    }
                }

                val isSingleAudioSpec = tool.inputSpec == InputSpec.SingleAudio ||
                    tool.inputSpec == InputSpec.AudioAndImage ||
                    tool.inputSpec == InputSpec.AudioAndWatermarkAudio
                val doneAudioOutput = (state.progress as? ToolProgress.Done)
                    ?.result
                    ?.outputs
                    ?.firstOrNull { it.mimeType.startsWith("audio/") }
                val showAudioCard = !tool.isStub && (
                    (isSingleAudioSpec && state.selectedUris.isNotEmpty()) ||
                        (tool.group == ToolGroup.AUDIO && doneAudioOutput != null)
                    )
                if (showAudioCard) {
                    AudioBeforeAfterPreviewCard(
                        inputUri = if (isSingleAudioSpec) state.selectedUris.firstOrNull() else null,
                        outputFile = doneAudioOutput?.file,
                        showTrimControls = tool.id == "AUD-09",
                        startSec = (state.paramValues["startSec"] as? Number)?.toFloat() ?: 0f,
                        endSec = (state.paramValues["endSec"] as? Number)?.toFloat() ?: 15f,
                        onRangeChange = { newStart, newEnd ->
                            vm.setParam("startSec", newStart)
                            vm.setParam("endSec", newEnd)
                        }
                    )
                }

                if (tool.params.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(text = stringResource(R.string.label_parameters), style = MaterialTheme.typography.titleSmall)
                            tool.params.forEach { param ->
                                when (param) {
                                    is ToolParam.SliderParam -> {
                                        val v = (state.paramValues[param.key] as? Number)?.toFloat() ?: param.defaultValue
                                        ParamSlider(
                                            label = if (isEnglish) param.labelEn else param.labelId,
                                            value = v,
                                            min = param.min,
                                            max = param.max,
                                            step = param.step,
                                            unit = param.unit,
                                            onValueChange = { vm.setParam(param.key, it) }
                                        )
                                    }
                                    is ToolParam.DropdownParam -> {
                                        val current = state.paramValues[param.key]?.toString() ?: param.defaultOption
                                        ParamDropdown(
                                            label = if (isEnglish) param.labelEn else param.labelId,
                                            options = param.options,
                                            selectedOption = current,
                                            onOptionSelected = { vm.setParam(param.key, it) }
                                        )
                                    }
                                    is ToolParam.ToggleParam -> {
                                        val checked = state.paramValues[param.key] as? Boolean ?: param.defaultValue
                                        ParamToggle(
                                            label = if (isEnglish) param.labelEn else param.labelId,
                                            checked = checked,
                                            onCheckedChange = { vm.setParam(param.key, it) }
                                        )
                                    }
                                    is ToolParam.TextParam -> {
                                        val txt = state.paramValues[param.key]?.toString() ?: param.defaultValue
                                        OutlinedTextField(
                                            value = txt,
                                            onValueChange = { vm.setParam(param.key, it) },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true,
                                            label = { Text(if (isEnglish) param.labelEn else param.labelId) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (tool.requiresNetwork && !state.isRunning) {
                            scope.launch {
                                val consent = runCatching { container.aiClient.hasUploadConsent() }.getOrDefault(false)
                                if (consent) triggerExecution() else showUploadConsent = true
                            }
                        } else {
                            triggerExecution()
                        }
                    },
                    enabled = !state.isRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("run_tool_button")
                ) {
                    Text(
                        text = when {
                            state.isRunning -> stringResource(R.string.action_processing)
                            tool.isStub -> stringResource(R.string.action_run_dummy_job)
                            else -> stringResource(R.string.action_run_tool)
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                if (showUploadConsent) {
                    AlertDialog(
                        onDismissRequest = { showUploadConsent = false },
                        title = {
                            Text(
                                if (isEnglish) "Allow cloud upload?" else "Izinkan unggahan cloud?"
                            )
                        },
                        text = {
                            Text(
                                if (isEnglish) {
                                    "This tool sends your file to the AI gateway configured in Settings. " +
                                        "Files are processed for this request only and the gateway keeps no copy " +
                                        "beyond the job lifetime. Continue?"
                                } else {
                                    "Tool ini mengirim berkas Anda ke AI Gateway yang diatur di Pengaturan. " +
                                        "Berkas hanya diproses untuk permintaan ini dan gateway tidak menyimpan " +
                                        "salinan melebihi masa hidup job. Lanjutkan?"
                                }
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                showUploadConsent = false
                                scope.launch {
                                    runCatching { container.aiClient.grantUploadConsent() }
                                    triggerExecution()
                                }
                            }) {
                                Text(if (isEnglish) "Allow upload" else "Izinkan unggah")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showUploadConsent = false }) {
                                Text(if (isEnglish) "Cancel" else "Batal")
                            }
                        }
                    )
                }

                when (val prog = state.progress) {
                    is ToolProgress.Queued -> ProgressSheet(
                        percent = null,
                        message = stringResource(R.string.progress_queued),
                        onCancel = { vm.cancel() }
                    )
                    is ToolProgress.Running -> ProgressSheet(
                        percent = prog.percent,
                        message = prog.message,
                        onCancel = { vm.cancel() }
                    )
                    is ToolProgress.Failed -> ErrorState(
                        message = prog.error,
                        onRetry = { triggerExecution() }
                    )
                    is ToolProgress.Done -> {
                        val firstOut = prog.result.outputs.firstOrNull()
                        ResultCard(
                            summary = prog.result.summary,
                            onOpen = firstOut?.let { out ->
                                {
                                    runCatching {
                                        context.startActivity(
                                            Intent.createChooser(container.storage.buildOpenIntent(out.file, out.mimeType), out.displayName)
                                        )
                                    }
                                }
                            },
                            onShare = firstOut?.let { out ->
                                {
                                    context.startActivity(
                                        Intent.createChooser(container.storage.buildShareIntent(out.file, out.mimeType), out.displayName)
                                    )
                                }
                            },
                            onSave = firstOut?.let { out ->
                                {
                                    val defaultTreeUri = settings.outputFolderUri
                                    if (defaultTreeUri.isNotBlank()) {
                                        scope.launch(Dispatchers.IO) {
                                            val savedUri = container.storage.saveToTreeUri(
                                                source = out.file,
                                                treeUri = defaultTreeUri.toUri(),
                                                mimeType = out.mimeType
                                            )
                                            withContext(Dispatchers.Main) {
                                                if (savedUri != null) {
                                                    Toast.makeText(context, savedToast, Toast.LENGTH_SHORT).show()
                                                } else {
                                                    pendingExportFile = out
                                                    safSaveLauncher.launch(out.displayName)
                                                }
                                            }
                                        }
                                    } else {
                                        pendingExportFile = out
                                        safSaveLauncher.launch(out.displayName)
                                    }
                                }
                            },
                            onUploadToCloud = firstOut?.let { out ->
                                {
                                    scope.launch(Dispatchers.IO) {
                                        val config = container.cloudStore.load()
                                        if (!config.isConfigured()) {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    if (settings.isEnglish) "Configure a cloud connection in Settings first" else "Atur koneksi cloud di Pengaturan dulu",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        } else {
                                            container.uploadQueue.enqueue(out.file, out.mimeType, config.provider)
                                            val uploaded = container.uploadQueue.processPending(
                                                configProvider = { container.cloudStore.load() },
                                                persistConfig = { container.cloudStore.save(it) }
                                            )
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    if (uploaded > 0) {
                                                        if (settings.isEnglish) "Uploaded to cloud" else "Terunggah ke cloud"
                                                    } else {
                                                        if (settings.isEnglish) "Queued — will retry" else "Masuk antrean — akan dicoba ulang"
                                                    },
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            },
                        ) {
                            prog.result.outputs.forEach { out ->
                                if (out.mimeType.startsWith("video/") && tool.id == "AUD-28") {
                                    VideoTrimPreviewCard(
                                        videoUri = out.file.toUri(),
                                        showTrimStrip = false,
                                        startSec = 0f,
                                        endSec = 0f,
                                        onRangeChange = { _, _ -> }
                                    )
                                }
                                if (out.mimeType.startsWith("image/")) {
                                    AsyncImage(
                                        model = out.file,
                                        contentDescription = out.displayName,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(220.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                }
                                if (out.mimeType.startsWith("audio/")) {
                                    OutlinedButton(
                                        onClick = {
                                            if (isPlayingAudio) {
                                                runCatching { audioPlayer?.stop(); audioPlayer?.release() }
                                                audioPlayer = null
                                                isPlayingAudio = false
                                            } else {
                                                runCatching {
                                                    audioPlayer?.release()
                                                    audioPlayer = MediaPlayer().apply {
                                                        setDataSource(out.file.absolutePath)
                                                        prepare()
                                                        setOnCompletionListener { isPlayingAudio = false }
                                                        start()
                                                    }
                                                    isPlayingAudio = true
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(if (isPlayingAudio) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (isPlayingAudio) stringResource(R.string.action_stop_audio)
                                            else stringResource(R.string.action_play_audio)
                                        )
                                    }
                                }
                            }
                            prog.result.textOutput?.let { txt ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(12.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(stringResource(R.string.label_output), style = MaterialTheme.typography.labelMedium)
                                            IconButton(onClick = {
                                                clipboard.setText(AnnotatedString(txt))
                                                Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.action_copy), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        Text(txt, style = MonoTextStyle)
                                    }
                                }
                            }
                        }
                    }
                    null -> Unit
                }
            }
        }
    }
}
