package com.smartexpense.ui

import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.R
import com.smartexpense.domain.Money
import com.smartexpense.domain.ReminderSettings
import com.smartexpense.domain.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Starting balances chosen during onboarding. They are saved only when the flow finishes. */
data class StartingBalances(val wallet: Long, val gpay: Long, val walletId: String?, val gpayId: String?)

private enum class OnboardingStep { Welcome, HowItWorks, Balances, Reminders, Restore, Restored }

/**
 * First-run flow from the startup redesign: welcome, how Tally works, starting balances and the
 * optional reminder and app lock step, plus restoring from a backup. Nothing is written to the
 * ledger until [onFinish], except a restore, which the person confirms explicitly.
 */
@Composable fun OnboardingScreen(model: AppViewModel, s: Snapshot, busy: Boolean, onFinish: (StartingBalances?) -> Unit) {
    var stepName by rememberSaveable { mutableStateOf(OnboardingStep.Welcome.name) }
    val step = OnboardingStep.valueOf(stepName)
    fun go(next: OnboardingStep) { stepName = next.name }

    // Balances stay local until the flow ends, so going back never saves them twice.
    var wallet by rememberSaveable { mutableStateOf("") }
    var gpay by rememberSaveable { mutableStateOf("") }
    var walletId by rememberSaveable { mutableStateOf("new") }
    var gpayId by rememberSaveable { mutableStateOf("new") }
    var keepBalances by rememberSaveable { mutableStateOf(false) }
    val active = s.accounts.filterNot { it.archived }
    fun balance(value: String) = if (value.isBlank()) 0L else runCatching { Money.parse(value).also { require(it >= 0) } }.getOrNull()
    val walletAmount = if (walletId == "new") balance(wallet) else active.find { it.id == walletId }?.balance
    val gpayAmount = if (gpayId == "new") balance(gpay) else active.find { it.id == gpayId }?.balance
    val total = if (walletAmount != null && gpayAmount != null) runCatching { Math.addExact(walletAmount, gpayAmount) }.getOrNull() else null
    val balancesValid = total != null && (walletId == "new" || gpayId == "new" || walletId != gpayId)
    fun finish() = onFinish(if (keepBalances && balancesValid) StartingBalances(
        if (walletId == "new") walletAmount!! else 0L, if (gpayId == "new") gpayAmount!! else 0L,
        walletId.takeUnless { it == "new" }, gpayId.takeUnless { it == "new" }) else null)

    val back: (() -> Unit)? = when (step) {
        OnboardingStep.Welcome, OnboardingStep.Restored -> null
        OnboardingStep.HowItWorks, OnboardingStep.Restore -> { { go(OnboardingStep.Welcome) } }
        OnboardingStep.Balances -> { { go(OnboardingStep.HowItWorks) } }
        OnboardingStep.Reminders -> { { go(OnboardingStep.Balances) } }
    }
    BackHandler(back != null && !busy) { back?.invoke() }

    when (step) {
        OnboardingStep.Welcome -> OnboardingPage(dot = 0, primary = "Continue", onPrimary = { go(OnboardingStep.HowItWorks) },
            secondary = "Restore from a backup", onSecondary = { go(OnboardingStep.Restore) }, busy = busy) { WelcomeContent() }
        OnboardingStep.HowItWorks -> OnboardingPage(dot = 1, onBack = back, onSkip = { go(OnboardingStep.Balances) },
            primary = "Continue", onPrimary = { go(OnboardingStep.Balances) }, busy = busy) { HowItWorksContent() }
        OnboardingStep.Balances -> OnboardingPage(dot = 2, onBack = back, onSkip = { keepBalances = false; go(OnboardingStep.Reminders) },
            primary = "Continue", primaryEnabled = balancesValid, onPrimary = { keepBalances = true; go(OnboardingStep.Reminders) },
            footnote = "Tally never connects to your bank or Google Pay", busy = busy) {
            BalancesContent(active, wallet, { wallet = it }, gpay, { gpay = it }, walletId, { walletId = it }, gpayId, { gpayId = it },
                walletAmount, gpayAmount, total)
        }
        OnboardingStep.Reminders -> OnboardingPage(dot = 3, onBack = back, primary = "Start using Tally", onPrimary = ::finish,
            secondary = "Not now", onSecondary = ::finish, busy = busy) { RemindersContent(model, busy) }
        OnboardingStep.Restore -> RestoreStep(model, s, busy, back = { go(OnboardingStep.Welcome) },
            onStartFresh = { go(OnboardingStep.HowItWorks) }, onRestored = { go(OnboardingStep.Restored) })
        OnboardingStep.Restored -> OnboardingPage(dot = null, primary = "Open Tally", onPrimary = { onFinish(null) }, busy = busy) { RestoredContent(s) }
    }
}

