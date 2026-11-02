package com.local.monopoly.game

/*
 * Pure game data: the classic US Monopoly board (Atlantic City),
 * the 16 Chance + 16 Community Chest cards and the state snapshots.
 * No Android or Compose dependencies here (colors are stored as Long).
 */

const val START_CASH = 1500
const val GO_SALARY = 200
const val JAIL_FINE = 50
const val LUXURY_TAX = 100
const val BANK_HOUSES = 32
const val BANK_HOTELS = 12

enum class SpaceType {
    STREET, RAILROAD, UTILITY, CHANCE, COMMUNITY_CHEST,
    INCOME_TAX, LUXURY_TAX, GO, JAIL_VISITING, FREE_PARKING, GO_TO_JAIL
}

val OWNABLE_SPACES = setOf(SpaceType.STREET, SpaceType.RAILROAD, SpaceType.UTILITY)

/** The 8 classic color groups with their official board colors. */
enum class Group(val label: String, val colorHex: Long) {
    BROWN("Brown", 0xFF955436),
    LIGHT_BLUE("Light Blue", 0xFFAAE0FA),
    PINK("Pink", 0xFFD93A96),
    ORANGE("Orange", 0xFFF7941D),
    RED("Red", 0xFFED1B24),
    YELLOW("Yellow", 0xFFFEF200),
    GREEN("Green", 0xFF1FB25A),
    DARK_BLUE("Dark Blue", 0xFF0072BB)
}

data class Space(
    val index: Int,
    val name: String,
    /** Short label lines printed on the tiny board tile. */
    val tileLabel: List<String>,
    val type: SpaceType,
    val price: Int = 0,
    val group: Group? = null,
    val baseRent: Int = 0,
    val rent1: Int = 0,
    val rent2: Int = 0,
    val rent3: Int = 0,
    val rent4: Int = 0,
    val rentHotel: Int = 0,
    val houseCost: Int = 0,
    val icon: String = ""
)

private fun street(
    i: Int, name: String, label: List<String>, g: Group, price: Int,
    r: Int, r1: Int, r2: Int, r3: Int, r4: Int, rh: Int, hc: Int
) = Space(i, name, label, SpaceType.STREET, price, g, r, r1, r2, r3, r4, rh, hc)

