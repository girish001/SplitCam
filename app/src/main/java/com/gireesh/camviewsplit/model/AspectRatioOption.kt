package com.gireesh.camviewsplit.model

enum class AspectRatioOption(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_9_16("9:16", 9f / 16f),
    RATIO_1_1("1:1", 1f),
    RATIO_4_3("4:3", 4f / 3f),
    FIT("Fit", null),
    FILL("Fill", null)
}
