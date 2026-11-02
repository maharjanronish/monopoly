package com.local.monopoly.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.local.monopoly.game.BOARD
import com.local.monopoly.game.Engine
import com.local.monopoly.game.GameState
import com.local.monopoly.game.PropertyState
import com.local.monopoly.game.Space
import com.local.monopoly.game.SpaceType

/**
 * The 40-space board, drawn entirely with layout + color — no images.
 * GO is the bottom-right corner; tokens move left, up, right, down.
 */
@Composable
fun Board(engine: Engine, visualPos: Map<Int, Int>, onTileTap: (Int) -> Unit) {
    val state = engine.state ?: return
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BoardBg)
            .border(3.dp, BoardBorder, RoundedCornerShape(12.dp))
    ) {
        val s = maxWidth.value          // board side in dp
        val c = s * 0.125f              // corner size
        val t = (s - 2f * c) / 9f       // side tile size

        // ---- the 40 tiles ----
        BOARD.forEach { space ->
            val r = tileRect(space.index, s, c, t)
            val ps = state.props[space.index]
            Box(
                Modifier
                    .offset(r.x.dp, r.y.dp)
                    .size(r.w.dp, r.h.dp)
                    .background(TileBg)
                    .border(0.5.dp, GridLine)
                    .clickable { onTileTap(space.index) }
            ) {
                TileContent(space, ps, state, r, edgeOf(space.index))
            }
        }

        // ---- center panel ----
        CenterPanel(engine, s, c)

        // ---- player tokens ----
        val order = state.players.filter { !it.bankrupt }
            .sortedBy { if (it.id == state.currentPlayer.id) 1 else 0 }
        order.forEach { p ->
            val posIdx = visualPos[p.id] ?: p.pos
            val r = tileRect(posIdx, s, c, t)
            val ts = t * 0.62f
            val spread = minOf(r.w, r.h) * 0.30f
            val col = p.id % 3
            val row = p.id / 3
            val tx = r.x + r.w / 2f + (col - 1) * spread - ts / 2f
            val ty = r.y + r.h / 2f + (row - 1) * spread - ts / 2f
            val isCur = p.id == state.currentPlayer.id
            val ax by animateDpAsState(tx.dp, tween(durationMillis = 70))
            val ay by animateDpAsState(ty.dp, tween(durationMillis = 70))
            Box(Modifier.offset(ax, ay).size(ts.dp)) {
                TokenChip(p.token, p.colorHex, ts.dp, isCur)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// tile geometry
// ---------------------------------------------------------------------------

private val BoardBg = Color(0xFFE7E2D0)
private val TileBg = Color(0xFFFFFFFF)
private val BoardBorder = Color(0xFF17130C)
private val GridLine = Color(0x30808080)

private enum class Edge { BOTTOM, LEFT, TOP, RIGHT }

private fun edgeOf(i: Int): Edge = when {
    i in 1..9 -> Edge.BOTTOM
    i in 11..19 -> Edge.LEFT
    i in 21..29 -> Edge.TOP
    i in 31..39 -> Edge.RIGHT
    else -> Edge.BOTTOM
}

private class TileRect(val x: Float, val y: Float, val w: Float, val h: Float)

private fun tileRect(i: Int, s: Float, c: Float, t: Float): TileRect = when {
    i == 0 -> TileRect(s - c, s - c, c, c)                    // GO
    i in 1..9 -> TileRect(s - c - t * i, s - c, t, c)         // bottom row
    i == 10 -> TileRect(0f, s - c, c, c)                      // Jail
    i in 11..19 -> TileRect(0f, s - c - t * (i - 10), c, t)   // left column
    i == 20 -> TileRect(0f, 0f, c, c)                         // Free Parking
    i in 21..29 -> TileRect(c + t * (i - 21), 0f, t, c)       // top row
    i == 30 -> TileRect(s - c, 0f, c, c)                      // Go To Jail
    else -> TileRect(s - c, c + t * (i - 31), c, t)           // right column
}

// ---------------------------------------------------------------------------
// tile content
// ---------------------------------------------------------------------------

@Composable
private fun BoxScope.TileContent(
    space: Space,
    ps: PropertyState?,
    state: GameState,
    r: TileRect,
    edge: Edge
) {
    val ownerColor = ps?.owner?.let { oid ->
        state.players.firstOrNull { it.id == oid }?.let { Color(it.colorHex) }
    }
    when (space.type) {
        SpaceType.STREET -> {
            val barThick = minOf(r.w, r.h) * 0.28f
            when (edge) {
                Edge.BOTTOM -> Column(Modifier.fillMaxSize()) {
                    HousesBar(space, ps, Modifier.fillMaxWidth().height(barThick.dp))
                    TileTexts(space, Modifier.weight(1f).fillMaxWidth())
                    OwnerStrip(ownerColor, ps?.mortgaged == true, Modifier.fillMaxWidth().height(2.5.dp))
                }
                Edge.TOP -> Column(Modifier.fillMaxSize()) {
                    OwnerStrip(ownerColor, ps?.mortgaged == true, Modifier.fillMaxWidth().height(2.5.dp))
                    TileTexts(space, Modifier.weight(1f).fillMaxWidth())
                    HousesBar(space, ps, Modifier.fillMaxWidth().height(barThick.dp))
                }
                Edge.LEFT -> Row(Modifier.fillMaxSize()) {
                    OwnerStrip(ownerColor, ps?.mortgaged == true, Modifier.width(2.5.dp).fillMaxHeight())
                    TileTexts(space, Modifier.weight(1f).fillMaxHeight())
                    HousesBar(space, ps, Modifier.width(barThick.dp).fillMaxHeight())
                }
                Edge.RIGHT -> Row(Modifier.fillMaxSize()) {
                    HousesBar(space, ps, Modifier.width(barThick.dp).fillMaxHeight())
                    TileTexts(space, Modifier.weight(1f).fillMaxHeight())
                    OwnerStrip(ownerColor, ps?.mortgaged == true, Modifier.width(2.5.dp).fillMaxHeight())
                }
            }
            if (ps?.mortgaged == true) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.5f)))
            }
        }
        SpaceType.GO -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "GO",
                color = Color(0xFFC62828),
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(1.dp))
            Canvas(Modifier.size(16.dp, 8.dp)) {
                val path = Path()
                path.moveTo(0f, size.height / 2f)
                path.lineTo(size.width, size.height * 0.12f)
                path.lineTo(size.width, size.height * 0.88f)
                path.close()
                drawPath(path, Color(0xFFC62828))
            }
            Text(
                "COLLECT \$200 SALARY AS YOU PASS",
                fontSize = 5.sp,
                lineHeight = 5.5.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFF4A453B)
            )
        }
        SpaceType.JAIL_VISITING -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🚔", fontSize = 13.sp)
            Text("JAIL", fontWeight = FontWeight.Black, fontSize = 9.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                repeat(3) { Box(Modifier.size(2.dp, 8.dp).background(Color(0xFF17130C))) }
            }
            Text(
                "JUST\nVISITING",
                fontSize = 5.5.sp,
                lineHeight = 6.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFF6D675B)
            )
        }
        SpaceType.FREE_PARKING -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("FREE", color = Color(0xFFC62828), fontWeight = FontWeight.Black, fontSize = 10.sp)
            Text("PARKING", fontSize = 6.5.sp, color = Color(0xFF4A453B))
            Text("🚗", fontSize = 13.sp)
        }
        SpaceType.GO_TO_JAIL -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🚨", fontSize = 13.sp)
            Text("GO TO", fontSize = 6.sp, color = Color(0xFF4A453B))
            Text("JAIL", fontWeight = FontWeight.Black, fontSize = 9.sp)
        }
        SpaceType.CHANCE -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("❓", fontSize = 13.sp)
            Text("CHANCE", fontSize = 6.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4A453B))
        }
        SpaceType.COMMUNITY_CHEST -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🎁", fontSize = 12.sp)
            Text(
                "COMMUNITY\nCHEST",
                fontSize = 5.5.sp,
                lineHeight = 6.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = Color(0xFF4A453B)
            )
        }
        SpaceType.INCOME_TAX -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("💰", fontSize = 12.sp)
            Text(
                "INCOME\nTAX",
                fontSize = 6.5.sp,
                lineHeight = 7.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "PAY \$200\nOR 10%",
                fontSize = 5.5.sp,
                lineHeight = 6.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFF6D675B)
            )
        }
        SpaceType.LUXURY_TAX -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("💎", fontSize = 12.sp)
            Text(
                "LUXURY\nTAX",
                fontSize = 6.5.sp,
                lineHeight = 7.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
            Text("PAY \$100", fontSize = 5.5.sp, color = Color(0xFF6D675B))
        }
        SpaceType.RAILROAD, SpaceType.UTILITY -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(space.icon, fontSize = 12.sp)
            space.tileLabel.forEach {
                Text(
                    it,
                    fontSize = 6.sp,
                    lineHeight = 6.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
            Text("\$${space.price}", fontSize = 6.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TileTexts(space: Space, modifier: Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        space.tileLabel.forEach { line ->
            Text(
                line,
                fontSize = 6.5.sp,
                lineHeight = 7.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFF17130C),
                maxLines = 1
            )
        }
        if (space.price > 0) {
            Text(
                "\$${space.price}",
                fontSize = 6.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3E3A31)
            )
        }
    }
}

