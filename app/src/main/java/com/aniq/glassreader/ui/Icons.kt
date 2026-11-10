package com.aniq.glassreader.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Thin, rounded line icons in the spirit of SF Symbols. */
object Line {
    private fun stroke(name: String, vararg paths: String, width: Float = 1.8f): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (p in paths) {
            b.addPath(
                addPathNodes(p),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    private fun filled(name: String, path: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(addPathNodes(path), fill = SolidColor(Color.Black))
            .build()

    private const val HEART =
        "M12,20.2C12,20.2 3.6,15.1 3.6,9.1C3.6,6.5 5.6,4.6 8,4.6C9.7,4.6 11.2,5.6 12,7C12.8,5.6 14.3,4.6 16,4.6C18.4,4.6 20.4,6.5 20.4,9.1C20.4,15.1 12,20.2 12,20.2Z"

    val Clock by lazy { stroke("clock", "M12,3.8A8.2,8.2 0,1 1,11.99,3.8Z", "M12,7.5V12L15,14") }
    val Plus by lazy { stroke("plus", "M12,5.5V18.5", "M5.5,12H18.5", width = 2f) }
    val Back by lazy { stroke("back", "M14.5,5.5L8,12L14.5,18.5", width = 2.2f) }
    val Close by lazy { stroke("close", "M7,7L17,17", "M17,7L7,17", width = 2f) }
    val Heart by lazy { stroke("heart", HEART) }
    val HeartFill by lazy { filled("heartFill", HEART) }
    val Trash by lazy { stroke("trash", "M4.5,7H19.5", "M9.5,7V5.2H14.5V7", "M6.5,7L7.4,19.5H16.6L17.5,7", "M10.2,10.5V16", "M13.8,10.5V16") }
    val Copy by lazy { stroke("copy", "M8.5,8.5H18.5V18.5H8.5Z", "M15.5,8.5V5.5H5.5V15.5H8.5") }
    val Books by lazy { stroke("books", "M4.5,4.5H8.5V19.5H4.5Z", "M10.5,4.5H14.5V19.5H10.5Z", "M16.3,5.6L19.6,4.8L22,18.9L18.7,19.7Z") }
    val Quote by lazy { stroke("quote", "M5,5.5V18.5", "M9.5,7H19.5", "M9.5,12H18", "M9.5,17H15") }
    val ListIcon by lazy {
        stroke("list", "M9,6.5H20", "M9,12H20", "M9,17.5H20", "M4.6,6.5H4.7", "M4.6,12H4.7", "M4.6,17.5H4.7", width = 2f)
    }
    val Share by lazy { stroke("share", "M12,3.8V14.5", "M8.2,7.6L12,3.8L15.8,7.6", "M7.5,10.5H6V20H18V10.5H16.5") }
    val Open by lazy { stroke("open", "M5,6.5C7.5,5.2 10,5.2 12,6.8C14,5.2 16.5,5.2 19,6.5V18.5C16.5,17.2 14,17.2 12,18.8C10,17.2 7.5,17.2 5,18.5Z", "M12,6.8V18.8") }
    val Sliders by lazy { stroke("sliders", "M4,7.5H11", "M15,7.5H20", "M13,5.2V9.8", "M4,16.5H7", "M11,16.5H20", "M9,14.2V18.8") }
    val Photo by lazy { stroke("photo", "M4.5,5.5H19.5V18.5H4.5Z", "M4.5,15.5L9,11L13,15L15.5,12.5L19.5,16.5", "M15.5,8.6H15.6") }
    val Gear by lazy {
        stroke("gear",
            "M19.43,12.98c0.04,-0.32 0.07,-0.64 0.07,-0.98c0,-0.34 -0.03,-0.66 -0.07,-0.98l2.11,-1.65c0.19,-0.15 0.24,-0.42 0.12,-0.64l-2,-3.46c-0.12,-0.22 -0.39,-0.3 -0.61,-0.22l-2.49,1c-0.52,-0.4 -1.08,-0.73 -1.69,-0.98l-0.38,-2.65C14.46,2.18 14.25,2 14,2h-4C9.75,2 9.54,2.18 9.51,2.42l-0.38,2.65c-0.61,0.25 -1.17,0.59 -1.69,0.98l-2.49,-1c-0.23,-0.09 -0.49,0 -0.61,0.22l-2,3.46c-0.13,0.22 -0.07,0.49 0.12,0.64l2.11,1.65c-0.04,0.32 -0.07,0.65 -0.07,0.98c0,0.33 0.03,0.66 0.07,0.98l-2.11,1.65c-0.19,0.15 -0.24,0.42 -0.12,0.64l2,3.46c0.12,0.22 0.39,0.3 0.61,0.22l2.49,-1c0.52,0.4 1.08,0.73 1.69,0.98l0.38,2.65c0.03,0.24 0.24,0.42 0.49,0.42h4c0.25,0 0.46,-0.18 0.49,-0.42l0.38,-2.65c0.61,-0.25 1.17,-0.59 1.69,-0.98l2.49,1c0.23,0.09 0.49,0 0.61,-0.22l2,-3.46c0.12,-0.22 0.07,-0.49 -0.12,-0.64l-2.11,-1.65z",
            "M12,8.6a3.4,3.4 0 1,0 0,6.8a3.4,3.4 0 1,0 0,-6.8z",
            width = 1.6f)
    }
    val More by lazy {
        stroke("more", "M6,12H6.1", "M12,12H12.1", "M18,12H18.1", width = 2.6f)
    }
    val Refresh by lazy { stroke("refresh", "M18.6,12.5A6.6,6.6 0,1 1,16.4,7.1", "M17,3.6V7.6H13") }
    val Bookmark by lazy { stroke("bm", "M7,4.5H17V19.5L12,15.8L7,19.5Z") }
    val BookmarkFill by lazy { filled("bmFill", "M6.2,4.5C6.2,4 6.6,3.6 7.1,3.6H16.9C17.4,3.6 17.8,4 17.8,4.5V20.2L12,16.1L6.2,20.2Z") }
    val Note by lazy { stroke("note", "M4.5,19.5H9L19,9.5L14.5,5L4.5,15Z", "M12.5,7L17,11.5") }
    val Define by lazy { stroke("define", "M10.5,4.5A6,6 0,1 1,10.49,4.5Z", "M14.8,14.8L19.5,19.5", "M8,12.5L10.5,7.5L13,12.5", "M8.8,11H12.2") }
    val Trophy by lazy { stroke("trophy", "M7.5,4.5H16.5V9.5A4.5,4.5 0,0 1,7.5,9.5Z", "M7.5,6.5H4.5C4.5,9.5 6,11 8,11", "M16.5,6.5H19.5C19.5,9.5 18,11 16,11", "M12,14V17.5", "M8.5,19.5H15.5", "M9.5,17.5H14.5") }
    val Chevron by lazy { stroke("chev", "M9.5,6L15.5,12L9.5,18", width = 2f) }
    val Paragraph by lazy { stroke("para", "M4.5,6H19.5", "M4.5,10H19.5", "M4.5,14H19.5", "M4.5,18H13") }
    val Search by lazy { stroke("search", "M10.6,4.4A6.2,6.2 0,1 1,10.59,4.4Z", "M15.3,15.3L19.8,19.8", width = 2f) }
    val Undo by lazy { stroke("undo", "M9,14.5L4.5,10L9,5.5", "M4.5,10H14.5A5,5 0,0 1,14.5,20H11", width = 2f) }
    private const val FLAME = "M12.2,2.8C12.6,6 16.8,8.3 17.6,12.6C18.4,17 15.4,20.8 11.9,20.8C8.4,20.8 5.8,18.1 6.1,14.6C6.3,12.3 7.6,10.8 8.6,9.9C8.7,11.4 9.4,12.6 10.5,13.1C10.2,9.4 11.4,5.4 12.2,2.8Z"
    val Flame by lazy { filled("flame", FLAME) }
    val FlameLine by lazy { stroke("flameLine", FLAME) }
    val TextSize by lazy { stroke("textSize", "M3.8,18.5L8.5,5.8L13.2,18.5", "M5.6,14H11.4", "M14.6,18.5L17.6,10.8L20.6,18.5", "M15.6,16H19.6") }
    val Marker by lazy { stroke("marker", "M14.8,4.6L19.4,9.2L10.2,18.4H5.6V13.8Z", "M12.6,6.8L17.2,11.4", "M4.5,20.5H19.5") }
    val Page by lazy { stroke("page", "M6.8,3.6H14L18.4,8V20.4H6.8Z", "M14,3.6V8H18.4", "M9.4,12.6H15.8", "M9.4,16H14") }
    val Moon by lazy { stroke("moon", "M19.4,14.6A8,8 0,1 1,9.4,4.6A6.4,6.4 0,0 0,19.4,14.6Z") }
    val Phone by lazy { stroke("phone", "M7.6,3.4H16.4C17,3.4 17.4,3.8 17.4,4.4V19.6C17.4,20.2 17,20.6 16.4,20.6H7.6C7,20.6 6.6,20.2 6.6,19.6V4.4C6.6,3.8 7,3.4 7.6,3.4Z", "M11,17.6H13") }
    val Wave by lazy { stroke("wave", "M3.4,12H6.2L8.2,7.2L11.2,16.8L13.6,9.2L15.6,14.2L17,12H20.6") }
    val Check by lazy { stroke("check", "M5.5,12.5L10,17L18.5,7.5", width = 2f) }

    private const val STAR =
        "M12,3.7L14.55,8.85L20.25,9.7L16.1,13.7L17.1,19.35L12,16.65L6.9,19.35L7.9,13.7L3.75,9.7L9.45,8.85Z"
    val Star by lazy { stroke("star", STAR, width = 1.4f) }
    val StarFill by lazy { filled("starFill", STAR) }
}
