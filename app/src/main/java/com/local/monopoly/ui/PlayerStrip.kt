package com.local.monopoly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.monopoly.game.GameState

/** Horizontal strip of player cards. Tap one to inspect their properties. */
@Composable
fun PlayerStrip(state: GameState, onTap: (Int) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        items(state.players, key = { it.id }) { p ->
            val cur = p.id == state.currentPlayer.id
            Column(
                Modifier
                    .width(76.dp)
                    .alpha(if (p.bankrupt) 0.45f else 1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (cur) Color(p.colorHex).copy(alpha = 0.16f) else Color.White)
                    .border(
                        if (cur) 2.dp else 1.dp,
                        if (cur) Color(p.colorHex) else Color(0xFFD8D2C4),
                        RoundedCornerShape(9.dp)
                    )
                    .clickable { onTap(p.id) }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.token, fontSize = 11.sp)
                    Spacer(Modifier.width(3.dp))
                    Text(
                        p.name,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    if (p.bankrupt) "OUT" else "\$${money(p.cash)}",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (p.bankrupt) Color(0xFFB0473C) else Color(0xFF17130C)
                )
                Text(
                    "${state.props.values.count { it.owner == p.id }} properties",
                    fontSize = 8.sp,
                    color = Color(0xFF8A8578)
                )
            }
        }
    }
}
