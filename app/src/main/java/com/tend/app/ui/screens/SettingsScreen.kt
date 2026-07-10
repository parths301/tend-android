package com.tend.app.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tend.app.MainViewModel
import com.tend.app.Tab
import com.tend.app.data.SettingsRepository
import com.tend.app.ui.components.Kicker
import com.tend.app.ui.components.ScreenTitle
import com.tend.app.ui.components.TendCard
import com.tend.app.ui.components.tapNoRipple
import com.tend.app.ui.theme.Border
import com.tend.app.ui.theme.Card
import com.tend.app.ui.theme.Cream
import com.tend.app.ui.theme.Faint
import com.tend.app.ui.theme.Ink
import com.tend.app.ui.theme.Muted
import com.tend.app.ui.theme.RowDivider
import com.tend.app.ui.theme.SegBg
import com.tend.app.ui.theme.SpaceGrotesk
import com.tend.app.ui.theme.Teal
import com.tend.app.ui.theme.Terracotta

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val weeks by vm.heatmapWeeks.collectAsStateWithLifecycle()
    val showAiBar by vm.showAiBar.collectAsStateWithLifecycle()
    val provider by vm.provider.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()
    val savedKey by vm.apiKey.collectAsStateWithLifecycle()
    val modelsUi by vm.models.collectAsStateWithLifecycle()

    // Load the model list as soon as a key is available.
    LaunchedEffect(provider, savedKey) {
        if (savedKey.isNotEmpty() && modelsUi.models.isEmpty() && !modelsUi.loading && modelsUi.error == null) {
            vm.refreshModels()
        }
    }

    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                Modifier
                    .size(34.dp)
                    .border(1.dp, Border, RoundedCornerShape(50))
                    .tapNoRipple { vm.selectTab(Tab.Today) },
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", fontSize = 18.sp)
            }
            Column {
                Kicker("TEND")
                ScreenTitle("Settings")
            }
        }

        // ── Ask Tend / BYOK ─────────────────────────────
        SectionLabel("ASK TEND · BRING YOUR OWN KEY")
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Pick a provider and paste its API key to power \"Ask Tend\". " +
                        "Available models load automatically. Without a key, Tend uses a " +
                        "built-in offline assistant. Keys are stored encrypted on this device only.",
                    fontSize = 12.5.sp, color = Muted, lineHeight = 18.sp,
                )

                FieldLabel("Provider")
                Row(Modifier.background(SegBg, RoundedCornerShape(12.dp)).padding(3.dp)) {
                    ProviderButton(
                        Modifier.weight(1f), "Gemini",
                        provider == SettingsRepository.PROVIDER_GEMINI,
                    ) { vm.setProvider(SettingsRepository.PROVIDER_GEMINI) }
                    ProviderButton(
                        Modifier.weight(1f), "Claude",
                        provider == SettingsRepository.PROVIDER_ANTHROPIC,
                    ) { vm.setProvider(SettingsRepository.PROVIDER_ANTHROPIC) }
                }

                var keyInput by rememberSaveable(provider, savedKey) { mutableStateOf(savedKey) }
                FieldLabel("${SettingsRepository.providerLabel(provider)} API key")
                InputBox(
                    value = keyInput,
                    onChange = { keyInput = it },
                    placeholder = SettingsRepository.keyPlaceholder(provider),
                    visualTransformation = if (keyInput == savedKey && savedKey.isNotEmpty())
                        PasswordVisualTransformation() else VisualTransformation.None,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .background(Ink, RoundedCornerShape(99.dp))
                            .tapNoRipple { vm.setApiKey(keyInput) }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text("Save key", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cream)
                    }
                    if (savedKey.isNotEmpty()) {
                        Box(
                            Modifier
                                .border(1.dp, Border, RoundedCornerShape(99.dp))
                                .tapNoRipple { vm.setApiKey("") }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            Text("Remove key", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted)
                        }
                    }
                }

                // ── model picker — appears once a key is saved ──
                if (savedKey.isNotEmpty()) {
                    HorizontalDivider(color = RowDivider, thickness = 1.dp)
                    FieldLabel("Model")
                    when {
                        modelsUi.loading -> Text("Fetching models…", fontSize = 12.5.sp, color = Faint)
                        modelsUi.error != null -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Couldn't load models: ${modelsUi.error}",
                                    fontSize = 12.sp, color = Terracotta, lineHeight = 17.sp,
                                )
                                Box(
                                    Modifier
                                        .border(1.dp, Border, RoundedCornerShape(99.dp))
                                        .tapNoRipple { vm.refreshModels() }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                                }
                            }
                        }
                        modelsUi.models.isEmpty() ->
                            Text("No models available for this key.", fontSize = 12.5.sp, color = Faint)
                        else -> Column {
                            modelsUi.models.forEach { m ->
                                ModelRow(name = m, selected = m == model) { vm.setModel(m) }
                            }
                        }
                    }
                    Text(
                        "✓ Ask Tend is using ${SettingsRepository.providerLabel(provider)} ($model)",
                        fontSize = 11.5.sp, color = Teal,
                    )
                } else {
                    Text(
                        "No key — Ask Tend runs in offline mode",
                        fontSize = 11.5.sp, color = Faint,
                    )
                }
            }
        }

        // ── Appearance ──────────────────────────────────
        SectionLabel("APPEARANCE")
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Heatmap width", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "$weeks wk", fontFamily = SpaceGrotesk, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, color = Terracotta,
                        )
                    }
                    Slider(
                        value = weeks.toFloat(),
                        onValueChange = { vm.setHeatmapWeeks(it.toInt()) },
                        valueRange = 8f..17f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = Ink,
                            activeTrackColor = Ink,
                            inactiveTrackColor = SegBg,
                        ),
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Show AI bar", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text("\"Ask Tend\" pill above the tab bar", fontSize = 11.5.sp, color = Faint)
                    }
                    Switch(
                        checked = showAiBar,
                        onCheckedChange = { vm.setShowAiBar(it) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Ink,
                            checkedThumbColor = Cream,
                        ),
                    )
                }
            }
        }

        // ── Widgets ─────────────────────────────────────
        SectionLabel("HOME SCREEN WIDGETS")
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Add Tend widgets from your launcher", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Long-press your home screen → Widgets → Tend. " +
                        "\"Today\" shows your habits with one-tap check-off; " +
                        "\"Streak\" tracks your best running streak.",
                    fontSize = 12.sp, color = Muted, lineHeight = 17.sp,
                )
            }
        }
    }
}

@Composable
private fun ProviderButton(modifier: Modifier, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (selected) Card else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(9.dp))
            .tapNoRipple(onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Ink else Faint,
        )
    }
}

@Composable
private fun ModelRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tapNoRipple(onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .then(
                    if (selected) Modifier.background(Ink, RoundedCornerShape(50))
                    else Modifier.border(1.5.dp, Border, RoundedCornerShape(50))
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Text("✓", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Cream)
        }
        Text(
            name,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Ink else Muted,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp, color = Faint,
        modifier = Modifier.padding(start = 2.dp),
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Muted)
}

@Composable
private fun InputBox(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(13.dp))
            .border(1.dp, Border, RoundedCornerShape(13.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            textStyle = TextStyle(fontSize = 13.sp, color = Ink),
            visualTransformation = visualTransformation,
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isEmpty()) {
            Text(placeholder, fontSize = 13.sp, color = Faint)
        }
    }
}
