package com.exactpro.example.testing

import net.corda.core.identity.CordaX500Name

@JvmField
val PARTY_A_NAME = CordaX500Name("PartyA", "London", "GB")

@JvmField
val PARTY_B_NAME = CordaX500Name("PartyB", "New York", "US")

@JvmField
val CCP_NAME = CordaX500Name("PartyC", "Moscow", "RU")