/** The colored inner-edge bar, carrying houses / the hotel. */
@Composable
private fun HousesBar(space: Space, ps: PropertyState?, modifier: Modifier) {
    Box(
        modifier.background(Color(space.group!!.colorHex)),
        contentAlignment = Alignment.Center
    ) {
        val h = ps?.houses ?: 0
        when {
            h in 1..4 -> Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                repeat(h) {
                    Box(
                        Modifier
                            .size(3.dp)
                            .background(Color(0xFF1E8E3E), RoundedCornerShape(1.dp))
                            .border(0.5.dp, Color.White)
                    )
                }
            }
            h == 5 -> Box(
                Modifier
                    .size(10.dp, 5.dp)
                    .background(Color(0xFFC62828), RoundedCornerShape(1.dp))
                    .border(0.5.dp, Color.White)
            )
        }
    }
}

/** Thin strip on the outer edge showing who owns the tile. */
@Composable
private fun OwnerStrip(ownerColor: Color?, mortgaged: Boolean, modifier: Modifier) {
    val bg = when {
        ownerColor == null -> Color.Transparent
        mortgaged -> Color(0xFF6E6A5E)
        else -> ownerColor
    }
    Box(modifier.background(bg))
}

// ---------------------------------------------------------------------------
// center panel
// ---------------------------------------------------------------------------

