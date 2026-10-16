package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pure Kotlin self-contained QR Code generator (Version 1-3 Byte Mode QR Code).
 * Generates an accurate 2D boolean matrix and renders with Jetpack Compose Canvas.
 */
object QrCodeEncoder {

    fun encode(text: String): Array<BooleanArray> {
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        // Determine version (Version 1: up to 17 bytes L, Version 2: up to 32 bytes L, Version 3: up to 53 bytes L, Version 4: up to 78 bytes L)
        val len = bytes.size
        val version = when {
            len <= 14 -> 1
            len <= 26 -> 2
            len <= 42 -> 3
            len <= 62 -> 4
            else -> 5
        }
        val size = 17 + 4 * version
        val modules = Array(size) { BooleanArray(size) }
        val isFunction = Array(size) { BooleanArray(size) }

        // 1. Finder patterns (7x7)
        drawFinder(modules, isFunction, 0, 0)
        drawFinder(modules, isFunction, size - 7, 0)
        drawFinder(modules, isFunction, 0, size - 7)

        // 2. Alignment pattern for version >= 2
        if (version >= 2) {
            val alignPos = when (version) {
                2 -> 18
                3 -> 22
                4 -> 26
                else -> 30
            }
            drawAlignment(modules, isFunction, alignPos, alignPos)
        }

        // 3. Timing patterns
        for (i in 8 until size - 8) {
            if (!isFunction[6][i]) {
                modules[6][i] = (i % 2 == 0)
                isFunction[6][i] = true
            }
            if (!isFunction[i][6]) {
                modules[i][6] = (i % 2 == 0)
                isFunction[i][6] = true
            }
        }

        // 4. Reserve format info
        for (i in 0..8) {
            isFunction[8][i] = true
            isFunction[i][8] = true
            isFunction[8][size - 1 - i] = true
            isFunction[size - 1 - i][8] = true
        }
        // Dark module
        modules[4 * version + 9][8] = true
        isFunction[4 * version + 9][8] = true

        // 5. Data encoding (Byte mode: 0100 + 8-bit count + bytes)
        val bitBuffer = mutableListOf<Int>()
        // Mode indicator 0100 (byte mode)
        addBits(bitBuffer, 4, 4)
        // Character count indicator (8 bits for versions 1-9)
        addBits(bitBuffer, len, 8)
        for (b in bytes) {
            addBits(bitBuffer, b.toInt() and 0xFF, 8)
        }

        val totalDataCodewords = when (version) {
            1 -> 19
            2 -> 34
            3 -> 55
            4 -> 80
            else -> 108
        }
        val totalEccCodewords = when (version) {
            1 -> 7
            2 -> 10
            3 -> 15
            4 -> 20
            else -> 26
        }

        // Terminator (up to 4 zeroes)
        val neededBits = totalDataCodewords * 8
        val termLen = minOf(4, neededBits - bitBuffer.size)
        repeat(termLen) { bitBuffer.add(0) }
        // Pad to byte boundary
        while (bitBuffer.size % 8 != 0) {
            bitBuffer.add(0)
        }
        // Pad bytes (0xEC, 0x11)
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIndex = 0
        while (bitBuffer.size < neededBits) {
            addBits(bitBuffer, padBytes[padIndex % 2], 8)
            padIndex++
        }

        // Convert bits to data codewords
        val dataCodewords = IntArray(totalDataCodewords)
        for (i in 0 until totalDataCodewords) {
            var byteVal = 0
            for (j in 0 until 8) {
                byteVal = (byteVal shl 1) or bitBuffer[i * 8 + j]
            }
            dataCodewords[i] = byteVal
        }

        // Generate Reed-Solomon ECC
        val eccCodewords = generateEcc(dataCodewords, totalEccCodewords)

        // All codewords
        val allCodewords = dataCodewords + eccCodewords

        // Convert all codewords to bit stream
        val fullBitStream = mutableListOf<Int>()
        for (cw in allCodewords) {
            addBits(fullBitStream, cw, 8)
        }

        // 6. Place data modules in zigzag pattern
        var bitIndex = 0
        var right = size - 1
        var upward = true
        while (right > 0) {
            if (right == 6) right-- // Skip vertical timing column
            val rows = if (upward) (size - 1 downTo 0) else (0 until size)
            for (y in rows) {
                for (x in intArrayOf(right, right - 1)) {
                    if (!isFunction[y][x]) {
                        val bit = if (bitIndex < fullBitStream.size) fullBitStream[bitIndex++] else 0
                        // Apply Mask Pattern 0: (row + col) % 2 == 0
                        val invert = ((x + y) % 2 == 0)
                        modules[y][x] = if (invert) (bit == 0) else (bit == 1)
                    }
                }
            }
            right -= 2
            upward = !upward
        }

        // 7. Write format info (Error correction Level L = 01, Mask pattern 0 = 000 -> 01000 = 8)
        // Format info bits for L-0: 0x77C4 (15 bits with BCH code)
        val formatBits = 0x77C4
        for (i in 0..14) {
            val bit = ((formatBits shr (14 - i)) and 1) == 1
            if (i < 6) {
                modules[8][i] = bit
            } else if (i < 8) {
                modules[8][i + 1] = bit
            } else {
                modules[14 - i][8] = bit
            }

            if (i < 8) {
                modules[size - 1 - i][8] = bit
            } else {
                modules[8][size - 15 + i] = bit
            }
        }

        return modules
    }

