package com.tend.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tend.app.ChatSize
import com.tend.app.MainViewModel
import com.tend.app.Tab
import com.tend.app.domain.chat.ChatMode
import com.tend.app.ui.components.tapNoRipple
import com.tend.app.ui.motion.TendHaptic
import com.tend.app.ui.motion.TendMotion
import com.tend.app.ui.motion.bouncyTap
import com.tend.app.ui.theme.Border
import com.tend.app.ui.theme.Card
import com.tend.app.ui.theme.CardBorder
import com.tend.app.ui.theme.ChipText
import com.tend.app.ui.theme.Cream
import com.tend.app.ui.theme.Dashed
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.Ink
import com.tend.app.ui.theme.Muted
import com.tend.app.ui.theme.Scrim
import com.tend.app.ui.theme.SegBg
import com.tend.app.ui.theme.SelectionBorder
import com.tend.app.ui.theme.Sheet
import com.tend.app.ui.theme.SpaceGrotesk
import com.tend.app.ui.theme.Terracotta
import kotlinx.coroutines.launch

/**
 * Ask Tend, compact or full screen.
 *
 * There is one implementation of the chat. The size only decides how the
 * container is drawn and how tall the message list is — the header, list,
 * composer and every action are the same composables reading the same
 * ViewModel state, so expanding cannot lose a draft or drop a feature.
 */
@Composable
fun ChatSurface(vm: MainViewModel) {
    val shell by vm.shell.collectAsStateWithLifecycle()

    when (shell.chatSize) {
        ChatSize.Sheet -> Box(
            Modifier
                .fillMaxSize()
                .background(Scrim)
                // Dismissing by tapping away is a retreat, not an action — no buzz.
                .tapNoRipple(TendHaptic.None) { vm.closeAi() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .tapNoRipple(TendHaptic.None) { /* eat clicks so the scrim doesn't dismiss */ }
                    .background(Sheet, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                    .imePadding(),
            ) {
                Box(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(36.dp).height(4.dp).background(Dashed, RoundedCornerShape(2.dp)))
                }
                ChatBody(vm, Modifier.heightIn(min = 120.dp, max = 340.dp))
            }
        }

        ChatSize.FullScreen -> Column(
            Modifier
                .fillMaxSize()
                .background(Sheet)
                .systemBarsPadding()
                .imePadding(),
        ) {
            ChatBody(vm, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChatBody(vm: MainViewModel, listModifier: Modifier) {
    val shell by vm.shell.collectAsStateWithLifecycle()
    val chat by vm.chat.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val mode by vm.chatMode.collectAsStateWithLifecycle()
    val effectiveMode by vm.effectiveChatMode.collectAsStateWithLifecycle()
    val threads by vm.threads.collectAsStateWithLifecycle()
    val activeThread by vm.activeThread.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var showThreads by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableLongStateOf(0L) }
    var notice by remember { mutableStateOf<String?>(null) }

    val title = threads.firstOrNull { it.id == activeThread }?.title ?: "Tend"

    ChatHeader(
        title = title,
        size = shell.chatSize,
        onToggleSize = { if (shell.chatSize == ChatSize.Sheet) vm.expandChat() else vm.collapseChat() },
        onThreads = { showThreads = true },
        onNewChat = { vm.newChat() },
        onSettings = { vm.closeAi(); vm.selectTab(Tab.Settings) },
        onClose = { vm.closeAi() },
    )

    ModeRow(
        mode = mode,
        effectiveMode = effectiveMode,
        onSelect = { vm.setChatMode(it) },
    )

    HorizontalDivider(color = SegBg, thickness = 1.dp)

    if (selection.isNotEmpty()) {
        SelectionBar(
            count = selection.size,
            onDelete = { vm.deleteSelectedMessages() },
            onCancel = { vm.clearSelection() },
        )
    }

    notice?.let {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            Text(it, fontSize = 11.5.sp, color = Terracotta, lineHeight = 16.sp)
        }
    }

    // Messages
    val listState = rememberLazyListState()
    LaunchedEffect(chat.size, shell.aiThinking) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(chat.lastIndex)
    }
    LazyColumn(
        listModifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(chat, key = { it.message.id }) { msg ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ChatMessageItem(
                    msg = msg,
                    selected = msg.message.id in selection,
                    selectionActive = selection.isNotEmpty(),
                    onTap = { vm.toggleSelected(msg.message.id) },
                    onLongPress = { actionsFor = msg.message.id },
                    onLinkClick = { ref ->
                        scope.launch {
                            notice = if (vm.resolveLink(ref)) null
                            else "That ${ref.type.display.lowercase()} has been deleted."
                        }
                    },
                )
                if (actionsFor == msg.message.id) {
                    MessageActionSheet(
                        inContext = msg.inContext,
                        onAddToContext = { vm.toggleInContext(msg.message.id) },
                        onDelete = { vm.deleteMessage(msg.message.id) },
                        onSelect = { vm.toggleSelected(msg.message.id) },
                        onDismiss = { actionsFor = 0L },
                    )
                }
            }
        }
        if (shell.aiThinking) {
            item(key = "thinking") { ThinkingBubble() }
        }
    }

    // Suggestion chips
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Chip("Plan my morning") { vm.chipPlanMorning() }
        Chip("Add a task") { vm.chipAddTask() }
        Chip("How's my week?") { vm.chipWeekSummary() }
    }

    Composer(
        value = draft,
        onChange = vm::setDraft,
        onSend = { vm.sendAi(draft) },
    )

    if (showThreads) {
        ThreadPickerDialog(
            threads = threads,
            activeThread = activeThread,
            onOpen = { vm.openThread(it); showThreads = false },
            onDelete = { vm.deleteThread(it) },
            onRename = { id, name -> vm.renameThread(id, name) },
            onPin = { id, pinned -> vm.setThreadPinned(id, pinned) },
            onClearCurrent = { vm.clearChat(); showThreads = false },
            onDismiss = { showThreads = false },
        )
    }
}

