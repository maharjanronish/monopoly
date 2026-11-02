package com.local.monopoly.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.monopoly.game.BOARD
import com.local.monopoly.game.Engine

/**
 * Ascending auction. The current bidder either raises (fixed increments,
 * minimum raise $10) or passes. The high bidder cannot pass. Hot-seat style:
 * the phone is handed around, the active bidder is highlighted.
 */
@Composable
fun AuctionDialog(engine: Engine) {
    val state = engine.state ?: return
    val a = state.auction ?: return
    val space = BOARD[a.propertyIndex]
    val bidder = state.players.first { it.id == a.bidderId }
    val minNext = a.currentBid + 10

    AlertDialog(
        onDismissRequest = { }, // an auction cannot be dismissed
        title = { Text("🔨 Auction — ${space.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    if (a.currentBid == 0) "No bids yet"
                    else "High bid: \$${money(a.currentBid)} — ${state.players.first { it.id == a.highestBidderId }.name}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Now bidding: ${bidder.token} ${bidder.name}  (cash \$${money(bidder.cash)})",
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    listOf(10, 50, 100).forEach { inc ->
                        val amt = a.currentBid + inc
                        OutlinedButton(
                            onClick = { engine.auctionBid(amt) },
                            enabled = amt <= bidder.cash,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp)
                        ) { Text("+$inc", fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                state.players.filter { !it.bankrupt }.forEach { p ->
                    val status = when {
                        p.id == a.highestBidderId -> "🏆 high bid"
                        p.id !in a.active -> "passed"
                        else -> "still in"
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${p.token} ${p.name}",
                            fontSize = 11.sp,
                            fontWeight = if (p.id == a.bidderId) FontWeight.Bold else FontWeight.Normal,
                            color = if (p.id == a.bidderId) Color(0xFFD32F2F) else Color.Unspecified,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            status,
                            fontSize = 11.sp,
                            color = Color(0xFF8A8578),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { engine.auctionBid(minNext) },
                enabled = minNext <= bidder.cash
            ) { Text("BID \$${money(minNext)}") }
        },
        dismissButton = {
            TextButton(
                onClick = { engine.auctionPass() },
                enabled = a.highestBidderId != a.bidderId
            ) { Text("PASS") }
        }
    )
}
