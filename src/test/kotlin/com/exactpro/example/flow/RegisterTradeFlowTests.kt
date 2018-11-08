package com.exactpro.example.flow

import com.exactpro.example.testing.CCP_NAME
import com.exactpro.example.testing.PARTY_A_NAME
import com.exactpro.example.utils.FixMessage
import com.exactpro.example.utils.Side
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.InMemoryMessagingNetwork.ServicePeerAllocationStrategy
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test

class RegisterTradeFlowTests {

    private lateinit var mockNet: MockNetwork
    private lateinit var partyANode: StartedMockNode
    private lateinit var partyA: Party
    private lateinit var ccpNode: StartedMockNode
    private lateinit var ccp: Party
    private lateinit var notary: Party

    @Before
    fun start() {
        val cordappPackages = listOf(
                "com.exactpro.example.contract", "com.exactpro.example.flow",
                "com.exactpro.example.schema")
        mockNet = MockNetwork(
                servicePeerAllocationStrategy = ServicePeerAllocationStrategy.RoundRobin(),
                cordappPackages = cordappPackages)
        partyANode = mockNet.createPartyNode(PARTY_A_NAME)
        partyA = partyANode.info.identityFromX500Name(PARTY_A_NAME)
        ccpNode = mockNet.createPartyNode(CCP_NAME)
        ccp = ccpNode.info.identityFromX500Name(CCP_NAME)
        notary = mockNet.defaultNotaryIdentity
    }

    @After
    fun cleanUp() {
        mockNet.stopNodes()
    }

    @Test
    fun `register a trade`() {
        val fixMessage = FixMessage(
                tradeId = "1",
                symbol = "MSFT",
                side = Side.BUY,
                price = 2.0,
                size = 100.0)
        val future = partyANode.startFlow(RegisterTradeFlow(fixMessage, ccp))
        mockNet.runNetwork()
        val tx = future.getOrThrow()
    }
}