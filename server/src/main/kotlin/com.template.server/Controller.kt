package com.template.server

import com.template.HarrisonIssueRequestFlow
import com.template.HarrisonState
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest
import javax.ws.rs.Consumes
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

private const val CONTROLLER_NAME = "config.controller.name"
val SERVICE_NAMES = listOf("Controller", "Network Map Service")

@RestController
@RequestMapping("template")
class Controller (
        private val rpc: NodeRPCConnection,
        @Value("\${config.rpc.port}") val rpcPort: Int,
        @Value("\${$CONTROLLER_NAME}") private val controllerName: String) {

    private val myLegalName: CordaX500Name = rpc.proxy.nodeInfo().legalIdentities.first().name

    @GetMapping("/getPeers")
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = rpc.proxy.networkMapSnapshot() //rpcOps.networkMapSnapshot()
        // all peers which is not current organisation, or Controller/Network Map Service
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }

    @GetMapping("/me")
    fun whoami() = mapOf("me" to myLegalName)

    @GetMapping("/harrisons")
    fun getHarrisons() : List<Map<String, String>> {
        val stateAndRefs = rpc.proxy.vaultQueryBy<HarrisonState>().states
        val states = stateAndRefs.map { it.state.data }
        return states.map { it.toJson() }
    }

//    @PutMapping("issue-harrison")
//    fun issueHarrison(@QueryParam("value") value : Int,
//                      @QueryParam("bank") bankString : String) : Response {
    @PostMapping(value = "/issue-harrison")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    private fun requestHarrisonIssuance(request: HttpServletRequest): Response {
        val bankString = request.getParameter("bank")
        val value = request.getParameter("value").toInt()
        val bankParty: CordaX500Name = CordaX500Name.parse(bankString)
        // Checks
        if(value < 0) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Value must not be negative.\n").build()
        }
        if(value % 42 != 0) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Value must be divisible by 42.\n").build()
        }
        val bank = rpc.proxy.wellKnownPartyFromX500Name(bankParty) ?:
                return Response.status(Response.Status.BAD_REQUEST).entity("Party named $bankParty cannot be found.\n").build()


        return try {
            val flowHandle = rpc.proxy.startTrackedFlow(::HarrisonIssueRequestFlow, value, bank)
            flowHandle.progress.subscribe{ println(">> $it") }

            val result = flowHandle.returnValue.getOrThrow()

            Response.status(Response.Status.CREATED).entity("Transaction id ${result.id} committed to ledger.\n").build()
        } catch (ex: Throwable) {
            // log me
            Response.status(Response.Status.BAD_REQUEST).entity(ex.message!!).build()
        }
    }

    // Converting state to json
    private fun HarrisonState.toJson(): Map<String, String> {
        return mapOf("owner" to owner.name.organisation, "issuer" to issuer.name.toString(), "value" to value.toString())
    }

}