/** Shared frame: round back button and Skip on top, scrolling body, dots and pill buttons below. */
@Composable private fun OnboardingPage(
    dot: Int?, primary: String, onPrimary: () -> Unit, busy: Boolean,
    onBack: (() -> Unit)? = null, onSkip: (() -> Unit)? = null, primaryEnabled: Boolean = true,
    secondary: String? = null, onSecondary: (() -> Unit)? = null, footnote: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxSize().background(Tokens.background), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxSize().imePadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) RoundBackButton(onBack, !busy)
                Spacer(Modifier.weight(1f))
                if (onSkip != null) TextButton(onSkip, enabled = !busy) { Text("Skip", style = MaterialTheme.typography.bodyLarge, color = Tokens.accent) }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.pageInset, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally, content = content)
            Column(Modifier.fillMaxWidth().padding(start = Tokens.pageInset, end = Tokens.pageInset, top = 12.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dot != null) PageDots(4, dot)
                Spacer(Modifier.height(4.dp))
                Button(onPrimary, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = primaryEnabled && !busy, shape = Tokens.pill,
                    colors = ButtonDefaults.buttonColors(containerColor = Tokens.accent, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Text(primary, style = MaterialTheme.typography.labelLarge)
                }
                if (secondary != null && onSecondary != null) TextButton(onSecondary, Modifier.heightIn(min = 48.dp), enabled = !busy) {
                    Text(secondary, style = MaterialTheme.typography.bodyLarge, color = Tokens.accent)
                }
                footnote?.let { Text(it, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = Tokens.muted, textAlign = TextAlign.Center) }
            }
        }
    }
}

@Composable private fun RoundBackButton(onBack: () -> Unit, enabled: Boolean) {
    Surface(onClick = onBack, enabled = enabled, shape = CircleShape, color = Tokens.paper, shadowElevation = 1.dp,
        modifier = Modifier.size(44.dp).semantics { contentDescription = "Back" }) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_chevron_down), null, Modifier.size(16.dp).rotate(90f), tint = Tokens.text)
        }
    }
}

@Composable private fun PageDots(count: Int, current: Int) {
    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "Step ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            Box(Modifier.size(if (index == current) 18.dp else 6.dp, 6.dp).clip(CircleShape)
                .background(if (index == current) Tokens.accent else Tokens.muted.copy(alpha = .35f)))
        }
    }
}

/** The app icon: a blue gradient tile with the white tally mark and a soft glow. */
@Composable internal fun TallyLogoTile(size: Dp) {
    val shape = RoundedCornerShape(size * .24f)
    val glow = Color(0xFF0A66D8)
    Box(Modifier.size(size).shadow(size * .3f, shape, ambientColor = glow, spotColor = glow)
        .background(Brush.linearGradient(listOf(Color(0xFF4C9BFF), Color(0xFF0A66D8), Color(0xFF0552B5))), shape),
        contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_tally_mark), null, Modifier.size(size * .55f), tint = Color.White)
    }
}

/** Soft tinted square holding an outline glyph, used above step titles. */
@Composable private fun SoftIconTile(icon: Int) {
    Box(Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).background(Tints.blueSoft), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), null, Modifier.size(28.dp), tint = Tokens.accent)
    }
}

@Composable private fun StepTitle(title: String, body: String?) {
    Text(title, Modifier.fillMaxWidth().padding(top = 14.dp).semantics { heading() }, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    body?.let { Text(it, Modifier.fillMaxWidth().padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge, color = Tokens.muted, textAlign = TextAlign.Center) }
}

@Composable private fun ColumnScope.WelcomeContent() {
    Spacer(Modifier.height(8.dp))
    TallyLogoTile(84.dp)
    Text("Welcome to Tally", Modifier.fillMaxWidth().padding(top = 18.dp).semantics { heading() },
        style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
    Text("Know what you can spend today, split bills with friends and keep every rupee in view.", Modifier.fillMaxWidth().padding(top = 10.dp, start = 12.dp, end = 12.dp),
        style = MaterialTheme.typography.bodyLarge, color = Tokens.muted, textAlign = TextAlign.Center)
    Spacer(Modifier.height(20.dp))
    GroupedCard {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("What you’ll see", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = Tokens.muted)
            Surface(shape = Tokens.pill, color = Tokens.secondarySurface) {
                Text("Example", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, color = Tokens.muted)
            }
        }
        ExampleRow({ ProgressRing(.68f, CategoryColors.food, 34.dp) }, "Left to spend today", "₹160 of a ₹500 daily limit")
        GroupedDivider(62.dp)
        ExampleRow({ InitialAvatar("Meera", Modifier.size(34.dp)) }, "Meera owes you", "Dinner split four ways") {
            Text("₹420", style = MaterialTheme.typography.titleMedium, color = Tokens.positive)
        }
        GroupedDivider(62.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SolidIcon(R.drawable.ic_food, CategoryColors.food, 34.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Food budget", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("79%", style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Tokens.secondarySurface)) {
                    Box(Modifier.fillMaxWidth(.79f).fillMaxHeight().clip(CircleShape).background(CategoryColors.food))
                }
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        PromiseChip(R.drawable.ic_lock_outline, "On this phone")
        PromiseChip(R.drawable.ic_wifi_outline, "Works offline")
        PromiseChip(R.drawable.ic_account, "No account")
    }
}

@Composable private fun ExampleRow(leading: @Composable () -> Unit, title: String, caption: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
        }
        trailing?.invoke()
    }
}

