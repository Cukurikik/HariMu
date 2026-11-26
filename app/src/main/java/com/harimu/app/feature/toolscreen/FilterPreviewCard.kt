package com.harimu.app.feature.toolscreen

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.harimu.app.R
import com.harimu.app.core.engines.ImageFilterPipeline
import com.harimu.app.core.engines.ImageFilterSpec

/**
 * IMG-33 live preview. The colour matrix is built by [ImageFilterPipeline] — the exact same
 * 20 values the export path feeds into `ColorMatrixColorFilter` — then handed to Compose as a
 * GPU `ColorFilter`. Blur and vignette are not colour matrices; they are applied on export and
 * stated as such in the caption.
 */
@Composable
fun FilterPreviewCard(
    imageUri: Uri,
    preset: String,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    temperature: Float,
    blurRadius: Int,
    vignette: Int
) {
    val spec = ImageFilterSpec(
        preset = preset,
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        temperature = temperature,
        blurRadius = blurRadius,
        vignette = vignette
    )
    val colorFilter = remember(spec) {
        ColorFilter.colorMatrix(ColorMatrix(ImageFilterPipeline.matrixValues(spec)))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.image_preview_filter_title),
                style = MaterialTheme.typography.titleSmall
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = stringResource(R.string.cd_filter_preview),
                    contentScale = ContentScale.Fit,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
            }
            Text(
                text = stringResource(R.string.image_preview_filter_caption, blurRadius, vignette),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
