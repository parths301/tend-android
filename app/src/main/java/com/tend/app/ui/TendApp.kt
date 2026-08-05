package com.tend.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tend.app.Celebration
import com.tend.app.MainViewModel
import com.tend.app.Tab
import com.tend.app.ui.motion.CelebrationOverlay
import com.tend.app.ui.motion.CelebrationStyle
import com.tend.app.ui.motion.TendHaptic
import com.tend.app.ui.motion.TendMotion
import com.tend.app.ui.motion.bouncyTap
import com.tend.app.ui.screens.AiSheet
import com.tend.app.ui.screens.DetailScreen
import com.tend.app.ui.screens.PlanScreen
import com.tend.app.ui.screens.SettingsScreen
import com.tend.app.ui.screens.StatsScreen
import com.tend.app.ui.screens.TasksScreen
import com.tend.app.ui.screens.TodayScreen
import com.tend.app.ui.theme.CardBorder
import com.tend.app.ui.theme.Cream
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.Ink
import com.tend.app.ui.theme.TerracottaLight
import kotlin.math.abs

@Composable
fun TendApp(vm: MainViewModel = viewModel()) {
    val shell by vm.shell.collectAsStateWithLifecycle()
    val showAiBar by vm.showAiBar.collectAsStateWithLifecycle()

    // Celebrations arrive as one-shot events and are hosted at the shell, above
    // every tab, so the moment survives the user navigating away mid-animation.
    var celebration by remember { mutableStateOf<Celebration?>(null) }
    LaunchedEffect(Unit) {
        vm.celebrations.collect { celebration = it }
    }

    // Back closes overlays / detail screens before exiting the app.
    BackHandler(enabled = shell.aiOpen || shell.tab == Tab.Detail || shell.tab == Tab.Settings) {
        if (shell.aiOpen) vm.closeAi() else vm.selectTab(Tab.Today)
    }

    // Notification deep links (e.g. nightly check-in → tomorrow's plan).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.consumeDeepLink()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Cream)
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            // Content area — horizontal swipe switches the four main tabs
            Box(
                Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        var dragX = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { dragX = 0f },
                            onDragEnd = {
                                if (abs(dragX) > 70f) vm.swipe(if (dragX < 0) 1 else -1)
                            },
                            onDragCancel = { dragX = 0f },
                            onHorizontalDrag = { _, amount -> dragX += amount },
                        )
                    }
            ) {
                key(shell.tab) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (shell.tab) {
                            Tab.Today -> TodayScreen(vm)
                            Tab.Plan -> PlanScreen(vm)
                            Tab.Tasks -> TasksScreen(vm)
                            Tab.Stats -> StatsScreen(vm)
                            Tab.Detail -> DetailScreen(vm)
                            Tab.Settings -> SettingsScreen(vm)
                        }
                    }
                }
            }

            val aiBarVisible = showAiBar && shell.tab != Tab.Detail && shell.tab != Tab.Settings
            if (aiBarVisible) {
                AiBar { vm.openAi() }
            }

            NavBar(current = shell.tab, onSelect = vm::selectTab)
        }

        if (shell.aiOpen) {
            AiSheet(vm)
        }

        celebration?.let { moment ->
            CelebrationOverlay(
                headline = moment.headline,
                detail = moment.detail,
                style = when (moment.kind) {
                    Celebration.Kind.DayComplete -> CelebrationStyle.Burst
                    Celebration.Kind.StreakMilestone -> CelebrationStyle.Ring
                },
                onDismiss = { celebration = null },
            )
        }
    }
}

@Composable
private fun AiBar(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().background(Cream).padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Ink, RoundedCornerShape(99.dp))
                .bouncyTap(pressedScale = TendMotion.PressScaleLarge, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("✦", fontSize = 15.sp, color = TerracottaLight)
            Text(
                "Ask Tend — add habits, tasks, plans…",
                fontSize = 13.5.sp,
                color = Color(0xBFF5F1E8),
                modifier = Modifier.weight(1f),
            )
            Text("swipe ⇄ tabs", fontSize = 12.sp, color = Color(0x66F5F1E8))
        }
    }
}

@Composable
private fun NavBar(current: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Cream)) {
        HorizontalDivider(color = CardBorder, thickness = 1.dp)
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 4.dp)) {
            NavItem(Modifier.weight(1f), "Today", current == Tab.Today, { onSelect(Tab.Today) }) { color, active ->
                Box(
                    Modifier
                        .size(13.dp)
                        .then(if (active) Modifier.background(color, CircleShape) else Modifier)
                        .border(2.5.dp, color, CircleShape)
                )
            }
            NavItem(Modifier.weight(1f), "Plan", current == Tab.Plan, { onSelect(Tab.Plan) }) { color, _ ->
                Box(Modifier.size(13.dp).border(2.5.dp, color, RoundedCornerShape(4.dp)))
            }
            NavItem(Modifier.weight(1f), "Tasks", current == Tab.Tasks, { onSelect(Tab.Tasks) }) { color, _ ->
                Box(
                    Modifier.size(13.dp).border(2.5.dp, color, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", fontSize = 6.sp, fontWeight = FontWeight.Black, color = color)
                }
            }
            NavItem(Modifier.weight(1f), "Stats", current == Tab.Stats, { onSelect(Tab.Stats) }) { color, _ ->
                Row(
                    Modifier.height(13.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box(Modifier.width(3.dp).height(7.dp).background(color, RoundedCornerShape(1.5.dp)))
                    Box(Modifier.width(3.dp).height(13.dp).background(color, RoundedCornerShape(1.5.dp)))
                    Box(Modifier.width(3.dp).height(10.dp).background(color, RoundedCornerShape(1.5.dp)))
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    modifier: Modifier,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    icon: @Composable (Color, Boolean) -> Unit,
) {
    val color = if (active) Ink else Faint
    Column(
        modifier
            .bouncyTap(haptic = TendHaptic.Select, pressedScale = TendMotion.PressScaleLarge, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        icon(color, active)
        Text(
            label, fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
            color = color,
        )
    }
}
