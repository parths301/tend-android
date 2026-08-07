package com.tend.app.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tend.app.MainViewModel
import com.tend.app.data.db.Attachment
import com.tend.app.ui.motion.TendHaptic
import com.tend.app.ui.theme.Card
import com.tend.app.ui.theme.CardBorder
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.Ink
import com.tend.app.ui.theme.Muted
import com.tend.app.ui.theme.SegBg
import com.tend.app.ui.theme.Terracotta

/**
 * The one way an attachment is drawn, anywhere.
 *
 * Chat, memory and detail screens all use these, so behaviour cannot drift
 * between surfaces — the requirement was explicitly that they stay consistent.
 */
@Composable
fun AttachmentRow(
    attachment: Attachment,
    onOpen: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(10.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
            .then(if (onOpen != null) Modifier.tapNoRipple(TendHaptic.Select, onOpen) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier.size(28.dp).background(SegBg, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(glyphFor(attachment.mime), fontSize = 12.sp)
        }
        Box(Modifier.weight(1f)) {
            Text(
                attachment.displayName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(humanSize(attachment.sizeBytes), fontSize = 10.sp, color = Faint)
        if (onRemove != null) {
            Text(
                "✕",
                fontSize = 11.sp,
                color = Terracotta,
                modifier = Modifier.tapNoRipple(TendHaptic.Select, onRemove).padding(start = 2.dp),
            )
        }
    }
}

/** A compact strip of pending attachments, for a composer. */
@Composable
fun AttachmentStrip(attachments: List<PendingAttachment>, onRemove: (PendingAttachment) -> Unit) {
    if (attachments.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        attachments.forEach { pending ->
            Row(
                Modifier
                    .background(SegBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(glyphFor(pending.mime), fontSize = 10.sp)
                Text(
                    pending.displayName,
                    fontSize = 10.5.sp,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 110.dp),
                )
                Text(
                    "✕",
                    fontSize = 10.sp,
                    color = Terracotta,
                    modifier = Modifier.tapNoRipple(TendHaptic.Select) { onRemove(pending) },
                )
            }
        }
    }
}

/** A file the user picked but hasn't sent yet. */
data class PendingAttachment(
    val uri: Uri,
    val displayName: String,
    val mime: String,
    val sizeBytes: Long,
)

/**
 * Reads a picked document's real name and size.
 *
 * A `content://` URI carries neither, and the last path segment is usually an
 * opaque id — showing that instead of "receipt.pdf" is the difference between
 * an attachment list you can read and one you can't.
 */
fun readPickedFile(context: Context, uri: Uri): PendingAttachment {
    var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    var size = 0L
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            if (nameIndex >= 0) cursor.getString(nameIndex)?.let { name = it }
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
        }
    }
    return PendingAttachment(
        uri = uri,
        displayName = name,
        mime = context.contentResolver.getType(uri) ?: "application/octet-stream",
        sizeBytes = size,
    )
}

private fun glyphFor(mime: String): String = when {
    mime.startsWith("image/") -> "🖼"
    mime.startsWith("audio/") -> "🎵"
    mime.startsWith("video/") -> "🎬"
    mime.contains("pdf") -> "📄"
    else -> "📎"
}

private fun humanSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

/**
 * "Files" for a habit, task or plan block — the same [AttachmentRow] chat and
 * Memory already use, backed by the same generic `attachments` table keyed on
 * [ownerType]/[ownerId]. One composable so the picker, permission handling and
 * empty state can't drift between the three edit surfaces that embed it.
 */
@Composable
fun AttachmentsSection(vm: MainViewModel, ownerType: String, ownerId: Long) {
    val flow = remember(ownerType, ownerId) { vm.attachmentsFor(ownerType, ownerId) }
    val attachments by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.addOwnerAttachment(ownerType, ownerId, it) }
    }

    Column {
        Text(
            "ATTACHMENTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
            color = Faint, modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
        )
        if (attachments.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                attachments.forEach { att ->
                    AttachmentRow(att, onRemove = { vm.removeAttachment(att.id) })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        DashedAddBox("Add a file") { picker.launch(arrayOf("*/*")) }
    }
}
