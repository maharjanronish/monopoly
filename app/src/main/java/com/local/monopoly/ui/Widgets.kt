package com.local.monopoly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.monopoly.game.GameState
import com.local.monopoly.game.Phase
import com.local.monopoly.game.Player

val GameState.currentPlayer: Player get() = players[currentTurn]

/** 1500 -> "1,500" (grouping without locale surprises). */
fun money(n: Int): String {
    if (n < 0) return "-" + money(-n)
    val s = n.toString()
    if (s.length <= 3) return s
    val sb = StringBuilder()
    for (i in s.indices) {
        sb.append(s[i])
        val remaining = s.length - 1 - i
        if (remaining > 0 && remaining % 3 == 0) sb.append(',')
    }
    return sb.toString()
}

@Composable
fun TokenChip(token: String, colorHex: Long, diameter: Dp = 20.dp, isCurrent: Boolean = false) {
    Box(
        Modifier
            .size(diameter)
            .background(Color(colorHex), CircleShape)
            .border(if (isCurrent) 2.dp else 1.2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(token, fontSize = (diameter.value * 0.52f).sp)
    }
}

@Composable
fun BigButton(
    text: String,
    bg: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SmallButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        enabled = enabled,
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

fun phaseHint(state: GameState): String = when (state.phase) {
    Phase.TURN_START -> if (state.lastRollWasDoubles) "Doubles! Roll again" else "Tap ROLL below"
    Phase.ROLLING -> "Rolling the dice…"
    Phase.MOVING -> "Moving…"
    Phase.BUY_DECISION -> "Buy or auction this property"
    Phase.AUCTION -> "Auction in progress"
    Phase.CARD_SHOWN -> "A card was drawn"
    Phase.TAX_DECISION -> "Choose how to pay the tax"
    Phase.JAIL_CHOICE -> "In Jail — choose an option"
    Phase.DEBT -> "A debt must be settled!"
    Phase.AWAIT_END_TURN -> "Build, trade, or end your turn"
    Phase.GAME_OVER -> "Game over"
}
