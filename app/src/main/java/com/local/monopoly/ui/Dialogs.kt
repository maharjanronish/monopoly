package com.local.monopoly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.monopoly.game.BOARD
import com.local.monopoly.game.Deck
import com.local.monopoly.game.Engine
import com.local.monopoly.game.OWNABLE_SPACES
import com.local.monopoly.game.PropertyState
import com.local.monopoly.game.Space
import com.local.monopoly.game.SpaceType

// ---------------------------------------------------------------------------
// deed card (shared)
// ---------------------------------------------------------------------------

@Composable
fun DeedBody(space: Space) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Color(space.group?.colorHex ?: 0xFF8A8578))
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (space.group != null) {
                    Text("TITLE DEED", fontSize = 8.sp, color = Color(0xFF17130C), letterSpacing = 1.sp)
                }
                Text(
                    space.name,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = Color(0xFF17130C),
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        when (space.type) {
            SpaceType.STREET -> {
                DeedRow("Rent", space.baseRent)
                DeedRow("With 1 House", space.rent1)
                DeedRow("With 2 Houses", space.rent2)
                DeedRow("With 3 Houses", space.rent3)
                DeedRow("With 4 Houses", space.rent4)
                DeedRow("With HOTEL", space.rentHotel)
                DeedRow("House cost", space.houseCost, "each")
                DeedRow("Mortgage value", space.price / 2)
            }
            SpaceType.RAILROAD -> {
                DeedRow("1 Railroad", 25)
                DeedRow("2 Railroads", 50)
                DeedRow("3 Railroads", 100)
                DeedRow("4 Railroads", 200)
                DeedRow("Mortgage value", space.price / 2)
            }
            SpaceType.UTILITY -> {
                Text("If ONE Utility is owned, rent is 4× the dice roll.", fontSize = 11.sp, lineHeight = 15.sp)
                Text("If BOTH are owned, rent is 10× the dice roll.", fontSize = 11.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(4.dp))
                DeedRow("Mortgage value", space.price / 2)
            }
            else -> {}
        }
    }
}

