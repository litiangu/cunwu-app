package com.litiangu.wuji.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Lucide 图标集（ISC License，https://lucide.dev）。
 *
 * 由一次性脚本从官网 SVG 转换生成，规格统一为 24x24 viewBox、stroke 2、
 * round cap/join。线宽比例由 strokeWidth / viewport 决定，图标放大时比例不变，
 * 视觉重量与 24dp 场景一致，因此不为大尺寸单独做变体。
 */
object WujiIcons {
    private fun lucide(vararg paths: String, strokeWidth: Float = 2f): ImageVector =
        ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val House: ImageVector by lazy {
        lucide(
            "M15 21v-8a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v8",
            "M3 10a2 2 0 0 1 .709-1.528l7-6a2 2 0 0 1 2.582 0l7 6A2 2 0 0 1 21 10v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
        )
    }

    val ChartLine: ImageVector by lazy {
        lucide(
            "M3 3v16a2 2 0 0 0 2 2h16",
            "m19 9-5 5-4-4-3 3",
        )
    }

    val Plus: ImageVector by lazy {
        lucide(
            "M5 12h14",
            "M12 5v14",
        )
    }

    val X: ImageVector by lazy {
        lucide(
            "M18 6 6 18",
            "m6 6 12 12",
        )
    }

    val ArrowLeft: ImageVector by lazy {
        lucide(
            "m12 19-7-7 7-7",
            "M19 12H5",
        )
    }

    val ArrowRight: ImageVector by lazy {
        lucide(
            "M5 12h14",
            "m12 5 7 7-7 7",
        )
    }

    val ChevronLeft: ImageVector by lazy {
        lucide(
            "m15 18-6-6 6-6",
        )
    }

    val ChevronRight: ImageVector by lazy {
        lucide(
            "m9 18 6-6-6-6",
        )
    }

    val EllipsisVertical: ImageVector by lazy {
        lucide(
            "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
            "M11 5a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
            "M11 19a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
        )
    }

    val Check: ImageVector by lazy {
        lucide(
            "M20 6 9 17l-5-5",
        )
    }

    val Wallet: ImageVector by lazy {
        lucide(
            "M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1",
            "M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4",
        )
    }

    val Package: ImageVector by lazy {
        lucide(
            "M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z",
            "M12 22V12",
            "M3.29 7 12 12 20.71 7",
            "m7.5 4.27 9 5.15",
        )
    }

    val ChartPie: ImageVector by lazy {
        lucide(
            "M21 12c.552 0 1.005-.449.95-.998a10 10 0 0 0-8.953-8.951c-.55-.055-.998.398-.998.95v8a1 1 0 0 0 1 1z",
            "M21.21 15.89A10 10 0 1 1 8 2.83",
        )
    }

    val ShieldCheck: ImageVector by lazy {
        lucide(
            "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
            "m9 12 2 2 4-4",
        )
    }

    val ListFilter: ImageVector by lazy {
        lucide(
            "M2 5h20",
            "M6 12h12",
            "M9 19h6",
        )
    }

    val Rows3: ImageVector by lazy {
        lucide(
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z",
            "M21 9H3",
            "M21 15H3",
        )
    }

    val LayoutDashboard: ImageVector by lazy {
        lucide(
            "M4 3h5a1 1 0 0 1 1 1v7a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-7a1 1 0 0 1 1 -1Z",
            "M15 3h5a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1Z",
            "M15 12h5a1 1 0 0 1 1 1v7a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-7a1 1 0 0 1 1 -1Z",
            "M4 16h5a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1Z",
        )
    }

    val MoonStar: ImageVector by lazy {
        lucide(
            "M18 5h4",
            "M20 3v4",
            "M20.985 12.486a9 9 0 1 1-9.473-9.472c.405-.022.617.46.402.803a6 6 0 0 0 8.268 8.268c.344-.215.825-.004.803.401",
        )
    }

    val Grid3x3: ImageVector by lazy {
        lucide(
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z",
            "M3 9h18",
            "M3 15h18",
            "M9 3v18",
            "M15 3v18",
        )
    }

    val Search: ImageVector by lazy {
        lucide(
            "m21 21-4.34-4.34",
            "M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0",
        )
    }

    val Settings: ImageVector by lazy {
        lucide(
            "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
            "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
        )
    }

    val CalendarDays: ImageVector by lazy {
        lucide(
            "M8 2v3",
            "M16 2v3",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z",
            "M3 9h18",
            "M8 13h.01",
            "M12 13h.01",
            "M16 13h.01",
            "M8 17h.01",
            "M12 17h.01",
            "M16 17h.01",
        )
    }

    val Clock: ImageVector by lazy {
        lucide(
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
            "M12 6v6l4 2",
        )
    }

    val CalendarCheck: ImageVector by lazy {
        lucide(
            "M8 2v3",
            "M16 2v3",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z",
            "M3 9h18",
            "m9 15 2 2 4-4",
        )
    }