/** The 40 spaces, index 0 = GO, movement goes 0..39 (counter-clockwise on screen). */
val BOARD: List<Space> = listOf(
    Space(0, "GO", listOf("GO"), SpaceType.GO, icon = "➡️"),
    street(1, "Mediterranean Avenue", listOf("MEDITER-", "RANEAN"), Group.BROWN, 60, 2, 10, 30, 90, 160, 250, 50),
    Space(2, "Community Chest", listOf("COMMUNITY", "CHEST"), SpaceType.COMMUNITY_CHEST, icon = "🎁"),
    street(3, "Baltic Avenue", listOf("BALTIC"), Group.BROWN, 60, 4, 20, 60, 180, 320, 450, 50),
    Space(4, "Income Tax", listOf("INCOME", "TAX"), SpaceType.INCOME_TAX, icon = "💰"),
    Space(5, "Reading Railroad", listOf("READING", "RR"), SpaceType.RAILROAD, 200, icon = "🚂"),
    street(6, "Oriental Avenue", listOf("ORIENTAL"), Group.LIGHT_BLUE, 100, 6, 30, 90, 270, 400, 550, 50),
    Space(7, "Chance", listOf("CHANCE"), SpaceType.CHANCE, icon = "❓"),
    street(8, "Vermont Avenue", listOf("VERMONT"), Group.LIGHT_BLUE, 100, 6, 30, 90, 270, 400, 550, 50),
    street(9, "Connecticut Avenue", listOf("CONNECT-", "ICUT"), Group.LIGHT_BLUE, 120, 8, 40, 100, 300, 450, 600, 50),
    Space(10, "Jail", listOf("JAIL"), SpaceType.JAIL_VISITING, icon = "🚔"),
    street(11, "St. Charles Place", listOf("ST.", "CHARLES"), Group.PINK, 140, 10, 50, 150, 450, 625, 750, 100),
    Space(12, "Electric Company", listOf("ELECTRIC", "CO."), SpaceType.UTILITY, 150, icon = "💡"),
    street(13, "States Avenue", listOf("STATES"), Group.PINK, 140, 10, 50, 150, 450, 625, 750, 100),
    street(14, "Virginia Avenue", listOf("VIRGINIA"), Group.PINK, 160, 12, 60, 180, 500, 700, 900, 100),
    Space(15, "Pennsylvania Railroad", listOf("PENN.", "RR"), SpaceType.RAILROAD, 200, icon = "🚂"),
    street(16, "St. James Place", listOf("ST. JAMES"), Group.ORANGE, 180, 14, 70, 200, 550, 750, 950, 100),
    Space(17, "Community Chest", listOf("COMMUNITY", "CHEST"), SpaceType.COMMUNITY_CHEST, icon = "🎁"),
    street(18, "Tennessee Avenue", listOf("TENNES-", "SEE"), Group.ORANGE, 180, 14, 70, 200, 550, 750, 950, 100),
    street(19, "New York Avenue", listOf("NEW", "YORK"), Group.ORANGE, 200, 16, 80, 220, 600, 800, 1000, 100),
    Space(20, "Free Parking", listOf("FREE", "PARKING"), SpaceType.FREE_PARKING, icon = "🚗"),
    street(21, "Kentucky Avenue", listOf("KENTUCKY"), Group.RED, 220, 18, 90, 250, 700, 875, 1050, 150),
    Space(22, "Chance", listOf("CHANCE"), SpaceType.CHANCE, icon = "❓"),
    street(23, "Indiana Avenue", listOf("INDIANA"), Group.RED, 220, 18, 90, 250, 700, 875, 1050, 150),
    street(24, "Illinois Avenue", listOf("ILLINOIS"), Group.RED, 240, 20, 100, 300, 750, 925, 1100, 150),
    Space(25, "B. & O. Railroad", listOf("B. & O.", "RR"), SpaceType.RAILROAD, 200, icon = "🚂"),
    street(26, "Atlantic Avenue", listOf("ATLANTIC"), Group.YELLOW, 260, 22, 110, 330, 800, 975, 1150, 150),
    street(27, "Ventnor Avenue", listOf("VENTNOR"), Group.YELLOW, 260, 22, 110, 330, 800, 975, 1150, 150),
    Space(28, "Water Works", listOf("WATER", "WORKS"), SpaceType.UTILITY, 150, icon = "🚰"),
    street(29, "Marvin Gardens", listOf("MARVIN", "GARDENS"), Group.YELLOW, 280, 24, 120, 360, 850, 1025, 1200, 150),
    Space(30, "Go To Jail", listOf("GO TO", "JAIL"), SpaceType.GO_TO_JAIL, icon = "🚨"),
    street(31, "Pacific Avenue", listOf("PACIFIC"), Group.GREEN, 300, 26, 130, 390, 900, 1100, 1275, 200),
    street(32, "North Carolina Avenue", listOf("N.", "CAROLINA"), Group.GREEN, 300, 26, 130, 390, 900, 1100, 1275, 200),
    Space(33, "Community Chest", listOf("COMMUNITY", "CHEST"), SpaceType.COMMUNITY_CHEST, icon = "🎁"),
    street(34, "Pennsylvania Avenue", listOf("PENNSYL-", "VANIA"), Group.GREEN, 320, 28, 150, 450, 1000, 1200, 1400, 200),
    Space(35, "Short Line", listOf("SHORT", "LINE"), SpaceType.RAILROAD, 200, icon = "🚂"),
    Space(36, "Chance", listOf("CHANCE"), SpaceType.CHANCE, icon = "❓"),
    street(37, "Park Place", listOf("PARK", "PLACE"), Group.DARK_BLUE, 350, 35, 175, 500, 1100, 1300, 1500, 200),
    Space(38, "Luxury Tax", listOf("LUXURY", "TAX"), SpaceType.LUXURY_TAX, icon = "💎"),
    street(39, "Boardwalk", listOf("BOARD-", "WALK"), Group.DARK_BLUE, 400, 50, 200, 600, 1400, 1700, 2000, 200)
)

// ---------------------------------------------------------------------------
// Chance & Community Chest (classic card set)
// ---------------------------------------------------------------------------

enum class Deck { CHANCE, COMMUNITY_CHEST }

fun cardDeck(id: Int): Deck = if (id < 16) Deck.CHANCE else Deck.COMMUNITY_CHEST

