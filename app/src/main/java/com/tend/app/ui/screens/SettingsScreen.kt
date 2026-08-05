package com.tend.app.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tend.app.MainViewModel
import com.tend.app.PendingRestore
import com.tend.app.Tab
import com.tend.app.data.SettingsRepository
import com.tend.app.data.backup.BackupFormat
import com.tend.app.data.backup.BackupManager
import com.tend.app.domain.Time
import com.tend.app.ui.components.Kicker
import com.tend.app.ui.components.ScreenTitle
import com.tend.app.ui.components.Stepper
import com.tend.app.ui.components.TendCard
import com.tend.app.ui.components.TimeStepperRow
import com.tend.app.ui.components.tapNoRipple
import com.tend.app.ui.motion.LocalTendHaptics
import com.tend.app.ui.motion.TendHaptic
import com.tend.app.ui.motion.bouncyTap
import com.tend.app.ui.motion.shakeOnError
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
    val notificationsEnabled by vm.notificationsEnabled.collectAsStateWithLifecycle()
    val checkinEnabled by vm.checkinEnabled.collectAsStateWithLifecycle()
    val checkinMin by vm.checkinMin.collectAsStateWithLifecycle()
    val calendarEnabled by vm.calendarEnabled.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // Load the model list as soon as a key is available.
    LaunchedEffect(provider, savedKey) {
        if (savedKey.isNotEmpty() && modelsUi.models.isEmpty() && !modelsUi.loading && modelsUi.error == null) {
            vm.refreshModels()
        }
    }

    // Re-check OS permission grants every time the screen resumes — e.g. after the
    // user returns from the system "Alarms & reminders" or app-settings pages.
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionTick by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissionTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifGranted = remember(permissionTick) {
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
    val exactAllowed = remember(permissionTick) {
        Build.VERSION.SDK_INT < 31 ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }
    val calendarGranted = remember(permissionTick) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
    }

    val notifPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }
    val calendarPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionTick++
            if (granted) {
                vm.setCalendarEnabled(true)
                vm.refreshCalendar()
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
                    "Pick a provider and paste its API key to power \"Ask Tend\" and Auto-plan. " +
                        "Text models load automatically. Keys are stored encrypted on this device only.",
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

                // ── model dropdown — appears once a key is saved ──
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
                            Text("No text models available for this key.", fontSize = 12.5.sp, color = Faint)
                        else -> {
                            var menuOpen by remember { mutableStateOf(false) }
                            Box {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(Card, RoundedCornerShape(12.dp))
                                        .border(1.dp, Border, RoundedCornerShape(12.dp))
                                        .tapNoRipple { menuOpen = true }
                                        .padding(horizontal = 12.dp, vertical = 11.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(model, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                    Text("▾", fontSize = 13.sp, color = Muted)
                                }
                                DropdownMenu(
                                    expanded = menuOpen,
                                    onDismissRequest = { menuOpen = false },
                                    modifier = Modifier.heightIn(max = 300.dp).width(260.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    containerColor = Cream,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 8.dp,
                                    border = BorderStroke(1.dp, Border),
                                ) {
                                    modelsUi.models.forEach { m ->
                                        DropdownMenuItem(
                                            modifier = Modifier.height(42.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp),
                                            text = {
                                                Text(
                                                    if (m == model) "$m  ✓" else m,
                                                    fontSize = 12.5.sp,
                                                    color = if (m == model) Teal else Ink,
                                                    fontWeight = if (m == model) FontWeight.Bold else FontWeight.Medium,
                                                )
                                            },
                                            onClick = {
                                                vm.setModel(m)
                                                menuOpen = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        "✓ Using ${SettingsRepository.providerLabel(provider)} ($model)",
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

        // ── Notifications ───────────────────────────────
        SectionLabel("NOTIFICATIONS")
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Reminders", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Habit nudges and task due-time alerts",
                            fontSize = 11.5.sp, color = Faint,
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= 33 &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            vm.setNotificationsEnabled(enabled)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Cream),
                    )
                }

                if (notificationsEnabled) {
                    HorizontalDivider(color = RowDivider, thickness = 1.dp)
                    FieldLabel("Permissions")

                    // Notification permission (Android 13+)
                    if (Build.VERSION.SDK_INT >= 33) {
                        PermissionRow(
                            title = "Show notifications",
                            granted = notifGranted,
                            grantedNote = "Reminders can appear.",
                            deniedNote = "Blocked — reminders won't show.",
                            onAllow = {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            },
                        )
                    }

                    // Exact-alarm permission (Android 12+)
                    if (Build.VERSION.SDK_INT >= 31) {
                        PermissionRow(
                            title = "Exact alarms",
                            granted = exactAllowed,
                            grantedNote = "Reminders fire on the minute.",
                            deniedNote = "Off — reminders may arrive a few minutes late.",
                            onAllow = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                        Uri.parse("package:${context.packageName}"),
                                    )
                                )
                            },
                        )
                    }

                    HorizontalDivider(color = RowDivider, thickness = 1.dp)

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Nightly check-in", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Each evening: anything to add for tomorrow?",
                                fontSize = 11.5.sp, color = Faint,
                            )
                        }
                        Switch(
                            checked = checkinEnabled,
                            onCheckedChange = { vm.setCheckinEnabled(it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Cream),
                        )
                    }
                    if (checkinEnabled) {
                        TimeStepperRow(checkinMin, { vm.setCheckinMin(it) })
                    }
                }
            }
        }

        // ── Calendar ────────────────────────────────────
        SectionLabel("CALENDAR")
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!calendarGranted) {
                    Text(
                        "Connect your device calendar to see events on the Plan timeline " +
                            "and let Auto-plan schedule around them. Read-only.",
                        fontSize = 12.5.sp, color = Muted, lineHeight = 18.sp,
                    )
                    Box(
                        Modifier
                            .background(Ink, RoundedCornerShape(99.dp))
                            .tapNoRipple {
                                calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text("Connect calendar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cream)
                    }
                } else {
                    Text("✓ Calendar access allowed", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Teal)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Show calendar in Plan", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Events appear read-only and block Auto-plan slots",
                                fontSize = 11.5.sp, color = Faint,
                            )
                        }
                        Switch(
                            checked = calendarEnabled,
                            onCheckedChange = {
                                vm.setCalendarEnabled(it)
                                vm.refreshCalendar()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Ink, checkedThumbColor = Cream),
                        )
                    }
                }
            }
        }

        // ── Backup & restore ────────────────────────────
        SectionLabel("BACKUP & RESTORE")
        BackupCard(vm)

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

