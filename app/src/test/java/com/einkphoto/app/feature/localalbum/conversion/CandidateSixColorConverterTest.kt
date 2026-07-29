package com.einkphoto.app.feature.localalbum.conversion

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateSixColorConverterTest {
    @Test
    fun candidateBinHasFixed192000ByteLengthAndValidStructure() {
        val source = IntArray(CandidateSixColorProfile.PIXEL_COUNT) { 0xFFFFFFFF.toInt() }

        val result = convertCandidateArgbPixels(source)

        assertEquals(192000, result.candidateBin.size)
        assertTrue(result.validation.errors.toString(), result.validation.isStructurallyValid)
    }

    @Test
    fun exactPaletteColorsMapToTheirCandidateIndices() {
        CandidateSixColorProfile.palette.forEach { color ->
            assertEquals(
                color.chineseName,
                color.paletteIndex,
                quantizeArgbToCandidatePaletteIndex(color.previewArgb),
            )
        }
    }

    @Test
    fun conversionIsDeterministicForIdenticalInput() {
        val source = IntArray(CandidateSixColorProfile.PIXEL_COUNT) { offset ->
            val red = offset * 17 and 0xFF
            val green = offset * 31 and 0xFF
            val blue = offset * 47 and 0xFF
            0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
        }

        val first = convertCandidateArgbPixels(source)
        val second = convertCandidateArgbPixels(source.copyOf())

        assertArrayEquals(first.paletteIndices, second.paletteIndices)
        assertArrayEquals(first.previewArgb, second.previewArgb)
        assertArrayEquals(first.candidateBin, second.candidateBin)
    }

    @Test
    fun previewAndCandidateBinAreDerivedFromTheSamePaletteIndices() {
        val palette = CandidateSixColorProfile.palette
        val source = IntArray(CandidateSixColorProfile.PIXEL_COUNT) { offset ->
            palette[offset % palette.size].previewArgb
        }

        val result = convertCandidateArgbPixels(source)

        result.paletteIndices.indices.forEach { pixelOffset ->
            val expectedIndex = pixelOffset % palette.size
            assertEquals(expectedIndex, result.paletteIndices[pixelOffset].toInt())
            assertEquals(palette[expectedIndex].previewArgb, result.previewArgb[pixelOffset])
            val packed = result.candidateBin[pixelOffset / 2].toInt() and 0xFF
            val packedCode = if (pixelOffset % 2 == 0) packed ushr 4 else packed and 0x0F
            assertEquals(palette[expectedIndex].panelColorCode, packedCode)
        }
        assertTrue(result.validation.isStructurallyValid)
    }

    @Test
    fun candidatePackingUsesOfficialPanelCodesWithHighNibbleFirstBaseline() {
        val indices = ByteArray(CandidateSixColorProfile.PIXEL_COUNT)
        indices[0] = 0
        indices[1] = 1
        indices[2] = 2
        indices[3] = 3

        val packed = packCandidate4Bpp(indices)

        assertEquals(CandidateNibbleOrder.HIGH_NIBBLE_FIRST_PROVISIONAL_BASELINE, CandidateSixColorProfile.nibbleOrder)
        assertEquals(0x01, packed[0].toInt() and 0xFF)
        assertEquals(0x36, packed[1].toInt() and 0xFF)
    }

    @Test
    fun officialFloydSteinbergReferenceFixtureUsesLtrTtbAndPerWriteClamp() {
        val source = IntArray(CandidateSixColorProfile.PIXEL_COUNT) { 0xFFFFFFFF.toInt() }
        val fixture = intArrayOf(
            0xFF808080.toInt(), 0xFF5A5A5A.toInt(), 0xFFC81E1E.toInt(), 0xFF14DC14.toInt(),
            0xFF1414DC.toInt(), 0xFFDCDC14.toInt(), 0xFFB4B4B4.toInt(), 0xFF505050.toInt(),
        )
        fixture.copyInto(source)

        val result = convertCandidateArgbPixels(source)

        assertArrayEquals(
            byteArrayOf(1, 0, 2, 3, 4, 5, 1, 0, 1, 1, 1, 1),
            result.paletteIndices.copyOfRange(0, 12),
        )
        assertEquals(0x10, result.candidateBin[0].toInt() and 0xFF)
        assertEquals(0x36, result.candidateBin[1].toInt() and 0xFF)
        assertEquals(0x52, result.candidateBin[2].toInt() and 0xFF)
        assertTrue(result.validation.isStructurallyValid)
    }

    @Test
    fun officialPanelColorCodesMapBlackWhiteRedGreenBlueYellow() {
        assertEquals(listOf(0, 1, 3, 6, 5, 2), CandidateSixColorProfile.palette.map { it.panelColorCode })
    }

    @Test
    fun structuralValidatorRejectsWrongLengthAndUnknownCandidateIndex() {
        val wrongLength = CandidateFrameValidator.validateCandidateBinStructure(ByteArray(4))
        val unknownIndex = ByteArray(CandidateSixColorProfile.CANDIDATE_BIN_SIZE_BYTES)
            .also { it[0] = 0xF1.toByte() }

        assertTrue(!wrongLength.isStructurallyValid)
        assertTrue(
            !CandidateFrameValidator.validateCandidateBinStructure(unknownIndex).isStructurallyValid,
        )
    }
}