    private fun addBits(buffer: MutableList<Int>, value: Int, count: Int) {
        for (i in count - 1 downTo 0) {
            buffer.add((value shr i) and 1)
        }
    }

    private fun drawFinder(modules: Array<BooleanArray>, isFunction: Array<BooleanArray>, startX: Int, startY: Int) {
        for (dy in -1..7) {
            for (dx in -1..7) {
                val x = startX + dx
                val y = startY + dy
                if (x in modules.indices && y in modules.indices) {
                    val inCenter = dx in 2..4 && dy in 2..4
                    val inRing = dx in 0..6 && dy in 0..6 && (dx == 0 || dx == 6 || dy == 0 || dy == 6)
                    val isBlack = inCenter || inRing
                    modules[y][x] = isBlack
                    isFunction[y][x] = true
                }
            }
        }
    }

    private fun drawAlignment(modules: Array<BooleanArray>, isFunction: Array<BooleanArray>, centerX: Int, centerY: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val x = centerX + dx
                val y = centerY + dy
                val isBlack = (dx == 0 && dy == 0) || dx == -2 || dx == 2 || dy == -2 || dy == 2
                modules[y][x] = isBlack
                isFunction[y][x] = true
            }
        }
    }

    // Reed Solomon Error Correction in Galois Field 256
    private fun generateEcc(data: IntArray, eccCount: Int): IntArray {
        val exp = IntArray(512)
        val log = IntArray(256)
        var x = 1
        for (i in 0 until 255) {
            exp[i] = x
            exp[i + 255] = x
            log[x] = i
            x = (x shl 1)
            if (x >= 256) x = x xor 0x11D // Primitive polynomial x^8 + x^4 + x^3 + x^2 + 1
        }

        fun gfMul(a: Int, b: Int): Int {
            if (a == 0 || b == 0) return 0
            return exp[log[a] + log[b]]
        }

        // Generator polynomial
        var gen = intArrayOf(1)
        for (i in 0 until eccCount) {
            val root = exp[i]
            val nextGen = IntArray(gen.size + 1)
            for (j in gen.indices) {
                nextGen[j] = nextGen[j] xor gfMul(gen[j], root)
                nextGen[j + 1] = nextGen[j + 1] xor gen[j]
            }
            gen = nextGen
        }

        val result = IntArray(eccCount)
        val temp = IntArray(data.size + eccCount)
        System.arraycopy(data, 0, temp, 0, data.size)

        for (i in data.indices) {
            val factor = temp[i]
            if (factor != 0) {
                for (j in gen.indices) {
                    temp[i + j] = temp[i + j] xor gfMul(gen[j], factor)
                }
            }
        }

        System.arraycopy(temp, data.size, result, 0, eccCount)
        return result
    }
}

/**
 * Renders the generated QR Code as an interactive, crisp Compose Canvas
 */
@Composable
fun QrCodeView(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    moduleColor: Color = Color(0xFF121316),
    backgroundColor: Color = Color.White
) {
    val matrix = remember(content) {
        try {
            QrCodeEncoder.encode(content)
        } catch (e: Exception) {
            null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .background(backgroundColor, RoundedCornerShape(16.dp))
            .border(2.dp, Color(0xFF2C3240), RoundedCornerShape(16.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (matrix != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val matrixSize = matrix.size
                val moduleWidth = this.size.width / matrixSize
                val moduleHeight = this.size.height / matrixSize

                for (y in 0 until matrixSize) {
                    for (x in 0 until matrixSize) {
                        if (matrix[y][x]) {
                            drawRect(
                                color = moduleColor,
                                topLeft = Offset(x * moduleWidth, y * moduleHeight),
                                size = Size(moduleWidth + 0.3f, moduleHeight + 0.3f)
                            )
                        }
                    }
                }
            }
        }
    }
}