/**
 * Backups write a plain-JSON file into a folder the user picks once, and can
 * repeat on a schedule. Everything here is on-device: no account, no upload.
 */
@Composable
private fun BackupCard(vm: MainViewModel) {
    val folder by vm.backupFolder.collectAsStateWithLifecycle()
    val folderLabel by vm.backupFolderLabel.collectAsStateWithLifecycle()
    val interval by vm.backupInterval.collectAsStateWithLifecycle()
    val backupMin by vm.backupMin.collectAsStateWithLifecycle()
    val keep by vm.backupKeep.collectAsStateWithLifecycle()
    val status by vm.backupStatus.collectAsStateWithLifecycle()
    val busy by vm.backupBusy.collectAsStateWithLifecycle()
    val message by vm.backupMessage.collectAsStateWithLifecycle()
    val pendingRestore by vm.pendingRestore.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val haptics = LocalTendHaptics.current
    val hasFolder = folder.isNotBlank()
    val scheduled = interval != SettingsRepository.BACKUP_OFF

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { vm.setBackupFolder(it) } }

    val saveCopyPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFormat.MIME)
    ) { uri -> uri?.let { vm.exportTo(it) } }

    // Deliberately unfiltered: providers report .json files under half a dozen
    // MIME types, and the file is validated on read anyway.
    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { vm.previewRestore(it) } }

    TendCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Save everything — habits, check-ins, tasks, plans and notes — as a JSON " +
                    "file on this device. It stays readable in any text editor, and restores " +
                    "straight back into Tend. API keys are never written to the file.",
                fontSize = 12.5.sp, color = Muted, lineHeight = 18.sp,
            )

            FieldLabel("Backup folder")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (hasFolder) folderLabel else "Not chosen yet",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hasFolder) Ink else Faint,
                    modifier = Modifier.weight(1f).padding(end = 10.dp),
                )
                OutlinePill(if (hasFolder) "Change" else "Choose folder") { folderPicker.launch(null) }
            }

            HorizontalDivider(color = RowDivider, thickness = 1.dp)

            FieldLabel("Automatic backup")
            Row(Modifier.background(SegBg, RoundedCornerShape(12.dp)).padding(3.dp)) {
                listOf(
                    SettingsRepository.BACKUP_OFF,
                    SettingsRepository.BACKUP_DAILY,
                    SettingsRepository.BACKUP_WEEKLY,
                ).forEach { option ->
                    ProviderButton(
                        Modifier.weight(1f),
                        SettingsRepository.intervalLabel(option),
                        interval == option,
                    ) { vm.setBackupInterval(option) }
                }
            }

            if (scheduled) {
                if (!hasFolder) {
                    Text(
                        "Pick a folder above — scheduled backups have nowhere to write until you do.",
                        fontSize = 11.5.sp, color = Terracotta, lineHeight = 16.sp,
                    )
                }
                TimeStepperRow(backupMin, { vm.setBackupMin(it) }, hint = "runs around this time")
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Older backups in the folder are deleted", fontSize = 11.5.sp, color = Faint)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Stepper("−") { vm.setBackupKeep(keep - 1) }
                        Text(
                            "$keep", fontFamily = SpaceGrotesk, fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Stepper("+") { vm.setBackupKeep(keep + 1) }
                    }
                }
            }

            HorizontalDivider(color = RowDivider, thickness = 1.dp)

            when {
                status.failed -> Text(
                    "Last backup failed (${Time.stamp(status.atMillis)}): ${status.error}",
                    fontSize = 11.5.sp, color = Terracotta, lineHeight = 16.sp,
                )
                status.ran -> Text(
                    "✓ Last backup ${Time.stamp(status.atMillis)} · ${status.fileName}",
                    fontSize = 11.5.sp, color = Teal, lineHeight = 16.sp,
                )
                else -> Text("No backup yet.", fontSize = 11.5.sp, color = Faint)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var noFolderTick by remember { mutableIntStateOf(0) }
                Box(
                    Modifier
                        .shakeOnError(if (noFolderTick == 0) null else noFolderTick)
                        .background(if (hasFolder && !busy) Ink else Faint, RoundedCornerShape(99.dp))
                        .bouncyTap(haptic = if (hasFolder) TendHaptic.Confirm else TendHaptic.Reject) {
                            when {
                                busy -> Unit
                                hasFolder -> vm.backupNow()
                                // Points at the thing that's missing instead of
                                // swallowing the tap.
                                else -> noFolderTick++
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Text(
                        if (busy) "Working…" else "Back up now",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cream,
                    )
                }
                OutlinePill("Save a copy…") {
                    if (!busy) saveCopyPicker.launch(BackupManager.fileNameFor(System.currentTimeMillis()))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinePill("Share…") {
                    vm.shareBackup { intent ->
                        context.startActivity(Intent.createChooser(intent, "Share your Tend backup"))
                    }
                }
                OutlinePill("Restore…") { if (!busy) restorePicker.launch(arrayOf("*/*")) }
            }

            // A backup that failed and one that worked used to read identically —
            // same row, same weight. Now failure shakes once and buzzes Reject,
            // success ticks a Confirm, so the outcome registers before the text
            // is even read.
            message?.let { text ->
                val failed = status.failed
                LaunchedEffect(text) {
                    haptics.perform(if (failed) TendHaptic.Reject else TendHaptic.Confirm)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .shakeOnError(if (failed) text else null),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = if (failed) Terracotta else Ink,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "✕", fontSize = 12.sp, color = Faint,
                        modifier = Modifier.tapNoRipple { vm.clearBackupMessage() },
                    )
                }
            }
        }
    }

    pendingRestore?.let { pending ->
        RestoreConfirmDialog(
            pending = pending,
            onConfirm = { vm.confirmRestore() },
            onDismiss = { vm.cancelRestore() },
        )
    }
}

