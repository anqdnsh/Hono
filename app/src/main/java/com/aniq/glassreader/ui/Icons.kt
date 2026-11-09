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
}
