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
    val Paragraph by lazy { stroke("para", "M4.5,6H19.5", "M4.5,10H19.5", "M4.5,14H19.5", "M4.5,18H13") }
}