@Composable private fun PromiseChip(icon: Int, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, Modifier.size(14.dp), tint = Tokens.muted)
        Text(label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = Tokens.muted)
    }
}

@Composable private fun ColumnScope.HowItWorksContent() {
    Text("How Tally works", Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 22.dp).semantics { heading() },
        style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        FeatureRow(R.drawable.ic_pencil_outline, CategoryColors.food, "Add in seconds", "Type “lunch 180 gpay”, scan a bill, or tap a favourite on your home screen.")
        FeatureRow(R.drawable.ic_people, CategoryColors.bills, "Split with friends", "Share a bill any way you like. Tally keeps track of who owes what until you settle up.")
        FeatureRow(R.drawable.ic_target_outline, Tokens.accent, "Stay on budget", "A daily limit and monthly budgets warn you before you overspend.")
        FeatureRow(R.drawable.ic_lock_outline, Tokens.muted, "Private by design", "No bank login and no account. Everything stays on this phone and works offline.")
    }
}

@Composable private fun FeatureRow(icon: Int, color: Color, title: String, body: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Top) {
        Icon(painterResource(icon), null, Modifier.padding(top = 2.dp).size(30.dp), tint = color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Tokens.muted)
        }
    }
}

@Composable private fun ColumnScope.BalancesContent(
    active: List<com.smartexpense.domain.Account>,
    wallet: String, onWallet: (String) -> Unit, gpay: String, onGpay: (String) -> Unit,
    walletId: String, onWalletId: (String) -> Unit, gpayId: String, onGpayId: (String) -> Unit,
    walletAmount: Long?, gpayAmount: Long?, total: Long?
) {
    SoftIconTile(R.drawable.ic_wallet_outline)
    StepTitle("Your starting balances", "Enter what you have right now.\nTally adds and subtracts from here.")
    Spacer(Modifier.height(20.dp))
    GroupedCard {
        // Phones that already have accounts can reuse one instead of creating a new Wallet or GPay.
        if (active.isNotEmpty()) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)) {
            Choice("Wallet account", walletId, listOf("new" to "Create Wallet") + active.filter { it.id != gpayId }.map { it.id to it.name }, onWalletId)
        }
        BalanceRow(R.drawable.ic_wallet_outline, CategoryColors.wallet, "Wallet", "Cash on hand", wallet, onWallet, editable = walletId == "new", fixed = walletAmount)
        GroupedDivider(62.dp)
        if (active.isNotEmpty()) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp)) {
            Choice("GPay account", gpayId, listOf("new" to "Create GPay") + active.filter { it.id != walletId }.map { it.id to it.name }, onGpayId)
        }
        BalanceRow(R.drawable.ic_phone_outline, CategoryColors.brand, "GPay", "Balance in your UPI app", gpay, onGpay, editable = gpayId == "new", fixed = gpayAmount)
    }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Total available", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Tokens.muted)
        Text(total?.let(::referenceMoney) ?: "₹—", style = MaterialTheme.typography.titleMedium)
    }
    val invalid = (walletId == "new" && walletAmount == null) || (gpayId == "new" && gpayAmount == null)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.ic_info), null, Modifier.padding(top = 1.dp).size(15.dp), tint = if (invalid) Tokens.negative else Tokens.muted)
        Text(when {
            invalid -> "Enter an amount of zero or more, with up to two decimal places."
            walletAmount != null && gpayAmount != null && total == null -> "The combined balance is too large. Check the amounts."
            walletId != "new" && walletId == gpayId -> "Choose different accounts for Wallet and GPay."
            else -> "Starting at ₹0 is fine. You can change these any time in Accounts."
        }, style = MaterialTheme.typography.bodySmall, color = if (invalid) Tokens.negative else Tokens.muted)
    }
}