sealed class Effect {
    data class AdvanceTo(val target: Int) : Effect()
    object AdvanceToNearestRailroad : Effect()
    object AdvanceToNearestUtility : Effect()
    object BackThree : Effect()
    data class Gain(val amount: Int) : Effect()
    data class Pay(val amount: Int) : Effect()
    data class CollectFromEach(val amount: Int) : Effect()
    data class PayEach(val amount: Int) : Effect()
    data class Repairs(val perHouse: Int, val perHotel: Int) : Effect()
    object GoToJail : Effect()
    object JailFree : Effect()
}

data class GameCard(val id: Int, val deck: Deck, val text: String, val effect: Effect)

val ALL_CARDS: List<GameCard> = listOf(
    // ---- Chance (ids 0..15) ----
    GameCard(0, Deck.CHANCE, "Advance to GO. (Collect \$200)", Effect.AdvanceTo(0)),
    GameCard(1, Deck.CHANCE, "Advance to Illinois Avenue. If you pass GO, collect \$200.", Effect.AdvanceTo(24)),
    GameCard(2, Deck.CHANCE, "Advance to St. Charles Place. If you pass GO, collect \$200.", Effect.AdvanceTo(11)),
    GameCard(3, Deck.CHANCE, "Advance token to the nearest Utility. If unowned you may buy it. If owned, throw dice and pay the owner 10× the amount thrown.", Effect.AdvanceToNearestUtility),
    GameCard(4, Deck.CHANCE, "Advance token to the nearest Railroad and pay the owner twice the rental. If unowned you may buy it.", Effect.AdvanceToNearestRailroad),
    GameCard(5, Deck.CHANCE, "Advance token to the nearest Railroad and pay the owner twice the rental. If unowned you may buy it.", Effect.AdvanceToNearestRailroad),
    GameCard(6, Deck.CHANCE, "The Bank pays you a dividend of \$50.", Effect.Gain(50)),
    GameCard(7, Deck.CHANCE, "Get Out of Jail Free. Keep this card until needed or trade it.", Effect.JailFree),
    GameCard(8, Deck.CHANCE, "Go Back 3 Spaces.", Effect.BackThree),
    GameCard(9, Deck.CHANCE, "Go to Jail. Go directly to Jail — do not pass GO, do not collect \$200.", Effect.GoToJail),
    GameCard(10, Deck.CHANCE, "Make general repairs on all your property: for each house pay \$25, for each hotel pay \$100.", Effect.Repairs(25, 100)),
    GameCard(11, Deck.CHANCE, "Speeding fine. Pay \$15.", Effect.Pay(15)),
    GameCard(12, Deck.CHANCE, "Take a trip to Reading Railroad. If you pass GO, collect \$200.", Effect.AdvanceTo(5)),
    GameCard(13, Deck.CHANCE, "Take a walk on the Boardwalk. Advance token to Boardwalk.", Effect.AdvanceTo(39)),
    GameCard(14, Deck.CHANCE, "You have been elected Chairman of the Board. Pay each player \$50.", Effect.PayEach(50)),
    GameCard(15, Deck.CHANCE, "Your building loan matures. Collect \$150.", Effect.Gain(150)),
    // ---- Community Chest (ids 16..31) ----
    GameCard(16, Deck.COMMUNITY_CHEST, "Advance to GO. (Collect \$200)", Effect.AdvanceTo(0)),
    GameCard(17, Deck.COMMUNITY_CHEST, "Bank error in your favor. Collect \$200.", Effect.Gain(200)),
    GameCard(18, Deck.COMMUNITY_CHEST, "Doctor's fee. Pay \$50.", Effect.Pay(50)),
    GameCard(19, Deck.COMMUNITY_CHEST, "From sale of stock you get \$50.", Effect.Gain(50)),
    GameCard(20, Deck.COMMUNITY_CHEST, "Get Out of Jail Free. Keep this card until needed or trade it.", Effect.JailFree),
    GameCard(21, Deck.COMMUNITY_CHEST, "Go to Jail. Go directly to Jail — do not pass GO, do not collect \$200.", Effect.GoToJail),
    GameCard(22, Deck.COMMUNITY_CHEST, "Grand Opera Night. Collect \$50 from every player for opening night seats.", Effect.CollectFromEach(50)),
    GameCard(23, Deck.COMMUNITY_CHEST, "Holiday Fund matures. Receive \$100.", Effect.Gain(100)),
    GameCard(24, Deck.COMMUNITY_CHEST, "Income tax refund. Collect \$20.", Effect.Gain(20)),
    GameCard(25, Deck.COMMUNITY_CHEST, "It is your birthday. Collect \$10 from every player.", Effect.CollectFromEach(10)),
    GameCard(26, Deck.COMMUNITY_CHEST, "Life insurance matures. Collect \$100.", Effect.Gain(100)),
    GameCard(27, Deck.COMMUNITY_CHEST, "Pay hospital fees of \$100.", Effect.Pay(100)),
    GameCard(28, Deck.COMMUNITY_CHEST, "Pay school fees of \$50.", Effect.Pay(50)),
    GameCard(29, Deck.COMMUNITY_CHEST, "Receive \$25 consultancy fee.", Effect.Gain(25)),
    GameCard(30, Deck.COMMUNITY_CHEST, "You are assessed for street repairs: pay \$40 per house and \$115 per hotel you own.", Effect.Repairs(40, 115)),
    GameCard(31, Deck.COMMUNITY_CHEST, "You have won second prize in a beauty contest. Collect \$10.", Effect.Gain(10))
)

