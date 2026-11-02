package com.local.monopoly.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.monopoly.game.Engine
import com.local.monopoly.game.Phase
import kotlinx.coroutines.delay

/**
 * The main game screen: header, board, player strip and a contextual
 * action area. Phase dialogs appear on top as needed.
 */
@Composable
fun GameScreen(engine: Engine) {
    val state = engine.state ?: return

    var visualPos by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var deedIdx by remember { mutableStateOf<Int?>(null) }
    var detailsId by remember { mutableStateOf<Int?>(null) }
    var showManage by remember { mutableStateOf(false) }
    var showTrade by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }

    // ---- token movement animation ----
    LaunchedEffect(state.moveAnimId) {
        val anim = state.moveAnim
        if (state.moveAnimId == 0 || anim == null) return@LaunchedEffect
        for (step in anim.path) {
            visualPos = visualPos + (anim.playerId to step)
            delay(70)
        }
        delay(150)
        engine.onMoveAnimationDone()
    }

    BackHandler { showMenu = true }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F0E4))
    ) {
        // ---- header ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "MONOPOLY",
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                fontSize = 16.sp,
                color = Color(0xFF17130C)
            )
            Spacer(Modifier.width(8.dp))
            Text("Turn ${state.turnCounter}", fontSize = 12.sp, color = Color(0xFF8A8578))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showMenu = true }) { Text("☰", fontSize = 16.sp) }
        }

        // ---- board ----
        Board(engine, visualPos, onTileTap = { deedIdx = it })

        // ---- players ----
        PlayerStrip(state, onTap = { detailsId = it })

        // ---- actions (scrolls on very small screens) ----
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ActionArea(
                engine,
                onManage = { showManage = true },
                onTrade = { showTrade = true }
            )
        }
    }

    // ---- phase-driven dialogs ----
    when (state.phase) {
        Phase.BUY_DECISION -> BuyDialog(engine)
        Phase.AUCTION -> AuctionDialog(engine)
        Phase.CARD_SHOWN -> CardDialog(engine)
        Phase.TAX_DECISION -> TaxDialog(engine)
        Phase.DEBT -> DebtDialog(
            engine,
            onManage = { showManage = true },
            onTrade = { showTrade = true }
        )
        Phase.GAME_OVER -> GameOverDialog(engine)
        else -> {}
    }

    // ---- optional dialogs ----
    val actorId = if (state.phase == Phase.DEBT) {
        state.debt?.debtorId ?: state.currentPlayer.id
    } else {
        state.currentPlayer.id
    }
    if (showManage) ManageDialog(engine, actorId) { showManage = false }
    if (showTrade) TradeDialog(engine, actorId) { showTrade = false }
    if (showMenu) {
        MenuDialog(
            engine,
            onRules = { showRules = true; showMenu = false },
            onNew = { confirmNew = true; showMenu = false },
            onDismiss = { showMenu = false }
        )
    }
    if (showRules) RulesDialog { showRules = false }
    if (confirmNew) ConfirmNewGameDialog(engine) { confirmNew = false }
    deedIdx?.let { DeedDialog(engine, it) { deedIdx = null } }
    detailsId?.let { PlayerDialog(engine, it) { detailsId = null } }
}

@Composable
private fun ActionArea(engine: Engine, onManage: () -> Unit, onTrade: () -> Unit) {
    val state = engine.state ?: return
    val cur = state.currentPlayer
    val canManage = state.phase in setOf(
        Phase.TURN_START, Phase.AWAIT_END_TURN, Phase.DEBT, Phase.JAIL_CHOICE
    )

    when (state.phase) {
        Phase.TURN_START -> {
            BigButton("🎲  ROLL THE DICE", Color(0xFFD32F2F)) { engine.rollDice() }
        }
        Phase.JAIL_CHOICE -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "🔒 ${cur.name} is in Jail — attempt ${cur.jailTurns + 1} of 3",
                    fontSize = 11.sp,
                    color = Color(0xFF6D675B)
                )
                BigButton("🎲  ROLL FOR DOUBLES", Color(0xFFB8860B)) { engine.jailRoll() }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallButton(
                        "💳 PAY \$50",
                        Modifier.weight(1f),
                        cur.cash >= 50
                    ) { engine.jailPay() }
                    SmallButton(
                        "🎫 USE CARD (${cur.jailCardIds.size})",
                        Modifier.weight(1f),
                        cur.jailCardIds.isNotEmpty()
                    ) { engine.jailUseCard() }
                }
            }
        }
        Phase.AWAIT_END_TURN -> {
            BigButton("➡️  END ${cur.name.uppercase()}'S TURN", Color(0xFF1E7D46)) { engine.endTurn() }
        }
        Phase.ROLLING, Phase.MOVING -> {
            BigButton("…", Color(0xFF9E9E9E), enabled = false) { }
        }
        Phase.GAME_OVER -> {
            BigButton("🎉  NEW GAME", Color(0xFFD32F2F)) { engine.newGame() }
        }
        Phase.DEBT -> {
            val debtorName = state.debt?.let { d -> state.players.first { it.id == d.debtorId }.name } ?: ""
            Text(
                "⚠️ $debtorName must settle a debt — see the dialog.",
                fontSize = 12.sp,
                color = Color(0xFFB0473C),
                fontWeight = FontWeight.SemiBold
            )
        }
        else -> {}
    }

    Spacer(Modifier.height(2.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SmallButton("🔨 BUILD / MORTGAGE", Modifier.weight(1f), canManage, onManage)
        SmallButton("🤝 TRADE", Modifier.weight(1f), canManage, onTrade)
    }

    state.log.takeLast(2).forEach {
        Text(it, fontSize = 9.sp, color = Color(0xFF8A8578), maxLines = 1)
    }
}
