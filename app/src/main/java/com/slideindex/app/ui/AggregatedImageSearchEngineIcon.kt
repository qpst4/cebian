package com.slideindex.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.slideindex.app.search.AggregatedImageSearchIconLoader
import com.slideindex.app.search.ImageSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val AggregatedIconCornerFraction = 0.24f

@Composable
fun AggregatedImageSearchEngineIcon(
    engine: ImageSearchEngine,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    var bitmap by remember(engine) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(engine) {
        bitmap = withContext(Dispatchers.IO) {
            AggregatedImageSearchIconLoader.load(context, engine)
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val shape = RoundedCornerShape(maxWidth * AggregatedIconCornerFraction)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            val image = bitmap
            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = engine.displayName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = engine.displayName.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
