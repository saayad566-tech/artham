package `in`.artham.fundamentals.data

/**
 * A short editorial list of liquid, well-known NSE symbols, used for the first-run
 * "get real data" action so a new install becomes useful in about a minute instead of
 * requiring a two-thousand-company sync.
 *
 * These are public ticker identifiers, not licensed data. Nothing about these companies
 * is bundled: the figures are fetched on the user's device, on demand.
 */
object SeedSymbols {
    val liquidLargeCaps: List<String> = listOf(
        "RELIANCE", "TCS", "HDFCBANK", "ICICIBANK", "INFY", "BHARTIARTL", "SBIN", "LT",
        "ITC", "HINDUNILVR", "KOTAKBANK", "AXISBANK", "BAJFINANCE", "MARUTI", "M&M",
        "SUNPHARMA", "TITAN", "ULTRACEMCO", "ASIANPAINT", "NESTLEIND", "WIPRO", "HCLTECH",
        "TECHM", "POWERGRID", "NTPC", "ONGC", "COALINDIA", "TATASTEEL", "JSWSTEEL",
        "HINDALCO", "GRASIM", "CIPLA", "DRREDDY", "DIVISLAB", "APOLLOHOSP", "BAJAJFINSV",
        "ADANIPORTS", "ADANIENT", "TATAMOTORS", "EICHERMOT", "HEROMOTOCO", "BAJAJ-AUTO",
        "BRITANNIA", "DABUR", "GODREJCP", "PIDILITIND", "SIEMENS", "ABB", "DLF",
        "INDUSINDBK", "SBILIFE", "HDFCLIFE", "ICICIGI", "TRENT", "DMART", "IRCTC",
        "IRFC", "PFC", "RECLTD", "GAIL", "BPCL", "IOC", "VEDL", "SAIL", "NMDC"
    )
}
