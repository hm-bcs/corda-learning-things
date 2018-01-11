package com.template

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

// *****************
// * Contract Code *
// *****************
val IOU_CONTRACT_ID = "com.template.IOUContract"

class IOUContract : Contract {
    // Our Create command.
    class Create : CommandData


    /**
     * @param tx Takes in a transaction to verify.
     * @throws IllegalArgumentException if transaction proposal is rejected.
     * Does not throw an exception if valid.
     */
    override fun verify(tx: LedgerTransaction) {
        // To decide if the transaction is valid, the verify function has
        // access to tx.inputs, tx.outputs, tx.commands, attachments, time-window.

        // Test that transaction has one create command,
        // if there isn't one, or there is more than one, exception will be thrown.
        val command = tx.commands.requireSingleCommand<Create>()

        // requireThat blocks require that the right side is true,
        // else exception is thrown with left side as the message.
        requireThat {
            // No inputs
            "No inputs should be consumed when issuing an IOU." using (tx.inputs.isEmpty())
            // One output
            "There should be one output state of type IOUState." using (tx.outputs.size == 1)

            // IOU-specific constraints.
            val out = tx.outputsOfType<IOUState>().single()
            // Value not negative
            "The IOU's value must be non-negative." using (out.value > 0)
            // Lender not same as borrower
            "The lender and the borrower cannot be the same entity." using (out.lender != out.borrower)

            // Constraints on the signers.
            "There must be two signers." using (command.signers.toSet().size == 2)
            "The borrower and lender must be signers." using (command.signers.containsAll(listOf(
                    out.borrower.owningKey, out.lender.owningKey)))
        }
    }
}

// *********
// * State *
// *********
// IOU state
// has a value of the IOU, borrower and lender.
// Party is a type in corda used for entities on the network.
// Participants is a list of parties who should be notified of creation/consumption of the state.
class IOUState(val value: Int, val lender: Party, val borrower: Party) : ContractState {
    // All corda states must extend ContractState

    override val participants get() = listOf(lender, borrower)
}
