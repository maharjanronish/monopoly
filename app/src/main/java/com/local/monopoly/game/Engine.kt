package com.local.monopoly.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The rules engine: a pure state machine over [GameState].
 * The UI calls the public functions (usually from button handlers) and
 * re-renders from [state]. Two callbacks — [onDiceAnimationDone] and
 * [onMoveAnimationDone] — are invoked by the UI when its animations finish.
 *
 * Rules implemented (official US Monopoly):
 *  - $1500 starting cash, $200 salary for passing/landing on GO
 *  - doubles roll again; 3 consecutive doubles -> Jail (no move)
 *  - buy or auction unowned property (declining always auctions)
 *  - rent: doubled unimproved rent on a full color set; house/hotel rents;
 *    railroads 25/50/100/200; utilities 4x / 10x dice
 *  - even-build rule, 32 house / 12 hotel bank stock, half-price sell-back
 *  - mortgages (unmortgage costs mortgage + 10%), no rent while mortgaged
 *  - Jail: pay $50, use a card, or try doubles 3 times
 *  - Income Tax $200 or 10% of net worth; Luxury Tax $100
 *  - 16 Chance + 16 Community Chest cards incl. repairs and per-player payments
 *  - trading properties (no buildings on the group), cash and jail cards
 *  - bankruptcy: assets to the creditor (buildings sold to the bank at half
 *    price, 10% fee on mortgaged titles) or back to the bank
 */
class Engine(private val saver: SaveStore) {

    var state: GameState? by mutableStateOf(null)
        private set

    // ------------------------------------------------------------------
    // lifecycle
    // ------------------------------------------------------------------

    fun hasSave(): Boolean = saver.exists()

    fun resume() {
        state = saver.load()
    }

    fun discardSave() {
        saver.clear()
    }

    fun newGame() {
        saver.clear()
        state = null
    }

