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
    val Back: ImageVector by lazy { icon("Back", "M15 18l-6-6 6-6") }
    val Chevron: ImageVector by lazy { icon("Chevron", "M6 9l6 6 6-6") }
    val Sliders: ImageVector by lazy {
        icon("Sliders", "M4 7h10M18 7h2M4 17h4M12 17h8", "M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M8 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0")
    }
    val Parrot: ImageVector by lazy {
        icon("Parrot", "M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z", "M9 10h5a2.5 2.5 0 0 1 0 5h-4", "M11 8l-2 2 2 2")
    }
    val Headphones: ImageVector by lazy {
        icon("Headphones", "M4 15v-3a8 8 0 0 1 16 0v3", "M3 14h4v6H3z", "M17 14h4v6h-4z")
    }
    val Silent: ImageVector by lazy { icon("Silent", "M4 9h4l5-4v14l-5-4H4z", "M17 9l4 6", "M21 9l-4 6") }
    val Face: ImageVector by lazy {
        icon("Face", "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M9 10h.01M15 10h.01", "M8.5 14.5a4.5 4.5 0 0 0 7 0")
    }
    val Mic: ImageVector by lazy { icon("Mic", "M9 3h6v11H9z", "M5 11a7 7 0 0 0 14 0M12 18v3") }

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
