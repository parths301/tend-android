package com.tend.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tend.app.MainViewModel
import com.tend.app.domain.chat.AdvancedConfig
import com.tend.app.domain.chat.Personality
import com.tend.app.ui.components.DialogInput
import com.tend.app.ui.components.TendCard
import com.tend.app.ui.components.tapNoRipple
import com.tend.app.ui.motion.TendHaptic
import com.tend.app.ui.motion.TendMotion
import com.tend.app.ui.motion.bouncyTap
import com.tend.app.ui.theme.Border
import com.tend.app.ui.theme.Card
import com.tend.app.ui.theme.Cream
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.Ink
import com.tend.app.ui.theme.Muted
import com.tend.app.ui.theme.SegBg
import com.tend.app.ui.theme.SpaceGrotesk
import com.tend.app.ui.theme.Teal
import com.tend.app.ui.theme.Terracotta

/**
 * Raw prompt and JSON config.
 *
 * Both are validated before they can be saved and both have a reset, because
 * the failure this section could otherwise cause — a config that breaks every
 * message with no obvious way back — is worse than not having the section.
 */
@Composable
fun AdvancedSettingsCard(vm: MainViewModel) {
    val storedPrompt by vm.customPrompt.collectAsStateWithLifecycle()
    val storedJson by vm.advancedJson.collectAsStateWithLifecycle()

    var promptOpen by remember { mutableStateOf(false) }
    var jsonOpen by remember { mutableStateOf(false) }

    TendCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Edit the exact instructions sent to the model, and the numbers the " +
                    "pipeline runs on. Both are checked before they save, and both can be " +
                    "reset to the shipped defaults.",
                fontSize = 12.5.sp, color = Muted, lineHeight = 18.sp,
            )

            SettingRow(
                label = "System prompt",
                value = if (storedPrompt.isBlank()) "Default" else "Customised",
                customised = storedPrompt.isNotBlank(),
            ) { promptOpen = true }

            SettingRow(
                label = "JSON config",
                value = if (storedJson.isBlank()) "Default" else "Customised",
                customised = storedJson.isNotBlank(),
            ) { jsonOpen = true }
        }
    }

    if (promptOpen) {
        PromptEditor(
            initial = storedPrompt.ifBlank { AdvancedConfig.defaultPrompt() },
            isCustom = storedPrompt.isNotBlank(),
            onSave = { vm.setCustomPrompt(it); promptOpen = false },
            onReset = { vm.resetCustomPrompt(); promptOpen = false },
            onDismiss = { promptOpen = false },
        )
    }

    if (jsonOpen) {
        JsonEditor(
            initial = storedJson.ifBlank { AdvancedConfig.DEFAULTS.toPrettyJson() },
            validate = { AdvancedConfig.validate(it) },
            onSave = { vm.saveAdvancedJson(it) },
            onSaved = { jsonOpen = false },
            onReset = { vm.resetAdvancedJson(); jsonOpen = false },
            onDismiss = { jsonOpen = false },
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String, customised: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .bouncyTap(haptic = TendHaptic.Select, pressedScale = TendMotion.PressScaleLarge, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink, modifier = Modifier.weight(1f))
        Text(
            value,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (customised) Teal else Faint,
        )
        Text("  ›", fontSize = 13.sp, color = Muted)
    }
}

@Composable
private fun PromptEditor(
    initial: String,
    isCustom: Boolean,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("System prompt", fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Sent verbatim. Use ${AdvancedConfig.STATE_PLACEHOLDER} where your habits, " +
                        "tasks and plan should be spliced in — if you leave it out, they're " +
                        "appended at the end rather than dropped.",
                    fontSize = 11.5.sp, color = Muted, lineHeight = 16.sp,
                )
                CodeField(text) { text = it }
                Text(
                    "The reply must still be JSON in the documented shape, or Tend can't apply " +
                        "what the model asks for.",
                    fontSize = 11.sp, color = Terracotta, lineHeight = 15.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Filled("Save") { onSave(text) }
                    if (isCustom) Outlined("Reset to default", onReset)
                    Outlined("Cancel", onDismiss)
                }
            }
        }
    }
}