    val CircleCheck: ImageVector by lazy {
        lucide(
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
            "m16 9-5.5 5.5L8 12",
        )
    }

    val RefreshCw: ImageVector by lazy {
        lucide(
            "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
            "M21 3v5h-5",
            "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
            "M8 16H3v5",
        )
    }

    val Lock: ImageVector by lazy {
        lucide(
            "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2Z",
            "M7 11V7a5 5 0 0 1 10 0v4",
        )
    }

    val Award: ImageVector by lazy {
        lucide(
            "m15.477 12.89 1.515 8.526a.5.5 0 0 1-.81.47l-3.58-2.687a1 1 0 0 0-1.197 0l-3.586 2.686a.5.5 0 0 1-.81-.469l1.514-8.526",
            "M6 8a6 6 0 1 0 12 0a6 6 0 1 0 -12 0",
        )
    }

    val ArchiveRestore: ImageVector by lazy {
        lucide(
            "M3 3h18a1 1 0 0 1 1 1v3a1 1 0 0 1 -1 1h-18a1 1 0 0 1 -1 -1v-3a1 1 0 0 1 1 -1Z",
            "M4 8v11a2 2 0 0 0 2 2h2",
            "M20 8v11a2 2 0 0 1-2 2h-2",
            "m9 15 3-3 3 3",
            "M12 12v9",
        )
    }

    val ImageOff: ImageVector by lazy {
        lucide(
            "M2 2L22 22",
            "M10.41 10.41a2 2 0 1 1-2.83-2.83",
            "M13.5 13.5L6 21",
            "M18 12L21 15",
            "M3.59 3.59A1.99 1.99 0 0 0 3 5v14a2 2 0 0 0 2 2h14c.55 0 1.052-.22 1.41-.59",
            "M21 15V5a2 2 0 0 0-2-2H9",
        )
    }

    val ArrowUp: ImageVector by lazy {
        lucide(
            "m5 12 7-7 7 7",
            "M12 19V5",
        )
    }

    val ArrowDown: ImageVector by lazy {
        lucide(
            "M12 5v14",
            "m19 12-7 7-7-7",
        )
    }

    val TriangleAlert: ImageVector by lazy {
        lucide(
            "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
            "M12 9v4",
            "M12 17h.01",
        )
    }

    val Calendar: ImageVector by lazy {
        lucide(
            "M8 2v3",
            "M16 2v3",
            "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z",
            "M3 9h18",
        )
    }

    val CirclePlus: ImageVector by lazy {
        lucide(
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
            "M8 12h8",
            "M12 8v8",
        )
    }

