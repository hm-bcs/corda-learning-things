package com.template

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndContract
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.randomOrNull
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.serialization.SerializationWhitelist
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.webserver.services.WebServerPluginRegistry
import sun.security.provider.certpath.OCSPResponse
import java.util.function.Function
import javax.print.DocFlavor
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

// *****************
// * API Endpoints *
// *****************
//val SERVICE_NAMES = listOf("Controller", "Network Map Service")
//
//@Path("template")
//class TemplateApi(val rpcOps: CordaRPCOps) {
//    private val myLegalName: CordaX500Name = rpcOps.nodeInfo().legalIdentities.first().name
//    // Accessible at /api/template/templateGetEndpoint.
//    @GET
//    @Path("templateGetEndpoint")
//    @Produces(MediaType.APPLICATION_JSON)
//    fun templateGetEndpoint(): Response {
//        return Response.ok("Template GET endpoint.").build()
//    }
//
//    // /api/template/getPeers
//    @GET
//    @Path("getPeers")
//    @Produces(MediaType.APPLICATION_JSON)
//    fun getPeers(): Map<String, List<CordaX500Name>> {
//        val nodeInfo = rpcOps.networkMapSnapshot()
//        // all peers which is not current organisation, or Controller/Network Map Service
//        return mapOf("peers" to nodeInfo
//                .map { it.legalIdentities.first().name }
//                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
//    }
//
//    @GET
//    @Path("me")
//    @Produces(MediaType.APPLICATION_JSON)
//    fun whoami() = mapOf("me" to myLegalName)
//
//    @GET
//    @Path("Harrisons")
//    @Produces(MediaType.APPLICATION_JSON)
//    fun getHarrisons() = rpcOps.vaultQueryBy<HarrisonState>().states
//
//    @PUT
//    @Path("issue-harrison")
//    fun issueHarrison(@QueryParam("value") value : Int,
//                      @QueryParam("bank") bankString : String) : Response {
//
//        val bankParty: CordaX500Name = CordaX500Name.parse(bankString)
//        // Checks
//        if(value < 0) {
//            return Response.status(Response.Status.BAD_REQUEST).entity("Value must not be negative.\n").build()
//        }
//        if(value % 42 != 0) {
//            return Response.status(Response.Status.BAD_REQUEST).entity("Value must be divisible by 42.\n").build()
//        }
//        val bank = rpcOps.wellKnownPartyFromX500Name(bankParty) ?:
//                return Response.status(Response.Status.BAD_REQUEST).entity("Party named $bankParty cannot be found.\n").build()
//
//        //
//        return try {
//            val flowHandle = rpcOps.startTrackedFlow(::HarrisonIssueRequestFlow, value, bank)
//            flowHandle.progress.subscribe{ println(">> $it") }
//
//            val result = flowHandle.returnValue.getOrThrow()
//
//            Response.status(Response.Status.CREATED).entity("Transaction id ${result.id} committed to ledger.\n").build()
//        } catch (ex: Throwable) {
//            // log me
//            Response.status(Response.Status.BAD_REQUEST).entity(ex.message!!).build()
//        }
//    }
//}

// *********
// * Flows *
// *********
@InitiatingFlow
@StartableByRPC
class HarrisonIssueRequestFlow(val harrisonValue: Int, val otherParty: Party) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {
        val notary = serviceHub.networkMapCache.notaryIdentities.randomOrNull()

        val txBuilder = TransactionBuilder(notary = notary)

        val outputState = HarrisonState(harrisonValue, ourIdentity, otherParty)
        val outputContractAndState = StateAndContract(outputState, HARRISON_ISSUANCE_CONTRACT_ID)
        val cmd = Command(HarrisonIssueContract.Create(), listOf(ourIdentity.owningKey, otherParty.owningKey))

        txBuilder.withItems(outputContractAndState, cmd)

        txBuilder.verify(serviceHub)

        val signedTx = serviceHub.signInitialTransaction(txBuilder)

        val otherpartySession = initiateFlow(otherParty)

        val fullySignedTx = subFlow(CollectSignaturesFlow(signedTx, listOf(otherpartySession), CollectSignaturesFlow.tracker()))

        return subFlow(FinalityFlow(fullySignedTx))
    }
}

// *******************
// * Flow Responders *
// *******************

@InitiatedBy(HarrisonIssueRequestFlow::class)
class HarrisonIssueResponderFlow(val otherPartySession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val signTransactionFlow = object : SignTransactionFlow(otherPartySession, SignTransactionFlow.tracker()) {
            override fun checkTransaction(stx: SignedTransaction) {
                requireThat {
                    val output = stx.tx.outputs.single().data
                    "Output must be HarrisonState object" using (output is HarrisonState)
                    val state = output as HarrisonState
                    "Value must be divisible by 42" using (state.value % 42 == 0)
                }
            }
        }
        return subFlow(signTransactionFlow)
    }
}

// ***********
// * Plugins *
// ***********
//class TemplateWebPlugin : WebServerPluginRegistry {
//    // A list of classes that expose web JAX-RS REST APIs.
//    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::TemplateApi))
//    //A list of directories in the resources directory that will be served by Jetty under /web.
//    // This template's web frontend is accessible at /web/template.
//    override val staticServeDirs: Map<String, String> = mapOf(
//            // This will serve the templateWeb directory in resources to /web/template
//            "template" to javaClass.classLoader.getResource("templateWeb").toExternalForm()
//    )
//}

// Serialization whitelist.
class TemplateSerializationWhitelist : SerializationWhitelist {
    override val whitelist: List<Class<*>> = listOf(TemplateData::class.java)
}

// This class is not annotated with @CordaSerializable, so it must be added to the serialization whitelist, above, if
// we want to send it to other nodes within a flow.
data class TemplateData(val payload: String)
