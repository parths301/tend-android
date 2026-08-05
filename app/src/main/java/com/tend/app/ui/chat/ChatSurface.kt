package com.tend.app.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tend.app.ChatSize
import com.tend.app.MainViewModel
import com.tend.app.Tab
import com.tend.app.domain.chat.ChatMode
import com.tend.app.ui.components.AttachmentStrip
import com.tend.app.ui.components.tapNoRipple
import com.tend.app.ui.motion.LocalReduceMotion
import com.tend.app.ui.motion.LocalTendHaptics
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
    val reduceMotion = LocalReduceMotion.current

    val expansion by animateFloatAsState(
        targetValue = if (shell.chatSize == ChatSize.FullScreen) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else TendMotion.Progress,
        label = "chatExpansion",
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Scrim.copy(alpha = Scrim.alpha * (1f - expansion)))
            // Dismissing by tapping away is a retreat, not an action — no buzz.
            .tapNoRipple(TendHaptic.None) { vm.closeAi() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val density = LocalDensity.current
        val maxHeightPx = with(density) { maxHeight.toPx() }
        var restingHeightPx by remember { mutableFloatStateOf(0f) }
        val restingFraction = if (maxHeightPx > 0f) (restingHeightPx / maxHeightPx).coerceIn(0f, 1f) else 0f
        val corner = 26.dp * (1f - expansion)
        val insets = WindowInsets.systemBars.asPaddingValues()
        val atRest = expansion == 0f

        Column(
            Modifier
                .fillMaxWidth()
                .tapNoRipple(TendHaptic.None) { /* eat clicks so the scrim doesn't dismiss */ }
                .then(
                    if (atRest) Modifier.wrapContentHeight()
                    else Modifier.fillMaxHeight(lerp(restingFraction, 1f, expansion).coerceIn(0f, 1f))
                )
                .background(Sheet, RoundedCornerShape(topStart = corner, topEnd = corner))
                .padding(
                    top = insets.calculateTopPadding() * expansion,
                    bottom = insets.calculateBottomPadding() * expansion,
                )
                .then(
                    if (atRest) Modifier.onSizeChanged { restingHeightPx = it.height.toFloat() }
                    else Modifier
                )
                .imePadding(),
        ) {
            DragHandle(
                chatSize = shell.chatSize,
                onExpand = vm::expandChat,
                onCollapse = vm::collapseChat,
            )
            ChatBody(vm, if (atRest) Modifier.heightIn(min = 120.dp, max = 340.dp) else Modifier.weight(1f))
        }
    }
}

/**
 * The drag-handle pill, doubling as the expand/collapse control.
 *
 * A swipe is decided once, at release, against a fixed threshold — the same
 * discrete pattern as the tab swipe in `TendApp` — rather than tracked live,
 * because [ChatSurface]'s expansion is state-driven: a drag that never
 * crosses the threshold has nothing to snap back, only the pill's own
 * press/drag feedback does.
 */
@Composable
private fun DragHandle(chatSize: ChatSize, onExpand: () -> Unit, onCollapse: () -> Unit) {
    val haptics = LocalTendHaptics.current
    val reduceMotion = LocalReduceMotion.current
    var dragging by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (dragging && !reduceMotion) TendMotion.PopScale else 1f,
        animationSpec = if (dragging) TendMotion.PressDown else TendMotion.PressRelease,
        label = "dragHandleScale",
    )

    Box(
        Modifier
            .fillMaxWidth()
            // Before the padding, so the whole 24dp band is grabbable, not just the pill.
            .pointerInput(chatSize) {
                val thresholdPx = 48.dp.toPx()
                var dragY = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragY = 0f; dragging = true },
                    onDragEnd = {
                        dragging = false
                        when {
                            chatSize == ChatSize.Sheet && dragY < -thresholdPx -> {
                                haptics.perform(TendHaptic.Select)
                                onExpand()
                            }
                            chatSize == ChatSize.FullScreen && dragY > thresholdPx -> {
                                haptics.perform(TendHaptic.Select)
                                onCollapse()
                            }
                        }
                    },
                    onDragCancel = { dragging = false; dragY = 0f },
                    onVerticalDrag = { _, amount -> dragY += amount },
                )
            }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(36.dp)
                .height(4.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .background(Dashed, RoundedCornerShape(2.dp))
        )
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
    val pending by vm.pendingAttachments.collectAsStateWithLifecycle()

    // OpenDocument rather than GetContent: only the former can be granted a
    // persistable permission, and without that an attachment stops resolving
    // after a reboot.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.attachToDraft(it) }
    }

    val scope = rememberCoroutineScope()
    var showThreads by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableLongStateOf(0L) }
    var notice by remember { mutableStateOf<String?>(null) }

    val title = threads.firstOrNull { it.id == activeThread }?.title ?: "Tend"

    ChatHeader(
        title = title,
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

    AttachmentStrip(pending) { vm.removePendingAttachment(it) }

    Composer(
        value = draft,
        onChange = vm::setDraft,
        onAttach = { picker.launch(arrayOf("*/*")) },
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
private fun Composer(
    value: String,
    onChange: (String) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(SegBg, RoundedCornerShape(50))
                .bouncyTap(haptic = TendHaptic.Select, onClick = onAttach),
            contentAlignment = Alignment.Center,
        ) {
            Text("＋", fontSize = 16.sp, color = Muted)
        }
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
