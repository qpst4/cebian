package com.slideindex.app.ui.viewmodel

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.slideindex.app.search.ImageViewTargetResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImageViewerDropdownOption(
    val packageName: String?,
    val label: String,
    val iconBitmap: ImageBitmap?,
)

sealed interface ImageViewerOptionsState {
    data object Loading : ImageViewerOptionsState

    data class Ready(
        val options: List<ImageViewerDropdownOption>,
    ) : ImageViewerOptionsState
}

@HiltViewModel
class FloatBallPickSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _imageViewerOptions = MutableStateFlow<ImageViewerOptionsState>(ImageViewerOptionsState.Loading)
    val imageViewerOptions: StateFlow<ImageViewerOptionsState> = _imageViewerOptions.asStateFlow()

    init {
        viewModelScope.launch {
            val options = withContext(Dispatchers.IO) {
                buildImageViewerOptions(context)
            }
            _imageViewerOptions.value = ImageViewerOptionsState.Ready(options)
        }
    }

    companion object {
        private const val ICON_SIZE_PX = 96

        internal fun buildImageViewerOptions(context: Context): List<ImageViewerDropdownOption> {
            return buildList {
                add(
                    ImageViewerDropdownOption(
                        packageName = null,
                        label = ASK_EVERY_TIME_LABEL,
                        iconBitmap = null,
                    ),
                )
                ImageViewTargetResolver.listTargets(context).forEach { target ->
                    val iconBitmap = target.icon
                        ?.toBitmap(ICON_SIZE_PX, ICON_SIZE_PX)
                        ?.asImageBitmap()
                    add(
                        ImageViewerDropdownOption(
                            packageName = target.packageName,
                            label = target.label,
                            iconBitmap = iconBitmap,
                        ),
                    )
                }
            }
        }

        const val ASK_EVERY_TIME_LABEL = "每次都询问"
    }
}