    fun startGame(setups: List<PlayerSetup>) {
        if (setups.size < 2) return
        val players = setups.mapIndexed { i, s ->
            Player(i, s.name.ifBlank { "Player ${i + 1}" }, s.token, s.colorHex)
        }
        val props = BOARD.filter { it.type in OWNABLE_SPACES }.associate { it.index to PropertyState() }
        state = GameState(
            players = players,
            props = props,
            chancePile = shuffledIds(Deck.CHANCE),
            chestPile = shuffledIds(Deck.COMMUNITY_CHEST),
            log = listOf("🎬 New game — ${players.size} players, \$1,500 each. ${players.first().name} goes first!")
        )
        maybeSave()
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun GameState.cur(): Player = players[currentTurn]

    private fun GameState.player(id: Int): Player = players.first { it.id == id }

    private fun GameState.withPlayer(id: Int, transform: (Player) -> Player): GameState =
        copy(players = players.map { if (it.id == id) transform(it) else it })

    private fun GameState.push(msg: String): GameState =
        copy(log = (log + msg).takeLast(40))

    private fun mutate(transform: (GameState) -> GameState) {
        val s = state ?: return
        state = transform(s)
        maybeSave()
    }

    private fun maybeSave() {
        val s = state ?: return
        if (s.phase == Phase.GAME_OVER) saver.clear() else saver.save(s)
    }

    private fun shuffledIds(deck: Deck): List<Int> {
        val ids = ALL_CARDS.filter { it.deck == deck }.map { it.id }.toMutableList()
        java.util.Collections.shuffle(ids, Dice.rng)
        return ids
    }

    /** Recompute the bank's building stock from what is on the board. */
    private fun restock(s: GameState): GameState {
        val housesOnBoard = s.props.values.sumOf { if (it.houses == 5) 0 else it.houses }
        val hotelsOnBoard = s.props.values.count { it.houses == 5 }
        return s.copy(
            housesStock = (BANK_HOUSES - housesOnBoard).coerceAtLeast(0),
            hotelsStock = (BANK_HOTELS - hotelsOnBoard).coerceAtLeast(0)
        )
    }

    fun netWorthOf(pid: Int): Int {
        val s = state ?: return 0
        return netWorth(s, pid)
    }

    private fun netWorth(s: GameState, id: Int): Int {
        var w = s.player(id).cash
        s.props.forEach { (idx, ps) ->
            if (ps.owner == id) {
                w += BOARD[idx].price
                if (ps.houses in 1..4) w += ps.houses * BOARD[idx].houseCost
                else if (ps.houses == 5) w += BOARD[idx].houseCost
            }
        }
        return w
    }

    fun propertiesOf(pid: Int): List<Int> {
        val s = state ?: return emptyList()
        return s.props.filter { it.value.owner == pid }.keys.sorted()
    }

    /** Properties of [pid] that may be traded (no buildings standing on the color group). */
    fun tradableProperties(pid: Int): List<Int> {
        val s = state ?: return emptyList()
        return s.props.filter { (idx, ps) ->
            ps.owner == pid && (BOARD[idx].type != SpaceType.STREET ||
                BOARD.none { it.group == BOARD[idx].group && s.props.getValue(it.index).houses > 0 })
        }.keys.sorted()
    }

    fun rentPreview(idx: Int): Int? {
        val s = state ?: return null
        val space = BOARD.getOrNull(idx) ?: return null
        val ps = s.props[idx] ?: return null
        val owner = ps.owner ?: return null
        if (ps.mortgaged) return 0
        return rentFor(s, space, ps, owner, 1)
    }

    // ------------------------------------------------------------------
    // turn flow
    // ------------------------------------------------------------------

    fun rollDice() {
        mutate { s ->
            if (s.phase != Phase.TURN_START) return@mutate s
            val d = Dice.roll()
            s.copy(
                phase = Phase.ROLLING,
                dice = d,
                rollContext = RollContext.NORMAL,
                lastRollWasDoubles = Dice.isDoubles(d),
                diceAnimId = s.diceAnimId + 1
            )
        }
    }

    fun jailRoll() {
        mutate { s ->
            if (s.phase != Phase.JAIL_CHOICE) return@mutate s
            val d = Dice.roll()
            s.copy(
                phase = Phase.ROLLING,
                dice = d,
                rollContext = RollContext.JAIL,
                lastRollWasDoubles = false,
                diceAnimId = s.diceAnimId + 1
            )
        }
    }

    /** Called by the UI after the dice animation settles. */
    fun onDiceAnimationDone() {
        mutate { s ->
            if (s.phase != Phase.ROLLING) return@mutate s
            val d = s.dice ?: return@mutate s
            when (s.rollContext) {
                RollContext.NORMAL -> {
                    val dbl = Dice.isDoubles(d)
                    if (dbl) {
                        val consec = s.consecutiveDoubles + 1
                        if (consec >= 3) {
                            return@mutate sendToJail(s, "😵 ${s.cur().name} rolled 3 doubles in a row — straight to Jail!")
                        }
                        beginMove(s.copy(consecutiveDoubles = consec), Dice.sum(d), extraRoll = true, msg = "🎲 ${s.cur().name} rolled ${d.first}+${d.second} (doubles!)")
                    } else {
                        beginMove(s.copy(consecutiveDoubles = 0), Dice.sum(d), extraRoll = false, msg = "🎲 ${s.cur().name} rolled ${d.first}+${d.second}")
                    }
                }
                RollContext.JAIL -> {
                    val p = s.cur()
                    if (Dice.isDoubles(d)) {
                        beginMove(
                            s.withPlayer(p.id) { it.copy(inJail = false, jailTurns = 0) },
                            Dice.sum(d),
                            extraRoll = false, // no extra roll after leaving jail with doubles
                            msg = "🎉 ${p.name} rolled doubles and left Jail!"
                        )
                    } else {
                        val turns = p.jailTurns + 1
                        if (turns >= 3) {
                            if (p.cash >= JAIL_FINE) {
                                val paid = s.withPlayer(p.id) { it.copy(cash = it.cash - JAIL_FINE, inJail = false, jailTurns = 0) }
                                beginMove(paid, Dice.sum(d), extraRoll = false, msg = "💳 ${p.name} paid \$$JAIL_FINE after 3 failed attempts")
                            } else {
                                s.withPlayer(p.id) { it.copy(jailTurns = turns) }
                                    .copy(phase = Phase.DEBT, debt = DebtInfo(p.id, null, JAIL_FINE, DebtResume.LANDING, moveDice = d))
                                    .push("⚠️ ${p.name} owes the \$$JAIL_FINE fine and can't pay!")
                            }
                        } else {
                            s.withPlayer(p.id) { it.copy(jailTurns = turns) }
                                .copy(phase = Phase.AWAIT_END_TURN, lastRollWasDoubles = false)
                                .push("🔒 No doubles — ${p.name} stays in Jail (attempt $turns of 3)")
                        }
                    }
                }
            }
        }
    }

    private fun beginMove(s: GameState, steps: Int, extraRoll: Boolean, msg: String?): GameState {
        val p = s.cur()
        val from = p.pos
        val target = (from + steps) % 40
        val passedGo = from + steps >= 40
        val path = (1..steps).map { (from + it) % 40 }
        var ns = s.withPlayer(p.id) { it.copy(pos = target) }.copy(
            phase = Phase.MOVING,
            lastRollWasDoubles = extraRoll,
            moveAnim = MoveAnim(p.id, path, s.moveAnimId + 1),
            moveAnimId = s.moveAnimId + 1
        )
        if (msg != null) ns = ns.push(msg)
        if (passedGo) ns = ns.withPlayer(p.id) { it.copy(cash = it.cash + GO_SALARY) }
            .push("💰 ${p.name} passed GO and collected \$$GO_SALARY")
        return ns
    }

    /** Called by the UI after the token finishes walking. */
    fun onMoveAnimationDone() {
        mutate { s ->
            if (s.phase != Phase.MOVING) return@mutate s
            resolveLanding(s.copy(moveAnim = null))
        }
    }

    private fun resolveLanding(s: GameState): GameState {
        val p = s.cur()
        val space = BOARD[p.pos]
        val mult = s.rentMultiplier
        val ns = s.copy(rentMultiplier = 1)
        return when (space.type) {
            SpaceType.GO, SpaceType.JAIL_VISITING, SpaceType.FREE_PARKING -> finishLanding(ns)
            SpaceType.GO_TO_JAIL -> sendToJail(ns, "🚨 ${p.name} was sent directly to Jail!")
            SpaceType.INCOME_TAX -> ns.copy(phase = Phase.TAX_DECISION)
            SpaceType.LUXURY_TAX -> chargeDebtOrFinish(ns, p.id, LUXURY_TAX, null, "💎 ${p.name} paid \$$LUXURY_TAX Luxury Tax")
            SpaceType.CHANCE, SpaceType.COMMUNITY_CHEST -> drawCard(ns)
            SpaceType.STREET, SpaceType.RAILROAD, SpaceType.UTILITY -> {
                val ps = ns.props.getValue(space.index)
                when {
                    ps.owner == null -> ns.copy(phase = Phase.BUY_DECISION)
                    ps.owner == p.id -> finishLanding(ns)
                    ps.mortgaged -> finishLanding(ns) // no rent while mortgaged
                    else -> {
                        val owner = ps.owner!!
                        val rent = rentFor(ns, space, ps, owner, mult)
                        chargeDebtOrFinish(ns, p.id, rent, owner, "💸 ${p.name} paid \$$rent rent to ${ns.player(owner).name}")
                    }
                }
            }
        }
    }

    private fun rentFor(s: GameState, space: Space, ps: PropertyState, ownerId: Int, mult: Int): Int {
        return when (space.type) {
            SpaceType.STREET -> {
                if (ps.houses == 0) {
                    val ownsAll = BOARD.filter { it.group == space.group }.all { s.props.getValue(it.index).owner == ownerId }
                    if (ownsAll) space.baseRent * 2 else space.baseRent
                } else when (ps.houses) {
                    1 -> space.rent1
                    2 -> space.rent2
                    3 -> space.rent3
                    4 -> space.rent4
                    else -> space.rentHotel
                }
            }
            SpaceType.RAILROAD -> {
                val n = BOARD.count { it.type == SpaceType.RAILROAD && s.props.getValue(it.index).owner == ownerId }
                25 * (1 shl (n - 1)) * mult
            }
            SpaceType.UTILITY -> {
                val diceSum = s.dice?.let { Dice.sum(it) } ?: 7
                if (mult == 10) 10 * diceSum
                else {
                    val n = BOARD.count { it.type == SpaceType.UTILITY && s.props.getValue(it.index).owner == ownerId }
                    (if (n >= 2) 10 else 4) * diceSum
                }
            }
            else -> 0
        }
    }

    /** After landing is fully resolved: doubles grant another roll. */
    private fun finishLanding(s: GameState): GameState {
        val p = s.cur()
        return if (s.lastRollWasDoubles && !p.inJail && !p.bankrupt && s.phase != Phase.GAME_OVER) {
            s.copy(phase = Phase.TURN_START).push("🎲 Doubles — ${p.name} rolls again!")
        } else {
            s.copy(phase = Phase.AWAIT_END_TURN)
        }
    }

    private fun sendToJail(s: GameState, msg: String): GameState {
        val p = s.cur()
        return s.withPlayer(p.id) { it.copy(pos = 10, inJail = true, jailTurns = 0) }
            .copy(phase = Phase.AWAIT_END_TURN, consecutiveDoubles = 0, lastRollWasDoubles = false)
            .push(msg)
    }

    fun endTurn() {
        mutate { s ->
            if (s.phase != Phase.AWAIT_END_TURN && s.phase != Phase.TURN_START) return@mutate s
            nextTurn(s)
        }
    }

    private fun nextTurn(s: GameState): GameState {
        if (s.players.count { !it.bankrupt } <= 1) {
            return gameOver(s)
        }
        var i = s.currentTurn
        var guard = 0
        do {
            i = (i + 1) % s.players.size
            guard++
            if (guard > s.players.size + 2) return s
        } while (s.players[i].bankrupt)
        val p = s.players[i]
        return s.copy(
            currentTurn = i,
            turnCounter = s.turnCounter + 1,
            consecutiveDoubles = 0,
            lastRollWasDoubles = false,
            phase = if (p.inJail) Phase.JAIL_CHOICE else Phase.TURN_START
        ).push("▶ ${p.name}'s turn")
    }

    private fun gameOver(s: GameState): GameState {
        val winner = s.players.first { !it.bankrupt }
        return s.copy(phase = Phase.GAME_OVER, winnerId = winner.id, auction = null, currentCard = null, debt = null, pending = emptyList())
            .push("🏆 ${winner.name} wins the game!")
    }

    // ------------------------------------------------------------------
    // buy / auction
    // ------------------------------------------------------------------

    fun buyCurrent() {
        mutate { s ->
            if (s.phase != Phase.BUY_DECISION) return@mutate s
            val p = s.cur()
            val space = BOARD[p.pos]
            if (space.type !in OWNABLE_SPACES || p.cash < space.price) return@mutate s
            val ps = s.props.getValue(space.index)
            if (ps.owner != null) return@mutate s
            val ns = s.withPlayer(p.id) { it.copy(cash = it.cash - space.price) }
                .copy(props = s.props + (space.index to ps.copy(owner = p.id)))
            finishLanding(ns.push("🏠 ${p.name} bought ${space.name} for \$${space.price}"))
        }
    }

    fun startAuction() {
        mutate { s ->
            if (s.phase != Phase.BUY_DECISION) return@mutate s
            val idx = s.cur().pos
            val active = s.players.filter { !it.bankrupt }.map { it.id }
            if (active.size < 2) {
                return@mutate finishLanding(s.push("🔨 Nobody else can bid — ${BOARD[idx].name} stays unowned"))
            }
            s.copy(
                phase = Phase.AUCTION,
                auction = AuctionInfo(idx, 0, null, active, active.first())
            ).push("🔨 ${s.cur().name} declined — ${BOARD[idx].name} goes to auction!")
        }
    }

    fun auctionBid(amount: Int) {
        mutate { s ->
            val a = s.auction ?: return@mutate s
            if (s.phase != Phase.AUCTION) return@mutate s
            val bidder = s.player(a.bidderId)
            if (amount <= a.currentBid || amount > bidder.cash) return@mutate s
            advanceBid(
                s.copy(auction = a.copy(currentBid = amount, highestBidderId = a.bidderId))
                    .push("🙋 ${bidder.name} bids \$$amount")
            )
        }
    }

    fun auctionPass() {
        mutate { s ->
            val a = s.auction ?: return@mutate s
            if (s.phase != Phase.AUCTION) return@mutate s
            // The high bidder cannot pass while others are still in.
            if (a.bidderId == a.highestBidderId) return@mutate s
            val remaining = a.active.filter { it != a.bidderId }
            val msg = "🙅 ${s.player(a.bidderId).name} passes"
            var ns = s.copy(auction = a.copy(active = remaining))
            when {
                remaining.size == 1 && a.currentBid > 0 -> {
                    val winnerId = a.highestBidderId!!
                    val winner = ns.player(winnerId)
                    val idx = a.propertyIndex
                    ns = ns.withPlayer(winnerId) { it.copy(cash = it.cash - a.currentBid) }
                        .copy(
                            props = ns.props + (idx to ns.props.getValue(idx).copy(owner = winnerId)),
                            auction = null
                        )
                    finishLanding(ns.push("$msg — 🏠 ${winner.name} won ${BOARD[idx].name} at auction for \$${a.currentBid}"))
                }
                remaining.size <= 1 -> {
                    val idx = a.propertyIndex
                    finishLanding(ns.copy(auction = null).push("$msg — nobody bought ${BOARD[idx].name}"))
                }
                else -> advanceBid(ns.push(msg))
            }
        }
    }

    private fun advanceBid(s: GameState): GameState {
        val a = s.auction ?: return s
        val curIdx = a.active.indexOf(a.bidderId)
        if (curIdx < 0) return s
        val next = a.active[(curIdx + 1) % a.active.size]
        return s.copy(auction = a.copy(bidderId = next))
    }

    // ------------------------------------------------------------------
    // cards
    // ------------------------------------------------------------------

    private fun drawCard(s: GameState): GameState {
        val space = BOARD[s.cur().pos]
        val deck = if (space.type == SpaceType.CHANCE) Deck.CHANCE else Deck.COMMUNITY_CHEST
        var pile = if (deck == Deck.CHANCE) s.chancePile else s.chestPile
        if (pile.isEmpty()) {
            val held = s.players.flatMap { it.jailCardIds }.filter { cardDeck(it) == deck }
            val rebuilt = ALL_CARDS.filter { it.deck == deck && it.id !in held }.map { it.id }.toMutableList()
            java.util.Collections.shuffle(rebuilt, Dice.rng)
            pile = rebuilt
        }
        val id = pile.first()
        val card = ALL_CARDS.first { it.id == id }
        val rest = pile.drop(1)
        val ns = if (deck == Deck.CHANCE) s.copy(chancePile = rest) else s.copy(chestPile = rest)
        return ns.copy(currentCard = card, phase = Phase.CARD_SHOWN)
            .push("🃏 ${s.cur().name} draws: ${card.text}")
    }

    fun confirmCard() {
        mutate { s ->
            if (s.phase != Phase.CARD_SHOWN) return@mutate s
            val card = s.currentCard ?: return@mutate s
            val p = s.cur()

            // Return the card to the bottom of its pile — unless it is a
            // Get Out of Jail Free card, which the player keeps.
            var ns = if (card.effect is Effect.JailFree) {
                s.withPlayer(p.id) { it.copy(jailCardIds = it.jailCardIds + card.id) }
            } else if (card.deck == Deck.CHANCE) {
                s.copy(chancePile = s.chancePile + card.id)
            } else {
                s.copy(chestPile = s.chestPile + card.id)
            }
            ns = ns.copy(currentCard = null)

            when (val e = card.effect) {
                is Effect.AdvanceTo -> advanceTo(ns, e.target)
                Effect.AdvanceToNearestRailroad -> {
                    val t = nextOf(p.pos, listOf(5, 15, 25, 35))
                    advanceTo(ns.copy(rentMultiplier = 2), t)
                }
                Effect.AdvanceToNearestUtility -> {
                    val t = nextOf(p.pos, listOf(12, 28))
                    val d = Dice.roll() // fresh roll, animated by the UI
                    advanceTo(ns.copy(rentMultiplier = 10, dice = d, diceAnimId = ns.diceAnimId + 1), t)
                }
                Effect.BackThree -> {
                    val target = (p.pos + 37) % 40
                    val path = listOf((p.pos + 39) % 40, (p.pos + 38) % 40, target)
                    ns.withPlayer(p.id) { it.copy(pos = target) }
                        .copy(
                            phase = Phase.MOVING,
                            moveAnim = MoveAnim(p.id, path, ns.moveAnimId + 1),
                            moveAnimId = ns.moveAnimId + 1
                        )
                }
                is Effect.Gain -> finishLanding(
                    ns.withPlayer(p.id) { it.copy(cash = it.cash + e.amount) }
                        .push("💰 ${p.name} collected \$${e.amount}")
                )
                is Effect.Pay -> chargeDebtOrFinish(ns, p.id, e.amount, null, "💸 ${p.name} paid \$${e.amount}")
                is Effect.CollectFromEach -> {
                    val pend = s.players.filter { !it.bankrupt && it.id != p.id }
                        .map { PendingPayment(it.id, p.id, e.amount) }
                    finishOrPending(ns.copy(pending = ns.pending + pend))
                }
                is Effect.PayEach -> {
                    val pend = s.players.filter { !it.bankrupt && it.id != p.id }
                        .map { PendingPayment(p.id, it.id, e.amount) }
                    finishOrPending(ns.copy(pending = ns.pending + pend))
                }
                is Effect.Repairs -> {
                    var houses = 0
                    var hotels = 0
                    ns.props.forEach { (idx, ps) ->
                        if (ps.owner == p.id) {
                            when (ps.houses) {
                                5 -> hotels++
                                0 -> {}
                                else -> houses += ps.houses
                            }
                        }
                    }
                    val total = houses * e.perHouse + hotels * e.perHotel
                    if (total == 0) {
                        finishLanding(ns.push("🔨 ${p.name} owns no buildings — nothing to repair"))
                    } else {
                        chargeDebtOrFinish(ns, p.id, total, null, "🔨 ${p.name} paid \$$total in repairs ($houses 🏠, $hotels 🏨)")
                    }
                }
                Effect.GoToJail -> sendToJail(ns, "🚨 ${p.name} was sent directly to Jail!")
                Effect.JailFree -> finishLanding(ns.push("🎫 ${p.name} keeps the Get Out of Jail Free card"))
            }
        }
    }

    private fun nextOf(pos: Int, candidates: List<Int>): Int =
        candidates.filter { it > pos }.minOrNull() ?: candidates.minOrNull()!!

    private fun advanceTo(s: GameState, target: Int): GameState {
        val p = s.cur()
        val dist = (target - p.pos + 40) % 40
        if (dist == 0) return finishLanding(s)
        val passedGo = target < p.pos || target == 0
        val path = (1..dist).map { (p.pos + it) % 40 }
        var ns = s.withPlayer(p.id) { it.copy(pos = target) }
            .copy(
                phase = Phase.MOVING,
                moveAnim = MoveAnim(p.id, path, s.moveAnimId + 1),
                moveAnimId = s.moveAnimId + 1
            )
        if (passedGo) ns = ns.withPlayer(p.id) { it.copy(cash = it.cash + GO_SALARY) }
            .push("💰 ${p.name} passed GO and collected \$$GO_SALARY")
        return ns
    }

    // ------------------------------------------------------------------
    // taxes
    // ------------------------------------------------------------------

    /** Returns (flatAmount, percentAmount) for the current income tax decision. */
    fun incomeTaxOptions(): Pair<Int, Int>? {
        val s = state ?: return null
        if (s.phase != Phase.TAX_DECISION) return null
        val worth = netWorth(s, s.cur().id)
        return 200 to (worth / 10)
    }

    fun chooseTax(flat: Boolean) {
        mutate { s ->
            if (s.phase != Phase.TAX_DECISION) return@mutate s
            val amount = if (flat) 200 else netWorth(s, s.cur().id) / 10
            chargeDebtOrFinish(s, s.cur().id, amount, null, "💸 ${s.cur().name} paid \$$amount Income Tax")
        }
    }

    // ------------------------------------------------------------------
    // jail options
    // ------------------------------------------------------------------

    fun jailPay() {
        mutate { s ->
            if (s.phase != Phase.JAIL_CHOICE) return@mutate s
            val p = s.cur()
            if (p.cash >= JAIL_FINE) {
                s.withPlayer(p.id) { it.copy(cash = it.cash - JAIL_FINE, inJail = false, jailTurns = 0) }
                    .copy(phase = Phase.TURN_START)
                    .push("💳 ${p.name} paid \$$JAIL_FINE to leave Jail — roll the dice")
            } else {
                s.copy(phase = Phase.DEBT, debt = DebtInfo(p.id, null, JAIL_FINE, DebtResume.TURN_START))
                    .push("⚠️ ${p.name} owes the \$$JAIL_FINE fine and can't pay!")
            }
        }
    }

    fun jailUseCard() {
        mutate { s ->
            if (s.phase != Phase.JAIL_CHOICE) return@mutate s
            val p = s.cur()
            val cardId = p.jailCardIds.firstOrNull() ?: return@mutate s
            val withoutCard = s.withPlayer(p.id) { it.copy(jailCardIds = it.jailCardIds.drop(1), inJail = false, jailTurns = 0) }
            val ns = if (cardDeck(cardId) == Deck.CHANCE) {
                withoutCard.copy(chancePile = withoutCard.chancePile + cardId)
            } else {
                withoutCard.copy(chestPile = withoutCard.chestPile + cardId)
            }
            ns.copy(phase = Phase.TURN_START).push("🎫 ${p.name} used a Get Out of Jail Free card — roll the dice")
        }
    }

    // ------------------------------------------------------------------
    // debt & bankruptcy
    // ------------------------------------------------------------------

    /**
     * Charge [amount]. If the payer can afford it, pay and continue with
     * [onPaidLogic]; otherwise switch to the DEBT phase (the UI lets them
     * mortgage/sell/trade, pay, or go bankrupt).
     */
    private fun chargeDebtOrFinish(s: GameState, fromId: Int, amount: Int, toId: Int?, msg: String): GameState {
        val p = s.player(fromId)
        return if (p.cash >= amount) {
            var ns = s.withPlayer(fromId) { it.copy(cash = it.cash - amount) }
            if (toId != null) ns = ns.withPlayer(toId) { it.copy(cash = it.cash + amount) }
            finishLanding(ns.push(msg))
        } else {
            s.copy(phase = Phase.DEBT, debt = DebtInfo(fromId, toId, amount, DebtResume.LANDING))
                .push("⚠️ ${p.name} owes \$$amount and can't pay!")
        }
    }

    private fun finishOrPending(s: GameState): GameState {
        val ns = continuePending(s)
        return if (ns.phase == Phase.DEBT) ns else finishLanding(ns)
    }

    /** Process the queue of per-player payments; pauses at the first insolvent payer. */
    private fun continuePending(s0: GameState): GameState {
        var s = s0
        while (true) {
            val head = s.pending.firstOrNull() ?: return s
            val p = s.player(head.fromId)
            if (p.bankrupt || head.amount <= 0) {
                s = s.copy(pending = s.pending.drop(1))
                continue
            }
            if (p.cash >= head.amount) {
                var ns = s.withPlayer(head.fromId) { it.copy(cash = it.cash - head.amount) }
                if (head.toId != null) {
                    val toId = head.toId!!
                    ns = ns.withPlayer(toId) { it.copy(cash = it.cash + head.amount) }
                }
                val toName = head.toId?.let { s.player(it).name } ?: "the Bank"
                s = ns.copy(pending = ns.pending.drop(1)).push("💸 ${p.name} paid \$${head.amount} to $toName")
            } else {
                return s.copy(phase = Phase.DEBT, debt = DebtInfo(head.fromId, head.toId, head.amount, DebtResume.LANDING))
                    .push("⚠️ ${p.name} owes \$${head.amount} and can't pay!")
            }
        }
    }

    fun payDebt() {
        mutate { s ->
            if (s.phase != Phase.DEBT) return@mutate s
            val d = s.debt ?: return@mutate s
            val p = s.player(d.debtorId)
            if (p.cash < d.amount) return@mutate s
            var ns = s.withPlayer(d.debtorId) { it.copy(cash = it.cash - d.amount) }
            if (d.creditorId != null) {
                val cred = d.creditorId!!
                ns = ns.withPlayer(cred) { it.copy(cash = it.cash + d.amount) }
            }
            ns = ns.copy(debt = null).push("💳 ${p.name} paid \$${d.amount}")
            when {
                d.moveDice != null -> beginMove(ns, Dice.sum(d.moveDice!!), extraRoll = false, msg = null)
                d.resume == DebtResume.TURN_START -> ns.copy(phase = Phase.TURN_START)
                else -> {
                    val cont = continuePending(ns)
                    if (cont.phase == Phase.DEBT) cont else finishLanding(cont)
                }
            }
        }
    }

    fun declareBankruptcy() {
        mutate { s ->
            if (s.phase != Phase.DEBT) return@mutate s
            val d = s.debt ?: return@mutate s
            val debtor = s.player(d.debtorId)

            // 1) Sell every building back to the bank at half price.
            var cashBack = 0
            val newProps = HashMap(s.props)
            s.props.forEach { (idx, ps) ->
                if (ps.owner == debtor.id && ps.houses > 0) {
                    val hc = BOARD[idx].houseCost
                    cashBack += if (ps.houses == 5) hc / 2 else ps.houses * hc / 2
                    newProps[idx] = ps.copy(houses = 0)
                }
            }
            var ns = s.withPlayer(debtor.id) { it.copy(cash = it.cash + cashBack) }
                .copy(props = newProps.toMap())

            if (d.creditorId != null) {
                // 2a) Bankruptcy to another player.
                val creditorId = d.creditorId!!
                val debtorCash = ns.player(debtor.id).cash
                val debtorCards = ns.player(debtor.id).jailCardIds
                val transferred = ns.props.mapValues { (_, ps) ->
                    if (ps.owner == debtor.id) ps.copy(owner = creditorId) else ps
                }
                var fees = 0
                transferred.forEach { (idx, ps) ->
                    if (ps.owner == creditorId && ps.mortgaged) fees += BOARD[idx].price / 20
                }
                ns = ns.copy(props = transferred)
                    .withPlayer(creditorId) {
                        it.copy(
                            cash = it.cash + debtorCash,
                            jailCardIds = it.jailCardIds + debtorCards
                        )
                    }
                    .withPlayer(debtor.id) {
                        it.copy(cash = 0, bankrupt = true, inJail = false, jailTurns = 0, jailCardIds = emptyList())
                    }
                ns = restock(ns)
                if (fees > 0) {
                    ns = ns.push("🏦 ${ns.player(creditorId).name} owes \$${fees} in 10% mortgage transfer fees")
                    val cCash = ns.player(creditorId).cash
                    ns = if (cCash >= fees) {
                        ns.withPlayer(creditorId) { it.copy(cash = it.cash - fees) }
                    } else {
                        ns.withPlayer(creditorId) { it.copy(cash = 0) }
                            .copy(pending = ns.pending + PendingPayment(creditorId, null, fees - cCash))
                    }
                }
            } else {
                // 2b) Bankruptcy to the bank: properties become unowned again.
                val cleared = ns.props.mapValues { (_, ps) ->
                    if (ps.owner == debtor.id) PropertyState() else ps
                }
                var chance = ns.chancePile
                var chest = ns.chestPile
                ns.player(debtor.id).jailCardIds.forEach { id ->
                    if (cardDeck(id) == Deck.CHANCE) chance += id else chest += id
                }
                ns = ns.copy(props = cleared, chancePile = chance, chestPile = chest)
                    .withPlayer(debtor.id) {
                        it.copy(cash = 0, bankrupt = true, inJail = false, jailTurns = 0, jailCardIds = emptyList())
                    }
                ns = restock(ns)
            }

            // 3) Drop the debtor from any queued payments.
            ns = ns.copy(
                pending = ns.pending.filter { it.fromId != debtor.id && it.toId != debtor.id },
                debt = null
            ).push("☠️ ${debtor.name} is bankrupt and out of the game!")

            // 4) Winner?
            val alive = ns.players.filter { !it.bankrupt }
            if (alive.size == 1) {
                return@mutate gameOver(ns)
            }

            // 5) Resume: pending payments, or the turn flow.
            if (ns.pending.isNotEmpty()) {
                val cont = continuePending(ns)
                if (cont.phase == Phase.DEBT) return@mutate cont
                return@mutate if (cont.cur().id == debtor.id) nextTurn(cont) else finishLanding(cont)
            }
            if (ns.cur().id == debtor.id) nextTurn(ns) else finishLanding(ns)
        }
    }

    // ------------------------------------------------------------------
    // build / mortgage
    // ------------------------------------------------------------------

    /** Returns null on success, or an error message for the UI to show. */
    fun buildHouse(pid: Int, idx: Int): String? {
        var err: String? = null
        mutate { s ->
            val space = BOARD.getOrNull(idx) ?: run { err = "Unknown property"; return@mutate s }
            val ps = s.props[idx] ?: run { err = "Not a purchasable property"; return@mutate s }
            val p = s.player(pid)
            if (ps.owner != pid) { err = "You don't own this"; return@mutate s }
            if (space.type != SpaceType.STREET) { err = "Can only build on streets"; return@mutate s }
            val groupSpaces = BOARD.filter { it.group == space.group }
            if (!groupSpaces.all { s.props.getValue(it.index).owner == pid }) {
                err = "You need the whole ${space.group?.label} set"; return@mutate s
            }
            if (groupSpaces.any { s.props.getValue(it.index).mortgaged }) {
                err = "A property in this color set is mortgaged"; return@mutate s
            }
            val minH = groupSpaces.minOf { s.props.getValue(it.index).houses }
            if (ps.houses > minH) { err = "Build evenly — another tile has fewer buildings"; return@mutate s }
            if (ps.houses >= 5) { err = "Already has a hotel"; return@mutate s }
            if (p.cash < space.houseCost) { err = "Not enough cash"; return@mutate s }
            if (ps.houses == 4 && s.hotelsStock < 1) { err = "No hotels left in the bank"; return@mutate s }
            if (ps.houses < 4 && s.housesStock < 1) { err = "No houses left in the bank"; return@mutate s }

            var ns = s.withPlayer(pid) { it.copy(cash = it.cash - space.houseCost) }
            ns = ns.copy(props = ns.props + (idx to ps.copy(houses = ps.houses + 1)))
            ns = restock(ns)
            val what = if (ps.houses == 4) "a HOTEL 🏨" else "a house 🏠"
            ns.push("🔨 ${p.name} built $what on ${space.name} (\$${space.houseCost})")
        }
        return err
    }

    fun sellBuilding(pid: Int, idx: Int): String? {
        var err: String? = null
        mutate { s ->
            val space = BOARD.getOrNull(idx) ?: run { err = "Unknown property"; return@mutate s }
            val ps = s.props[idx] ?: run { err = "Not a purchasable property"; return@mutate s }
            val p = s.player(pid)
            if (ps.owner != pid) { err = "You don't own this"; return@mutate s }
            if (ps.houses <= 0) { err = "Nothing to sell"; return@mutate s }
            val groupSpaces = BOARD.filter { it.group == space.group }
            val maxH = groupSpaces.maxOf { s.props.getValue(it.index).houses }
            if (ps.houses < maxH) { err = "Sell evenly — another tile has more buildings"; return@mutate s }

            var ns = s.withPlayer(pid) { it.copy(cash = it.cash + space.houseCost / 2) }
            val newHouses = if (ps.houses == 5) {
                if (s.housesStock >= 4) 4 else 0 // keep 4 houses if the bank has them
            } else ps.houses - 1
            ns = ns.copy(props = ns.props + (idx to ps.copy(houses = newHouses)))
            ns = restock(ns)
            ns.push("🔻 ${p.name} sold a building on ${space.name} (+\$${space.houseCost / 2})")
        }
        return err
    }

    fun mortgage(pid: Int, idx: Int): String? {
        var err: String? = null
        mutate { s ->
            val space = BOARD.getOrNull(idx) ?: run { err = "Unknown property"; return@mutate s }
            val ps = s.props[idx] ?: run { err = "Not a purchasable property"; return@mutate s }
            val p = s.player(pid)
            if (ps.owner != pid) { err = "You don't own this"; return@mutate s }
            if (ps.mortgaged) { err = "Already mortgaged"; return@mutate s }
            if (space.type == SpaceType.STREET) {
                val groupSpaces = BOARD.filter { it.group == space.group }
                if (groupSpaces.any { s.props.getValue(it.index).houses > 0 }) {
                    err = "Sell all buildings in this color set first"; return@mutate s
                }
            }
            var ns = s.withPlayer(pid) { it.copy(cash = it.cash + space.price / 2) }
            ns = ns.copy(props = ns.props + (idx to ps.copy(mortgaged = true)))
            ns.push("🏦 ${p.name} mortgaged ${space.name} (+\$${space.price / 2})")
        }
        return err
    }

    fun unmortgage(pid: Int, idx: Int): String? {
        var err: String? = null
        mutate { s ->
            val space = BOARD.getOrNull(idx) ?: run { err = "Unknown property"; return@mutate s }
            val ps = s.props[idx] ?: run { err = "Not a purchasable property"; return@mutate s }
            val p = s.player(pid)
            if (ps.owner != pid) { err = "You don't own this"; return@mutate s }
            if (!ps.mortgaged) { err = "Not mortgaged"; return@mutate s }
            val cost = space.price / 2 + space.price / 20 // principal + 10% interest
            if (p.cash < cost) { err = "Not enough cash (need \$$cost)"; return@mutate s }
            var ns = s.withPlayer(pid) { it.copy(cash = it.cash - cost) }
            ns = ns.copy(props = ns.props + (idx to ps.copy(mortgaged = false)))
            ns.push("🔑 ${p.name} lifted the mortgage on ${space.name} (\$$cost)")
        }
        return err
    }

    // ------------------------------------------------------------------
    // trading
    // ------------------------------------------------------------------

    fun executeTrade(o: TradeOffer): String? {
        var err: String? = null
        mutate { s ->
            val from = s.player(o.fromId)
            val to = s.player(o.toId)
            if (from.bankrupt || to.bankrupt) { err = "A player is out of the game"; return@mutate s }
            if (o.cashFrom < 0 || o.cashTo < 0 || o.cashFrom > from.cash || o.cashTo > to.cash) {
                err = "Invalid cash amount"; return@mutate s
            }
            if (o.jailCardsFrom < 0 || o.jailCardsTo < 0 ||
                o.jailCardsFrom > from.jailCardIds.size || o.jailCardsTo > to.jailCardIds.size
            ) { err = "Not enough jail cards"; return@mutate s }
            if (o.cashFrom == 0 && o.cashTo == 0 && o.propsFrom.isEmpty() && o.propsTo.isEmpty() &&
                o.jailCardsFrom == 0 && o.jailCardsTo == 0
            ) { err = "Empty trade"; return@mutate s }

            fun tradable(idx: Int, pid: Int): Boolean {
                val sp = BOARD.getOrNull(idx) ?: return false
                val ps = s.props[idx] ?: return false
                if (ps.owner != pid) return false
                if (sp.type == SpaceType.STREET) {
                    if (BOARD.any { it.group == sp.group && s.props.getValue(it.index).houses > 0 }) return false
                }
                return true
            }
            if (!o.propsFrom.all { tradable(it, o.fromId) } || !o.propsTo.all { tradable(it, o.toId) }) {
                err = "Sell the buildings on that color set first"; return@mutate s
            }

            var ns = s
            if (o.cashFrom > 0) {
                ns = ns.withPlayer(o.fromId) { it.copy(cash = it.cash - o.cashFrom) }
                    .withPlayer(o.toId) { it.copy(cash = it.cash + o.cashFrom) }
            }
            if (o.cashTo > 0) {
                ns = ns.withPlayer(o.toId) { it.copy(cash = it.cash - o.cashTo) }
                    .withPlayer(o.fromId) { it.copy(cash = it.cash + o.cashTo) }
            }
            val pm = HashMap(ns.props)
            o.propsFrom.forEach { pm[it] = pm.getValue(it).copy(owner = o.toId) }
            o.propsTo.forEach { pm[it] = pm.getValue(it).copy(owner = o.fromId) }
            ns = ns.copy(props = pm.toMap())

            val fromCardsNow = ns.player(o.fromId).jailCardIds
            val toCardsNow = ns.player(o.toId).jailCardIds
            val fromFinal = fromCardsNow.dropLast(o.jailCardsFrom) + toCardsNow.takeLast(o.jailCardsTo)
            val toFinal = toCardsNow.dropLast(o.jailCardsTo) + fromCardsNow.takeLast(o.jailCardsFrom)
            ns = ns.withPlayer(o.fromId) { it.copy(jailCardIds = fromFinal) }
                .withPlayer(o.toId) { it.copy(jailCardIds = toFinal) }

            ns.push("🤝 Trade complete: ${from.name} ↔ ${to.name}")
        }
        return err
    }
}
