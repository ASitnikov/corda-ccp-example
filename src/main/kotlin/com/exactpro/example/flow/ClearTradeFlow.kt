package com.exactpro.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.exactpro.example.contract.TradeContract
import com.exactpro.example.schema.TradeSchemaV1
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.Builder.equal
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

@InitiatingFlow
@StartableByRPC
class ClearTradeFlow(val tradeId: String, val ccp: Party) : FlowLogic<Void?>() {

    @Suspendable
    override fun call(): Void? {
        val ccpSession = initiateFlow(ccp)
        ccpSession.send(ClearTradeRequest(tradeId))
        return null
    }

    @CordaSerializable
    class ClearTradeRequest(val tradeId: String)
}

@InitiatedBy(ClearTradeFlow::class)
class ClearTradeHandler(val otherPartyFlow: FlowSession) : FlowLogic<Void?>() {

    companion object {
        object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction.")
        object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
        object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
        object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): Void? {
        val tradeId = otherPartyFlow.receive<ClearTradeFlow.ClearTradeRequest>()
                .unwrap { data -> data }.tradeId
        val expression = builder {
            /* 'equal' not 'equalS' */
            TradeSchemaV1.PersistentTradeState::tradeId.equal(tradeId)
            TradeSchemaV1.PersistentTradeState::cleared.equal(false)
        }
        val queryCriteria = QueryCriteria.VaultCustomQueryCriteria(expression)
        val tradeStateAndRefs
                = serviceHub.vaultService.queryBy<TradeContract.State>(queryCriteria).states

        if (tradeStateAndRefs.size == 2) {
            // Stage 1.
            // Generate an unsigned transaction.
            progressTracker.currentStep = GENERATING_TRANSACTION

            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            val txCommand = Command(
                    TradeContract.Commands.ClearTrade(), listOf(ourIdentity.owningKey))
            val clearedA = tradeStateAndRefs[0].state.data.copy(cleared = true)
            val clearedB = tradeStateAndRefs[1].state.data.copy(cleared = true)
            val txBuilder = TransactionBuilder(notary)
                    .addInputState(tradeStateAndRefs[0])
                    .addInputState(tradeStateAndRefs[1])
                    .addOutputState(clearedA, TradeContract.ID)
                    .addOutputState(clearedB, TradeContract.ID)
                    .addCommand(txCommand)

            // Stage 2.
            // Verify that the transaction is valid.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            txBuilder.verify(serviceHub)

            // Stage 3.
            // Sign the transaction.
            progressTracker.currentStep = SIGNING_TRANSACTION
            val ptx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            // Notarise and record the transaction in our vaults.
            progressTracker.currentStep = FINALISING_TRANSACTION
            subFlow(FinalityFlow(ptx, FINALISING_TRANSACTION.childProgressTracker()))
        }
        return null
    }
}

