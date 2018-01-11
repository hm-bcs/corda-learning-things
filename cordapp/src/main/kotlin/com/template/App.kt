package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

// *****************
// * API Endpoints *
// *****************
@Path("template")
class TemplateApi(val rpcOps: CordaRPCOps) {
    // Accessible at /api/template/templateGetEndpoint.
    @GET
    @Path("templateGetEndpoint")
    @Produces(MediaType.APPLICATION_JSON)
    fun templateGetEndpoint(): Response {
        return Response.ok("Template GET endpoint.").build()
    }
}

// *********
// * Flows *
// *********
@InitiatingFlow // Flow can be initiated by the node
@StartableByRPC // Flow can be started by owner via remote procedure call
class IOUFlow(val iouValue: Int, // By having parameters, we can use them in the call method.
              val otherParty: Party) : FlowLogic<Unit>() {
    // Extending FlowLogic allows us to create a flow. The type is what the flow.call will return, in this case, nothing.

    /** The progress tracker provides checkpoints indicating the progress of the flow to observers. */
    override val progressTracker = ProgressTracker()

    // The call method is what happens when calling the flow.
    @Suspendable // This allows method to be suspended if running too long, to move onto other flows.
    override fun call() {
        // We get the notary from the network map, obtained through the service hub.
        val notary = serviceHub.networkMapCache.notaryIdentities[0]

        // To actually create the transaction, we need a transaction builder.
        // It allows us to add inputs, outputs, commands, etc to the transaction.
        val txBuilder = TransactionBuilder(notary = notary)

        // We create the transaction components.
        val outputState = IOUState(iouValue, ourIdentity, otherParty)
        val outputContractAndState = StateAndContract(outputState, IOU_CONTRACT_ID)
        val cmd = Command(IOUContract.Create(), listOf(ourIdentity.owningKey, otherParty.owningKey))
        // The command includes the contract and the list of required signers.

        // We add the items to the builder.
        txBuilder.withItems(outputContractAndState, cmd)

        // Make sure you verify the transaction before you sign it.
        // Verifying the transaction via the given contract above. (IOU_CONTRACT_ID)
        txBuilder.verify(serviceHub)

        // We sign the transaction, so it is effectively immutable.
        // Changing it would invalidate our signature.
        // It returns a signed transaction, which is a transaction and list of signatures.
        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        // Creating a session with the other party, so we can get them to sign the transaction.
        val otherpartySession = initiateFlow(otherParty)

        // Obtaining the counterparty's signature.
        // The collect signatures flow takes:
        // > Transaction signed by flow initiator
        // > List of flow sessions between flow initiator and required signers.
        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, listOf(otherpartySession), CollectSignaturesFlow.tracker()))

        // We finalise the transaction using the FinalityFlow.
        // The FinalityFlows provides:
        // > Notarisation if required(if there are inputs to be consumed / time windows).
        // > Recording in nodes vault.
        // > Sending it to other participants to record.
        subFlow(FinalityFlow(fullySignedTx))
    }
}

// *******************
// * Flow Responders *
// *******************

@InitiatedBy(IOUFlow::class)
class IOUFlowResponder(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // The CollectSignatureFlow we are receiving expects us to return our signature.
        // SignTransactionFlow is an abstract class which is pre-defined to do this.
        val signTransactionFlow = object : SignTransactionFlow(otherPartySession, SignTransactionFlow.tracker()) {
            // SignTransactionFlow will automatically verfiy and then sign the transaction,
            // but just because the transaction is contractually valid doesn't mean we
            // should sign it. So we override checkTransaction to add extra constraints.
            override fun checkTransaction(stx: SignedTransaction) {
                // Extra requirements we impose for ourselves as the contract is still valid,
                // but we want to ensure we get the best deal for us.
                requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an IOU transaction." using (output is IOUState)
                    val iou = output as IOUState
                    "The IOU's value can't be too high." using (iou.value < 100)
                }
            }
        }

        subFlow(signTransactionFlow)
    }
}

// ***********
// * Plugins *
// ***********
class TemplateWebPlugin : WebServerPluginRegistry {
    // A list of classes that expose web JAX-RS REST APIs.
    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::TemplateApi))
    //A list of directories in the resources directory that will be served by Jetty under /web.
    // This template's web frontend is accessible at /web/template.
    override val staticServeDirs: Map<String, String> = mapOf(
            // This will serve the templateWeb directory in resources to /web/template
            "template" to javaClass.classLoader.getResource("templateWeb").toExternalForm()
    )
}

// Serialization whitelist.
class TemplateSerializationWhitelist : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf(TemplateData::class.java)
}

// This class is not annotated with @CordaSerializable, so it must be added to the serialization whitelist, above, if
// we want to send it to other nodes within a flow.
data class TemplateData(val payload: String)
