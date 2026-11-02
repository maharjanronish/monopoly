package com.local.monopoly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.monopoly.game.BOARD
import com.local.monopoly.game.Engine
import com.local.monopoly.game.SpaceType

/**
 * Build / sell houses & hotels and manage mortgages for one player.
 * The engine enforces the even-build rule, bank stock and cash limits;
 * any error message is shown at the top of the list.
 */
@Composable
fun ManageDialog(engine: Engine, playerId: Int, onDismiss: () -> Unit) {
    val state = engine.state ?: return
    val p = state.players.first { it.id == playerId }
    var err by remember { mutableStateOf<String?>(null) }

    val owned = BOARD
        .filter { state.props[it.index]?.owner == playerId }
        .sortedWith(compareBy({ it.group?.ordinal ?: 8 }, { it.index }))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔨 ${p.name} — cash \$${money(p.cash)}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Bank stock: ${state.housesStock} 🏠 / ${state.hotelsStock} 🏨 available",
                    fontSize = 11.sp,
                    color = Color(0xFF6D675B)
                )
                err?.let {
                    Text(it, color = Color(0xFFC62828), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.padding(2.dp))
                if (owned.isEmpty()) {
                    Text("No properties yet — nothing to manage.", fontSize = 12.sp)
                }
                owned.forEach { space ->
                    val ps = state.props.getValue(space.index)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(
                                    Color(space.group?.colorHex ?: 0xFF9E9E9E),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                space.name,
                                fontSize = 11.sp,
                                fontWeight = if (ps.mortgaged) FontWeight.Normal else FontWeight.SemiBold
                            )
                            Text(
                                when {
                                    ps.houses == 5 -> "🏨 Hotel"
                                    ps.houses > 0 -> "🏠 ×${ps.houses}"
                                    ps.mortgaged -> "mortgaged"
                                    else -> "no buildings"
                                },
                                fontSize = 9.sp,
                                color = Color(0xFF8A8578)
                            )
                        }
                        if (space.type == SpaceType.STREET) {
                            TextButton(
                                onClick = { err = engine.buildHouse(playerId, space.index) },
                                enabled = ps.houses < 5 && !ps.mortgaged,
                                modifier = Modifier.padding(0.dp)
                            ) { Text("＋\$${space.houseCost}", fontSize = 10.sp) }
                            TextButton(
                                onClick = { err = engine.sellBuilding(playerId, space.index) },
                                enabled = ps.houses > 0,
                                modifier = Modifier.padding(0.dp)
                            ) { Text("－", fontSize = 12.sp) }
                        }
                        if (ps.mortgaged) {
                            val cost = space.price / 2 + space.price / 20
                            TextButton(
                                onClick = { err = engine.unmortgage(playerId, space.index) },
                                enabled = p.cash >= cost,
                                modifier = Modifier.padding(0.dp)
                            ) { Text("Lift \$${money(cost)}", fontSize = 10.sp) }
                        } else {
                            val groupClear = space.type != SpaceType.STREET ||
                                BOARD.filter { it.group == space.group }
                                    .all { state.props.getValue(it.index).houses == 0 }
                            TextButton(
                                onClick = { err = engine.mortgage(playerId, space.index) },
                                enabled = groupClear,
                                modifier = Modifier.padding(0.dp)
                            ) { Text("Mtg +\$${money(space.price / 2)}", fontSize = 10.sp) }
                        }
                    }
                }
                if (state.phase == com.local.monopoly.game.Phase.DEBT) {
                    Spacer(Modifier.padding(3.dp))
                    Text(
                        "⚠️ You are settling a debt — raise cash, then PAY in the debt dialog.",
                        fontSize = 10.sp,
                        color = Color(0xFFB0473C)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } }
    )
}
