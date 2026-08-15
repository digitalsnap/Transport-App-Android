"""Generate XlsxSeedData.kt from the transport-research workbook.

Usage:
    python tools/generate_xlsx_seed.py "D:\\Downloads\\Chrome\\Philippine_Transport_Terminals_and_Fares_2026.xlsx"

Parses sheet 8 (DIRECT ROUTES - Bus) and sheet 9 (DIRECT ROUTES - Sea) into
core-network/src/main/java/com/ridevibe/core/network/mock/XlsxSeedData.kt.
Rows without a parsable PHP fare are skipped (the workbook marks many fares
NOT FOUND); the curated seeds in MockDatabase remain as fallback for those.
"""

import re
import sys

import pandas as pd

OUT_PATH = "core-network/src/main/java/com/ridevibe/core/network/mock/XlsxSeedData.kt"

# ── Name normalisation ───────────────────────────────────────────────────────
# Route endpoints are aliased onto the app's existing hub names where they are
# clearly the same place, so workbook routes merge with curated corridors.

ORIGIN_ALIASES = {
    "manila": ["Pasay"],
    "pasay": ["Pasay"],
    "cubao": ["Cubao"],
    "pitx": ["PITX"],
    "pitx gate 1, 2nd floor": ["PITX"],
    "pitx or cubao": ["PITX", "Cubao"],
    "araneta bus port": ["Cubao"],
    "araneta city bus port": ["Cubao"],
    "ermita (padre faura st.)": ["Ermita"],
    "ermita": ["Ermita"],
    "sampaloc": ["Sampaloc"],
    "avenida": ["Avenida"],
    "caloocan": ["Caloocan"],
    "monumento": ["Caloocan"],
    "kamias": ["Cubao"],
    "muntinlupa": ["Alabang VTX"],
    "alabang": ["Alabang VTX"],
    "turbina, calamba": ["Turbina Calamba"],
    "santa rosa integrated terminal (srit), laguna": ["Santa Rosa SRIT"],
    "one ayala, makati": ["One Ayala"],
    "binan, laguna": ["Binan"],
    "mandaue, cebu": ["Mandaue"],
    "baguio": ["Baguio"],
    "victory liner terminals": ["Cubao", "Pasay", "Caloocan"],
    "cebu south bus terminal": ["Cebu South Bus Terminal"],
    "cebu north bus terminal (sm city cebu)": ["Cebu North Bus Terminal"],
}

DEST_ALIASES = {
    "davao city": "Davao DCOTT",
    "cagayan de oro": "Cagayan de Oro Agora",
    "tacloban city, leyte": "Tacloban Terminal",
    "surigao city (lipata)": "Surigao",
    "baguio city": "Baguio",
    "baguio (governor pack rd.)": "Baguio",
    "baguio": "Baguio",
    "iloilo city": "Iloilo Ceres Terminal",
    "san carlos city, pangasinan": "San Carlos (Pangasinan)",
    "roxas city, capiz": "Roxas",
    "olongapo / subic": "Olongapo",
    "batangas": "Batangas Grand Terminal",
    "batangas grand terminal": "Batangas Grand Terminal",
    "jam lemery terminal, batangas": "Lemery",
    "sm lipa, batangas": "Lipa",
    "dumaguete, negros oriental": "Dumaguete Ceres Terminal",
    "ormoc city, leyte": "Ormoc",
    "ormoc city, leyte (via carigara)": "Ormoc",
    "rawis, laoang, northern samar": "Laoang",
}

