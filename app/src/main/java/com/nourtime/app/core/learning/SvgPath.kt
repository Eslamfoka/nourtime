package com.nourtime.app.core.learning

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * SVG path data ("M10 10 L20 20 Z") turned into polygons, for tapping a region of a coloring picture.
 * Drawing uses Compose's own parser, which is exact; tapping only needs a close outline. Supports every
 * path command (M L H V C S Q T A Z, absolute and relative).
 */
object SvgPath {
    /**
     * Polygons (x0, y0, x1, y1, …) of each sub-path, scaled by 1 / [width] and 1 / [height]. Sub-paths
     * with fewer than [minPoints] points are dropped (an area needs 3; a traced stroke only 2).
     */
    fun flatten(d: String, width: Float = 1f, height: Float = 1f, minPoints: Int = 3): List<FloatArray> {
        val out = mutableListOf<FloatArray>()
        var poly = mutableListOf<Float>()
        fun point(x: Float, y: Float) {
            poly += x / width
            poly += y / height
        }
        fun close() {
            if (poly.size >= 2 * minPoints) out += poly.toFloatArray()
            poly = mutableListOf()
        }

        val tokens = tokenize(d)
        var i = 0
        var cmd = ' '
        var x = 0f
        var y = 0f
        var startX = 0f
        var startY = 0f
        // Reflection points for S and T.
        var lastCx = 0f
        var lastCy = 0f
        var lastCmd = ' '
        fun num(): Float = (tokens.getOrNull(i++) as? Tok.Num)?.value ?: throw IllegalArgumentException("missing number in path")

        while (i < tokens.size) {
            val t = tokens[i]
            if (t is Tok.Cmd) {
                cmd = t.c
                i++
                if (cmd == 'Z' || cmd == 'z') {
                    x = startX
                    y = startY
                    close()
                    lastCmd = cmd
                    continue
                }
            } else if (cmd == ' ') {
                throw IllegalArgumentException("path data must start with a command")
            }
            val rel = cmd.isLowerCase()
            val ox = if (rel) x else 0f
            val oy = if (rel) y else 0f
            when (cmd.uppercaseChar()) {
                'M' -> {
                    close()
                    x = ox + num(); y = oy + num()
                    startX = x; startY = y
                    point(x, y)
                    // Further pairs after M are line-tos.
                    cmd = if (rel) 'l' else 'L'
                }
                'L' -> { x = ox + num(); y = oy + num(); point(x, y) }
                'H' -> { x = (if (rel) x else 0f) + num(); point(x, y) }
                'V' -> { y = (if (rel) y else 0f) + num(); point(x, y) }
                'C' -> {
                    val c1x = ox + num(); val c1y = oy + num()
                    val c2x = ox + num(); val c2y = oy + num()
                    val ex = ox + num(); val ey = oy + num()
                    cubic(x, y, c1x, c1y, c2x, c2y, ex, ey, ::point)
                    lastCx = c2x; lastCy = c2y
                    x = ex; y = ey
                }
                'S' -> {
                    val reflect = lastCmd.uppercaseChar() == 'C' || lastCmd.uppercaseChar() == 'S'
                    val c1x = if (reflect) 2 * x - lastCx else x
                    val c1y = if (reflect) 2 * y - lastCy else y
                    val c2x = ox + num(); val c2y = oy + num()
                    val ex = ox + num(); val ey = oy + num()
                    cubic(x, y, c1x, c1y, c2x, c2y, ex, ey, ::point)
                    lastCx = c2x; lastCy = c2y
                    x = ex; y = ey
                }
                'Q' -> {
                    val cx = ox + num(); val cy = oy + num()
                    val ex = ox + num(); val ey = oy + num()
                    quad(x, y, cx, cy, ex, ey, ::point)
                    lastCx = cx; lastCy = cy
                    x = ex; y = ey
                }
                'T' -> {
                    val reflect = lastCmd.uppercaseChar() == 'Q' || lastCmd.uppercaseChar() == 'T'
                    val cx = if (reflect) 2 * x - lastCx else x
                    val cy = if (reflect) 2 * y - lastCy else y
                    val ex = ox + num(); val ey = oy + num()
                    quad(x, y, cx, cy, ex, ey, ::point)
                    lastCx = cx; lastCy = cy
                    x = ex; y = ey
                }
                'A' -> {
                    val rx = num(); val ry = num(); val rot = num()
                    val large = num() != 0f; val sweep = num() != 0f
                    val ex = ox + num(); val ey = oy + num()
                    arc(x, y, rx, ry, rot, large, sweep, ex, ey, ::point)
                    x = ex; y = ey
                }
                else -> throw IllegalArgumentException("unknown path command $cmd")
            }
            // Numbers right after a command's arguments repeat that command (the loop keeps [cmd]).
            lastCmd = cmd
        }
        close()
        return out
    }

