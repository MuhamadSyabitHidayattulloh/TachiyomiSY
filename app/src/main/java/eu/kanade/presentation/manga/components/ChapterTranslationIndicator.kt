package eu.kanade.presentation.manga.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.tachiyomi.data.translation.model.TranslationProgress
import eu.kanade.tachiyomi.data.translation.model.TranslationStage
import eu.kanade.tachiyomi.data.translation.model.TranslationState
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.IconButtonTokens
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.secondaryItemAlpha

enum class ChapterTranslationAction {
    START,
    CANCEL,
    DELETE,
}

@Composable
fun ChapterTranslationIndicator(
    enabled: Boolean,
    progress: TranslationProgress,
    onClick: (ChapterTranslationAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showProgressDialog by remember { mutableStateOf(false) }

    if (showProgressDialog) {
        TranslationProgressDialog(
            progress = progress,
            onDismiss = { showProgressDialog = false },
            onCancel = {
                onClick(ChapterTranslationAction.CANCEL)
                showProgressDialog = false
            },
        )
    }

    when (progress.state) {
        TranslationState.NOT_TRANSLATED -> NotTranslatedIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = { onClick(ChapterTranslationAction.START) },
        )
        TranslationState.QUEUED, TranslationState.IN_PROGRESS -> TranslatingIndicator(
            enabled = enabled,
            progress = progress,
            modifier = modifier,
            onClick = { showProgressDialog = true },
        )
        TranslationState.COMPLETED -> TranslatedIndicator(
            enabled = enabled,
            modifier = modifier,
            onClickAction = onClick,
        )
        TranslationState.FAILED -> ErrorTranslationIndicator(
            enabled = enabled,
            modifier = modifier,
            onClick = { showProgressDialog = true },
        )
    }
}

@Composable
private fun NotTranslatedIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onClick = onClick,
            )
            .secondaryItemAlpha(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Translate,
            contentDescription = stringResource(SYMR.strings.action_translate),
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TranslatingIndicator(
    enabled: Boolean,
    progress: TranslationProgress,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val strokeColor = MaterialTheme.colorScheme.primary
        if (progress.state == TranslationState.QUEUED || progress.progressFraction == 0f) {
            CircularProgressIndicator(
                modifier = IndicatorModifier,
                color = strokeColor,
                strokeWidth = IndicatorStrokeWidth,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
            )
        } else {
            val animatedProgress by animateFloatAsState(
                targetValue = progress.progressFraction,
                animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
            )
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = IndicatorModifier,
                color = strokeColor,
                strokeWidth = IndicatorStrokeWidth,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
            )
        }
        Icon(
            imageVector = Icons.Outlined.Translate,
            contentDescription = null,
            modifier = IconInProgressModifier,
            tint = strokeColor,
        )
    }
}

@Composable
private fun TranslatedIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClickAction: (ChapterTranslationAction) -> Unit,
) {
    var isMenuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onClick = { isMenuExpanded = true },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Translate,
            contentDescription = null,
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.primary,
        )
        DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
            DropdownMenuItem(
                text = { Text(text = stringResource(SYMR.strings.action_retranslate)) },
                onClick = {
                    onClickAction(ChapterTranslationAction.START)
                    isMenuExpanded = false
                },
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(SYMR.strings.action_delete_translation)) },
                onClick = {
                    onClickAction(ChapterTranslationAction.DELETE)
                    isMenuExpanded = false
                },
            )
        }
    }
}

@Composable
private fun ErrorTranslationIndicator(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(IconButtonTokens.StateLayerSize)
            .commonClickable(
                enabled = enabled,
                hapticFeedback = LocalHapticFeedback.current,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = stringResource(SYMR.strings.translation_status_failed),
            modifier = Modifier.size(IndicatorSize),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
fun TranslationProgressDialog(
    progress: TranslationProgress,
    onDismiss: () -> Unit,
    onCancel: () -> Unit,
) {
    val stageName = when (progress.stage) {
        TranslationStage.IDLE -> "Queued"
        TranslationStage.DETECTION -> stringResource(SYMR.strings.translation_stage_detection)
        TranslationStage.OCR -> stringResource(SYMR.strings.translation_stage_ocr)
        TranslationStage.CLEANING -> stringResource(SYMR.strings.translation_stage_cleaning)
        TranslationStage.TRANSLATION -> stringResource(SYMR.strings.translation_stage_translation)
        TranslationStage.CANVAS_RENDER -> stringResource(SYMR.strings.translation_stage_canvas)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(SYMR.strings.pref_category_translations)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Text(
                    text = "Status: ${progress.state.name} ($stageName)",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (progress.totalPages > 0) {
                    Text(
                        text = "Page ${progress.currentPage} / ${progress.totalPages}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress.progressFraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Logs:",
                    style = MaterialTheme.typography.labelMedium,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Column {
                        progress.logs.forEach { log ->
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (progress.errorMessage != null) {
                            Text(
                                text = "Error: ${progress.errorMessage}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            if (progress.state == TranslationState.IN_PROGRESS || progress.state == TranslationState.QUEUED) {
                TextButton(onClick = onCancel) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            }
        },
    )
}

private fun Modifier.commonClickable(
    enabled: Boolean,
    hapticFeedback: HapticFeedback,
    onClick: () -> Unit,
) = this.combinedClickable(
    enabled = enabled,
    onClick = {
        onClick()
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    },
    role = Role.Button,
    interactionSource = null,
    indication = ripple(
        bounded = false,
        radius = IconButtonTokens.StateLayerSize / 2,
    ),
)

private val IndicatorSize = 24.dp
private val IndicatorPadding = 2.dp
private val IndicatorStrokeWidth = IndicatorPadding

private val IndicatorModifier = Modifier
    .size(IndicatorSize)
    .padding(IndicatorPadding)
private val IconInProgressModifier = Modifier
    .size(IndicatorSize - 8.dp)
