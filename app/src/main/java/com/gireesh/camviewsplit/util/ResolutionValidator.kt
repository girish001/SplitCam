package com.gireesh.camviewsplit.util

data class ResolutionValidationResult(
    val isValid: Boolean,
    val width: Int,
    val height: Int,
    val aspectRatioLabel: String,
    val errorMessage: String? = null
)

object ResolutionValidator {

    private const val MIN_DIMENSION = 128
    private const val MAX_DIMENSION = 4096

    fun validateAndNormalize(widthInput: Int, heightInput: Int): ResolutionValidationResult {
        if (widthInput < MIN_DIMENSION || heightInput < MIN_DIMENSION) {
            return ResolutionValidationResult(
                isValid = false,
                width = widthInput,
                height = heightInput,
                aspectRatioLabel = "Invalid",
                errorMessage = "Width and Height must be at least ${MIN_DIMENSION}px."
            )
        }

        if (widthInput > MAX_DIMENSION || heightInput > MAX_DIMENSION) {
            return ResolutionValidationResult(
                isValid = false,
                width = widthInput,
                height = heightInput,
                aspectRatioLabel = "Invalid",
                errorMessage = "Width and Height cannot exceed ${MAX_DIMENSION}px."
            )
        }

        // Hardware video encoders (e.g. H.264 / MediaCodec) require even dimensions
        val evenWidth = if (widthInput % 2 != 0) widthInput - 1 else widthInput
        val evenHeight = if (heightInput % 2 != 0) heightInput - 1 else heightInput

        val ratioLabel = calculateAspectRatioLabel(evenWidth, evenHeight)

        return ResolutionValidationResult(
            isValid = true,
            width = evenWidth,
            height = evenHeight,
            aspectRatioLabel = ratioLabel,
            errorMessage = null
        )
    }

    fun calculateAspectRatioLabel(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "Invalid"
        val gcdValue = gcd(width, height)
        val wRatio = width / gcdValue
        val hRatio = height / gcdValue

        return when {
            wRatio == 9 && hRatio == 16 -> "9:16 (Portrait)"
            wRatio == 16 && hRatio == 9 -> "16:9 (Landscape)"
            wRatio == 1 && hRatio == 1 -> "1:1 (Square)"
            wRatio == 4 && hRatio == 3 -> "4:3"
            wRatio == 3 && hRatio == 4 -> "3:4"
            else -> "$wRatio:$hRatio"
        }
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val temp = y
            y = x % y
            x = temp
        }
        return if (x < 1) 1 else x
    }
}