    val ImagePlus: ImageVector by lazy {
        lucide(
            "M16 5h6",
            "M19 2v6",
            "M21 11.5V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7.5",
            "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
            "M7 9a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        )
    }

    val Camera: ImageVector by lazy {
        lucide(
            "M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z",
            "M9 13a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
        )
    }

    val Images: ImageVector by lazy {
        lucide(
            "m22 11-1.296-1.296a2.4 2.4 0 0 0-3.408 0L11 16",
            "M4 8a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2",
            "M12 7a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
            "M10 2h10a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2Z",
        )
    }

    val WandSparkles: ImageVector by lazy {
        lucide(
            "m21.64 3.64-1.28-1.28a1.21 1.21 0 0 0-1.72 0L2.36 18.64a1.21 1.21 0 0 0 0 1.72l1.28 1.28a1.2 1.2 0 0 0 1.72 0L21.64 5.36a1.2 1.2 0 0 0 0-1.72",
            "m14 7 3 3",
            "M5 6v4",
            "M19 14v4",
            "M10 2v2",
            "M7 8H3",
            "M21 16h-4",
            "M11 3H9",
        )
    }

    val SquarePen: ImageVector by lazy {
        lucide(
            "M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7",
            "M18.375 2.625a1 1 0 0 1 3 3l-9.013 9.014a2 2 0 0 1-.853.505l-2.873.84a.5.5 0 0 1-.62-.62l.84-2.873a2 2 0 0 1 .506-.852z",
        )
    }

    val TabletSmartphone: ImageVector by lazy {
        lucide(
            "M5 8h6a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-6a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2Z",
            "M5 4a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2h-2.4",
            "M8 18h.01",
        )
    }

    val Refrigerator: ImageVector by lazy {
        lucide(
            "M5 6a4 4 0 0 1 4-4h6a4 4 0 0 1 4 4v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6Z",
            "M5 10h14",
            "M15 7v6",
        )
    }

    val Armchair: ImageVector by lazy {
        lucide(
            "M19 9V6a2 2 0 0 0-2-2H7a2 2 0 0 0-2 2v3",
            "M3 16a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-5a2 2 0 0 0-4 0v1.5a.5.5 0 0 1-.5.5h-9a.5.5 0 0 1-.5-.5V11a2 2 0 0 0-4 0z",
            "M5 18v2",
            "M19 18v2",
        )
    }

    val Wrench: ImageVector by lazy {
        lucide(
            "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.106-3.105c.32-.322.863-.22.983.218a6 6 0 0 1-8.259 7.057l-7.91 7.91a1 1 0 0 1-2.999-3l7.91-7.91a6 6 0 0 1 7.057-8.259c.438.12.54.662.219.984z",
        )
    }

    val Car: ImageVector by lazy {
        lucide(
            "M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2",
            "M5 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
            "M9 17h6",
            "M15 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        )
    }

    val Dumbbell: ImageVector by lazy {
        lucide(
            "M17.596 12.768a2 2 0 1 0 2.829-2.829l-1.768-1.767a2 2 0 0 0 2.828-2.829l-2.828-2.828a2 2 0 0 0-2.829 2.828l-1.767-1.768a2 2 0 1 0-2.829 2.829z",
            "m2.5 21.5 1.4-1.4",
            "m20.1 3.9 1.4-1.4",
            "M5.343 21.485a2 2 0 1 0 2.829-2.828l1.767 1.768a2 2 0 1 0 2.829-2.829l-6.364-6.364a2 2 0 1 0-2.829 2.829l1.768 1.767a2 2 0 0 0-2.828 2.829z",
            "m9.6 14.4 4.8-4.8",
        )
    }

    val Shapes: ImageVector by lazy {
        lucide(
            "M8.3 10a.7.7 0 0 1-.626-1.079L11.4 3a.7.7 0 0 1 1.198-.043L16.3 8.9a.7.7 0 0 1-.572 1.1Z",
            "M4 14h5a1 1 0 0 1 1 1v5a1 1 0 0 1 -1 1h-5a1 1 0 0 1 -1 -1v-5a1 1 0 0 1 1 -1Z",
            "M14 17.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0",
        )
    }

    val EyeOff: ImageVector by lazy {
        lucide(
            "M10.733 5.076a10.744 10.744 0 0 1 11.205 6.575 1 1 0 0 1 0 .696 10.747 10.747 0 0 1-1.444 2.49",
            "M14.084 14.158a3 3 0 0 1-4.242-4.242",
            "M17.479 17.499a10.75 10.75 0 0 1-15.417-5.151 1 1 0 0 1 0-.696 10.75 10.75 0 0 1 4.446-5.143",
            "m2 2 20 20",
        )
    }

    val Vibrate: ImageVector by lazy {
        lucide(
            "m2 8 2 2-2 2 2 2-2 2",
            "m22 8-2 2 2 2-2 2 2 2",
            "M9 5h6a1 1 0 0 1 1 1v12a1 1 0 0 1 -1 1h-6a1 1 0 0 1 -1 -1v-12a1 1 0 0 1 1 -1Z",
        )
    }

    val Anchor: ImageVector by lazy {
        lucide(
            "M12 6v16",
            "m19 13 2-1a9 9 0 0 1-18 0l2 1",
            "M9 11h6",
            "M10 4a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        )
    }

    val Info: ImageVector by lazy {
        lucide(
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
            "M12 16v-4",
            "M12 8h.01",
        )
    }

    val CircleUserRound: ImageVector by lazy {
        lucide(
            "M17.925 20.056a6 6 0 0 0-11.851.001",
            "M8 11a4 4 0 1 0 8 0a4 4 0 1 0 -8 0",
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0",
        )
    }

    val FileText: ImageVector by lazy {
        lucide(
            "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
            "M14 2v5a1 1 0 0 0 1 1h5",
            "M10 9H8",
            "M16 13H8",
            "M16 17H8",
        )
    }

    val Code: ImageVector by lazy {
        lucide(
            "m16 18 6-6-6-6",
            "m8 6-6 6 6 6",
        )
    }

    val BellRing: ImageVector by lazy {
        lucide(
            "M10.268 21a2 2 0 0 0 3.464 0",
            "M22 8c0-2.3-.8-4.3-2-6",
            "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326",
            "M4 2C2.8 3.7 2 5.7 2 8",
        )
    }

    val Folder: ImageVector by lazy {
        lucide(
            "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z",
        )
    }

    val Share2: ImageVector by lazy {
        lucide(
            "M15 5a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M3 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M15 19a3 3 0 1 0 6 0a3 3 0 1 0 -6 0",
            "M8.59 13.51L15.42 17.49",
            "M15.41 6.51L8.59 10.49",
        )
    }

    val Download: ImageVector by lazy {
        lucide(
            "M12 15V3",
            "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
            "m7 10 5 5 5-5",
        )
    }

    val Trash2: ImageVector by lazy {
        lucide(
            "M10 11v6",
            "M14 11v6",
            "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
            "M3 6h18",
            "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
        )
    }

    val RotateCcw: ImageVector by lazy {
        lucide(
            "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8",
            "M3 3v5h5",
        )
    }

    val Dot: ImageVector by lazy {
        lucide(
            "M11 12a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
        )
    }

}
