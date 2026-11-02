package com.local.monopoly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.monopoly.game.Engine
import com.local.monopoly.game.PlayerSetup

/** The 8 classic-style tokens (emoji = zero image assets, full color). */
private val TOKENS = listOf("🎩", "🚗", "🐕", "🚢", "👢", "🧵", "🐈", "🛒")

/** Seat colors for up to 6 players. */
private val PLAYER_COLORS = listOf(
    0xFFD32F2F, 0xFF1E6FB8, 0xFF2AA860, 0xFFE8720C, 0xFF7B1FA2, 0xFF00838F
)

@Composable
fun SetupScreen(engine: Engine) {
    var count by remember { mutableStateOf(2) }
    var names by remember { mutableStateOf(List(6) { "" }) }
    var tokens by remember { mutableStateOf(List(6) { TOKENS[it] }) }
    var saveDiscarded by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- logo ----
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .background(Color(0xFF17130C), RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    "MONOPOLY",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    fontSize = 34.sp,
                    letterSpacing = 2.sp
                )
            }
            Text(
                "Fast-dealing property game · hot-seat edition",
                fontSize = 11.sp,
                color = Color(0xFF8A8578)
            )
        }

        // ---- resume banner ----
        if (engine.hasSave() && !saveDiscarded) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFF3CD))
                    .border(1.dp, Color(0xFFE0C36A), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("💾 Saved game found", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Continue where you left off?", fontSize = 11.sp, color = Color(0xFF6D675B))
                }
                TextButton(onClick = { engine.resume() }) { Text("RESUME") }
                TextButton(onClick = { engine.discardSave(); saveDiscarded = true }) { Text("✕") }
            }
        }

        // ---- player count ----
        Text("HOW MANY PLAYERS?", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (2..6).forEach { n ->
                val selected = count == n
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (selected) Color(0xFFD32F2F) else Color.White)
                        .border(
                            2.dp,
                            if (selected) Color(0xFFD32F2F) else Color(0xFFD8D2C4),
                            CircleShape
                        )
                        .clickable {
                            count = n
                            tokens = dedupeTokens(tokens, n)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$n",
                        color = if (selected) Color.White else Color(0xFF4A453B),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }

        // ---- player rows ----
        (0 until count).forEach { i ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .background(Color(PLAYER_COLORS[i]), CircleShape)
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(tokens[i], fontSize = 14.sp)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    TextField(
                        value = names[i],
                        onValueChange = { v ->
                            names = names.toMutableList().also { list -> list[i] = v.take(12) }
                        },
                        label = { Text("Player ${i + 1} name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 40.dp)
                ) {
                    TOKENS.forEach { t ->
                        val taken = (0 until count).any { it != i && tokens[it] == t }
                        val selected = tokens[i] == t
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) Color(PLAYER_COLORS[i]) else Color(0xFFF1EDE2)
                                )
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) Color(0xFF17130C) else Color(0xFFD8D2C4),
                                    CircleShape
                                )
                                .clickable(enabled = !taken) {
                                    tokens = tokens.toMutableList().also { list -> list[i] = t }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                t,
                                fontSize = 15.sp,
                                color = if (taken) Color(0xFFB9B3A6) else Color.Unspecified
                            )
                        }
                    }
                }
            }
        }

        Text(
            "💵 Each player starts with \$1,500 — official breakdown: 2×\$500, 2×\$100, 2×\$50, 6×\$20, 5×\$10, 5×\$5, 5×\$1.",
            fontSize = 11.sp,
            color = Color(0xFF6D675B),
            lineHeight = 15.sp
        )

        Button(
            onClick = {
                engine.startGame(
                    (0 until count).map { PlayerSetup(names[it], tokens[it], PLAYER_COLORS[it]) }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("▶  START GAME", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }

        Text(
            "No ads · no internet · no permissions — dice are cryptographically random.",
            fontSize = 10.sp,
            color = Color(0xFFA29C8E),
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

/** Make sure no token is selected twice when the player count grows. */
private fun dedupeTokens(tokens: List<String>, count: Int): List<String> {
    val t = tokens.toMutableList()
    for (i in 0 until count) {
        for (j in i + 1 until count) {
            if (t[i] == t[j]) {
                val free = TOKENS.firstOrNull { tok -> (0 until count).none { k -> t[k] == tok } }
                if (free != null) t[j] = free
            }
        }
    }
    return t
}
