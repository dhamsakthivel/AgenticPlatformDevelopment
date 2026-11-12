from dataclasses import dataclass


@dataclass(frozen=True)
class Instrument:
    symbol: str
    name: str
    sector: str


# NSE Indices Nifty 50 list as published for late 2026. Using today's members
# for a multi-year backtest leaves out companies that dropped out of the index.
NIFTY_50: tuple[Instrument, ...] = (
    Instrument("ADANIENT", "Adani Enterprises", "Metals & Mining"),
    Instrument("ADANIPORTS", "Adani Ports", "Services"),
    Instrument("APOLLOHOSP", "Apollo Hospitals", "Healthcare"),
    Instrument("ASIANPAINT", "Asian Paints", "Consumer Durables"),
    Instrument("AXISBANK", "Axis Bank", "Financial Services"),
    Instrument("BAJAJ-AUTO", "Bajaj Auto", "Automobile and Auto Components"),
    Instrument("BAJFINANCE", "Bajaj Finance", "Financial Services"),
    Instrument("BAJAJFINSV", "Bajaj Finserv", "Financial Services"),
    Instrument("BEL", "Bharat Electronics", "Capital Goods"),
    Instrument("BHARTIARTL", "Bharti Airtel", "Telecommunication"),
    Instrument("BSE", "BSE", "Financial Services"),
    Instrument("CIPLA", "Cipla", "Healthcare"),
    Instrument("COALINDIA", "Coal India", "Oil Gas & Consumable Fuels"),
    Instrument("DRREDDY", "Dr. Reddy's Laboratories", "Healthcare"),
    Instrument("EICHERMOT", "Eicher Motors", "Automobile and Auto Components"),
    Instrument("ETERNAL", "Eternal", "Consumer Services"),
    Instrument("GRASIM", "Grasim Industries", "Construction Materials"),
    Instrument("HCLTECH", "HCL Technologies", "Information Technology"),
    Instrument("HDFCBANK", "HDFC Bank", "Financial Services"),
    Instrument("HDFCLIFE", "HDFC Life Insurance", "Financial Services"),
    Instrument("HINDALCO", "Hindalco Industries", "Metals & Mining"),
    Instrument("HINDUNILVR", "Hindustan Unilever", "Fast Moving Consumer Goods"),
    Instrument("ICICIBANK", "ICICI Bank", "Financial Services"),
    Instrument("INDIGO", "InterGlobe Aviation", "Services"),
    Instrument("INFY", "Infosys", "Information Technology"),
    Instrument("ITC", "ITC", "Fast Moving Consumer Goods"),
    Instrument("JIOFIN", "Jio Financial Services", "Financial Services"),
    Instrument("JSWSTEEL", "JSW Steel", "Metals & Mining"),
    Instrument("KOTAKBANK", "Kotak Mahindra Bank", "Financial Services"),
    Instrument("LT", "Larsen & Toubro", "Construction"),
    Instrument("M&M", "Mahindra & Mahindra", "Automobile and Auto Components"),
    Instrument("MARUTI", "Maruti Suzuki India", "Automobile and Auto Components"),
    Instrument("MAXHEALTH", "Max Healthcare Institute", "Healthcare"),
    Instrument("NESTLEIND", "Nestle India", "Fast Moving Consumer Goods"),
    Instrument("NTPC", "NTPC", "Power"),
    Instrument("ONGC", "ONGC", "Oil Gas & Consumable Fuels"),
    Instrument("POWERGRID", "Power Grid Corporation", "Power"),
    Instrument("RELIANCE", "Reliance Industries", "Oil Gas & Consumable Fuels"),
    Instrument("SBILIFE", "SBI Life Insurance", "Financial Services"),
    Instrument("SBIN", "State Bank of India", "Financial Services"),
    Instrument("SHRIRAMFIN", "Shriram Finance", "Financial Services"),
    Instrument("SUNPHARMA", "Sun Pharmaceutical", "Healthcare"),
    Instrument("TATACONSUM", "Tata Consumer Products", "Fast Moving Consumer Goods"),
    Instrument("TATASTEEL", "Tata Steel", "Metals & Mining"),
    Instrument("TCS", "Tata Consultancy Services", "Information Technology"),
    Instrument("TECHM", "Tech Mahindra", "Information Technology"),
    Instrument("TITAN", "Titan Company", "Consumer Durables"),
    Instrument("TMPV", "Tata Motors Passenger Vehicles", "Automobile and Auto Components"),
    Instrument("TRENT", "Trent", "Consumer Services"),
    Instrument("ULTRACEMCO", "UltraTech Cement", "Construction Materials"),
)

LIQUID_10: tuple[str, ...] = (
    "RELIANCE",
    "TCS",
    "HDFCBANK",
    "ICICIBANK",
    "INFY",
    "BHARTIARTL",
    "LT",
    "SBIN",
    "ITC",
    "HINDUNILVR",
)

INDEX_SYMBOL = "^NSEI"


def select_universe(name: str) -> tuple[Instrument, ...]:
    if name == "nifty50":
        return NIFTY_50
    if name == "liquid10":
        wanted = set(LIQUID_10)
        return tuple(item for item in NIFTY_50 if item.symbol in wanted)
    raise ValueError(f"unknown universe {name}")


def sector_map(universe: tuple[Instrument, ...]) -> dict[str, str]:
    return {item.symbol: item.sector for item in universe}
