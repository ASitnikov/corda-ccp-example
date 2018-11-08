package com.exactpro.example.utils

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class Side {
    BUY,
    SELL
}