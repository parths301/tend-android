package com.tend.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tend.app.ChatMsgUi
import com.tend.app.domain.chat.EntityRef
import com.tend.app.domain.chat.ResponseSource
import com.tend.app.ui.components.AttachmentRow
import com.tend.app.ui.components.tapNoRipple
import com.tend.app.ui.motion.TendHaptic
import com.tend.app.ui.motion.TendMotion
import com.tend.app.ui.motion.bouncyTap
import com.tend.app.ui.theme.Card
import com.tend.app.ui.theme.CardBorder
import com.tend.app.ui.theme.ContextBorder
import com.tend.app.ui.theme.ContextTint
import com.tend.app.ui.theme.Cream
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.Ink
import com.tend.app.ui.theme.LinkChipBg
import com.tend.app.ui.theme.LinkChipBorder
import com.tend.app.ui.theme.Muted
import com.tend.app.ui.theme.SelectionBorder
import com.tend.app.ui.theme.SelectionTint
import com.tend.app.ui.theme.Terracotta

/**
 * One message, in whichever of its states applies.
 *
 * A model-written message gets no tint of its own — it sits on the ordinary
 * card surface and is marked by its "AI"/"OFFLINE" tag alone. The two tints
 * that remain are mutually exclusive and checked in a fixed order — selection
 * wins over context — so a tint always means "this message is marked", and a
 * message can never show two meanings at once.
 */
@Composable
fun ChatMessageItem(
    msg: ChatMsgUi,
    selected: Boolean,
    selectionActive: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onLinkClick: (EntityRef) -> Unit,
) {
    val generated = msg.fromAi && msg.source != ResponseSource.System
    val shape = if (msg.fromAi) {
        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
    }

    val background = when {
        selected -> SelectionTint
        msg.inContext -> ContextTint
        msg.fromAi -> Card
        else -> Ink
    }
    val outline = when {
        selected -> SelectionBorder
        msg.inContext -> ContextBorder
        msg.fromAi -> CardBorder
        else -> Color.Transparent
    }
    val textColor = if (msg.fromAi || selected || msg.inContext) Ink else Cream

    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .align(if (msg.fromAi) Alignment.CenterStart else Alignment.CenterEnd)
                .widthIn(max = 320.dp)
                .background(background, shape)
                .border(1.dp, outline, shape)
                // A tap selects only once selection mode is on, so ordinary
                // reading never trips a bulk action.
                .bouncyTap(
                    haptic = if (selectionActive) TendHaptic.Select else TendHaptic.None,
                    pressedScale = TendMotion.PressScaleLarge,
                    onLongClick = onLongPress,
                    onClick = { if (selectionActive) onTap() },
                )
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (generated || msg.inContext) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (generated) MessageTag(msg.source.tagLabel, Muted)
                    if (msg.inContext) MessageTag("IN CONTEXT", ContextBorder)
                }
            }

            Text(
                msg.text,
                fontSize = 13.5.sp,
                lineHeight = 19.5.sp,
                color = textColor,
            )

            // Same rendering primitive as memory and the detail screens, so an
            // attachment looks and behaves identically wherever it appears.
            msg.attachments.forEach { AttachmentRow(it) }

            // Chips come from message_links, never from reading the text, so a
            // chip always points at a row that was really created.
            msg.links.forEach { ref ->
                LinkedEntityChip(ref) { onLinkClick(ref) }
            }
        }
    }
}

/** The "AI" / "OFFLINE" marker required on anything a model produced. */
@Composable
private fun MessageTag(label: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(5.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    ) {
        Text(
            label,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.7.sp,
            color = color,
        )
    }
}

private val ResponseSource.tagLabel: String
    get() = when (this) {
        ResponseSource.Cloud -> "AI"
        ResponseSource.Local -> "OFFLINE"
        ResponseSource.System -> ""
    }

/**
 * A tappable pointer to something the message created. Rendered as a card-like
 * chip rather than styled text so it reads as interactive at a glance.
 */
@Composable
fun LinkedEntityChip(ref: EntityRef, onClick: () -> Unit) {
    Row(
        Modifier
            .background(LinkChipBg, RoundedCornerShape(9.dp))
            .border(1.dp, LinkChipBorder, RoundedCornerShape(9.dp))
            .bouncyTap(haptic = TendHaptic.Select, pressedScale = TendMotion.PressScaleLarge, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            ref.type.display.uppercase(),
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.6.sp,
            color = Muted,
        )
        Text(
            ref.label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Ink,
        )
        Text("›", fontSize = 13.sp, color = Faint)
    }
}

/** Row of actions on a single message, shown from a long press. */
@Composable
fun MessageActionSheet(
    inContext: Boolean,
    onAddToContext: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(12.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ActionText(if (inContext) "Remove from context" else "Add to AI context", Modifier.weight(1f)) {
            onAddToContext(); onDismiss()
        }
        ActionText("Select", Modifier.weight(1f)) { onSelect(); onDismiss() }
        ActionText("Delete", Modifier.weight(1f), Terracotta) { onDelete(); onDismiss() }
    }
}

@Composable
private fun ActionText(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = Ink,
    onClick: () -> Unit,
) {
    Box(
        modifier.tapNoRipple(TendHaptic.Select, onClick = onClick).padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}