@Composable
private fun RestoreConfirmDialog(
    pending: PendingRestore,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = pending.snapshot
    Dialog(onDismissRequest = onDismiss) {
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Restore this backup?", fontFamily = SpaceGrotesk, fontSize = 17.sp, fontWeight = FontWeight.Bold)

                Text(
                    if (s.createdAtMillis > 0) "Written ${Time.stamp(s.createdAtMillis)} by Tend ${s.appVersion}"
                    else "Tend ${s.appVersion}",
                    fontSize = 12.sp, color = Muted,
                )

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    RestoreCountRow("Habits", s.habits.size)
                    RestoreCountRow("Check-ins", s.logs.size)
                    RestoreCountRow("Tasks", s.tasks.size)
                    RestoreCountRow("Plan blocks", s.plans.size)
                    RestoreCountRow("Notes", s.notes.size)
                }

                Text(
                    "This replaces everything currently in Tend on this device. " +
                        "It can't be undone — back up first if you're not sure.",
                    fontSize = 12.sp, color = Terracotta, lineHeight = 17.sp,
                )

                if (pending.warnings.isNotEmpty()) {
                    Text(
                        "Some rows will be skipped: ${pending.warnings.joinToString("; ")}.",
                        fontSize = 11.5.sp, color = Muted, lineHeight = 16.sp,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .background(Ink, RoundedCornerShape(99.dp))
                            .tapNoRipple(onConfirm)
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text("Replace my data", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cream)
                    }
                    OutlinePill("Cancel", onDismiss)
                }
            }
        }
    }
}

@Composable
private fun RestoreCountRow(label: String, count: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.5.sp, color = Muted)
        Text(
            "$count", fontFamily = SpaceGrotesk, fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold, color = Ink,
        )
    }
}

/** Bordered secondary action pill. */
@Composable
private fun OutlinePill(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, Border, RoundedCornerShape(99.dp))
            .bouncyTap(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

@Composable
private fun ProviderButton(modifier: Modifier, label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (selected) Card else Color.Transparent, RoundedCornerShape(9.dp))
            .bouncyTap(haptic = TendHaptic.Select, onClick = onClick)
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

/** One OS-permission row: title + live status, and an "Allow" pill when not yet granted. */
@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    grantedNote: String,
    deniedNote: String,
    onAllow: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (granted) grantedNote else deniedNote,
                fontSize = 11.5.sp,
                color = if (granted) Teal else Terracotta,
                lineHeight = 16.sp,
            )
        }
        if (granted) {
            Text("✓ Allowed", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Teal)
        } else {
            Box(
                Modifier
                    .border(1.dp, Border, RoundedCornerShape(99.dp))
                    .tapNoRipple(onAllow)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text("Allow", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Ink)
            }
        }
    }
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