@Composable
private fun JsonEditor(
    initial: String,
    validate: (String) -> AdvancedConfig.Validation,
    onSave: (String) -> String?,
    onSaved: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    // Validate as they type so the error appears next to the mistake, not after
    // pressing Save and losing the thread of what changed.
    val liveError = (validate(text) as? AdvancedConfig.Validation.Invalid)?.message

    Dialog(onDismissRequest = onDismiss) {
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("JSON config", fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "historyWindow (1–200) messages of context in AI mode · localThinkingMs " +
                        "(0–5000) the offline pause · temperature (0–2).",
                    fontSize = 11.5.sp, color = Muted, lineHeight = 16.sp,
                )
                CodeField(text) { text = it; error = null }

                (error ?: liveError)?.let {
                    Text(it, fontSize = 11.5.sp, color = Terracotta, lineHeight = 16.sp)
                }
                if (liveError == null && error == null) {
                    Text("Valid.", fontSize = 11.5.sp, color = Teal)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Filled("Save", enabled = liveError == null) {
                        val failure = onSave(text)
                        if (failure == null) onSaved() else error = failure
                    }
                    Outlined("Reset to default", onReset)
                    Outlined("Cancel", onDismiss)
                }
            }
        }
    }
}

@Composable
private fun CodeField(value: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 260.dp)
            .background(SegBg, RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .verticalScroll(rememberScrollState())
            .padding(10.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(fontSize = 11.5.sp, fontFamily = FontFamily.Monospace, color = Ink),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Personality profiles.
 *
 * Selecting one changes both AI and offline replies — offline can only honour
 * brevity, but it honours it, so the setting is never inert.
 */
@Composable
fun PersonalityCard(vm: MainViewModel) {
    val profiles by vm.personalities.collectAsStateWithLifecycle()
    val active by vm.activePersonality.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Personality?>(null) }

    TendCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Sets how replies read. Applies in both modes — offline can only act on " +
                    "brevity, but it does act on it.",
                fontSize = 12.5.sp, color = Muted, lineHeight = 18.sp,
            )

            profiles.forEach { profile ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (profile.id == active.id) Teal.copy(alpha = 0.10f) else Color.Transparent,
                            RoundedCornerShape(10.dp),
                        )
                        .bouncyTap(haptic = TendHaptic.Select, pressedScale = TendMotion.PressScaleLarge) {
                            vm.selectPersonality(profile.id)
                        }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile.name,
                            fontSize = 12.5.sp,
                            fontWeight = if (profile.id == active.id) FontWeight.Bold else FontWeight.Medium,
                            color = if (profile.id == active.id) Teal else Ink,
                        )
                        Text(
                            profile.tone.ifBlank { "Tend's default voice" },
                            fontSize = 10.5.sp, color = Faint,
                        )
                    }
                    MiniAction("Copy") { vm.duplicatePersonality(profile) }
                    if (!profile.builtIn) {
                        MiniAction("Edit") { editing = profile }
                        MiniAction("Delete", Terracotta) { vm.deletePersonality(profile.id) }
                    }
                }
            }

            Outlined("New personality") {
                editing = Personality(id = System.currentTimeMillis(), name = "New personality")
            }
        }
    }

    editing?.let { profile ->
        PersonalityEditor(
            profile = profile,
            onSave = { vm.savePersonality(it); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun PersonalityEditor(
    profile: Personality,
    onSave: (Personality) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(profile.name) }
    var tone by remember { mutableStateOf(profile.tone) }
    var traits by remember { mutableStateOf(profile.traits.joinToString(", ")) }
    var notes by remember { mutableStateOf(profile.notes) }

    Dialog(onDismissRequest = onDismiss) {
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Personality", fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                DialogInput(name, { name = it }, "Name")
                DialogInput(tone, { tone = it }, "Tone, e.g. warm and direct")
                DialogInput(traits, { traits = it }, "Traits, comma separated")
                DialogInput(notes, { notes = it }, "Behaviour notes")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Filled("Save", enabled = name.isNotBlank()) {
                        onSave(
                            profile.copy(
                                name = name.trim(),
                                tone = tone.trim(),
                                traits = traits.split(",").map(String::trim).filter(String::isNotEmpty),
                                notes = notes.trim(),
                                builtIn = false,
                            )
                        )
                    }
                    Outlined("Cancel", onDismiss)
                }
            }
        }
    }
}

@Composable
private fun MiniAction(label: String, color: Color = Muted, onClick: () -> Unit) {
    Box(Modifier.tapNoRipple(TendHaptic.Select, onClick).padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text(label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun Filled(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (enabled) Ink else Faint, RoundedCornerShape(99.dp))
            .bouncyTap(haptic = TendHaptic.Confirm, enabled = enabled, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cream)
    }
}

@Composable
private fun Outlined(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, Border, RoundedCornerShape(99.dp))
            .bouncyTap(haptic = TendHaptic.Select, pressedScale = TendMotion.PressScaleLarge, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}