@Composable private fun BalanceRow(icon: Int, color: Color, name: String, caption: String, value: String, onChange: (String) -> Unit, editable: Boolean, fixed: Long?) {
    val focus = LocalFocusManager.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        SolidIcon(icon, color, 34.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
        }
        if (!editable) Text(fixed?.let(::referenceMoney) ?: "₹—", style = MaterialTheme.typography.titleMedium, color = Tokens.muted)
        else Surface(shape = RoundedCornerShape(10.dp), color = Tokens.secondarySurface) {
            BasicTextField(value, { onChange(it.replace(",", "").removePrefix("₹")) },
                modifier = Modifier.widthIn(min = 96.dp, max = 140.dp).heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 10.dp)
                    .semantics { contentDescription = "$name balance in rupees" },
                textStyle = MaterialTheme.typography.titleMedium.copy(color = Tokens.text, textAlign = TextAlign.End),
                cursorBrush = SolidColor(Tokens.accent), singleLine = true, visualTransformation = RupeeMoneyTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
        }
    }
}

@Composable private fun ColumnScope.RemindersContent(model: AppViewModel, busy: Boolean) {
    val context = LocalContext.current
    val lock = LocalDeviceLock.current
    val lockView = lock?.view
    var remindersOn by rememberSaveable { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    fun turnOn() = model.action("Daily reminder on") {
        model.repository.saveReminderSettings(ReminderSettings(true, 20, 0)); remindersOn = true
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> denied = !granted; if (granted) turnOn() }
    SoftIconTile(R.drawable.ic_bell_outline)
    StepTitle("Stay on track", "Two optional extras. You can change both later in Settings.")
    Spacer(Modifier.height(20.dp))
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ExtraCard(R.drawable.ic_bell_outline, CategoryColors.gray, "Daily reminder",
            "A nudge at 8:00 pm to review anything that’s due. It never shows amounts.",
            if (remindersOn) "Reminders on" else "Turn on reminders", done = remindersOn, enabled = !busy) {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            else turnOn()
        }
        ExtraCard(R.drawable.ic_fingerprint_outline, CategoryColors.brand, "App lock",
            "Open Tally with your fingerprint, or your phone’s PIN, pattern or password if it doesn’t work.",
            if (lockView?.enabled == true) "App lock on" else "Set up app lock", done = lockView?.enabled == true,
            enabled = !busy && lock != null && lockView?.busy != true) { lock?.requestSetting(true) }
        if (denied) ReferenceInfo("Notification permission was not granted, so the reminder stays off.")
        lockView?.message?.let { ReferenceInfo(it) }
    }
}