SEA_ALIASES = {
    "batangas": "Batangas Port",
    "calapan, or. mindoro": "Calapan",
    "calapan": "Calapan",
    "roxas, or. mindoro": "Roxas (Mindoro)",
    "caticlan, aklan": "Caticlan",
    "roxas city, capiz": "Roxas",
    "romblon": "Romblon",
    "sibuyan (magdiwang)": "Sibuyan",
    "cajidiocan, romblon": "Cajidiocan",
    "cajidiocan": "Cajidiocan",
    "cebu (pier 3)": "Cebu Port",
    "cebu city": "Cebu Port",
    "cebu": "Cebu Port",
    "surigao city": "Surigao Port",
    "dapitan": "Dapitan Port",
    "dapitan city": "Dapitan Port",
    "dumaguete": "Dumaguete Port",
    "dumaguete city": "Dumaguete Port",
    "nasipit, agusan del norte": "Nasipit Port",
    "cagayan de oro": "Cagayan de Oro Port",
    "cagayan de oro (mon/wed/fri)": "Cagayan de Oro Port",
    "cagayan de oro (sunday)": "Cagayan de Oro Port",
    "cagayan de oro (tue/thu/sat)": "Cagayan de Oro Port",
    "cebu (mon/wed/fri)": "Cebu Port",
    "cebu (tue/thu/sat/sun)": "Cebu Port",
    "ozamiz": "Ozamiz Port",
    "iligan": "Iligan Port",
    "iligan (via ozamiz)": "Iligan Port",
    "iloilo": "Iloilo Port",
    "masbate": "Masbate",
    "tagbilaran, bohol": "Tagbilaran Port",
    "hilongos, leyte": "Hilongos",
    "baybay, leyte": "Baybay",
    "ormoc, leyte": "Ormoc Port",
    "zamboanga": "Zamboanga Port",
    "isabela, basilan": "Isabela (Basilan)",
    "lamitan, basilan": "Lamitan",
    "jolo, sulu": "Jolo",
    "bongao, tawi-tawi": "Bongao",
    "siquijor": "Siquijor",
    "hagnaya": "Hagnaya",
    "santa fe, bantayan island": "Santa Fe (Bantayan)",
    "el nido": "El Nido Port",
    "coron": "Coron Port",
    "manila": "Manila North Harbor",
}

LUXURY_WORDS = ("luxury", "royal", "first class", "sleeper", "premiere", "super deluxe", "vip", "private room", "executive", "lazyboy", "cabin a")
DELUXE_WORDS = ("deluxe", "aircon", "air-conditioned", "regular ac", "thaco", "p2p", "semi deluxe", "cabin", "tourist deluxe", "business", "ac,", "2nd a/c")


def norm(value):
    return re.sub(r"\s+", " ", str(value)).strip()


def bus_class(text):
    t = norm(text).lower()
    if any(w in t for w in LUXURY_WORDS):
        return "LUXURY"
    if any(w in t for w in DELUXE_WORDS):
        return "DELUXE"
    return "ORDINARY"


def parse_fare(text):
    """First plausible PHP fare in the cell, or None."""
    t = norm(text)
    if not t or t in "-" or "NOT FOUND" in t.upper() or t.lower().startswith("see "):
        return None
    if "TEMPORARILY UNAVAILABLE" in t.upper():
        return None
    php = re.search(r"PHP\s*([\d,]+)", t)
    if php:
        return float(php.group(1).replace(",", ""))
    if "US$" in t or "USD" in t:
        return None
    m = re.search(r"([\d,]+(?:\.\d+)?)", t)
    if not m:
        return None
    fare = float(m.group(1).replace(",", ""))
    return fare if fare >= 40 else None


def parse_fare_list(text):
    """All fares in a multi-class cell ('649.00 / 765.00 / 970.00')."""
    t = norm(text)
    if "US$" in t or "NOT FOUND" in t.upper():
        return []
    fares = []
    for token in t.split("/"):
        token = re.sub(r"\(.*?\)", "", token)
        m = re.search(r"([\d,]+(?:\.\d+)?)", token)
        if m:
            value = float(m.group(1).replace(",", ""))
            # ranges like 2,160-10,080 keep the lower bound
            if value >= 40:
                fares.append(value)
    return fares


def parse_duration_minutes(text, fare):
    t = norm(text).lower()
    m = re.search(r"(?:~\s*)?(\d+)\s*d\s*(\d+)\s*h", t)
    if m:
        return int(m.group(1)) * 1440 + int(m.group(2)) * 60
    m = re.search(r"(\d+)\s*h\s*(\d+)\s*m", t)
    if m:
        return int(m.group(1)) * 60 + int(m.group(2))
    m = re.search(r"(\d+(?:\.\d+)?)\s*(?:-\s*(\d+(?:\.\d+)?))?\s*h", t)
    if m:
        low = float(m.group(1))
        high = float(m.group(2)) if m.group(2) else low
        return int((low + high) / 2 * 60)
    # No published duration: rough distance proxy from the fare.
    return int(min(max(fare * 0.7, 90), 2200))


