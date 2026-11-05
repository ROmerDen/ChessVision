package com.chessvision.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.sp
import com.chessvision.chess.Pieces
import com.chessvision.chess.Position
import com.chessvision.chess.Sq
import com.chessvision.engine.PvLine
import com.chessvision.overlay.ArrowGeometry
import com.chessvision.overlay.ArrowPlanner
import com.chessvision.overlay.BoardRect

/** Доска с фигурами и стрелками для проверки геометрии/координат UCI без захвата экрана. */
@Composable
fun BoardView(
    position: Position,
    lines: List<PvLine>,
    modifier: Modifier = Modifier,
    whiteAtBottom: Boolean = true,
) {
    BoxWithConstraints(modifier.aspectRatio(1f)) {
        val cellDp = maxWidth / 8
        val fontSp = (cellDp.value * 0.72f).sp
        Column(Modifier.fillMaxSize()) {
            for (row in 0..7) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (col in 0..7) {
                        val sq = BoardRect.squareAtCell(row, col, whiteAtBottom)
                        val light = (Sq.file(sq) + Sq.rank(sq)) % 2 == 1
                        Box(
                            Modifier.weight(1f).fillMaxHeight()
                                .background(if (light) Color(0xFFEEEED2) else Color(0xFF769656)),
                            contentAlignment = Alignment.Center,
                        ) {
                            val piece = position.pieceAt(sq)
                            if (piece != ' ') Text(Pieces.glyph(piece), fontSize = fontSp, color = Color.Black)
                        }
                    }
                }
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val board = BoardRect(0f, 0f, minOf(size.width, size.height), whiteAtBottom)
            // Худшая линия рисуется первой, лучшая — поверх
            for (a in ArrowPlanner.plan(lines, board).asReversed()) {
                val pts = ArrowGeometry.polygon(
                    a.from, a.to, thickness = board.cell * 0.22f, tipInset = board.cell * 0.12f,
                )
                if (pts.size < 3) continue
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                    close()
                }
                drawPath(path, Color(a.argb))
            }
        }
    }
}
