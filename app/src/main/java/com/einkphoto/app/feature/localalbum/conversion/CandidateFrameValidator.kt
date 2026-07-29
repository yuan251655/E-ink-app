package com.einkphoto.app.feature.localalbum.conversion

data class CandidateFrameValidation(
    val isStructurallyValid: Boolean,
    val errors: List<String>,
)

/** Bounded structural validation for a provisional phone-side conversion result. */
object CandidateFrameValidator {
    private const val MAX_REPORTED_ERRORS = 16

    /**
     * Validates only the provisional 4bpp container shape and candidate palette-index range.
     * It does not claim that the bytes match the physical panel protocol.
     */
    fun validateCandidateBinStructure(candidateBin: ByteArray): CandidateFrameValidation {
        if (candidateBin.size != CandidateSixColorProfile.CANDIDATE_BIN_SIZE_BYTES) {
            return CandidateFrameValidation(false, listOf("candidate_bin_length_mismatch"))
        }
        val errors = mutableListOf<String>()
        candidateBin.forEachIndexed { byteOffset, byte ->
            if (errors.size >= MAX_REPORTED_ERRORS) return@forEachIndexed
            val unsigned = byte.toInt() and 0xFF
            val first = unsigned ushr 4
            val second = unsigned and 0x0F
            if (CandidateSixColorProfile.paletteIndexForPanelCode(first) == null) {
                errors.add("candidate_bin_high_nibble_out_of_range_at_$byteOffset")
            }
            if (errors.size < MAX_REPORTED_ERRORS && CandidateSixColorProfile.paletteIndexForPanelCode(second) == null) {
                errors.add("candidate_bin_low_nibble_out_of_range_at_$byteOffset")
            }
        }
        return CandidateFrameValidation(errors.isEmpty(), errors)
    }

    internal fun validate(
        paletteIndices: ByteArray,
        previewArgb: IntArray,
        candidateBin: ByteArray,
    ): CandidateFrameValidation {
        val errors = mutableListOf<String>()
        if (paletteIndices.size != CandidateSixColorProfile.PIXEL_COUNT) {
            errors.add("palette_index_count_mismatch")
        }
        if (previewArgb.size != CandidateSixColorProfile.PIXEL_COUNT) {
            errors.add("preview_pixel_count_mismatch")
        }
        if (candidateBin.size != CandidateSixColorProfile.CANDIDATE_BIN_SIZE_BYTES) {
            errors.add("candidate_bin_length_mismatch")
        }
        if (errors.isNotEmpty()) return CandidateFrameValidation(false, errors)

        val binStructure = validateCandidateBinStructure(candidateBin)
        if (!binStructure.isStructurallyValid) {
            errors.addAll(binStructure.errors.take(MAX_REPORTED_ERRORS))
            return CandidateFrameValidation(false, errors)
        }

        paletteIndices.indices.forEach { pixelOffset ->
            if (errors.size >= MAX_REPORTED_ERRORS) return@forEach
            val paletteIndex = paletteIndices[pixelOffset].toInt() and 0xFF
            if (paletteIndex !in CandidateSixColorProfile.palette.indices) {
                errors.add("palette_index_out_of_range_at_$pixelOffset")
                return@forEach
            }
            if (previewArgb[pixelOffset] != CandidateSixColorProfile.palette[paletteIndex].previewArgb) {
                errors.add("preview_palette_mismatch_at_$pixelOffset")
            }

            val packed = candidateBin[pixelOffset / 2].toInt() and 0xFF
            val packedCode = if (pixelOffset % 2 == 0) packed ushr 4 else packed and 0x0F
            val expectedCode = CandidateSixColorProfile.palette[paletteIndex].panelColorCode
            if (packedCode != expectedCode) {
                errors.add("candidate_bin_palette_mismatch_at_$pixelOffset")
            }
        }

        return CandidateFrameValidation(errors.isEmpty(), errors)
    }
}
