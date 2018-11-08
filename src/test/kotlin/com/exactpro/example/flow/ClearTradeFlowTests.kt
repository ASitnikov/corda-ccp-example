package com.exactpro.example.flow

import com.exactpro.example.testing.CCP_NAME
import com.exactpro.example.testing.PARTY_A_NAME
import com.exactpro.example.testing.PARTY_B_NAME
import com.exactpro.example.utils.FixMessage
import com.exactpro.example.utils.Side
import net.corda.core.identity.Party
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.AMOUNT
import net.corda.testing.node.InMemoryMessagingNetwork
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClearTradeFlowTests {

    private lateinit var mockNet: MockNetwork
    private lateinit var partyANode: StartedMockNode
    private lateinit var partyA: Party
    private lateinit var partyBNode: StartedMockNode
    private lateinit var partyB: Party
    private lateinit var ccpNode: StartedMockNode
    private lateinit var ccp: Party
    private lateinit var notary: Party

    @Before
    fun start() {
        mockNet = MockNetwork(servicePeerAllocationStrategy = InMemoryMessagingNetwork.ServicePeerAllocationStrategy.RoundRobin(), cordappPackages = listOf("com.exactpro.example.contract", "com.exactpro.example.flow", "com.exactpro.example.schema"))
        partyANode = mockNet.createPartyNode(PARTY_A_NAME)
        partyA = partyANode.info.identityFromX500Name(PARTY_A_NAME)
        partyBNode = mockNet.createPartyNode(PARTY_B_NAME)
        partyB = partyBNode.info.identityFromX500Name(PARTY_B_NAME)
        ccpNode = mockNet.createPartyNode(CCP_NAME)
        ccp = ccpNode.info.identityFromX500Name(CCP_NAME)

        notary = mockNet.defaultNotaryIdentity
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `clear a trade`() {
        val fixMessageA = FixMessage(
                tradeId = "1",
                symbol = "MSFT",
                side = Side.BUY,
                price = 2.0,
                size = 100.0)
        val futureA = partyANode.startFlow(RegisterTradeFlow(fixMessageA, ccp))

        val fixMessageB = FixMessage(
                tradeId = "1",
                symbol = "MSFT",
                side = Side.SELL,
                price = 2.0,
                size = 100.0)
        val futureB = partyBNode.startFlow(RegisterTradeFlow(fixMessageB, ccp))

        mockNet.runNetwork()
        futureA.getOrThrow()
        futureB.getOrThrow()

        val future = partyANode.startFlow(ClearTradeFlow("1", ccp))

        mockNet.runNetwork()
        future.getOrThrow()
    }
}