    /** Even-odd test over all polygons (holes in a shape work as in SVG's even-odd rule). */
    fun contains(polygons: List<FloatArray>, x: Float, y: Float): Boolean {
        var inside = false
        for (p in polygons) {
            var j = p.size - 2
            var k = 0
            while (k < p.size) {
                val xi = p[k]; val yi = p[k + 1]
                val xj = p[j]; val yj = p[j + 1]
                if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
                j = k
                k += 2
            }
        }
        return inside
    }

    private const val STEPS = 12

    private inline fun cubic(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, point: (Float, Float) -> Unit) {
        for (s in 1..STEPS) {
            val t = s.toFloat() / STEPS
            val u = 1 - t
            point(
                u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3,
                u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3,
            )
        }
    }

    private inline fun quad(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, point: (Float, Float) -> Unit) {
        for (s in 1..STEPS) {
            val t = s.toFloat() / STEPS
            val u = 1 - t
            point(u * u * x0 + 2 * u * t * x1 + t * t * x2, u * u * y0 + 2 * u * t * y1 + t * t * y2)
        }
    }

    /** SVG elliptical arc (endpoint parameterization, SVG spec appendix B.2.4). */
    private fun arc(
        x1: Float, y1: Float, rxIn: Float, ryIn: Float, rotationDeg: Float,
        large: Boolean, sweep: Boolean, x2: Float, y2: Float, point: (Float, Float) -> Unit,
    ) {
        var rx = abs(rxIn).toDouble()
        var ry = abs(ryIn).toDouble()
        if (rx == 0.0 || ry == 0.0) {
            point(x2, y2)
            return
        }
        val phi = rotationDeg * PI / 180
        val cosP = cos(phi)
        val sinP = sin(phi)
        val dx = (x1 - x2) / 2.0
        val dy = (y1 - y2) / 2.0
        val x1p = cosP * dx + sinP * dy
        val y1p = -sinP * dx + cosP * dy
        val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if (lambda > 1) {
            rx *= sqrt(lambda)
            ry *= sqrt(lambda)
        }
        val num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        var coef = if (den == 0.0) 0.0 else sqrt((num / den).coerceAtLeast(0.0))
        if (large == sweep) coef = -coef
        val cxp = coef * rx * y1p / ry
        val cyp = -coef * ry * x1p / rx
        val cx = cosP * cxp - sinP * cyp + (x1 + x2) / 2.0
        val cy = sinP * cxp + cosP * cyp + (y1 + y2) / 2.0
        fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
            val a = acos((dot / len).coerceIn(-1.0, 1.0))
            return if (ux * vy - uy * vx < 0) -a else a
        }
        val theta1 = angle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var delta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if (!sweep && delta > 0) delta -= 2 * PI
        if (sweep && delta < 0) delta += 2 * PI
        val steps = ceil(abs(delta) / (PI / 12)).toInt().coerceAtLeast(1)
        for (s in 1..steps) {
            val th = theta1 + delta * s / steps
            val px = cx + rx * cos(th) * cosP - ry * sin(th) * sinP
            val py = cy + rx * cos(th) * sinP + ry * sin(th) * cosP
            point(px.toFloat(), py.toFloat())
        }
    }

    private sealed interface Tok {
        data class Cmd(val c: Char) : Tok
        data class Num(val value: Float) : Tok
    }

    private fun tokenize(d: String): List<Tok> {
        val out = mutableListOf<Tok>()
        var i = 0
        while (i < d.length) {
            val c = d[i]
            when {
                c.isLetter() && c != 'e' && c != 'E' -> { out += Tok.Cmd(c); i++ }
                c.isDigit() || c == '-' || c == '+' || c == '.' -> {
                    val start = i
                    i++
                    var seenDot = c == '.'
                    var seenExp = false
                    while (i < d.length) {
                        val ch = d[i]
                        when {
                            ch.isDigit() -> i++
                            ch == '.' && !seenDot && !seenExp -> { seenDot = true; i++ }
                            (ch == 'e' || ch == 'E') && !seenExp -> {
                                seenExp = true; i++
                                if (i < d.length && (d[i] == '-' || d[i] == '+')) i++
                            }
                            else -> break
                        }
                    }
                    out += Tok.Num(d.substring(start, i).toFloat())
                }
                else -> i++ // spaces and commas
            }
        }
        return out
    }
}