@Composable private fun ExtraCard(icon: Int, color: Color, title: String, body: String, action: String, done: Boolean, enabled: Boolean, onClick: () -> Unit) {
    GroupedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                SolidIcon(icon, color, 36.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = Tokens.muted)
                }
            }
            Surface(onClick = lightHapticAction(onClick), enabled = enabled && !done, shape = Tokens.pill, color = Tokens.tonal, contentColor = Tokens.accent,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    if (done) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(18.dp))
                    Text(action, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

private class PickedBackup(val name: String, val detail: String, val bytes: ByteArray)

@Composable private fun RestoreStep(model: AppViewModel, s: Snapshot, busy: Boolean, back: () -> Unit, onStartFresh: () -> Unit, onRestored: () -> Unit) {
    val context = LocalContext.current
    var picked by remember { mutableStateOf<PickedBackup?>(null) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { picked?.bytes?.fill(0); model.clearRestore() } }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.action("Backup file chosen") {
            val (name, size) = withContext(Dispatchers.IO) {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) (cursor.getString(0) ?: "Backup file") to (if (cursor.isNull(1)) -1L else cursor.getLong(1)) else null
                } ?: ("Backup file" to -1L)
            }
            val bytes = withContext(Dispatchers.IO) {
                requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                    val buffer = java.io.ByteArrayOutputStream(); val chunk = ByteArray(8192); var count = input.read(chunk)
                    while (count >= 0) { require(buffer.size() + count <= 32 * 1024 * 1024) { "Backup is too large" }; buffer.write(chunk, 0, count); count = input.read(chunk) }
                    buffer.toByteArray()
                }
            }
            val sizeText = if (size >= 0) "${(size + 1023) / 1024} KB" else "${(bytes.size + 1023) / 1024} KB"
            picked?.bytes?.fill(0)
            picked = PickedBackup(name, "Chosen file · $sizeText", bytes); error = null
        }
    }
    fun restore() {
        val backup = picked ?: return
        model.action("Backup restored") {
            val secret = password.toCharArray()
            try {
                val summary = try { model.backup.inspect(backup.bytes, secret) } catch (e: Exception) {
                    error = "This file couldn’t be opened. Check the password, or choose another backup."; throw e
                }
                model.prepareRestore(backup.bytes, summary, password)
                model.restorePrepared()
                onRestored()
            } finally { secret.fill('\u0000') }
        }
    }
    val empty = s.records.isEmpty() && s.accounts.isEmpty()
    OnboardingPage(dot = null, onBack = back, primary = "Restore", primaryEnabled = picked != null, onPrimary = ::restore,
        secondary = "Start fresh instead", onSecondary = onStartFresh, busy = busy) {
        SoftIconTile(R.drawable.ic_upload_outline)
        StepTitle("Restore from a backup", "Pick the backup file you saved from your old phone.")
        Spacer(Modifier.height(20.dp))
        GroupedCard {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SolidIcon(R.drawable.ic_document_outline, CategoryColors.brand, 34.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(picked?.name ?: "No file chosen", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(picked?.detail ?: "Files, Downloads or Drive", style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
                }
                TextButton({ chooser.launch(arrayOf("application/octet-stream", "application/json", "*/*")) }, enabled = !busy) {
                    Text(if (picked == null) "Choose" else "Change", style = MaterialTheme.typography.bodyLarge, color = Tokens.accent)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        GroupedCard {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Password", style = MaterialTheme.typography.bodyLarge)
                    Text("Only if you encrypted the backup", style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
                }
                BasicTextField(password, { password = it; error = null },
                    modifier = Modifier.widthIn(min = 96.dp, max = 150.dp).heightIn(min = 44.dp).padding(vertical = 10.dp).semantics { contentDescription = "Backup password" },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Tokens.text, textAlign = TextAlign.End),
                    cursorBrush = SolidColor(Tokens.accent), singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    decorationBox = { inner -> Box(contentAlignment = Alignment.CenterEnd) {
                        if (password.isEmpty()) Text("Optional", style = MaterialTheme.typography.bodyLarge, color = Tokens.muted.copy(alpha = .7f))
                        inner()
                    } })
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_info), null, Modifier.padding(top = 1.dp).size(15.dp), tint = if (error != null || !empty) Tokens.negative else Tokens.muted)
            Text(error ?: if (empty) "This phone has no Tally data yet, so nothing will be replaced."
                else "Restoring replaces everything already in Tally on this phone.",
                style = MaterialTheme.typography.bodySmall, color = if (error != null || !empty) Tokens.negative else Tokens.muted)
        }
    }
}

@Composable private fun ColumnScope.RestoredContent(s: Snapshot) {
    val lastEntry = s.records.maxOfOrNull { it.date }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    val day = lastEntry?.format(DateTimeFormatter.ofPattern("d MMMM"))
    val accounts = s.accounts.filterNot { it.archived }
    val friends = s.people.count { it.id != "self" }
    Spacer(Modifier.height(40.dp))
    Box(Modifier.size(66.dp).clip(CircleShape).background(Tokens.accent), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_check), null, Modifier.size(32.dp), tint = Color.White)
    }
    StepTitle("Welcome back", if (day != null) "Your records from $day are on this phone." else "Your backup is on this phone.")
    Spacer(Modifier.height(20.dp))
    GroupedCard {
        RestoredRow("Entries", maxOf(s.transactionCount, s.records.size).toString())
        GroupedDivider(16.dp)
        RestoredRow("Accounts", if (accounts.isEmpty()) "None" else accounts.take(3).joinToString(", ") { it.name } + if (accounts.size > 3) " +${accounts.size - 3}" else "")
        GroupedDivider(16.dp)
        RestoredRow("Friends and groups", "$friends ${if (friends == 1) "friend" else "friends"} · ${s.groups.size} ${if (s.groups.size == 1) "group" else "groups"}")
        if (day != null) { GroupedDivider(16.dp); RestoredRow("Last entry", day) }
    }
    if (day != null) Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.ic_info), null, Modifier.padding(top = 1.dp).size(15.dp), tint = Tokens.muted)
        Text("Anything you spent after $day isn’t in this backup. Add it from Activity.", style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
    }
}

@Composable private fun RestoredRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = Tokens.muted, textAlign = TextAlign.End)
    }
}
