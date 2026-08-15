package com.ridevibe.core.network.mock

import com.ridevibe.core.domain.model.BusClass
import com.ridevibe.core.domain.model.RideKind

// ═════════════════════════════════════════════════════════════════════════════
// GENERATED FILE — DO NOT EDIT BY HAND.
// Source: Philippine_Transport_Terminals_and_Fares_2026.xlsx (research
// compilation, sheets 8-9). Regenerate with tools/generate_xlsx_seed.py.
// 108 bus corridors (171 services), 38 sea corridors (93 services).
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

internal val xlsxBusRoutes: Map<Pair<String, String>, List<XlsxService>> = mapOf(
    ("Pasay" to "Davao DCOTT") to listOf(
        XlsxService("Philtranco", BusClass.ORDINARY, 2545.0, listOf(13, 17), 1950, RideKind.BUS),
        XlsxService("Ceres Transport", BusClass.ORDINARY, 4500.0, listOf(13, 17), 2100, RideKind.BUS),
    ),
    ("Cubao" to "Davao DCOTT") to listOf(
        XlsxService("Philtranco", BusClass.ORDINARY, 2545.0, listOf(13, 17), 1950, RideKind.BUS),
    ),
    ("PITX" to "Davao DCOTT") to listOf(
        XlsxService("Davao Metro Shuttle", BusClass.DELUXE, 3680.0, listOf(13, 17), 2100, RideKind.BUS),
    ),
    ("Pasay" to "Cagayan de Oro Agora") to listOf(
        XlsxService("Yohance Express", BusClass.ORDINARY, 3200.0, listOf(7, 9), 2160, RideKind.BUS),
    ),
    ("Pasay" to "Tacloban Terminal") to listOf(
        XlsxService("Philtranco", BusClass.ORDINARY, 2500.0, listOf(13, 17), 1110, RideKind.BUS),
        XlsxService("Various operators", BusClass.DELUXE, 1350.0, listOf(10, 19), 1011, RideKind.BUS),
    ),
    ("Cubao" to "Tacloban Terminal") to listOf(
        XlsxService("Philtranco", BusClass.ORDINARY, 2500.0, listOf(13, 17), 1110, RideKind.BUS),
    ),
    ("Pasay" to "Maasin") to listOf(
        XlsxService("Philtranco", BusClass.ORDINARY, 2700.0, listOf(13, 17), 1350, RideKind.BUS),
    ),
    ("Cubao" to "Maasin") to listOf(
        XlsxService("Philtranco", BusClass.ORDINARY, 2700.0, listOf(13, 17), 1350, RideKind.BUS),
    ),
    ("Turbina Calamba" to "Ormoc") to listOf(
        XlsxService("Philtranco", BusClass.ORDINARY, 1769.0, listOf(13, 17), 1260, RideKind.BUS),
    ),
    ("Santa Rosa SRIT" to "Ormoc") to listOf(
        XlsxService("DLTB", BusClass.DELUXE, 1709.0, listOf(5, 10), 1080, RideKind.BUS),
    ),
    ("Pasay" to "Ormoc") to listOf(
        XlsxService("DLTB", BusClass.ORDINARY, 3100.0, listOf(3, 11, 19), 2170, RideKind.BUS),
    ),
    ("Pasay" to "Laoang") to listOf(
        XlsxService("DLTB", BusClass.DELUXE, 1489.0, listOf(15), 480, RideKind.BUS),
    ),
    ("Pasay" to "Guiuan") to listOf(
        XlsxService("DLTB", BusClass.DELUXE, 2091.0, listOf(13), 1463, RideKind.BUS),
    ),
    ("Pasay" to "Iloilo Ceres Terminal") to listOf(
        XlsxService("Philtranco", BusClass.ORDINARY, 1500.0, listOf(13, 17), 1020, RideKind.BUS),
    ),
    ("Cubao" to "Iloilo Ceres Terminal") to listOf(
        XlsxService("Philtranco", BusClass.ORDINARY, 1500.0, listOf(13, 17), 1020, RideKind.BUS),
    ),
    ("Pasay" to "Antique") to listOf(
        XlsxService("Ceres Transport", BusClass.DELUXE, 762.0, listOf(9), 420, RideKind.BUS),
    ),
    ("Pasay" to "Calapan") to listOf(
        XlsxService("RORO Bus", BusClass.ORDINARY, 638.0, listOf(6, 10, 18, 20), 446, RideKind.BUS),
    ),
    ("Turbina Calamba" to "San Jose") to listOf(
        XlsxService("RORO Bus", BusClass.ORDINARY, 774.0, listOf(6, 10, 18, 20), 541, RideKind.BUS),
    ),
    ("Pasay" to "Occidental Mindoro") to listOf(
        XlsxService("RORO Bus", BusClass.ORDINARY, 1269.0, listOf(13, 17), 888, RideKind.BUS),
        XlsxService("Partas", BusClass.ORDINARY, 935.0, listOf(6, 10, 18, 20), 654, RideKind.BUS),
    ),
    ("Pasay" to "Masbate") to listOf(
        XlsxService("RORO Bus", BusClass.ORDINARY, 1765.0, listOf(13, 17), 1235, RideKind.BUS),
        XlsxService("Bicol Isarog", BusClass.ORDINARY, 1200.0, listOf(13, 17), 840, RideKind.BUS),
    ),
    ("Mandaue" to "Masbate") to listOf(
        XlsxService("RORO Bus", BusClass.ORDINARY, 1214.0, listOf(13, 17), 849, RideKind.BUS),
    ),
    ("Turbina Calamba" to "Masbate") to listOf(
        XlsxService("Bicol Isarog", BusClass.ORDINARY, 1100.0, listOf(13, 17), 770, RideKind.BUS),
    ),
    ("Pasay" to "Baguio") to listOf(
        XlsxService("Victory Liner", BusClass.DELUXE, 669.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
        XlsxService("Victory Liner", BusClass.DELUXE, 680.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
        XlsxService("Victory Liner", BusClass.DELUXE, 801.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
        XlsxService("Victory Liner", BusClass.LUXURY, 1078.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
        XlsxService("Victory Liner", BusClass.LUXURY, 1616.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
        XlsxService("Genesis Transport", BusClass.DELUXE, 649.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
    ),
    ("Cubao" to "Baguio") to listOf(
        XlsxService("Victory Liner", BusClass.DELUXE, 655.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
        XlsxService("Victory Liner", BusClass.DELUXE, 666.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
        XlsxService("Victory Liner", BusClass.DELUXE, 784.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
        XlsxService("Victory Liner", BusClass.LUXURY, 1055.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
        XlsxService("Victory Liner", BusClass.LUXURY, 1581.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
        XlsxService("Genesis Transport", BusClass.DELUXE, 649.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
    ),
    ("Caloocan" to "Baguio") to listOf(
        XlsxService("Victory Liner", BusClass.DELUXE, 648.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
    ),
    ("Sampaloc" to "Baguio") to listOf(
        XlsxService("Victory Liner", BusClass.DELUXE, 657.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
    ),
    ("Avenida" to "Baguio") to listOf(
        XlsxService("Genesis / JoyBus", BusClass.DELUXE, 649.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
        XlsxService("Genesis / JoyBus", BusClass.DELUXE, 765.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
        XlsxService("Genesis / JoyBus", BusClass.LUXURY, 970.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
        XlsxService("Genesis / JoyBus", BusClass.LUXURY, 999.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
    ),
    ("PITX" to "Baguio") to listOf(
        XlsxService("Solid North Transit", BusClass.DELUXE, 665.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
        XlsxService("Solid North Transit", BusClass.LUXURY, 1080.0, listOf(6, 9, 12, 15, 18, 21), 360, RideKind.BUS),
        XlsxService("Solid North Transit", BusClass.LUXURY, 525.0, listOf(2, 6, 10, 14, 18, 22), 367, RideKind.BUS),
        XlsxService("Solid North Transit", BusClass.LUXURY, 760.0, listOf(5, 8, 11, 14, 17, 20), 532, RideKind.BUS),
        XlsxService("Solid North Transit", BusClass.ORDINARY, 500.0, listOf(5, 8, 11, 14, 17), 350, RideKind.BUS),
    ),
    ("Cubao" to "San Fernando") to listOf(
        XlsxService("Partas", BusClass.ORDINARY, 507.0, listOf(5, 8, 11, 14, 17), 354, RideKind.BUS),
    ),
    ("Pasay" to "San Fernando") to listOf(
        XlsxService("Partas", BusClass.ORDINARY, 507.0, listOf(5, 8, 11, 14, 17), 354, RideKind.BUS),
    ),
    ("Sampaloc" to "San Fernando") to listOf(
        XlsxService("Partas", BusClass.ORDINARY, 507.0, listOf(5, 8, 11, 14, 17), 354, RideKind.BUS),
        XlsxService("Viron Transit", BusClass.ORDINARY, 505.0, listOf(5, 8, 11, 14, 17), 353, RideKind.BUS),
    ),
    ("Cubao" to "Vigan") to listOf(
        XlsxService("Partas", BusClass.ORDINARY, 753.0, listOf(6, 10, 18, 20), 527, RideKind.BUS),
        XlsxService("Partas", BusClass.DELUXE, 800.0, listOf(6, 10, 18, 20), 560, RideKind.BUS),
    ),
    ("Pasay" to "Vigan") to listOf(
        XlsxService("Partas", BusClass.ORDINARY, 753.0, listOf(6, 10, 18, 20), 527, RideKind.BUS),
    ),
    ("Sampaloc" to "Vigan") to listOf(
        XlsxService("Partas", BusClass.ORDINARY, 753.0, listOf(6, 10, 18, 20), 527, RideKind.BUS),
    ),
    ("Cubao" to "Laoag") to listOf(
        XlsxService("Partas", BusClass.ORDINARY, 745.0, listOf(6, 10, 18, 20), 521, RideKind.BUS),
        XlsxService("Partas", BusClass.DELUXE, 960.0, listOf(6, 10, 18, 20), 672, RideKind.BUS),
        XlsxService("GV Florida", BusClass.ORDINARY, 700.0, listOf(6, 10, 18, 20), 489, RideKind.BUS),
    ),
    ("Pasay" to "Laoag") to listOf(
        XlsxService("Partas", BusClass.ORDINARY, 745.0, listOf(6, 10, 18, 20), 521, RideKind.BUS),
        XlsxService("GV Florida", BusClass.ORDINARY, 700.0, listOf(6, 10, 18, 20), 489, RideKind.BUS),
    ),
    ("Sampaloc" to "Laoag") to listOf(
        XlsxService("Partas", BusClass.ORDINARY, 745.0, listOf(6, 10, 18, 20), 521, RideKind.BUS),
        XlsxService("GV Florida", BusClass.ORDINARY, 700.0, listOf(6, 10, 18, 20), 489, RideKind.BUS),
    ),
    ("Cubao" to "Pagudpud") to listOf(
        XlsxService("Partas", BusClass.ORDINARY, 856.0, listOf(6, 10, 18, 20), 599, RideKind.BUS),
    ),
    ("Pasay" to "Pagudpud") to listOf(
        XlsxService("Partas", BusClass.ORDINARY, 856.0, listOf(6, 10, 18, 20), 599, RideKind.BUS),
    ),
    ("Sampaloc" to "Pagudpud") to listOf(
        XlsxService("Partas", BusClass.ORDINARY, 856.0, listOf(6, 10, 18, 20), 599, RideKind.BUS),
    ),
    ("Cubao" to "Bangued") to listOf(
        XlsxService("Partas", BusClass.ORDINARY, 711.0, listOf(6, 10, 18, 20), 497, RideKind.BUS),
        XlsxService("Partas", BusClass.DELUXE, 800.0, listOf(6, 10, 18, 20), 560, RideKind.BUS),
    ),
    ("Pasay" to "Bangued") to listOf(
        XlsxService("Partas", BusClass.ORDINARY, 711.0, listOf(6, 10, 18, 20), 497, RideKind.BUS),
    ),
    ("Sampaloc" to "Bangued") to listOf(
        XlsxService("Partas", BusClass.ORDINARY, 711.0, listOf(6, 10, 18, 20), 497, RideKind.BUS),
    ),
    ("Cubao" to "Tuguegarao") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 886.0, listOf(6, 10, 18, 20), 620, RideKind.BUS),
        XlsxService("GV Florida", BusClass.ORDINARY, 800.0, listOf(6, 10, 18, 20), 560, RideKind.BUS),
    ),
    ("Pasay" to "Tuguegarao") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 886.0, listOf(6, 10, 18, 20), 620, RideKind.BUS),
        XlsxService("GV Florida", BusClass.ORDINARY, 800.0, listOf(6, 10, 18, 20), 560, RideKind.BUS),
    ),
    ("Sampaloc" to "Tuguegarao") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 886.0, listOf(6, 10, 18, 20), 620, RideKind.BUS),
        XlsxService("GV Florida", BusClass.ORDINARY, 800.0, listOf(6, 10, 18, 20), 560, RideKind.BUS),
    ),
    ("Sampaloc" to "Isabela") to listOf(
        XlsxService("GV Florida", BusClass.ORDINARY, 700.0, listOf(6, 10, 18, 20), 489, RideKind.BUS),
    ),
    ("Cubao" to "Isabela") to listOf(
        XlsxService("GV Florida", BusClass.ORDINARY, 700.0, listOf(6, 10, 18, 20), 489, RideKind.BUS),
    ),
    ("Pasay" to "Isabela") to listOf(
        XlsxService("GV Florida", BusClass.ORDINARY, 700.0, listOf(6, 10, 18, 20), 489, RideKind.BUS),
    ),
    ("Sampaloc" to "Quirino") to listOf(
        XlsxService("GV Florida", BusClass.ORDINARY, 610.0, listOf(6, 10, 18, 20), 427, RideKind.BUS),
    ),
    ("Cubao" to "Quirino") to listOf(
        XlsxService("GV Florida", BusClass.ORDINARY, 610.0, listOf(6, 10, 18, 20), 427, RideKind.BUS),
    ),
    ("Pasay" to "Quirino") to listOf(
        XlsxService("GV Florida", BusClass.ORDINARY, 610.0, listOf(6, 10, 18, 20), 427, RideKind.BUS),
    ),
    ("Cubao" to "Dagupan") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 392.0, listOf(5, 8, 11, 14, 17), 274, RideKind.BUS),
        XlsxService("Solid North Transit", BusClass.ORDINARY, 1060.0, listOf(13, 17), 742, RideKind.BUS),
    ),
    ("Pasay" to "Dagupan") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 392.0, listOf(5, 8, 11, 14, 17), 274, RideKind.BUS),
    ),
    ("Caloocan" to "Dagupan") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 392.0, listOf(5, 8, 11, 14, 17), 274, RideKind.BUS),
    ),
    ("Cubao" to "Lingayen") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 407.0, listOf(5, 8, 11, 14, 17), 284, RideKind.BUS),
    ),
    ("Pasay" to "Lingayen") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 407.0, listOf(5, 8, 11, 14, 17), 284, RideKind.BUS),
    ),
    ("Caloocan" to "Lingayen") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 407.0, listOf(5, 8, 11, 14, 17), 284, RideKind.BUS),
    ),
    ("Cubao" to "Bolinao") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 500.0, listOf(5, 8, 11, 14, 17), 350, RideKind.BUS),
    ),
    ("Pasay" to "Bolinao") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 500.0, listOf(5, 8, 11, 14, 17), 350, RideKind.BUS),
    ),
    ("Caloocan" to "Bolinao") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 500.0, listOf(5, 8, 11, 14, 17), 350, RideKind.BUS),
    ),
    ("Cubao" to "Olongapo") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 285.0, listOf(5, 8, 11, 14, 17), 199, RideKind.BUS),
    ),
    ("Pasay" to "Olongapo") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 285.0, listOf(5, 8, 11, 14, 17), 199, RideKind.BUS),
    ),
    ("Caloocan" to "Olongapo") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 285.0, listOf(5, 8, 11, 14, 17), 199, RideKind.BUS),
        XlsxService("Saulog Transit", BusClass.ORDINARY, 300.0, listOf(5, 8, 11, 14, 17), 210, RideKind.BUS),
    ),
    ("PITX" to "Olongapo") to listOf(
        XlsxService("Saulog Transit", BusClass.ORDINARY, 300.0, listOf(5, 8, 11, 14, 17), 210, RideKind.BUS),
    ),
    ("Pasay ; Cubao" to "Olongapo") to listOf(
        XlsxService("Saulog Transit", BusClass.ORDINARY, 300.0, listOf(5, 8, 11, 14, 17), 210, RideKind.BUS),
    ),
    ("Cubao" to "Pampanga") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 219.0, listOf(5, 8, 11, 14, 17), 153, RideKind.BUS),
    ),
    ("Pasay" to "Pampanga") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 219.0, listOf(5, 8, 11, 14, 17), 153, RideKind.BUS),
    ),
    ("Caloocan" to "Pampanga") to listOf(
        XlsxService("Victory Liner", BusClass.ORDINARY, 219.0, listOf(5, 8, 11, 14, 17), 153, RideKind.BUS),
    ),
    ("Avenida" to "Dau") to listOf(
        XlsxService("Philippine Rabbit", BusClass.ORDINARY, 200.0, listOf(5, 8, 11, 14, 17), 140, RideKind.BUS),
    ),
    ("Caloocan" to "Dau") to listOf(
        XlsxService("Philippine Rabbit", BusClass.ORDINARY, 200.0, listOf(5, 8, 11, 14, 17), 140, RideKind.BUS),
    ),
    ("PITX" to "San Carlos (Pangasinan)") to listOf(
        XlsxService("Solid North Transit", BusClass.ORDINARY, 902.0, listOf(6, 10, 18, 20), 631, RideKind.BUS),
    ),
    ("Cubao" to "San Carlos (Pangasinan)") to listOf(
        XlsxService("Solid North Transit", BusClass.ORDINARY, 902.0, listOf(6, 10, 18, 20), 631, RideKind.BUS),
    ),
    ("PITX" to "Dagupan") to listOf(
        XlsxService("Solid North Transit", BusClass.ORDINARY, 1060.0, listOf(13, 17), 742, RideKind.BUS),
    ),
    ("Cubao" to "Banaue") to listOf(
        XlsxService("Coda Lines", BusClass.ORDINARY, 799.0, listOf(6, 10, 18, 20), 559, RideKind.BUS),
    ),
    ("Cubao" to "Bontoc") to listOf(
        XlsxService("Coda Lines", BusClass.ORDINARY, 1044.0, listOf(13, 17), 730, RideKind.BUS),
    ),
    ("Cubao" to "Sagada") to listOf(
        XlsxService("Coda Lines", BusClass.DELUXE, 912.0, listOf(20, 21, 22), 638, RideKind.BUS),
        XlsxService("Coda Lines", BusClass.LUXURY, 1176.0, listOf(20, 21, 22), 823, RideKind.BUS),
        XlsxService("Coda Lines", BusClass.DELUXE, 760.0, listOf(6, 10, 18, 20), 532, RideKind.BUS),
        XlsxService("Coda Lines", BusClass.LUXURY, 980.0, listOf(6, 10, 18, 20), 532, RideKind.BUS),
    ),
    ("Baguio" to "Sagada") to listOf(
        XlsxService("GL Trans", BusClass.ORDINARY, 220.0, listOf(5, 8, 11, 14, 17), 154, RideKind.BUS),
    ),
    ("Pasay" to "Naga") to listOf(
        XlsxService("DLTB", BusClass.ORDINARY, 650.0, listOf(6, 10, 18, 20), 510, RideKind.BUS),
        XlsxService("Bicol Isarog", BusClass.ORDINARY, 950.0, listOf(6, 10, 18, 20), 665, RideKind.BUS),
        XlsxService("Penafrancia Tours", BusClass.ORDINARY, 875.0, listOf(6, 10, 18, 20), 612, RideKind.BUS),
        XlsxService("Philtranco", BusClass.ORDINARY, 1350.0, listOf(6, 10, 18, 20), 510, RideKind.BUS),
    ),
    ("Pasay" to "Legazpi") to listOf(
        XlsxService("DLTB", BusClass.ORDINARY, 813.0, listOf(6, 10, 18, 20), 600, RideKind.BUS),
        XlsxService("Bicol Isarog", BusClass.ORDINARY, 950.0, listOf(6, 10, 18, 20), 665, RideKind.BUS),
        XlsxService("Penafrancia Tours", BusClass.ORDINARY, 950.0, listOf(6, 10, 18, 20), 665, RideKind.BUS),
        XlsxService("Philtranco", BusClass.ORDINARY, 1300.0, listOf(6, 10, 18, 20), 600, RideKind.BUS),
        XlsxService("Penafrancia", BusClass.LUXURY, 1500.0, listOf(6, 10, 18, 20), 690, RideKind.BUS),
    ),
    ("Pasay" to "Tabaco City") to listOf(
        XlsxService("DLTB", BusClass.ORDINARY, 857.0, listOf(6, 10, 18, 20), 599, RideKind.BUS),
    ),
    ("Pasay" to "Sorsogon") to listOf(
        XlsxService("DLTB", BusClass.ORDINARY, 996.0, listOf(6, 10, 18, 20), 697, RideKind.BUS),
        XlsxService("Penafrancia Tours", BusClass.ORDINARY, 1400.0, listOf(13, 17), 979, RideKind.BUS),
        XlsxService("Philtranco", BusClass.ORDINARY, 1100.0, listOf(6, 10, 18, 20), 690, RideKind.BUS),
    ),
    ("Cubao" to "Naga") to listOf(
        XlsxService("Bicol Isarog", BusClass.LUXURY, 1750.0, listOf(13, 17), 1225, RideKind.BUS),
        XlsxService("Superlines", BusClass.ORDINARY, 875.0, listOf(6, 10, 18, 20), 612, RideKind.BUS),
        XlsxService("Raymond Transportation", BusClass.ORDINARY, 600.0, listOf(6, 10, 18, 20), 420, RideKind.BUS),
        XlsxService("Cagsawa Travel", BusClass.ORDINARY, 760.0, listOf(6, 10, 18, 20), 532, RideKind.BUS),
        XlsxService("Philtranco", BusClass.ORDINARY, 1350.0, listOf(6, 10, 18, 20), 510, RideKind.BUS),
    ),
    ("Cubao" to "Legazpi") to listOf(
        XlsxService("Bicol Isarog", BusClass.LUXURY, 1750.0, listOf(13, 17), 1225, RideKind.BUS),
        XlsxService("Superlines", BusClass.ORDINARY, 950.0, listOf(6, 10, 18, 20), 665, RideKind.BUS),
        XlsxService("Raymond Transportation", BusClass.ORDINARY, 750.0, listOf(6, 10, 18, 20), 525, RideKind.BUS),
        XlsxService("Cagsawa Travel", BusClass.ORDINARY, 760.0, listOf(6, 10, 18, 20), 532, RideKind.BUS),
        XlsxService("Philtranco", BusClass.ORDINARY, 1300.0, listOf(6, 10, 18, 20), 600, RideKind.BUS),
    ),
    ("Pasay" to "Lagonoy") to listOf(
        XlsxService("Penafrancia Tours", BusClass.ORDINARY, 875.0, listOf(6, 10, 18, 20), 612, RideKind.BUS),
    ),
    ("Turbina Calamba" to "Legazpi") to listOf(
        XlsxService("Penafrancia Tours", BusClass.ORDINARY, 1000.0, listOf(6, 10, 18, 20), 700, RideKind.BUS),
    ),
    ("Cubao" to "Daet") to listOf(
        XlsxService("Superlines", BusClass.ORDINARY, 900.0, listOf(6, 10, 18, 20), 630, RideKind.BUS),
    ),
    ("Cubao" to "Tabaco City") to listOf(
        XlsxService("Superlines", BusClass.ORDINARY, 1300.0, listOf(13, 17), 909, RideKind.BUS),
    ),
    ("Sampaloc" to "Naga") to listOf(
        XlsxService("Raymond Transportation", BusClass.ORDINARY, 600.0, listOf(6, 10, 18, 20), 420, RideKind.BUS),
    ),
    ("Alabang VTX" to "Naga") to listOf(
        XlsxService("Raymond Transportation", BusClass.ORDINARY, 600.0, listOf(6, 10, 18, 20), 420, RideKind.BUS),
    ),
    ("Sampaloc" to "Legazpi") to listOf(
        XlsxService("Raymond Transportation", BusClass.ORDINARY, 750.0, listOf(6, 10, 18, 20), 525, RideKind.BUS),
    ),
    ("Alabang VTX" to "Legazpi") to listOf(
        XlsxService("Raymond Transportation", BusClass.ORDINARY, 750.0, listOf(6, 10, 18, 20), 525, RideKind.BUS),
    ),
    ("Sampaloc" to "Sorsogon") to listOf(
        XlsxService("Raymond Transportation", BusClass.ORDINARY, 800.0, listOf(6, 10, 18, 20), 560, RideKind.BUS),
    ),
    ("Cubao" to "Sorsogon") to listOf(
        XlsxService("Raymond Transportation", BusClass.ORDINARY, 800.0, listOf(6, 10, 18, 20), 560, RideKind.BUS),
        XlsxService("Philtranco", BusClass.ORDINARY, 1100.0, listOf(6, 10, 18, 20), 690, RideKind.BUS),
    ),
    ("Alabang VTX" to "Sorsogon") to listOf(
        XlsxService("Raymond Transportation", BusClass.ORDINARY, 800.0, listOf(6, 10, 18, 20), 560, RideKind.BUS),
    ),
    ("Ermita" to "Naga") to listOf(
        XlsxService("Cagsawa Travel", BusClass.ORDINARY, 760.0, listOf(6, 10, 18, 20), 532, RideKind.BUS),
    ),
    ("Ermita" to "Legazpi") to listOf(
        XlsxService("Cagsawa Travel", BusClass.ORDINARY, 760.0, listOf(6, 10, 18, 20), 532, RideKind.BUS),
    ),
    ("Ermita" to "Albay (other points)") to listOf(
        XlsxService("Cagsawa Travel", BusClass.ORDINARY, 895.0, listOf(6, 10, 18, 20), 626, RideKind.BUS),
    ),
    ("Cubao" to "Albay (other points)") to listOf(
        XlsxService("Cagsawa Travel", BusClass.ORDINARY, 895.0, listOf(6, 10, 18, 20), 626, RideKind.BUS),
    ),
    ("Pasay" to "Laguna") to listOf(
        XlsxService("JAM Liner", BusClass.ORDINARY, 194.0, listOf(5, 8, 11, 14, 17), 135, RideKind.BUS),
    ),
    ("Pasay" to "Batangas Grand Terminal") to listOf(
        XlsxService("JAM Liner", BusClass.ORDINARY, 300.0, listOf(5, 8, 11, 14, 17), 210, RideKind.BUS),
    ),
    ("Pasay" to "Lucena") to listOf(
        XlsxService("JAM Liner", BusClass.ORDINARY, 388.0, listOf(5, 8, 11, 14, 17), 271, RideKind.BUS),
    ),
    ("PITX" to "Batangas Grand Terminal") to listOf(
        XlsxService("JAM Liner", BusClass.ORDINARY, 200.0, listOf(5, 8, 11, 14, 17), 140, RideKind.BUS),
    ),
    ("PITX" to "Lemery") to listOf(
        XlsxService("JAM Liner", BusClass.ORDINARY, 220.0, listOf(5, 8, 11, 14, 17), 154, RideKind.BUS),
    ),
    ("PITX" to "Lipa") to listOf(
        XlsxService("JAM Liner", BusClass.ORDINARY, 156.0, listOf(5, 8, 11, 14, 17), 109, RideKind.BUS),
    ),
    ("One Ayala" to "Binan") to listOf(
        XlsxService("JAC Liner", BusClass.ORDINARY, 68.0, listOf(5, 8, 11, 14, 17), 90, RideKind.BUS),
    ),
    ("Binan" to "San Pablo") to listOf(
        XlsxService("JAC Liner", BusClass.ORDINARY, 121.0, listOf(5, 8, 11, 14, 17), 90, RideKind.BUS),
    ),
    ("Pasay" to "Tagaytay") to listOf(
        XlsxService("DLTB", BusClass.ORDINARY, 88.0, listOf(5, 8, 11, 14, 17), 90, RideKind.BUS),
    ),
)

