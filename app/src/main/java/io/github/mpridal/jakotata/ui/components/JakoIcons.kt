package io.github.mpridal.jakotata.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Obrysové ikony ze schváleného návrhu UI (docs/design), 24×24, tah 2. */
object JakoIcons {
    val Camera: ImageVector by lazy { icon("Camera", "M4 8h3l2-3h6l2 3h3v11H4z", "M8.5 13a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0 -7 0") }
    val SwitchCamera: ImageVector by lazy {
        icon("SwitchCamera", "M4 8h3l2-3h6l2 3h3v11H4z", "M9 13a3 3 0 0 1 5.1-2.1", "M14.6 9.2v1.9h-1.9", "M15 13a3 3 0 0 1-5.1 2.1", "M9.4 16.8v-1.9h1.9")
    }
    val Gallery: ImageVector by lazy { icon("Gallery", "M3 5h18v14H3z", "M7.4 10a1.6 1.6 0 1 0 3.2 0a1.6 1.6 0 1 0 -3.2 0", "M21 16l-5-5-8 8") }
    val Play: ImageVector by lazy { icon("Play", "M7 5l12 7-12 7z") }
    val Share: ImageVector by lazy { icon("Share", "M12 3v12", "M8 7l4-4 4 4", "M5 12v8h14v-8") }
    val Trash: ImageVector by lazy { icon("Trash", "M4 7h16", "M9 7V4h6v3", "M6 7l1 13h10l1-13") }
    val Close: ImageVector by lazy { icon("Close", "M6 6l12 12", "M18 6L6 18") }

    private fun icon(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        for (d in paths) {
            b.addPath(
                pathData = addPathNodes(d),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }
}
