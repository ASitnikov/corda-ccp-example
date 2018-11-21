package com.exactpro.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.exactpro.example.contract.TradeContract
import com.exactpro.example.utils.FixMessage
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class RegisterTradeFlow(val fixMessage: FixMessage, val ccp: Party)
    : FlowLogic<SignedTransaction>() {

    companion object {
        object GENERATING_TRANSACTION
            : ProgressTracker.Step("Generating transaction.")
        object VERIFYING_TRANSACTION
            : ProgressTracker.Step("Verifying contract constraints.")
        object EXCHANGING_IDENTITIES
            : ProgressTracker.Step("Exchanging identities with CCP using IdentitySyncFlow")
        object SIGNING_TRANSACTION
            : ProgressTracker.Step("Signing transaction with our private key.")
        object GATHERING_SIGS
            : ProgressTracker.Step("Getting the CCP signature.")
        {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }
        object FINALISING_TRANSACTION
            : ProgressTracker.Step("Obtaining notary signature and recording transaction.")
        {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                EXCHANGING_IDENTITIES,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        )
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        // Stage 1.
        // Generate an unsigned transaction.
        progressTracker.currentStep = GENERATING_TRANSACTION

        // Obtain a reference to the notary we want to use.
        val notary = serviceHub.networkMapCache.notaryIdentities.first()

        //Create anonymous identity
        val freshkKeyAndCert = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert,false)
        val anonymousme = freshkKeyAndCert.party.anonymise()

        val tradeState = TradeContract.State(
                tradeId = fixMessage.tradeId,
                symbol = fixMessage.symbol,
                side = fixMessage.side,
                price = fixMessage.price,
                size = fixMessage.size,
                buyerOrSeller = anonymousme,
                ccp = ccp,
                cleared = false)



        val txCommand = Command(
                TradeContract.Commands.RegisterTrade(),
                listOf(anonymousme.owningKey, ccp.owningKey))
        val txBuilder = TransactionBuilder(notary)
                .addOutputState(tradeState, TradeContract.ID)
                .addCommand(txCommand)

        // Stage 2.
        // Verify that the transaction is valid.
        progressTracker.currentStep = VERIFYING_TRANSACTION
        txBuilder.verify(serviceHub)

        // Stage 3.
        // Sign the transaction.
        progressTracker.currentStep = SIGNING_TRANSACTION
        val ptx = serviceHub.signInitialTransaction(txBuilder, listOf(anonymousme.owningKey))

        // Stage 4
        //Start session and use IdentitySyncFlow.Send
        progressTracker.currentStep = EXCHANGING_IDENTITIES
        val ccpSession = initiateFlow(ccp)
        subFlow(IdentitySyncFlow.Send(ccpSession, txBuilder.toWireTransaction(serviceHub) ))

        // Stage 5.
        // Getting the CCP signature.
        progressTracker.currentStep = GATHERING_SIGS
        val stx = subFlow(CollectSignaturesFlow(
                ptx, listOf(ccpSession), listOf(anonymousme.owningKey),GATHERING_SIGS.childProgressTracker()))

        // Stage 6.
        // Notarise and record the transaction in our vaults.
        progressTracker.currentStep = FINALISING_TRANSACTION
        return subFlow(FinalityFlow(stx, FINALISING_TRANSACTION.childProgressTracker()))
    }
}

@InitiatedBy(RegisterTradeFlow::class)
class AcceptTradeFlow(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
            override fun checkTransaction(stx: SignedTransaction) = requireThat {
                val output = stx.tx.outputs.single().data
                "This must be an TradeState transaction." using (output is TradeContract.State)
            }
        }
        //Receive message from IdentitySyncFlow
        subFlow(IdentitySyncFlow.Receive(otherPartyFlow))
        return subFlow(signTransactionFlow)
    }
}

