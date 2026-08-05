package com.tend.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tend.app.data.db.ChatThread
import com.tend.app.ui.components.DialogInput
import com.tend.app.ui.components.TendCard
import com.tend.app.ui.components.tapNoRipple
import com.tend.app.ui.motion.TendHaptic
import com.tend.app.ui.motion.TendMotion
import com.tend.app.ui.motion.bouncyTap
import com.tend.app.ui.theme.Border
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.Ink
import com.tend.app.ui.theme.Muted
import com.tend.app.ui.theme.SegBg
import com.tend.app.ui.theme.SpaceGrotesk
import com.tend.app.ui.theme.Terracotta
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Chat history: search, switch, rename, pin, delete.
 *
 * Deleting a thread and clearing the current one are separate controls with
 * separate confirmations, because they are separate intentions — one throws the
 * conversation away, the other keeps it and empties it.
 */
@Composable
fun ThreadPickerDialog(
    threads: List<ChatThread>,
    activeThread: Long,
    onOpen: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onPin: (Long, Boolean) -> Unit,
    onClearCurrent: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var renaming by remember { mutableLongStateOf(0L) }
    var confirmDelete by remember { mutableLongStateOf(0L) }
    var confirmClear by remember { mutableStateOf(false) }

    val shown = remember(threads, query) {
        if (query.isBlank()) threads
        else threads.filter { it.title.contains(query, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Chats", fontFamily = SpaceGrotesk, fontSize = 17.sp, fontWeight = FontWeight.Bold)

                DialogInput(query, { query = it }, "Search chats…")

                if (shown.isEmpty()) {
                    Text(
                        if (query.isBlank()) "No chats yet." else "Nothing matches \"$query\".",
                        fontSize = 12.5.sp,
                        color = Muted,
                    )
                }

                LazyColumn(
                    Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(shown, key = { it.id }) { thread ->
                        ThreadRow(
                            thread = thread,
                            active = thread.id == activeThread,
                            onOpen = { onOpen(thread.id) },
                            onRename = { renaming = thread.id },
                            onPin = { onPin(thread.id, !thread.pinned) },
                            onDelete = { confirmDelete = thread.id },
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Clear this chat", Terracotta) { confirmClear = true }
                    Pill("Close") { onDismiss() }
                }
            }
        }
    }

    if (renaming != 0L) {
        val current = threads.firstOrNull { it.id == renaming }
        RenameDialog(
            initial = current?.title.orEmpty(),
            onConfirm = { onRename(renaming, it); renaming = 0L },
            onDismiss = { renaming = 0L },
        )
    }

    if (confirmDelete != 0L) {
        ConfirmDialog(
            title = "Delete chat?",
            body = "The conversation goes for good. Anything it created — tasks, habits, " +
                "plan blocks — stays exactly where it is.",
            confirmLabel = "Delete",
            onConfirm = { onDelete(confirmDelete); confirmDelete = 0L },
            onDismiss = { confirmDelete = 0L },
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear this chat?",
            body = "Empties the messages but keeps the chat itself. Anything it created stays.",
            confirmLabel = "Clear",
            onConfirm = { onClearCurrent(); confirmClear = false },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun ThreadRow(
    thread: ChatThread,
    active: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (active) SegBg else Color.Transparent, RoundedCornerShape(10.dp))
            .bouncyTap(haptic = TendHaptic.Select, pressedScale = TendMotion.PressScaleLarge, onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                (if (thread.pinned) "📌 " else "") + thread.title,
                fontSize = 12.5.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(dayLabel(thread.updatedAt), fontSize = 10.sp, color = Faint)
        }
        RowAction("📌", onPin)
        RowAction("✎", onRename)
        RowAction("🗑", onDelete)
    }
}

@Composable
private fun RowAction(glyph: String, onClick: () -> Unit) {
    Box(Modifier.tapNoRipple(TendHaptic.Select, onClick).padding(4.dp)) {
        Text(glyph, fontSize = 12.sp, color = Muted)
    }
}

@Composable
private fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Rename chat", fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                DialogInput(name, { name = it }, "Chat name")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Save", Ink) { onConfirm(name) }
                    Pill("Cancel") { onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(body, fontSize = 12.5.sp, color = Muted, lineHeight = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(confirmLabel, Terracotta) { onConfirm() }
                    Pill("Cancel") { onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun Pill(label: String, color: Color = Ink, onClick: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, Border, RoundedCornerShape(99.dp))
            .bouncyTap(haptic = TendHaptic.Select, pressedScale = TendMotion.PressScaleLarge, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

private fun dayLabel(millis: Long): String {
    val day = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> "${day.dayOfMonth} ${day.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}"
    }
}
