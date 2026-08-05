package com.tend.app.ui.memory

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tend.app.MainViewModel
import com.tend.app.Tab
import com.tend.app.data.vault.MemoryItem
import com.tend.app.data.vault.VaultState
import com.tend.app.ui.components.DialogInput
import com.tend.app.ui.components.Kicker
import com.tend.app.ui.components.ScreenTitle
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
import com.tend.app.ui.theme.RowDivider
import com.tend.app.ui.theme.SegBg
import com.tend.app.ui.theme.SpaceGrotesk
import com.tend.app.ui.theme.Teal
import com.tend.app.ui.theme.Terracotta
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The Memory vault.
 *
 * Three states, and the screen is honest about which one it is in: not set up,
 * locked, unlocked. Nothing about the contents is shown while locked — not even
 * titles, because those are ciphertext until a key exists.
 */
@Composable
fun MemoryScreen(vm: MainViewModel) {
    val state by vm.vaultState.collectAsStateWithLifecycle()
    val recoveryCode by vm.recoveryCode.collectAsStateWithLifecycle()

    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Box(
                Modifier
                    .size(34.dp)
                    .border(1.dp, Border, RoundedCornerShape(50))
                    .tapNoRipple { vm.selectTab(Tab.Settings) },
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", fontSize = 18.sp)
            }
            Column {
                Kicker("TEND")
                ScreenTitle("Memory")
            }
        }

        when (state) {
            VaultState.NotSetUp -> SetupCard(vm)
            VaultState.Locked -> UnlockCard(vm)
            VaultState.Unlocked -> UnlockedVault(vm)
        }
    }

    recoveryCode?.let { RecoveryCodeDialog(it) { vm.dismissRecoveryCode() } }
}

@Composable
private fun SetupCard(vm: MainViewModel) {
    val error by vm.vaultError.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    TendCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Create your vault", fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(
                "Memory is encrypted with a key derived from this password. Tend never stores " +
                    "the password, and the assistant is never given access to what's inside — " +
                    "not in AI mode, not offline.",
                fontSize = 12.5.sp, color = Muted, lineHeight = 18.sp,
            )
            Text(
                "If you forget the password, the recovery code shown next is the only other way " +
                    "in. Without either, the contents can't be recovered by anyone, including us.",
                fontSize = 12.sp, color = Terracotta, lineHeight = 17.sp,
            )

            Secret(password, { password = it }, "Password")
            Secret(confirm, { confirm = it }, "Confirm password")

            error?.let { Text(it, fontSize = 11.5.sp, color = Terracotta) }
            if (confirm.isNotEmpty() && password != confirm) {
                Text("Those don't match.", fontSize = 11.5.sp, color = Terracotta)
            }

            Action("Create vault", enabled = password.isNotEmpty() && password == confirm) {
                vm.createVault(password)
                password = ""
                confirm = ""
            }
        }
    }
}

@Composable
private fun UnlockCard(vm: MainViewModel) {
    val error by vm.vaultError.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var usingCode by remember { mutableStateOf(false) }

    TendCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Memory is locked", fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold)

            if (usingCode) {
                Text(
                    "Enter the recovery code you saved when you created the vault. " +
                        "Dashes and capitals don't matter.",
                    fontSize = 12.5.sp, color = Muted, lineHeight = 18.sp,
                )
                DialogInput(code, { code = it }, "A1B2-C3D4-…")
            } else {
                Secret(password, { password = it }, "Password")
            }

            error?.let { Text(it, fontSize = 11.5.sp, color = Terracotta) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("Unlock", enabled = if (usingCode) code.isNotBlank() else password.isNotEmpty()) {
                    if (usingCode) vm.unlockVaultWithRecoveryCode(code) else vm.unlockVault(password)
                    password = ""
                    code = ""
                }
                Outline(if (usingCode) "Use password" else "Use recovery code") { usingCode = !usingCode }
            }
        }
    }
}

