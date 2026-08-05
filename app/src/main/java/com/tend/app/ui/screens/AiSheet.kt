package com.tend.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tend.app.ChatMsg
import com.tend.app.MainViewModel
import com.tend.app.Tab
import com.tend.app.ui.components.tapNoRipple
import com.tend.app.ui.motion.TendHaptic
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
import com.tend.app.ui.theme.Sheet
import com.tend.app.ui.theme.SpaceGrotesk
import com.tend.app.ui.theme.Terracotta

@Composable
fun AiSheet(vm: MainViewModel) {
    val chat by vm.chat.collectAsStateWithLifecycle()
    val shell by vm.shell.collectAsStateWithLifecycle()

    Box(
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
            // Drag handle
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).background(Dashed, RoundedCornerShape(2.dp)))
            }

            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text("✦", fontSize = 16.sp, color = Terracotta)
                Text(
                    "Tend", fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, modifier = Modifier.weight(1f),
                )
                // Settings entry point (BYOK etc.)
                Box(
                    Modifier
                        .size(28.dp)
                        .background(SegBg, RoundedCornerShape(50))
                        .tapNoRipple { vm.closeAi(); vm.selectTab(Tab.Settings) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⚙", fontSize = 13.sp, color = Muted)
                }
                Box(
                    Modifier
                        .size(28.dp)
                        .background(SegBg, RoundedCornerShape(50))
                        .tapNoRipple { vm.closeAi() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", fontSize = 12.sp, color = Muted)
                }
            }
            androidx.compose.material3.HorizontalDivider(color = SegBg, thickness = 1.dp)

            // Messages
            val scroll = rememberScrollState()
            LaunchedEffect(chat.size, shell.aiThinking) { scroll.scrollTo(scroll.maxValue) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 340.dp)
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                chat.forEach { Bubble(it) }
                if (shell.aiThinking) {
                    Bubble(ChatMsg(true, "…"))
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

            // Input row
            var input by remember { mutableStateOf("") }
            fun send() {
                if (input.isNotBlank()) {
                    vm.sendAi(input)
                    input = ""
                }
            }
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
                        value = input,
                        onValueChange = { input = it },
                        textStyle = TextStyle(fontSize = 13.5.sp, color = Ink),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (input.isEmpty()) {
                        Text("Add buy groceries at 5pm…", fontSize = 13.5.sp, color = Faint)
                    }
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .background(Ink, RoundedCornerShape(50))
                        .bouncyTap(haptic = TendHaptic.Confirm) { send() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("↑", fontSize = 17.sp, color = Cream)
                }
            }
        }
    }
}

@Composable
private fun Bubble(msg: ChatMsg) {
    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .align(if (msg.fromAi) Alignment.CenterStart else Alignment.CenterEnd)
                .widthIn(max = 300.dp)
                .then(
                    if (msg.fromAi) {
                        Modifier
                            .background(Card, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                            .border(1.dp, CardBorder, RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                    } else {
                        Modifier.background(Ink, RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp))
                    }
                )
                .padding(horizontal = 13.dp, vertical = 10.dp),
        ) {
            Text(
                msg.text,
                fontSize = 13.5.sp,
                lineHeight = 19.5.sp,
                color = if (msg.fromAi) Ink else Cream,
            )
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