def parse_hours(text, duration_minutes):
    t = norm(text).lower()
    if "odd hours" in t:
        return list(range(1, 24, 2))
    if "hourly" in t:
        return [2, 6, 10, 14, 18, 22]
    if "18 trips" in t:
        return [5, 7, 9, 11, 13, 15, 17, 19]
    if "multiple daily" in t:
        return [6, 9, 12, 15, 18, 21]
    m = re.match(r"(\d+)\s*daily", t)
    if m:
        n = min(int(m.group(1)), 8)
        step = max(24 // n, 2)
        return [(3 + i * step) % 24 for i in range(n)]
    times = sorted({int(h) for h in re.findall(r"(\d{1,2}):\d{2}", t) if int(h) < 24})
    if times:
        return times[:8]
    if "every 2.5" in t:
        return [5, 8, 11, 14, 17, 20]
    if duration_minutes >= 720:
        return [13, 17]
    if duration_minutes >= 360:
        return [6, 10, 18, 20]
    return [5, 8, 11, 14, 17]


def clean_operator(text):
    t = re.sub(r"\(.*?\)", "", norm(text)).strip().strip(";")
    t = t.split(";")[0].strip()
    if " / " in t and len(t) > 30:
        t = t.split(" / ")[0].strip()
    if re.match(r"^\d+\s+operators?$", t.lower()):
        t = "Various operators"
    return t or "Various operators"


def map_origins(text):
    t = norm(text)
    lowered = t.lower()
    if lowered in ORIGIN_ALIASES:
        return ORIGIN_ALIASES[lowered]
    # strip parentheticals, then split compound origins
    stripped = re.sub(r"\(.*?\)", "", t).strip()
    parts = re.split(r"\s*/\s*|\s+or\s+|\s+and\s+", stripped)
    out = []
    for part in parts:
        key = part.strip().lower().strip(",")
        if not key:
            continue
        mapped = ORIGIN_ALIASES.get(key)
        if mapped:
            out.extend(mapped)
        elif len(part.strip()) > 2 and "terminal" not in key:
            out.append(part.strip().split(",")[0])
    seen, unique = set(), []
    for name in out:
        if name not in seen:
            seen.add(name)
            unique.append(name)
    return unique[:3]


def map_dest(text):
    t = norm(text)
    lowered = t.lower()
    if lowered in DEST_ALIASES:
        return DEST_ALIASES[lowered]
    if "/" in t:  # multi-destination summary rows are not bookable corridors
        return None
    return t.split(",")[0].strip() or None


def sea_endpoint(text):
    key = re.sub(r"\(vehicles?\)", "", norm(text), flags=re.I).strip().lower()
    return SEA_ALIASES.get(key, norm(text).split(",")[0].strip())


def is_fastcraft(operator, klass):
    blob = f"{operator} {klass}".lower()
    return any(w in blob for w in ("fascraft", "fastcraft", "oceanjet", "supercat", "weesam", "ferry express"))


ROOM_RATE_WORDS = ("vip room", "vip (", "private room", "room (")  # per-room, not per-passenger


def is_room_rate(klass):
    return any(w in klass.lower() for w in ROOM_RATE_WORDS)


def is_multi_class(klass):
    return "/" in klass or " to " in klass.lower()


def parse_bus_sheet(xl):
    df = xl.parse("8. DIRECT ROUTES - Bus").dropna(how="all")
    routes = {}
    for _, row in df.iterrows():
        origin_cell, dest_cell = row.iloc[1], row.iloc[2]
        operator, klass, fare_cell = row.iloc[3], row.iloc[4], row.iloc[5]
        duration_cell, times_cell = row.iloc[7], row.iloc[8]
        if pd.isna(origin_cell) or pd.isna(dest_cell):
            continue
        dest = map_dest(dest_cell)
        origins = map_origins(origin_cell)
        if not dest or not origins:
            continue
        operator = clean_operator(operator)
        if operator in ("-", "Various operators") and "bookaway" not in str(row.iloc[11]).lower():
            if operator == "-":
                continue
        # multi-class rows: classes and fares both slash-separated and matched
        classes = [c.strip() for c in norm(klass).split("/")]
        fares = parse_fare_list(fare_cell)
        if len(fares) == len(classes) and len(fares) > 1:
            services = list(zip(classes, fares))
        else:
            fare = parse_fare(fare_cell)
            if fare is None:
                continue
            # a class range with one fare quotes the BASE fare — seed the base class
            services = [("Ordinary" if is_multi_class(norm(klass)) else norm(klass), fare)]
        # duration belongs to the route, not the class — derive from the base fare
        base_fare = min(f for _, f in services)
        duration = parse_duration_minutes(duration_cell, base_fare)
        for class_text, fare in services:
            hours = parse_hours(times_cell, duration)
            for origin in origins:
                if origin == dest:
                    continue
                routes.setdefault((origin, dest), []).append(
                    (operator, bus_class(class_text), fare, hours, duration, "BUS"),
                )
    return routes


def parse_sea_sheet(xl):
    df = xl.parse("9. DIRECT ROUTES - Sea").dropna(how="all")
    routes = {}
    for _, row in df.iterrows():
        corridor, route_cell = norm(row.iloc[0]), norm(row.iloc[1])
        operator, klass, fare_cell = row.iloc[2], row.iloc[3], row.iloc[4]
        schedule_cell, crossing_cell = row.iloc[9], row.iloc[10]
        if not route_cell or corridor.upper() in ("ROLLING CARGO", "NOT FOUND"):
            continue
        if re.search(r"vehicles?\)?$", route_cell, flags=re.I) or "rolling cargo" in route_cell.lower():
            continue
        if "sandakan" in route_cell.lower():  # international — out of app scope
            continue
        klass_blob = norm(klass).lower()
        if any(w in klass_blob for w in ("vehicle", "motorcycle", "not published", "rolling")):
            continue  # vehicle/cargo tariffs, not passenger fares
        parts = re.split(r"\s*<->\s*|\s*->\s*", route_cell)
        if len(parts) != 2:
            continue
        origin, dest = sea_endpoint(parts[0]), sea_endpoint(parts[1])
        if not origin or not dest or origin == dest:
            continue
        operator = clean_operator(operator)
        classes = [c.strip() for c in norm(klass).split("/")]
        fares = parse_fare_list(fare_cell)
        if len(fares) == len(classes) and len(fares) > 1:
            services = [(c, f) for c, f in zip(classes, fares) if not is_room_rate(c)]
        else:
            fare = parse_fare(fare_cell)
            if fare is None:
                continue
            services = [("Ordinary" if is_multi_class(norm(klass)) else norm(klass), fare)]
        if not services:
            continue
        # duration belongs to the route, not the class — derive from the base fare
        base_fare = min(f for _, f in services)
        duration = parse_duration_minutes(crossing_cell, base_fare * 0.45)
        for class_text, fare in services:
            hours = parse_hours(schedule_cell, duration)
            kind = "FASTCRAFT" if is_fastcraft(operator, class_text) else "FERRY"
            routes.setdefault((origin, dest), []).append(
                (operator, bus_class(class_text), fare, hours, duration, kind),
            )
    return routes


def emit(routes, name):
    lines = [f"internal val {name}: Map<Pair<String, String>, List<XlsxService>> = mapOf("]
    for (origin, dest), services in routes.items():
        deduped, seen = [], set()
        for service in services:
            key = (service[0], service[1], service[2])
            if key not in seen:
                seen.add(key)
                deduped.append(service)
        lines.append(f'    ("{origin}" to "{dest}") to listOf(')
        for operator, klass, fare, hours, duration, kind in deduped[:6]:
            hours_kt = ", ".join(str(h) for h in hours)
            lines.append(
                f'        XlsxService("{operator}", BusClass.{klass}, {fare}, listOf({hours_kt}), {duration}, RideKind.{kind}),'
            )
        lines.append("    ),")
    lines.append(")")
    return "\n".join(lines)


def main():
    workbook = sys.argv[1] if len(sys.argv) > 1 else r"D:\Downloads\Chrome\Philippine_Transport_Terminals_and_Fares_2026.xlsx"
    xl = pd.ExcelFile(workbook)
    bus = parse_bus_sheet(xl)
    sea = parse_sea_sheet(xl)
    bus_count = sum(len(v) for v in bus.values())
    sea_count = sum(len(v) for v in sea.values())

    header = f"""package com.ridevibe.core.network.mock

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.RideKind

// ═════════════════════════════════════════════════════════════════════════════
// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: Philippine_Transport_Terminals_and_Fares_2026.xlsx (research
// compilation, sheets 8-9). Regenerate with tools/generate_xlsx_seed.py.
// {len(bus)} bus corridors ({bus_count} services), {len(sea)} sea corridors ({sea_count} services).
// Fares are 2026 published figures where the workbook found them; rows the
// workbook marked NOT FOUND are omitted (curated seeds cover the gaps).
// Departure hours and durations are parsed where published, estimated where not.
// ═════════════════════════════════════════════════════════════════════════════

internal data class XlsxService(
    val operatorName: String,
    val busClass: BusClass,
    val farePhp: Double,
    val departureHours: List<Int>,
    val durationMinutes: Long,
    val kind: RideKind,
)

"""
    body = emit(bus, "xlsxBusRoutes") + "\n\n" + emit(sea, "xlsxSeaRoutes") + "\n"
    with open(OUT_PATH, "w", encoding="utf-8") as f:
        f.write(header + body)
    print(f"Wrote {OUT_PATH}: {len(bus)} bus corridors / {bus_count} services, {len(sea)} sea corridors / {sea_count} services")


if __name__ == "__main__":
    main()
