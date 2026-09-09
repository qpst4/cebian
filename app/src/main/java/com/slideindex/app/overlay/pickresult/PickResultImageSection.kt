package com.slideindex.app.overlay.pickresult

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.SearchEngineConfig

@Composable
internal fun PickResultImageSection(
    screenshot: Bitmap?,
    panelImages: List<Bitmap>,
    currentImageIndex: Int,
    imageDisplaySize: PickResultImageDisplaySize,
    searchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    modifier: Modifier = Modifier,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onImageSearch: () -> Unit,
    onShareEngineClick: (com.slideindex.app.settings.SearchEngineConfig) -> Unit,
    onPinToScreen: () -> Unit,
    onStash: () -> Unit,
    onImageClick: () -> Unit,
    onImageIndexChange: (Int) -> Unit,
    sectionExpanded: Boolean,
    onSectionExpandedChange: (Boolean) -> Unit
) {
    val images = panelImages.ifEmpty { listOfNotNull(screenshot) }
    Column(modifier = modifier) {
        PickResultSectionHeader(
            title = stringResource(R.string.float_ball_pick_result_image_section),
            expanded = sectionExpanded,
            onToggle = { onSectionExpandedChange(!sectionExpanded) },
            collapsible = true
        )
        if (images.isNotEmpty()) {
            PickResultImageSectionGallery(
                modifier = Modifier.weight(1f),
                images = images,
                currentImageIndex = currentImageIndex,
                imageDisplaySize = imageDisplaySize,
                searchEngines = searchEngines,
                onSave = onSave,
                onShare = onShare,
                onImageSearch = onImageSearch,
                onShareEngineClick = onShareEngineClick,
                onPinToScreen = onPinToScreen,
                onStash = onStash,
                onImageClick = onImageClick,
                onImageIndexChange = onImageIndexChange,
                sectionExpanded = sectionExpanded,
                onSectionExpandedChange = onSectionExpandedChange
            )
        }
    }
}

@Composable
internal fun PickResultImageSectionGallery(
    images: List<Bitmap>,
    currentImageIndex: Int,
    imageDisplaySize: PickResultImageDisplaySize,
    searchEngines: List<com.slideindex.app.settings.SearchEngineConfig>,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onImageSearch: () -> Unit,
    onShareEngineClick: (com.slideindex.app.settings.SearchEngineConfig) -> Unit,
    onPinToScreen: () -> Unit,
    onStash: () -> Unit,
    onImageClick: () -> Unit,
    onImageIndexChange: (Int) -> Unit,
    sectionExpanded: Boolean,
    onSectionExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = currentImageIndex.coerceIn(0, images.lastIndex),
        pageCount = { images.size }
    )
    LaunchedEffect(currentImageIndex) {
        if (pagerState.currentPage != currentImageIndex) {
            pagerState.scrollToPage(currentImageIndex.coerceIn(0, images.lastIndex))
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        if (pagerState.settledPage != currentImageIndex) {
            onImageIndexChange(pagerState.settledPage)
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (images.size == 1) {
                    val image = images.first()
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .width(imageDisplaySize.width)
                            .height(imageDisplaySize.height)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onImageClick),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        beyondViewportPageCount = 0
                    ) { page ->
                        val image = images[page]
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = image.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .width(imageDisplaySize.width)
                                    .height(imageDisplaySize.height)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onImageClick),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    Text(
                        text = "${pagerState.currentPage + 1}/${images.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(6.dp)
                            .background(
                                color = Color.Black.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (images.size > 1) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(images.size, key = { it }) { index ->
                        val selected = index == pagerState.currentPage
                        val thumb = images[index]
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onImageIndexChange(index) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
            PickResultImageSearchBar(
                engines = searchEngines,
                onShareEngineClick = onShareEngineClick,
                onShare = onShare,
                onImageSearch = onImageSearch,
                onSave = onSave,
                onPinToScreen = onPinToScreen,
                onStash = onStash,
                onThumbnailClick = { onSectionExpandedChange(!sectionExpanded) }
            )
    }
}