// ---------------------------------------------------------------------------
// Game state
// ---------------------------------------------------------------------------

data class PlayerSetup(val name: String, val token: String, val colorHex: Long)

data class Player(
    val id: Int,
    val name: String,
    val token: String,       // emoji token
    val colorHex: Long,      // player color (from their seat)
    val cash: Int = START_CASH,
    val pos: Int = 0,
    val inJail: Boolean = false,
    val jailTurns: Int = 0,  // failed doubles attempts this stay
    val jailCardIds: List<Int> = emptyList(), // held Get Out of Jail Free cards
    val bankrupt: Boolean = false
)

data class PropertyState(
    val owner: Int? = null,
    val houses: Int = 0,     // 0..4 houses, 5 = hotel
    val mortgaged: Boolean = false
)

enum class Phase {
    TURN_START,      // current player must roll
    ROLLING,         // dice animation running
    MOVING,          // token animation running
    BUY_DECISION,    // landed on unowned purchasable space
    AUCTION,         // auction in progress
    CARD_SHOWN,      // chance / chest card drawn, waiting for OK
    TAX_DECISION,    // income tax: $200 or 10%
    JAIL_CHOICE,     // current player is in jail
    DEBT,            // someone owes money they cannot pay (yet)
    AWAIT_END_TURN,  // done acting
    GAME_OVER
}

enum class RollContext { NORMAL, JAIL }

data class PendingPayment(val fromId: Int, val toId: Int?, val amount: Int)

enum class DebtResume { LANDING, TURN_START }

data class DebtInfo(
    val debtorId: Int,
    val creditorId: Int?,     // null = the Bank
    val amount: Int,
    val resume: DebtResume = DebtResume.LANDING,
    /** If set, after the debt is paid the debtor moves by this dice roll (3rd jail attempt). */
    val moveDice: Pair<Int, Int>? = null
)

data class AuctionInfo(
    val propertyIndex: Int,
    val currentBid: Int,          // 0 = no bid yet
    val highestBidderId: Int?,
    val active: List<Int>,        // player ids still in the auction
    val bidderId: Int             // whose turn it is to bid or pass
)

data class MoveAnim(val playerId: Int, val path: List<Int>, val animId: Int)

data class TradeOffer(
    val fromId: Int,
    val toId: Int,
    val cashFrom: Int, val cashTo: Int,
    val propsFrom: List<Int>, val propsTo: List<Int>,
    val jailCardsFrom: Int, val jailCardsTo: Int
)

data class GameState(
    val players: List<Player>,
    val currentTurn: Int = 0,
    val turnCounter: Int = 1,
    val phase: Phase = Phase.TURN_START,
    val dice: Pair<Int, Int>? = null,
    val rollContext: RollContext = RollContext.NORMAL,
    val consecutiveDoubles: Int = 0,
    val lastRollWasDoubles: Boolean = false,
    val props: Map<Int, PropertyState> = emptyMap(),
    val housesStock: Int = BANK_HOUSES,
    val hotelsStock: Int = BANK_HOTELS,
    val chancePile: List<Int> = emptyList(),
    val chestPile: List<Int> = emptyList(),
    val currentCard: GameCard? = null,
    val auction: AuctionInfo? = null,
    val debt: DebtInfo? = null,
    val pending: List<PendingPayment> = emptyList(),
    /** Rent multiplier from card effects (2 = railroad, 10 = utility). */
    val rentMultiplier: Int = 1,
    val moveAnim: MoveAnim? = null,
    val diceAnimId: Int = 0,
    val moveAnimId: Int = 0,
    val winnerId: Int? = null,
    val log: List<String> = emptyList()
)