@Composable
private fun UnlockedVault(vm: MainViewModel) {
    val context = LocalContext.current
    val results by vm.memoryResults.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableLongStateOf(0L) }

    LaunchedEffect(query) { vm.refreshMemory(query) }

    // Import copies the file into the vault; the picked URI is not retained, so
    // no persistable permission is needed here.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        } ?: "file"
        vm.addMemoryFile(uri, name, resolver.getType(uri) ?: "application/octet-stream", "")
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Unlocked",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Teal,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Lock",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Muted,
                        modifier = Modifier.tapNoRipple(TendHaptic.Select) { vm.lockVault() },
                    )
                }
                DialogInput(query, { query = it }, "Search text, captions, filenames…")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Action("Add note") { adding = true }
                    Outline("Add file") { picker.launch(arrayOf("*/*")) }
                }
                Text(
                    "Searches what you typed and the names of files you added. It doesn't read " +
                        "the inside of images.",
                    fontSize = 11.sp, color = Faint, lineHeight = 15.sp,
                )
            }
        }

        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                if (results.isEmpty()) {
                    Text(
                        if (query.isBlank()) "Nothing saved yet."
                        else "Nothing matches \"$query\".",
                        fontSize = 12.5.sp, color = Muted,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                results.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = RowDivider, thickness = 1.dp)
                    MemoryRow(item) { confirmDelete = item.id }
                }
            }
        }
    }

    if (adding) {
        AddNoteDialog(
            onSave = { title, body -> vm.addMemoryNote(title, body); adding = false },
            onDismiss = { adding = false },
        )
    }

    if (confirmDelete != 0L) {
        val id = confirmDelete
        ConfirmDialog(
            title = "Delete this entry?",
            body = "It's removed from the vault and can't be recovered.",
            confirmLabel = "Delete",
            onConfirm = { vm.deleteMemory(id); confirmDelete = 0L },
            onDismiss = { confirmDelete = 0L },
        )
    }
}

@Composable
private fun MemoryRow(item: MemoryItem, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(30.dp).background(SegBg, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    item.isImage -> "🖼"
                    item.hasBlob -> "📎"
                    else -> "✎"
                },
                fontSize = 13.sp,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    stamp(item.createdAt),
                    item.body.takeIf { it.isNotBlank() && it != item.title }?.take(40),
                ).joinToString(" · "),
                fontSize = 10.5.sp, color = Faint,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "🗑",
            fontSize = 12.sp,
            modifier = Modifier.tapNoRipple(TendHaptic.Select, onDelete),
        )
    }
}

@Composable
private fun RecoveryCodeDialog(code: String, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Save this recovery code", fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "This is shown once and is never stored. If you forget your password it is " +
                        "the only way back into the vault — write it down somewhere safe now.",
                    fontSize = 12.5.sp, color = Muted, lineHeight = 18.sp,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(SegBg, RoundedCornerShape(10.dp))
                        .padding(14.dp)
                ) {
                    Text(code, fontFamily = SpaceGrotesk, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                }
                Action("I've written it down", onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun AddNoteDialog(onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add to Memory", fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                DialogInput(title, { title = it }, "Title (optional)")
                DialogInput(body, { body = it }, "What should Tend remember?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Action("Save", enabled = body.isNotBlank()) { onSave(title, body) }
                    Outline("Cancel", onDismiss)
                }
            }
        }
    }
}

@Composable
internal fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        TendCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, fontFamily = SpaceGrotesk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(body, fontSize = 12.5.sp, color = Muted, lineHeight = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .background(Terracotta, RoundedCornerShape(99.dp))
                            .bouncyTap(haptic = TendHaptic.Confirm, onClick = onConfirm)
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(confirmLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cream)
                    }
                    Outline("Cancel", onDismiss)
                }
            }
        }
    }
}

@Composable
private fun Secret(value: String, onChange: (String) -> Unit, placeholder: String) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Ink),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Card, RoundedCornerShape(12.dp))
                    .border(1.dp, Border, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 11.dp)
            ) {
                if (value.isEmpty()) Text(placeholder, fontSize = 13.sp, color = Faint)
                inner()
            }
        },
    )
}

@Composable
private fun Action(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (enabled) Ink else Faint, RoundedCornerShape(99.dp))
            .bouncyTap(haptic = TendHaptic.Confirm, enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Cream)
    }
}

@Composable
private fun Outline(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .border(1.dp, Border, RoundedCornerShape(99.dp))
            .bouncyTap(haptic = TendHaptic.Select, pressedScale = TendMotion.PressScaleLarge, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

private fun stamp(millis: Long): String =
    DateTimeFormatter.ofPattern("d MMM").format(
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    )
