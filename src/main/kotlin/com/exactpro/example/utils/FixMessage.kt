package com.exactpro.example.utils

data class FixMessage(
        val tradeId: String,
        val symbol: String,
        val side: Side,
        val price: Double,
        val size: Double
)