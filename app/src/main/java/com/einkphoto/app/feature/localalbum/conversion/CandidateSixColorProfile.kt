package com.einkphoto.app.feature.localalbum.conversion

/**
 * Phone-side draft profile for the currently known 7.3-inch six-color panel baseline.
 *
 * This profile is deliberately named candidate/provisional: the physical panel color codes,
 * scan direction, orientation and nibble order still require device capability negotiation and
 * real-panel verification before the bytes can be called a final device frame.
 */
object CandidateSixColorProfile {
    const val WIDTH = 800
    const val HEIGHT = 480
    const val PIXEL_COUNT = WIDTH * HEIGHT
    const val CANDIDATE_BIN_SIZE_BYTES = PIXEL_COUNT / 2

    const val ALGORITHM_VERSION = "official-photopainter-floyd-steinberg-rgb888-v1"
    const val PROFILE_STATUS = "provisional_unverified"

    val nibbleOrder = CandidateNibbleOrder.HIGH_NIBBLE_FIRST_PROVISIONAL_BASELINE

    /**
     * Deterministic phone-preview palette. These indices are candidate indices, not verified
     * hardware color codes.
     */
    val palette: List<CandidatePaletteColor> = listOf(
        CandidatePaletteColor(0, 0, "黑色", 0xFF000000.toInt()),
        CandidatePaletteColor(1, 1, "白色", 0xFFFFFFFF.toInt()),
        CandidatePaletteColor(2, 3, "红色", 0xFFFF0000.toInt()),
        CandidatePaletteColor(3, 6, "绿色", 0xFF00FF00.toInt()),
        CandidatePaletteColor(4, 5, "蓝色", 0xFF0000FF.toInt()),
        CandidatePaletteColor(5, 2, "黄色", 0xFFFFFF00.toInt()),
    )

    fun paletteIndexForPanelCode(panelCode: Int): Int? =
        palette.indexOfFirst { it.panelColorCode == panelCode }.takeIf { it >= 0 }
}

data class CandidatePaletteColor(
    val paletteIndex: Int,
    val panelColorCode: Int,
    val chineseName: String,
    val previewArgb: Int,
)

enum class CandidateNibbleOrder {
    /** Pixel 0 in bits 7..4, pixel 1 in bits 3..0; not yet verified on the real panel. */
    HIGH_NIBBLE_FIRST_PROVISIONAL_BASELINE,
}
