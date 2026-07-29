package com.einkphoto.app.feature.localalbum.conversion

import android.graphics.Bitmap

/** Converts an already composed 800x480 bitmap into a phone preview and a candidate 4bpp frame. */
class CandidateSixColorConverter {
    fun convert(input: Bitmap): CandidateSixColorConversion {
        require(input.width == CandidateSixColorProfile.WIDTH) {
            "Candidate conversion requires an 800-pixel-wide composed bitmap"
        }
        require(input.height == CandidateSixColorProfile.HEIGHT) {
            "Candidate conversion requires a 480-pixel-high composed bitmap"
        }

        val sourceArgb = IntArray(CandidateSixColorProfile.PIXEL_COUNT)
        input.getPixels(
            sourceArgb,
            0,
            CandidateSixColorProfile.WIDTH,
            0,
            0,
            CandidateSixColorProfile.WIDTH,
            CandidateSixColorProfile.HEIGHT,
        )
        val payload = convertCandidateArgbPixels(sourceArgb)
        val preview = Bitmap.createBitmap(
            CandidateSixColorProfile.WIDTH,
            CandidateSixColorProfile.HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        preview.setPixels(
            payload.previewArgb,
            0,
            CandidateSixColorProfile.WIDTH,
            0,
            0,
            CandidateSixColorProfile.WIDTH,
            CandidateSixColorProfile.HEIGHT,
        )

        return CandidateSixColorConversion(
            previewBitmap = preview,
            candidateBin = payload.candidateBin,
            algorithmVersion = CandidateSixColorProfile.ALGORITHM_VERSION,
            profileStatus = CandidateSixColorProfile.PROFILE_STATUS,
            nibbleOrder = CandidateSixColorProfile.nibbleOrder,
            validation = payload.validation,
        )
    }
}

data class CandidateSixColorConversion(
    val previewBitmap: Bitmap,
    val candidateBin: ByteArray,
    val algorithmVersion: String,
    val profileStatus: String,
    val nibbleOrder: CandidateNibbleOrder,
    val validation: CandidateFrameValidation,
)

/** Pure conversion payload kept Android-free so its binary contract can be unit tested on the JVM. */
internal data class CandidateSixColorPayload(
    val paletteIndices: ByteArray,
    val previewArgb: IntArray,
    val candidateBin: ByteArray,
    val validation: CandidateFrameValidation,
)

internal fun convertCandidateArgbPixels(sourceArgb: IntArray): CandidateSixColorPayload {
    require(sourceArgb.size == CandidateSixColorProfile.PIXEL_COUNT) {
        "Candidate conversion requires exactly ${CandidateSixColorProfile.PIXEL_COUNT} pixels"
    }

    val workRgb = IntArray(sourceArgb.size * 3)
    sourceArgb.indices.forEach { pixelOffset ->
        val argb = sourceArgb[pixelOffset]
        val base = pixelOffset * 3
        workRgb[base] = argb ushr 16 and 0xFF
        workRgb[base + 1] = argb ushr 8 and 0xFF
        workRgb[base + 2] = argb and 0xFF
    }

    val paletteIndices = ByteArray(sourceArgb.size)
    val previewArgb = IntArray(sourceArgb.size)
    sourceArgb.indices.forEach { offset ->
        val base = offset * 3
        val red = workRgb[base]
        val green = workRgb[base + 1]
        val blue = workRgb[base + 2]
        val paletteIndex = nearestOfficialPaletteIndex(red, green, blue)
        val color = CandidateSixColorProfile.palette[paletteIndex]
        paletteIndices[offset] = paletteIndex.toByte()
        previewArgb[offset] = color.previewArgb
        diffuseOfficialFloydSteinberg(
            workRgb = workRgb,
            x = offset % CandidateSixColorProfile.WIDTH,
            y = offset / CandidateSixColorProfile.WIDTH,
            redError = red - (color.previewArgb ushr 16 and 0xFF),
            greenError = green - (color.previewArgb ushr 8 and 0xFF),
            blueError = blue - (color.previewArgb and 0xFF),
        )
    }
    val candidateBin = packCandidate4Bpp(paletteIndices)
    val validation = CandidateFrameValidator.validate(paletteIndices, previewArgb, candidateBin)

    return CandidateSixColorPayload(
        paletteIndices = paletteIndices,
        previewArgb = previewArgb,
        candidateBin = candidateBin,
        validation = validation,
    )
}

/** Official `ImgDecode_NearestColor` equivalent, preserving its palette-order tie breaking. */
internal fun quantizeArgbToCandidatePaletteIndex(argb: Int): Int {
    return nearestOfficialPaletteIndex(argb ushr 16 and 0xFF, argb ushr 8 and 0xFF, argb and 0xFF)
}

private fun nearestOfficialPaletteIndex(red: Int, green: Int, blue: Int): Int {
    var bestIndex = 0
    var bestDistance = 999999
    CandidateSixColorProfile.palette.forEach { color ->
        val candidate = color.previewArgb
        val redDelta = red - (candidate ushr 16 and 0xFF)
        val greenDelta = green - (candidate ushr 8 and 0xFF)
        val blueDelta = blue - (candidate and 0xFF)
        val distance = redDelta * redDelta + greenDelta * greenDelta + blueDelta * blueDelta
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = color.paletteIndex
        }
    }
    return bestIndex
}

/** Exact LTR/TTB Floyd-Steinberg sequence from `ImgDecode_DitherRgb888`. */
private fun diffuseOfficialFloydSteinberg(
    workRgb: IntArray,
    x: Int,
    y: Int,
    redError: Int,
    greenError: Int,
    blueError: Int,
) {
    val width = CandidateSixColorProfile.WIDTH
    val height = CandidateSixColorProfile.HEIGHT
    fun addError(pixelOffset: Int, weight: Int) {
        val base = pixelOffset * 3
        workRgb[base] = clampRgb(workRgb[base] + redError * weight / 16)
        workRgb[base + 1] = clampRgb(workRgb[base + 1] + greenError * weight / 16)
        workRgb[base + 2] = clampRgb(workRgb[base + 2] + blueError * weight / 16)
    }

    if (x + 1 < width) addError(y * width + x + 1, 7)
    if (y + 1 < height) {
        if (x > 0) addError((y + 1) * width + x - 1, 3)
        addError((y + 1) * width + x, 5)
        if (x + 1 < width) addError((y + 1) * width + x + 1, 1)
    }
}

private fun clampRgb(value: Int): Int = value.coerceIn(0, 255)

internal fun packCandidate4Bpp(paletteIndices: ByteArray): ByteArray {
    require(paletteIndices.size == CandidateSixColorProfile.PIXEL_COUNT) {
        "Candidate packing requires exactly ${CandidateSixColorProfile.PIXEL_COUNT} indices"
    }
    return ByteArray(CandidateSixColorProfile.CANDIDATE_BIN_SIZE_BYTES) { byteOffset ->
        val first = paletteIndices[byteOffset * 2].toInt() and 0xFF
        val second = paletteIndices[byteOffset * 2 + 1].toInt() and 0xFF
        require(first in CandidateSixColorProfile.palette.indices) { "Invalid palette index: $first" }
        require(second in CandidateSixColorProfile.palette.indices) { "Invalid palette index: $second" }
        val firstCode = CandidateSixColorProfile.palette[first].panelColorCode
        val secondCode = CandidateSixColorProfile.palette[second].panelColorCode
        ((firstCode shl 4) or secondCode).toByte()
    }
}
