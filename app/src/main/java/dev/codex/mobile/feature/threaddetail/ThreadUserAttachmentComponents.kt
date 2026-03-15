package dev.codex.mobile.feature.threaddetail

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.designsystem.theme.codeInline
import dev.codex.mobile.core.model.UserInputContent
import java.util.Base64

@Composable
internal fun ThreadUserAttachment(
    item: UserInputContent,
    isUser: Boolean,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is UserInputContent.Image -> ImageAttachmentCard(
            source = item.url,
            title = imageAttachmentTitle(item.url),
            supporting = imageAttachmentSupportingText(item.url),
            isUser = isUser,
            modifier = modifier,
        )

        is UserInputContent.LocalImage -> ImageAttachmentCard(
            source = null,
            title = "Image attached",
            supporting = fileNameFromPath(item.path),
            isUser = isUser,
            modifier = modifier,
        )

        is UserInputContent.Skill -> LabeledAttachmentCard(
            badge = "SKILL",
            title = item.name,
            supporting = item.path,
            isUser = isUser,
            modifier = modifier,
        )

        is UserInputContent.Mention -> LabeledAttachmentCard(
            badge = "APP",
            title = item.name,
            supporting = item.path,
            isUser = isUser,
            modifier = modifier,
        )

        is UserInputContent.Text -> LabeledAttachmentCard(
            badge = "TEXT",
            title = item.text,
            supporting = null,
            isUser = isUser,
            modifier = modifier,
        )
    }
}

@Composable
private fun ImageAttachmentCard(
    source: String?,
    title: String,
    supporting: String?,
    isUser: Boolean,
    modifier: Modifier = Modifier,
) {
    val decodedBitmap = remember(source) {
        source
            ?.takeIf(::isInlineDataImage)
            ?.let(::decodeInlineDataImage)
            ?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            ?.asImageBitmap()
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = attachmentContainerColor(isUser = isUser),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
        ) {
            Text(
                text = "IMAGE",
                style = MaterialTheme.typography.labelSmall,
                color = attachmentBadgeColor(isUser = isUser),
            )

            if (decodedBitmap != null) {
                Image(
                    bitmap = decodedBitmap,
                    contentDescription = "Attached image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    color = attachmentPlaceholderColor(isUser = isUser),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Image,
                            contentDescription = null,
                            tint = attachmentBadgeColor(isUser = isUser),
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            text = "Image attached",
                            style = MaterialTheme.typography.labelLarge,
                            color = attachmentTitleColor(isUser = isUser),
                        )
                    }
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = attachmentTitleColor(isUser = isUser),
            )

            supporting
                ?.takeIf { it.isNotBlank() }
                ?.let { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.codeInline,
                        color = attachmentSupportingColor(isUser = isUser),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
        }
    }
}

@Composable
private fun LabeledAttachmentCard(
    badge: String,
    title: String,
    supporting: String?,
    isUser: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = attachmentContainerColor(isUser = isUser),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = attachmentBadgeColor(isUser = isUser),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = attachmentTitleColor(isUser = isUser),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            supporting
                ?.takeIf { it.isNotBlank() }
                ?.let { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.codeInline,
                        color = attachmentSupportingColor(isUser = isUser),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
        }
    }
}

@Composable
private fun attachmentContainerColor(isUser: Boolean): Color = if (isUser) {
    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
} else {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
}

@Composable
private fun attachmentPlaceholderColor(isUser: Boolean): Color = if (isUser) {
    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.10f)
} else {
    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
}

@Composable
private fun attachmentBadgeColor(isUser: Boolean): Color = if (isUser) {
    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
} else {
    MaterialTheme.colorScheme.primary
}

@Composable
private fun attachmentTitleColor(isUser: Boolean): Color = if (isUser) {
    MaterialTheme.colorScheme.onPrimary
} else {
    MaterialTheme.colorScheme.onSurface
}

@Composable
private fun attachmentSupportingColor(isUser: Boolean): Color = if (isUser) {
    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f)
} else {
    MaterialTheme.colorScheme.onSurfaceVariant
}

private fun imageAttachmentTitle(source: String): String = when {
    isInlineDataImage(source) -> {
        val mimeType = source.substringAfter("data:").substringBefore(';')
        "${mimeType.substringAfter('/') .uppercase()} image attached"
    }

    else -> "Image attached"
}

private fun imageAttachmentSupportingText(source: String): String = when {
    isInlineDataImage(source) -> "Inline image"
    else -> fileNameFromPath(source)
}

private fun fileNameFromPath(path: String): String {
    val normalized = path.substringBefore('?').trimEnd('/', '\\')
    val slashIndex = maxOf(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'))
    val fileName = if (slashIndex >= 0) normalized.substring(slashIndex + 1) else normalized
    return fileName.ifBlank { "Attachment" }
}

private fun isInlineDataImage(value: String): Boolean = value.startsWith("data:image/", ignoreCase = true)

private fun decodeInlineDataImage(value: String): ByteArray? = runCatching {
    val base64Payload = value.substringAfter("base64,", missingDelimiterValue = "")
    if (base64Payload.isBlank()) null else Base64.getDecoder().decode(base64Payload)
}.getOrNull()

