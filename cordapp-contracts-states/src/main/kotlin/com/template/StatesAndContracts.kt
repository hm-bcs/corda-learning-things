package com.template

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

// *****************
// * Contract Code *
// *****************
val HARRISON_ISSUANCE_CONTRACT_ID = "com.template.HarrisonIssueContract"

class HarrisonIssueContract : Contract {
    class Create : CommandData

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<Create>()

        requireThat {
            "No inputs should be consumed" using (tx.inputs.isEmpty())
            "Only one output" using (tx.outputs.size == 1)

            // output is a single HarrisonState
            val outputState = tx.outputsOfType<HarrisonState>().single()
            "Non negative value" using (outputState.value > 0)

            "Must be two signers" using (command.signers.size == 2)
            "Signers must be issuer and receiver" using (command.signers.containsAll(
                    listOf(outputState.owner.owningKey, outputState.issuer.owningKey)
            ))
        }
    }
}

// **********
// * States *
// **********

class HarrisonState(val value: Int, val owner: Party, val issuer: Party) : ContractState {
    override val participants: List<AbstractParty>
        get() = listOf(owner, issuer)

}
