package com.exactpro.example.schema

import com.exactpro.example.utils.Side
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

object TradeSchema

object TradeSchemaV1 : MappedSchema(schemaFamily = TradeSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentTradeState::class.java)) {
    @Entity
    @Table(name = "trade_states")
    class PersistentTradeState(
            @Column(name = "trade_id")
            var tradeId: String,

            @Column(name = "symbol")
            var symbol: String,

            @Column(name = "side")
            var side: Side,

            @Column(name = "price")
            var price: Double,

            @Column(name = "size")
            var size: Double,

            @Column(name = "buyer_seller_name")
            var buyerOrSeller: AbstractParty?,

            @Column(name = "ccp_name")
            var ccp: Party?,

            @Column(name = "cleared")
            var cleared: Boolean
    ) : PersistentState() {
        constructor() : this("","",Side.BUY,0.0, 0.0, null, null, false)
    }
}