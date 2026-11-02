package com.local.monopoly.game

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Auto-save: the whole GameState is serialized to JSON and stored in
 * SharedPreferences after every player action (using apply(), so it never
 * blocks the UI thread). No extra library needed — org.json ships with Android.
 *
 * If the app is killed in the middle of a dice roll / move animation / open
 * dialog, the state is saved at the last "safe" point: the game resumes at the
 * start of the current player's turn (they simply roll again).
 */
class SaveStore(context: Context) {

    private val prefs = context.getSharedPreferences("monopoly_save", Context.MODE_PRIVATE)

    fun exists(): Boolean = prefs.contains("state")

    fun clear() {
        prefs.edit().remove("state").apply()
    }

    fun save(s0: GameState) {
        try {
            val stable = s0.phase in SAFE_PHASES
            val s = if (stable) s0 else s0.copy(
                phase = if (s0.players[s0.currentTurn].inJail) Phase.JAIL_CHOICE else Phase.TURN_START,
                currentCard = null,
                auction = null,
                moveAnim = null,
                rentMultiplier = 1,
                diceAnimId = 0,
                moveAnimId = 0,
                lastRollWasDoubles = false,
                consecutiveDoubles = 0
            )

            val o = JSONObject()
            o.put("v", 1)
            o.put("turn", s.currentTurn)
            o.put("turnCounter", s.turnCounter)
            o.put("phase", s.phase.name)
            s.dice?.let { o.put("dice1", it.first); o.put("dice2", it.second) }
            o.put("consec", s.consecutiveDoubles)
            o.put("doubles", s.lastRollWasDoubles)

            val pa = JSONArray()
            s.players.forEach { p ->
                val po = JSONObject()
                po.put("id", p.id)
                po.put("name", p.name)
                po.put("token", p.token)
                po.put("color", p.colorHex)
                po.put("cash", p.cash)
                po.put("pos", p.pos)
                po.put("jail", p.inJail)
                po.put("jt", p.jailTurns)
                po.put("cards", JSONArray(p.jailCardIds))
                po.put("out", p.bankrupt)
                pa.put(po)
            }
            o.put("players", pa)

            val propsObj = JSONObject()
            s.props.forEach { (idx, ps) ->
                if (ps.owner != null || ps.houses > 0 || ps.mortgaged) {
                    val po = JSONObject()
                    po.put("owner", ps.owner ?: -1)
                    po.put("houses", ps.houses)
                    po.put("mort", ps.mortgaged)
                    propsObj.put(idx.toString(), po)
                }
            }
            o.put("props", propsObj)

            o.put("chance", JSONArray(s.chancePile))
            o.put("chest", JSONArray(s.chestPile))
            o.put("housesStock", s.housesStock)
            o.put("hotelsStock", s.hotelsStock)

            s.debt?.let { d ->
                val dj = JSONObject()
                dj.put("debtor", d.debtorId)
                d.creditorId?.let { c -> dj.put("cred", c) }
                dj.put("amount", d.amount)
                dj.put("resume", d.resume.name)
                d.moveDice?.let { dj.put("md1", it.first); dj.put("md2", it.second) }
                o.put("debt", dj)
            }

            val pend = JSONArray()
            s.pending.forEach { pq ->
                val pj = JSONObject()
                pj.put("from", pq.fromId)
                pq.toId?.let { pj.put("to", it) }
                pj.put("amount", pq.amount)
                pend.put(pj)
            }
            o.put("pending", pend)

            val lg = JSONArray()
            s.log.takeLast(12).forEach { lg.put(it) }
            o.put("log", lg)
            s.winnerId?.let { o.put("winner", it) }

            prefs.edit().putString("state", o.toString()).apply()
        } catch (_: Exception) {
            // Saving must never crash the game.
        }
    }

