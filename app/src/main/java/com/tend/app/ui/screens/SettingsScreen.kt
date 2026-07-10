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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.tend.app.ui.theme.SegBg
import com.tend.app.ui.theme.SpaceGrotesk
import com.tend.app.ui.theme.Terracotta

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val weeks by vm.heatmapWeeks.collectAsStateWithLifecycle()
    val showAiBar by vm.showAiBar.collectAsStateWithLifecycle()
    val model by vm.model.collectAsStateWithLifecycle()
    val savedKey by vm.settings.apiKey.collectAsStateWithLifecycle()

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
                    "Add your Anthropic API key to power \"Ask Tend\" with Claude. " +
                        "Without a key, Tend uses a built-in offline assistant. " +
                        "The key is stored encrypted on this device only.",
                    fontSize = 12.5.sp, color = Muted, lineHeight = 18.sp,
                )

                var keyInput by rememberSaveable(savedKey) { mutableStateOf(savedKey) }
                FieldLabel("Anthropic API key")
                InputBox(
                    value = keyInput,
                    onChange = { keyInput = it },
                    placeholder = "sk-ant-…",
                    visualTransformation = if (keyInput == savedKey && savedKey.isNotEmpty())
                        PasswordVisualTransformation() else VisualTransformation.None,
                )

                var modelInput by rememberSaveable(model) { mutableStateOf(model) }
                FieldLabel("Model")
                InputBox(value = modelInput, onChange = { modelInput = it }, placeholder = "claude-opus-4-8")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .background(Ink, RoundedCornerShape(99.dp))
                            .tapNoRipple {
                                vm.setApiKey(keyInput)
                                vm.setModel(modelInput)
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cream)
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
                Text(
                    if (savedKey.isNotEmpty()) "✓ Key saved — Ask Tend is using Claude ($model)"
                    else "No key — Ask Tend runs in offline mode",
                    fontSize = 11.5.sp,
                    color = if (savedKey.isNotEmpty()) com.tend.app.ui.theme.Teal else Faint,
                )
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
