package com.smartexpense.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.R
import com.smartexpense.domain.Money
import com.smartexpense.domain.Snapshot

/** Exact-reference first-run form. Typing changes a local preview, never the ledger. */
@Composable fun BalanceSetupScreen(s: Snapshot, busy: Boolean, onContinue: (Long, Long, String?, String?) -> Unit) {
    var wallet by rememberSaveable { mutableStateOf("") }
    var gpay by rememberSaveable { mutableStateOf("") }
    var walletId by rememberSaveable { mutableStateOf("new") }
    var gpayId by rememberSaveable { mutableStateOf("new") }
    val active = s.accounts.filterNot { it.archived }
    fun balance(value: String) = runCatching { Money.parse(value).also { require(it >= 0) } }.getOrNull()
    val walletAmount = if (walletId == "new") balance(wallet) else active.find { it.id == walletId }?.balance
    val gpayAmount = if (gpayId == "new") balance(gpay) else active.find { it.id == gpayId }?.balance
    val total = if (walletAmount != null && gpayAmount != null) runCatching { Math.addExact(walletAmount, gpayAmount) }.getOrNull() else null
    val invalid = (walletId == "new" && wallet.isNotBlank() && walletAmount == null) || (gpayId == "new" && gpay.isNotBlank() && gpayAmount == null)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 520.dp).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_wallet_outline), null, Modifier.size(30.dp), tint = Tokens.accent)
                        Text("tally", style = MaterialTheme.typography.headlineMedium, color = Tokens.accent, fontWeight = FontWeight.Bold)
                    }
                    Text("SETUP", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp), color = Tokens.muted)
                }
            }
            item {
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your\nstarting point.", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 43.sp, lineHeight = 44.sp), modifier = Modifier.semantics { heading() })
                    Text("Add the money you have right now.", style = MaterialTheme.typography.bodyLarge, color = Tokens.muted)
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("TOTAL AVAILABLE", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = Tokens.muted)
                    Text(total?.let(::referenceMoney) ?: "₹—", style = MaterialTheme.typography.displaySmall.copy(fontSize = 58.sp, lineHeight = 66.sp), textAlign = TextAlign.Center)
                    Text("Wallet + GPay", style = MaterialTheme.typography.bodyMedium, color = Tokens.muted)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("STARTING BALANCES", Modifier.padding(start = 4.dp).semantics { heading() }, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp), color = Tokens.muted)
                    GroupedCard {
                        if (active.isNotEmpty()) Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Choice("Wallet account", walletId, listOf("new" to "Create Wallet") + active.filter { it.id != gpayId }.map { it.id to it.name }) { walletId = it }
                        }
                        if (walletId == "new") StartingBalanceInput("Wallet", "Cash on hand", wallet, { wallet = it }, R.drawable.ic_wallet_outline, ReferenceTone.Blue)
                        else GroupedRow("Wallet", R.drawable.ic_wallet_outline, "Cash on hand", walletAmount?.let(::referenceMoney), showChevron = false) {}
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Tokens.border.copy(alpha = .65f))
                        if (active.isNotEmpty()) Box(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Choice("GPay account", gpayId, listOf("new" to "Create GPay") + active.filter { it.id != walletId }.map { it.id to it.name }) { gpayId = it }
                        }
                        if (gpayId == "new") StartingBalanceInput("GPay", "Your tracked balance", gpay, { gpay = it }, R.drawable.ic_contactless, ReferenceTone.Lavender)
                        else GroupedRow("GPay", R.drawable.ic_contactless, "Your tracked balance", gpayAmount?.let(::referenceMoney), tone = ReferenceTone.Lavender, showChevron = false) {}
                    }
                    Text("You can start at ₹0 and update balances later.", style = MaterialTheme.typography.bodySmall, color = Tokens.muted)
                    if (invalid) Text("Enter a non-negative amount with up to two decimal places.", color = Tokens.negative, style = MaterialTheme.typography.bodySmall)
                    else if (walletAmount != null && gpayAmount != null && total == null) Text("The combined balance is too large. Check the amounts.", color = Tokens.negative)
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_lock_outline), null, Modifier.size(18.dp), tint = Tokens.muted)
                        Text("Private. Stored on your device.", style = MaterialTheme.typography.bodySmall, color = Tokens.muted, textAlign = TextAlign.Center)
                    }
                    Button(
                        onClick = { onContinue(if (walletId == "new") walletAmount!! else 0L, if (gpayId == "new") gpayAmount!! else 0L, walletId.takeUnless { it == "new" }, gpayId.takeUnless { it == "new" }) },
                        enabled = !busy && !invalid && total != null && (walletId == "new" || gpayId == "new" || walletId != gpayId),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).then(if(!busy && !invalid && total!=null && (walletId=="new" || gpayId=="new" || walletId!=gpayId)) Modifier.background(Brush.verticalGradient(listOf(Color(0xFF0870EA),Color(0xFF0066E8))),RoundedCornerShape(24.dp)) else Modifier), shape = RoundedCornerShape(24.dp),
                        colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Text("Continue to Tally", textAlign = TextAlign.Center)
                        Spacer(Modifier.width(12.dp))
                        Text("→", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
}

