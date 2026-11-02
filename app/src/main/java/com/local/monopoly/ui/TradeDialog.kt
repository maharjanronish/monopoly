package com.local.monopoly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.monopoly.game.BOARD
import com.local.monopoly.game.Engine
import com.local.monopoly.game.TradeOffer

private data class TradeItem(
    val idx: Int,
    val name: String,
    val colorHex: Long?,
    val mortgaged: Boolean
)

/**
 * Hot-seat trade: pick a partner, build an offer (properties, cash, jail
 * cards both ways), then hand the phone over — the partner accepts or
 * declines on the review screen.
 */
@Composable
fun TradeDialog(engine: Engine, fromId: Int, onDismiss: () -> Unit) {
    val state = engine.state ?: return
    val from = state.players.first { it.id == fromId }

    var partnerId by remember { mutableStateOf<Int?>(null) }
    var step by remember { mutableStateOf(0) }
    var err by remember { mutableStateOf<String?>(null) }
    var giveProps by remember { mutableStateOf(setOf<Int>()) }
    var getProps by remember { mutableStateOf(setOf<Int>()) }
    var cashFrom by remember { mutableStateOf(0) }
    var cashTo by remember { mutableStateOf(0) }
    var cardsFrom by remember { mutableStateOf(0) }
    var cardsTo by remember { mutableStateOf(0) }

    val partner = partnerId?.let { id -> state.players.firstOrNull { it.id == id } }

    if (partner == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("🤝 Trade — who trades with ${from.name}?") },
            text = {
                Column {
                    state.players.filter { !it.bankrupt && it.id != fromId }.forEach { p ->
                        BigButton(
                            "${p.token}  ${p.name}",
                            Color(p.colorHex),
                            Modifier.padding(vertical = 3.dp)
                        ) { partnerId = p.id }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
        )
        return
    }

    fun giveItems(pid: Int): List<TradeItem> = engine.tradableProperties(pid).map { idx ->
        val sp = BOARD[idx]
        val ps = state.props.getValue(idx)
        TradeItem(idx, sp.name, sp.group?.colorHex, ps.mortgaged)
    }

    if (step == 0) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("🤝 ${from.name} ↔ ${partner.name}") },
            text = {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = 420.dp)
                ) {
                    err?.let {
                        Text(it, color = Color(0xFFC62828), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("${from.token} ${from.name} GIVES", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    PropList(giveItems(fromId), giveProps) { idx ->
                        giveProps = if (idx in giveProps) giveProps - idx else giveProps + idx
                    }
                    CashStepper("Cash", cashFrom, from.cash) { cashFrom = it }
                    if (from.jailCardIds.isNotEmpty()) {
                        CardStepper("Jail cards", cardsFrom, from.jailCardIds.size) { cardsFrom = it }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("${partner.token} ${partner.name} GIVES", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    PropList(giveItems(partner.id), getProps) { idx ->
                        getProps = if (idx in getProps) getProps - idx else getProps + idx
                    }
                    CashStepper("Cash", cashTo, partner.cash) { cashTo = it }
                    if (partner.jailCardIds.isNotEmpty()) {
                        CardStepper("Jail cards", cardsTo, partner.jailCardIds.size) { cardsTo = it }
                    }
                }
            },
            confirmButton = { Button(onClick = { step = 1 }) { Text("PROPOSE") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
        )
    } else {
        val offer = TradeOffer(
            fromId, partner.id,
            cashFrom, cashTo,
            giveProps.toList(), getProps.toList(),
            cardsFrom, cardsTo
        )
        AlertDialog(
            onDismissRequest = { step = 0 },
            title = { Text("🤝 Trade proposal") },
            text = {
                Column {
                    Text("${partner.name}, do you accept?", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("${from.name} gives:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (giveProps.isEmpty() && cashFrom == 0 && cardsFrom == 0) {
                        Text("• nothing", fontSize = 11.sp)
                    }
                    giveProps.forEach { Text("• ${BOARD[it].name}", fontSize = 11.sp) }
                    if (cashFrom > 0) Text("• \$${money(cashFrom)} cash", fontSize = 11.sp)
                    if (cardsFrom > 0) Text("• $cardsFrom jail card(s)", fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("${partner.name} gives:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (getProps.isEmpty() && cashTo == 0 && cardsTo == 0) {
                        Text("• nothing", fontSize = 11.sp)
                    }
                    getProps.forEach { Text("• ${BOARD[it].name}", fontSize = 11.sp) }
                    if (cashTo > 0) Text("• \$${money(cashTo)} cash", fontSize = 11.sp)
                    if (cardsTo > 0) Text("• $cardsTo jail card(s)", fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val e = engine.executeTrade(offer)
                    if (e == null) onDismiss() else {
                        err = e
                        step = 0
                    }
                }) { Text("ACCEPT") }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text("DECLINE") } }
        )
    }
}

@Composable
private fun PropList(items: List<TradeItem>, selected: Set<Int>, onToggle: (Int) -> Unit) {
    if (items.isEmpty()) {
        Text(
            "No tradable properties (color sets with buildings can't be traded)",
            fontSize = 10.sp,
            color = Color(0xFF8A8578)
        )
    }
    items.forEach { item ->
        val checked = item.idx in selected
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { onToggle(item.idx) }
                .padding(vertical = 2.dp, horizontal = 2.dp)
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .background(
                        if (checked) Color(0xFF1E7D46) else Color.White,
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        1.dp,
                        if (checked) Color(0xFF1E7D46) else Color(0xFFB9B3A6),
                        RoundedCornerShape(4.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (checked) Text("✓", color = Color.White, fontSize = 11.sp)
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(10.dp)
                    .background(Color(item.colorHex ?: 0xFF9E9E9E), RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                item.name + if (item.mortgaged) " (mortgaged)" else "",
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CashStepper(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label: \$${money(value)}",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { onChange((value - 100).coerceIn(0, max)) }, enabled = value > 0) {
            Text("−100", fontSize = 10.sp)
        }
        TextButton(onClick = { onChange((value - 10).coerceIn(0, max)) }, enabled = value > 0) {
            Text("−10", fontSize = 10.sp)
        }
        TextButton(onClick = { onChange((value + 10).coerceAtMost(max)) }, enabled = value < max) {
            Text("+10", fontSize = 10.sp)
        }
        TextButton(onClick = { onChange((value + 100).coerceAtMost(max)) }, enabled = value < max) {
            Text("+100", fontSize = 10.sp)
        }
    }
}

@Composable
private fun CardStepper(label: String, value: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$label: $value / $max",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { onChange((value - 1).coerceIn(0, max)) }, enabled = value > 0) {
            Text("−1", fontSize = 10.sp)
        }
        TextButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max) {
            Text("+1", fontSize = 10.sp)
        }
    }
}