    fun load(): GameState? {
        try {
            val raw = prefs.getString("state", null) ?: return null
            val o = JSONObject(raw)

            val players = ArrayList<Player>()
            val pa = o.getJSONArray("players")
            for (i in 0 until pa.length()) {
                val po = pa.getJSONObject(i)
                val cards = ArrayList<Int>()
                val ca = po.optJSONArray("cards") ?: JSONArray()
                for (j in 0 until ca.length()) cards.add(ca.getInt(j))
                players.add(
                    Player(
                        id = po.getInt("id"),
                        name = po.getString("name"),
                        token = po.getString("token"),
                        colorHex = po.getLong("color"),
                        cash = po.getInt("cash"),
                        pos = po.getInt("pos").coerceIn(0, 39),
                        inJail = po.getBoolean("jail"),
                        jailTurns = po.getInt("jt"),
                        jailCardIds = cards,
                        bankrupt = po.getBoolean("out")
                    )
                )
            }
            if (players.size < 2) return null

            val ownable = BOARD.filter { it.type in OWNABLE_SPACES }.map { it.index }.toSet()
            val props = HashMap<Int, PropertyState>()
            ownable.forEach { props[it] = PropertyState() }
            val propsObj = o.optJSONObject("props") ?: JSONObject()
            propsObj.keys().forEach { k ->
                val idx = k.toInt()
                val po = propsObj.getJSONObject(k)
                if (idx in ownable) {
                    val owner = if (po.getInt("owner") == -1) null else po.getInt("owner")
                    props[idx] = PropertyState(
                        owner = if (owner != null && players.any { it.id == owner }) owner else null,
                        houses = po.getInt("houses").coerceIn(0, 5),
                        mortgaged = po.getBoolean("mort")
                    )
                }
            }

            fun pile(key: String, deck: Deck): List<Int> {
                val arr = o.optJSONArray(key) ?: JSONArray()
                val ids = ArrayList<Int>()
                for (i in 0 until arr.length()) ids.add(arr.getInt(i))
                return ids.filter { id -> ALL_CARDS.any { c -> c.id == id && c.deck == deck } }
            }

            var debt: DebtInfo? = null
            val dj = o.optJSONObject("debt")
            if (dj != null) {
                val md1 = dj.optInt("md1", 0)
                val md2 = dj.optInt("md2", 0)
                debt = DebtInfo(
                    debtorId = dj.getInt("debtor"),
                    creditorId = if (dj.has("cred")) dj.getInt("cred") else null,
                    amount = dj.getInt("amount"),
                    resume = try { DebtResume.valueOf(dj.getString("resume")) } catch (_: Exception) { DebtResume.LANDING },
                    moveDice = if (md1 in 1..6 && md2 in 1..6) Pair(md1, md2) else null
                )
            }

            val pending = ArrayList<PendingPayment>()
            val parr = o.optJSONArray("pending") ?: JSONArray()
            for (i in 0 until parr.length()) {
                val pj = parr.getJSONObject(i)
                pending.add(
                    PendingPayment(
                        fromId = pj.getInt("from"),
                        toId = if (pj.has("to")) pj.getInt("to") else null,
                        amount = pj.getInt("amount")
                    )
                )
            }

            val log = ArrayList<String>()
            val larr = o.optJSONArray("log") ?: JSONArray()
            for (i in 0 until larr.length()) log.add(larr.getString(i))

            val phase = try { Phase.valueOf(o.getString("phase")) } catch (_: Exception) { Phase.TURN_START }

            return GameState(
                players = players,
                currentTurn = o.getInt("turn").coerceIn(0, players.size - 1),
                turnCounter = o.optInt("turnCounter", 1),
                phase = phase,
                dice = if (o.has("dice1")) Pair(o.getInt("dice1").coerceIn(1, 6), o.getInt("dice2").coerceIn(1, 6)) else null,
                consecutiveDoubles = 0,
                lastRollWasDoubles = false,
                props = props,
                housesStock = o.optInt("housesStock", BANK_HOUSES),
                hotelsStock = o.optInt("hotelsStock", BANK_HOTELS),
                chancePile = pile("chance", Deck.CHANCE),
                chestPile = pile("chest", Deck.COMMUNITY_CHEST),
                currentCard = null,
                auction = null,
                debt = debt,
                pending = pending,
                rentMultiplier = 1,
                moveAnim = null,
                diceAnimId = 0,
                moveAnimId = 0,
                winnerId = if (o.has("winner")) o.getInt("winner") else null,
                log = log
            )
        } catch (_: Exception) {
            return null
        }
    }

    companion object {
        private val SAFE_PHASES = setOf(
            Phase.TURN_START, Phase.JAIL_CHOICE, Phase.AWAIT_END_TURN, Phase.DEBT, Phase.GAME_OVER
        )
    }
}