@Composable
private fun BoxScope.CenterPanel(engine: Engine, s: Float, c: Float) {
    val state = engine.state ?: return
    val inner = s - 2f * c
    Column(
        Modifier
            .offset(c.dp, c.dp)
            .size(inner.dp, inner.dp)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // the two classic card decks, slightly tilted
        Box(Modifier.fillMaxWidth().height(42.dp)) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .graphicsLayer { rotationZ = -12f }
                    .size(34.dp)
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .border(1.5.dp, Color(0xFFF7941D), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) { Text("❓", fontSize = 15.sp) }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer { rotationZ = 10f }
                    .size(34.dp)
                    .background(Color.White, RoundedCornerShape(4.dp))
                    .border(1.5.dp, Color(0xFF1E88E5), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) { Text("🎁", fontSize = 14.sp) }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .graphicsLayer { rotationZ = -5f }
                .background(Color(0xFFD32F2F), RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                "MONOPOLY",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                fontSize = 18.sp,
                letterSpacing = 1.5.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        DicePair(engine)
        Spacer(Modifier.height(8.dp))
        val cur = state.currentPlayer
        Row(verticalAlignment = Alignment.CenterVertically) {
            TokenChip(cur.token, cur.colorHex, 18.dp, true)
            Spacer(Modifier.width(5.dp))
            Text(cur.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            phaseHint(state),
            fontSize = 9.5.sp,
            color = Color(0xFF6D675B),
            textAlign = TextAlign.Center
        )
    }
}