internal val xlsxSeaRoutes: Map<Pair<String, String>, List<XlsxService>> = mapOf(
    ("Batangas Port" to "Calapan") to listOf(
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 680.0, listOf(1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23), 214, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 770.0, listOf(5, 8, 11, 14, 17), 242, RideKind.FASTCRAFT),
    ),
    ("Roxas (Mindoro)" to "Caticlan") to listOf(
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1340.0, listOf(3, 7, 11, 15, 19, 23), 422, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1550.0, listOf(3, 7, 11, 15, 19, 23), 488, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.DELUXE, 1750.0, listOf(3, 7, 11, 15, 19, 23), 551, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.LUXURY, 4400.0, listOf(3, 7, 11, 15, 19, 23), 1386, RideKind.FERRY),
    ),
    ("Batangas Port" to "Caticlan") to listOf(
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 2170.0, listOf(3, 9, 15, 21), 683, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 2270.0, listOf(3, 9, 15, 21), 715, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 2790.0, listOf(3, 9, 15, 21), 878, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.DELUXE, 3720.0, listOf(3, 9, 15, 21), 1171, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.LUXURY, 8300.0, listOf(3, 9, 15, 21), 2200, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.LUXURY, 14400.0, listOf(3, 9, 15, 21), 2200, RideKind.FERRY),
    ),
    ("Batangas Port" to "Roxas") to listOf(
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 2580.0, listOf(13, 17), 812, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 3200.0, listOf(13, 17), 812, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.DELUXE, 3820.0, listOf(13, 17), 812, RideKind.FERRY),
    ),
    ("Batangas Port" to "Romblon") to listOf(
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1240.0, listOf(22), 390, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1860.0, listOf(22), 390, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.DELUXE, 2790.0, listOf(22), 390, RideKind.FERRY),
    ),
    ("Batangas Port" to "Sibuyan") to listOf(
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1240.0, listOf(6, 10, 18, 20), 390, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1860.0, listOf(6, 10, 18, 20), 390, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.DELUXE, 3200.0, listOf(6, 10, 18, 20), 390, RideKind.FERRY),
    ),
    ("Batangas Port" to "Cajidiocan") to listOf(
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1550.0, listOf(6, 10, 18, 20), 488, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 2170.0, listOf(6, 10, 18, 20), 488, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.DELUXE, 3410.0, listOf(6, 10, 18, 20), 488, RideKind.FERRY),
    ),
    ("Cajidiocan" to "Roxas") to listOf(
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1035.0, listOf(6), 326, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1135.0, listOf(6), 326, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.DELUXE, 1550.0, listOf(6), 326, RideKind.FERRY),
    ),
    ("Romblon" to "Sibuyan") to listOf(
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 445.0, listOf(2), 140, RideKind.FERRY),
    ),
    ("Romblon" to "Roxas") to listOf(
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1550.0, listOf(2), 488, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1750.0, listOf(2), 488, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.DELUXE, 2170.0, listOf(2), 488, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.LUXURY, 6500.0, listOf(2), 488, RideKind.FERRY),
    ),
    ("Cebu Port" to "Surigao Port") to listOf(
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1550.0, listOf(20, 21), 488, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1650.0, listOf(20, 21), 519, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1960.0, listOf(20, 21), 617, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.DELUXE, 2380.0, listOf(20, 21), 749, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.LUXURY, 7700.0, listOf(20, 21), 2200, RideKind.FERRY),
        XlsxService("Cokaliong Shipping", BusClass.ORDINARY, 1584.0, listOf(6, 10, 18, 20), 498, RideKind.FERRY),
    ),
    ("Cebu Port" to "Dapitan Port") to listOf(
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1130.0, listOf(21), 355, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1440.0, listOf(21), 355, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1860.0, listOf(21), 355, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.DELUXE, 2270.0, listOf(21), 355, RideKind.FERRY),
    ),
    ("Nasipit Port" to "Cebu Port") to listOf(
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1500.0, listOf(18), 472, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 1650.0, listOf(18), 472, RideKind.FERRY),
        XlsxService("Starlite Ferries", BusClass.ORDINARY, 2000.0, listOf(18), 472, RideKind.FERRY),
    ),
    ("Cebu Port" to "Cagayan de Oro Port") to listOf(
        XlsxService("Trans-Asia Shipping", BusClass.ORDINARY, 1686.0, listOf(20), 405, RideKind.FERRY),
        XlsxService("Trans-Asia Shipping", BusClass.ORDINARY, 2022.0, listOf(20), 405, RideKind.FERRY),
        XlsxService("Trans-Asia Shipping", BusClass.ORDINARY, 1827.0, listOf(20), 405, RideKind.FERRY),
    ),
    ("Cagayan de Oro Port" to "Cebu Port") to listOf(
        XlsxService("Trans-Asia Shipping", BusClass.ORDINARY, 1827.0, listOf(6, 10, 18, 20), 405, RideKind.FERRY),
        XlsxService("Trans-Asia Shipping", BusClass.ORDINARY, 1686.0, listOf(6, 10, 18, 20), 405, RideKind.FERRY),
    ),
    ("Cebu Port" to "Ozamiz Port") to listOf(
        XlsxService("Trans-Asia Shipping", BusClass.ORDINARY, 1974.0, listOf(19), 690, RideKind.FERRY),
    ),
    ("Ozamiz Port" to "Iligan Port") to listOf(
        XlsxService("Trans-Asia Shipping", BusClass.ORDINARY, 322.0, listOf(8), 120, RideKind.FERRY),
    ),
    ("Iligan Port" to "Ozamiz Port") to listOf(
        XlsxService("Trans-Asia Shipping", BusClass.ORDINARY, 352.0, listOf(5, 8, 11, 14, 17), 120, RideKind.FERRY),
    ),
    ("Cebu Port" to "Iligan Port") to listOf(
        XlsxService("Trans-Asia Shipping", BusClass.ORDINARY, 1974.0, listOf(19), 900, RideKind.FERRY),
    ),
    ("Iligan Port" to "Cebu Port") to listOf(
        XlsxService("Trans-Asia Shipping", BusClass.ORDINARY, 2016.0, listOf(13, 17), 900, RideKind.FERRY),
    ),
    ("Cebu Port" to "Iloilo Port") to listOf(
        XlsxService("Trans-Asia Shipping", BusClass.ORDINARY, 1960.0, listOf(18), 135, RideKind.FERRY),
    ),
    ("Cebu Port" to "Masbate") to listOf(
        XlsxService("Trans-Asia Shipping", BusClass.ORDINARY, 1560.0, listOf(18), 780, RideKind.FERRY),
    ),
    ("Cebu Port" to "Tagbilaran Port") to listOf(
        XlsxService("Trans-Asia Shipping", BusClass.ORDINARY, 480.0, listOf(5, 8, 11, 14, 17), 151, RideKind.FERRY),
    ),
    ("Cagayan de Oro Port" to "Tagbilaran") to listOf(
        XlsxService("Trans-Asia Shipping", BusClass.ORDINARY, 1488.0, listOf(19), 690, RideKind.FERRY),
    ),
    ("Cebu Port" to "Hilongos") to listOf(
        XlsxService("Roble Shipping", BusClass.ORDINARY, 430.0, listOf(6, 10, 18, 20), 510, RideKind.FERRY),
        XlsxService("Roble Shipping", BusClass.ORDINARY, 480.0, listOf(6, 10, 18, 20), 510, RideKind.FERRY),
        XlsxService("Roble Shipping", BusClass.ORDINARY, 680.0, listOf(6, 10, 18, 20), 510, RideKind.FERRY),
        XlsxService("Roble Shipping", BusClass.DELUXE, 1245.0, listOf(6, 10, 18, 20), 510, RideKind.FERRY),
        XlsxService("Roble Shipping", BusClass.LUXURY, 2600.0, listOf(6, 10, 18, 20), 510, RideKind.FERRY),
        XlsxService("Roble Shipping", BusClass.LUXURY, 3000.0, listOf(6, 10, 18, 20), 510, RideKind.FERRY),
    ),
    ("Cebu Port" to "Baybay") to listOf(
        XlsxService("Roble Shipping", BusClass.ORDINARY, 450.0, listOf(6, 10, 18, 20), 510, RideKind.FERRY),
    ),
    ("Cebu Port" to "Ormoc Port") to listOf(
        XlsxService("Roble Shipping", BusClass.ORDINARY, 550.0, listOf(6, 10, 18, 20), 570, RideKind.FERRY),
        XlsxService("Roble Shipping", BusClass.ORDINARY, 600.0, listOf(6, 10, 18, 20), 570, RideKind.FERRY),
        XlsxService("Roble Shipping", BusClass.ORDINARY, 780.0, listOf(6, 10, 18, 20), 570, RideKind.FERRY),
        XlsxService("Roble Shipping", BusClass.DELUXE, 1515.0, listOf(6, 10, 18, 20), 570, RideKind.FERRY),
        XlsxService("Roble Shipping", BusClass.LUXURY, 3630.0, listOf(6, 10, 18, 20), 570, RideKind.FERRY),
    ),
    ("Cebu Port" to "Ormoc (fastcraft MV Superjoy)") to listOf(
        XlsxService("Roble Shipping", BusClass.ORDINARY, 550.0, listOf(5, 8, 11, 14, 17), 173, RideKind.FERRY),
        XlsxService("Roble Shipping", BusClass.ORDINARY, 650.0, listOf(5, 8, 11, 14, 17), 173, RideKind.FERRY),
    ),
    ("Zamboanga Port" to "Isabela (Basilan)") to listOf(
        XlsxService("Aleson Shipping", BusClass.ORDINARY, 90.0, listOf(5, 6, 15, 16), 90, RideKind.FERRY),
    ),
    ("Zamboanga Port" to "Lamitan") to listOf(
        XlsxService("Aleson Shipping", BusClass.ORDINARY, 120.0, listOf(5, 8, 11, 14, 17), 90, RideKind.FERRY),
    ),
    ("Zamboanga Port" to "Jolo") to listOf(
        XlsxService("Aleson Shipping", BusClass.ORDINARY, 850.0, listOf(20), 267, RideKind.FERRY),
    ),
    ("Zamboanga Port" to "Bongao") to listOf(
        XlsxService("Aleson Shipping", BusClass.ORDINARY, 1300.0, listOf(18), 409, RideKind.FERRY),
    ),
    ("Dapitan Port" to "Dumaguete Port") to listOf(
        XlsxService("Aleson Shipping", BusClass.ORDINARY, 500.0, listOf(22), 157, RideKind.FERRY),
    ),
    ("Dumaguete Port" to "Siquijor") to listOf(
        XlsxService("Aleson Shipping", BusClass.ORDINARY, 200.0, listOf(6, 10, 13, 18), 90, RideKind.FERRY),
    ),
    ("Hagnaya" to "Santa Fe (Bantayan)") to listOf(
        XlsxService("Super Shuttle Ferry", BusClass.ORDINARY, 400.0, listOf(5, 7, 9, 11, 13, 15, 17, 19), 125, RideKind.FERRY),
        XlsxService("Super Shuttle Ferry / AMTC", BusClass.ORDINARY, 300.0, listOf(5, 7, 9, 11, 13, 15, 17, 19), 94, RideKind.FERRY),
    ),
    ("Lapu-Lapu (Angasil)" to "Olango Island") to listOf(
        XlsxService("Sta. Rosa Ferry Express", BusClass.ORDINARY, 60.0, listOf(5, 8, 11, 14, 17), 90, RideKind.FASTCRAFT),
    ),
    ("El Nido Port" to "Coron Port") to listOf(
        XlsxService("Jomalia Shipping", BusClass.ORDINARY, 2800.0, listOf(7, 12), 210, RideKind.FERRY),
    ),
    ("Manila North Harbor" to "Davao City") to listOf(
        XlsxService("2GO Travel", BusClass.ORDINARY, 4450.0, listOf(13, 17), 3300, RideKind.FERRY),
    ),
)