@Composable
private fun DeedRow(label: String, value: Int, suffix: String = "") {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            if (suffix.isEmpty()) label else "$label ($suffix)",
            fontSize = 11.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            "\$${money(value)}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ---------------------------------------------------------------------------
// tile info
// ---------------------------------------------------------------------------

@Composable
fun DeedDialog(engine: Engine, idx: Int, onDismiss: () -> Unit) {
    val state = engine.state ?: return
    val space = BOARD[idx]
    val ps = state.props[idx] ?: PropertyState()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } },
        title = { Text(space.name, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                DeedBody(space)
                Spacer(Modifier.height(8.dp))
                when {
                    space.type !in OWNABLE_SPACES -> {}
                    ps.owner == null -> Text(
                        "Unowned — price \$${money(space.price)}",
                        fontSize = 11.sp,
                        color = Color(0xFF1E7D46),
                        fontWeight = FontWeight.SemiBold
                    )
                    else -> {
                        val owner = state.players.first { it.id == ps.owner }
                        Text(
                            "Owner: ${owner.token} ${owner.name}" +
                                if (ps.mortgaged) "  (MORTGAGED — no rent)" else "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (ps.houses == 5) Text("🏨 Hotel", fontSize = 11.sp)
                        else if (ps.houses > 0) Text("🏠 ${ps.houses} house(s)", fontSize = 11.sp)
                        val rent = engine.rentPreview(idx)
                        if (rent != null && rent > 0) {
                            Text("Rent if you land here: \$${money(rent)}", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    )
}

// ---------------------------------------------------------------------------
// buy decision
// ---------------------------------------------------------------------------

@Composable
fun BuyDialog(engine: Engine) {
    val state = engine.state ?: return
    val space = BOARD[state.currentPlayer.pos]
    val cash = state.currentPlayer.cash
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Buy ${space.name}?") },
        text = {
            Column {
                DeedBody(space)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Price \$${money(space.price)}   •   Your cash \$${money(cash)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            Button(onClick = { engine.buyCurrent() }, enabled = cash >= space.price) { Text("BUY") }
        },
        dismissButton = {
            TextButton(onClick = { engine.startAuction() }) { Text("AUCTION IT") }
        }
    )
}

// ---------------------------------------------------------------------------
// chance / community chest
// ---------------------------------------------------------------------------

@Composable
fun CardDialog(engine: Engine) {
    val state = engine.state ?: return
    val card = state.currentCard ?: return
    val isChance = card.deck == Deck.CHANCE
    AlertDialog(
        onDismissRequest = { },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isChance) "❓" else "🎁", fontSize = 20.sp)
                Spacer(Modifier.width(6.dp))
                Text(if (isChance) "Chance" else "Community Chest", fontWeight = FontWeight.Bold)
            }
        },
        text = { Text(card.text, fontSize = 14.sp, lineHeight = 19.sp) },
        confirmButton = { Button(onClick = { engine.confirmCard() }) { Text("OK") } }
    )
}

// ---------------------------------------------------------------------------
// income tax
// ---------------------------------------------------------------------------

@Composable
fun TaxDialog(engine: Engine) {
    val opts = engine.incomeTaxOptions() ?: return
    val (flat, pct) = opts
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Income Tax") },
        text = { Text("Choose how to pay:", fontSize = 13.sp) },
        confirmButton = {
            Button(onClick = { engine.chooseTax(true) }) { Text("PAY \$${money(flat)}") }
        },
        dismissButton = {
            TextButton(onClick = { engine.chooseTax(false) }) { Text("PAY 10% (\$${money(pct)})") }
        }
    )
}

// ---------------------------------------------------------------------------
// debt / bankruptcy
// ---------------------------------------------------------------------------

@Composable
fun DebtDialog(engine: Engine, onManage: () -> Unit, onTrade: () -> Unit) {
    val state = engine.state ?: return
    val d = state.debt ?: return
    val debtor = state.players.first { it.id == d.debtorId }
    val creditorName = d.creditorId
        ?.let { id -> state.players.first { p -> p.id == id }.name } ?: "the Bank"
    var confirmBk by remember { mutableStateOf(false) }

    if (confirmBk) {
        AlertDialog(
            onDismissRequest = { confirmBk = false },
            title = { Text("Declare bankruptcy?") },
            text = {
                Text("${debtor.name} gives everything to $creditorName and leaves the game. This cannot be undone.")
            },
            confirmButton = { Button(onClick = { engine.declareBankruptcy() }) { Text("BANKRUPT") } },
            dismissButton = { TextButton(onClick = { confirmBk = false }) { Text("CANCEL") } }
        )
    }

    AlertDialog(
        onDismissRequest = { },
        title = { Text("⚠️ ${debtor.name} owes \$${money(d.amount)}") },
        text = {
            Column {
                Text("Owed to $creditorName. Cash: \$${money(debtor.cash)}", fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Sell buildings, mortgage properties or make trades to raise funds — or declare bankruptcy.",
                    fontSize = 11.sp,
                    color = Color(0xFF6D675B),
                    lineHeight = 15.sp
                )
                if (d.moveDice != null) {
                    Text(
                        "(After paying you move by your roll of ${d.moveDice?.first}+${d.moveDice?.second}.)",
                        fontSize = 10.sp,
                        color = Color(0xFF6D675B)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { engine.payDebt() }, enabled = debtor.cash >= d.amount) {
                Text("PAY \$${money(d.amount)}")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onManage) { Text("🔨 SELL") }
                TextButton(onClick = onTrade) { Text("🤝 TRADE") }
                TextButton(onClick = { confirmBk = true }) {
                    Text("☠️", color = Color(0xFFB0473C))
                }
            }
        }
    )
}

// ---------------------------------------------------------------------------
// game over
// ---------------------------------------------------------------------------

@Composable
fun GameOverDialog(engine: Engine) {
    val state = engine.state ?: return
    val winner = state.players.first { it.id == state.winnerId }
    AlertDialog(
        onDismissRequest = { },
        title = { Text("🏆 ${winner.name} wins!") },
        text = {
            Column {
                Text("Final standings:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                val standings = state.players.sortedWith(compareBy({ it.bankrupt }, { -it.cash }))
                standings.forEach { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(p.token, fontSize = 14.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            p.name,
                            fontSize = 13.sp,
                            fontWeight = if (p.id == winner.id) FontWeight.Black else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (p.bankrupt) "bankrupt" else "\$${money(engine.netWorthOf(p.id))}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { engine.newGame() }) { Text("NEW GAME") } }
    )
}

// ---------------------------------------------------------------------------
// menu / rules / player details
// ---------------------------------------------------------------------------

@Composable
fun MenuDialog(engine: Engine, onRules: () -> Unit, onNew: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Menu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("✔ Progress is auto-saved after every action.", fontSize = 11.sp, color = Color(0xFF6D675B))
                Text("🎲 Dice use the phone's cryptographic random generator.", fontSize = 11.sp, color = Color(0xFF6D675B))
                Text("💾 Closing the app mid-turn resumes at that player's turn.", fontSize = 11.sp, color = Color(0xFF6D675B))
            }
        },
        confirmButton = { Button(onClick = onNew) { Text("NEW GAME") } },
        dismissButton = {
            Row {
                TextButton(onClick = onRules) { Text("RULES") }
                TextButton(onClick = onDismiss) { Text("CLOSE") }
            }
        }
    )
}

@Composable
fun RulesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rules quick reference") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                listOf(
                    "Roll the dice and act where you land. Doubles let you roll again — but 3 doubles in a row sends you straight to Jail.",
                    "Pass GO, collect \$200.",
                    "Land on an unowned space: buy it at the printed price, or it is auctioned — anyone can win it, minimum raise \$10.",
                    "Own a full color set: unimproved rent doubles. Build houses evenly across the set (one step at a time), then upgrade to a hotel.",
                    "The bank only has 32 houses and 12 hotels. When they're gone, nobody can build.",
                    "Mortgage a property for half its price; lift the mortgage later for mortgage value + 10%. No rent is collected on mortgaged property.",
                    "In Jail: pay \$50, use a Get Out of Jail Free card, or try to roll doubles — 3 attempts, then you must pay and move.",
                    "Income Tax: pay \$200 or 10% of your total worth. Luxury Tax: \$100.",
                    "You may trade properties (color set must be building-free), cash and jail cards with other players.",
                    "Can't pay a debt? Sell buildings and mortgage until you can — or declare bankruptcy. Your assets go to the creditor.",
                    "Last player standing wins."
                ).forEach { t ->
                    Text(
                        "• $t",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } }
    )
}

@Composable
fun PlayerDialog(engine: Engine, pid: Int, onDismiss: () -> Unit) {
    val state = engine.state ?: return
    val p = state.players.first { it.id == pid }
    val owned = BOARD
        .filter { state.props[it.index]?.owner == pid }
        .sortedWith(compareBy({ it.group?.ordinal ?: 8 }, { it.index }))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${p.token} ${p.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Cash: \$${money(p.cash)}   •   Net worth: \$${money(engine.netWorthOf(pid))}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                if (p.jailCardIds.isNotEmpty()) {
                    Text("🎫 Get Out of Jail Free ×${p.jailCardIds.size}", fontSize = 11.sp)
                }
                if (p.inJail) Text("🔒 In Jail", fontSize = 11.sp, color = Color(0xFFB0473C))
                Spacer(Modifier.height(6.dp))
                if (owned.isEmpty()) {
                    Text("No properties yet.", fontSize = 12.sp)
                }
                owned.forEach { sp ->
                    val ps = state.props.getValue(sp.index)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(Color(sp.group?.colorHex ?: 0xFF9E9E9E), RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(sp.name, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(
                            when {
                                ps.houses == 5 -> "🏨"
                                ps.houses > 0 -> "🏠×${ps.houses}"
                                ps.mortgaged -> "mortgaged"
                                else -> ""
                            },
                            fontSize = 11.sp,
                            color = if (ps.mortgaged) Color(0xFFB0473C) else Color.Unspecified
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } }
    )
}

@Composable
fun ConfirmNewGameDialog(engine: Engine, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a new game?") },
        text = { Text("The current game and its auto-save will be discarded.") },
        confirmButton = { Button(onClick = { engine.newGame() }) { Text("NEW GAME") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } }
    )
}