@Composable
private fun ChatHeader(
    title: String,
    size: ChatSize,
    onToggleSize: () -> Unit,
    onThreads: () -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("✦", fontSize = 16.sp, color = Terracotta)
        Text(
            title,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        HeaderButton("✎", onNewChat)
        HeaderButton("☰", onThreads)
        HeaderButton(if (size == ChatSize.Sheet) "⤢" else "⤡", onToggleSize)
        HeaderButton("⚙", onSettings)
        HeaderButton("✕", onClose)
    }
}

@Composable
private fun HeaderButton(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .background(SegBg, RoundedCornerShape(50))
            .tapNoRipple(TendHaptic.Select, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 12.sp, color = Muted)
    }
}

/**
 * The global mode switch.
 *
 * It shows what will actually happen, not just what is selected: picking AI
 * without a key says so, because a toggle that silently does something else is
 * worse than no toggle.
 */
@Composable
private fun ModeRow(mode: ChatMode, effectiveMode: ChatMode, onSelect: (ChatMode) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth().background(SegBg, RoundedCornerShape(11.dp)).padding(3.dp)) {
            ChatMode.entries.forEach { option ->
                ModeSegment(Modifier.weight(1f), option.label, mode == option) { onSelect(option) }
            }
        }
        if (mode == ChatMode.Ai && effectiveMode == ChatMode.Local) {
            Text(
                "No API key — running offline. Add one in Settings.",
                fontSize = 10.5.sp,
                color = Terracotta,
            )
        }
    }
}

@Composable
private fun ModeSegment(modifier: Modifier, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (active) Cream else Color.Transparent, RoundedCornerShape(9.dp))
            .bouncyTap(haptic = TendHaptic.Select, pressedScale = TendMotion.PressScaleLarge, onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
            color = if (active) Ink else Muted,
        )
    }
}

@Composable
private fun SelectionBar(count: Int, onDelete: () -> Unit, onCancel: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(SelectionBorder.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "$count selected",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Delete",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Terracotta,
            modifier = Modifier.tapNoRipple(TendHaptic.Confirm, onDelete),
        )
        Text(
            "Cancel",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Muted,
            modifier = Modifier.tapNoRipple(TendHaptic.Select, onCancel),
        )
    }
}

@Composable
private fun ThinkingBubble() {
    Box(
        Modifier
            .background(Card, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
            .padding(horizontal = 13.dp, vertical = 10.dp)
    ) {
        Text("…", fontSize = 13.5.sp, color = Ink)
    }
}

@Composable
private fun Composer(value: String, onChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .background(Card, RoundedCornerShape(99.dp))
                .border(1.dp, Border, RoundedCornerShape(99.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = TextStyle(fontSize = 13.5.sp, color = Ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (value.isEmpty()) {
                Text("Add buy groceries at 5pm…", fontSize = 13.5.sp, color = Faint)
            }
        }
        Box(
            Modifier
                .size(44.dp)
                .background(Ink, RoundedCornerShape(50))
                .bouncyTap(haptic = TendHaptic.Confirm) { onSend() },
            contentAlignment = Alignment.Center,
        ) {
            Text("↑", fontSize = 17.sp, color = Cream)
        }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, Border, RoundedCornerShape(99.dp))
            .bouncyTap(haptic = TendHaptic.Select, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ChipText)
    }
}
