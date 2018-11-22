package com.exactpro.example.contract

import com.exactpro.example.schema.TradeSchemaV1
import com.exactpro.example.utils.Side
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction

class TradeContract : Contract {

    data class State(
            val tradeId: String,
            val symbol: String,
            val side: Side,
            val price: Double,
            val size: Double,
            val buyerOrSeller: AbstractParty,
            val ccp: Party,
            val cleared: Boolean) : ContractState, QueryableState {

        override val participants: List<AbstractParty> = listOfNotNull(buyerOrSeller, ccp)

        override fun supportedSchemas(): Iterable<MappedSchema> = listOf(TradeSchemaV1)

        override fun generateMappedObject(schema: MappedSchema): PersistentState {
            return when (schema) {
                is TradeSchemaV1 -> TradeSchemaV1.PersistentTradeState(
                        tradeId = this.tradeId,
                        symbol =  this.symbol,
                        side = this.side,
                        price = this.price,
                        size = this.size,
                        buyerOrSeller = this.buyerOrSeller,
                        ccp = this.ccp,
                        cleared = this.cleared
                )
                else -> throw IllegalArgumentException("Unrecognised schema $schema")
            }
        }

    }

    companion object {
        val ID = "com.exactpro.example.contract.TradeContract"
    }

    // A transaction is considered valid if the verify() function of the contract of each of the transaction's input
    // and output states does not throw an exception.
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Commands>()
        when (command.value) {
            is Commands.RegisterTrade -> requireThat {
                "there is no input states" using (tx.inputStates.isEmpty())
                val output = tx.outputs.single().data
                "This must be an TradeState state" using (output is TradeContract.State)
                val tradeOutput = output as TradeContract.State
                "trade is not cleared" using (!tradeOutput.cleared)
                "size is positive" using (tradeOutput.size > 0)
                "price is positive" using (tradeOutput.price > 0)
            }
            is Commands.ClearTrade -> requireThat {
                "there are 2 input states" using (tx.inputStates.size == 2)
                "there are 2 output states" using (tx.outputStates.size == 2)
                val inputA = tx.inputs[0].state.data as TradeContract.State
                val inputB = tx.inputs[1].state.data as TradeContract.State
                "input[0] is not cleared" using (!inputA.cleared)
                "input[1] is not cleared" using (!inputB.cleared)
                "inputs have same tradeId" using (inputA.tradeId == inputB.tradeId)
                "inputs have different sides" using (inputA.side != inputB.side)
                val outputA = tx.outputs[0].data as TradeContract.State
                val outputB = tx.outputs[1].data as TradeContract.State
                "output[0] is cleared" using (outputA.cleared)
                "output[1] is cleared" using (outputB.cleared)
                "outputs have same tradeId" using (outputA.tradeId == outputB.tradeId)
                "outputs have different sides" using (outputA.side != outputB.side)
            }
        }

    }

    // Used to indicate the transaction's intent.
    sealed class Commands : TypeOnlyCommandData() {
        class RegisterTrade : Commands()
        class ClearTrade : Commands()
    }
}
