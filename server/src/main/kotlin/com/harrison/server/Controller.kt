package com.harrison.server

import com.harrison.HarrisonIssueRequestFlow
import com.harrison.HarrisonState
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpServletRequest

private const val CONTROLLER_NAME = "config.controller.name"
val SERVICE_NAMES = listOf("Controller", "Network Map Service")

@RestController
@RequestMapping("template")
class Controller (
        private val rpc: NodeRPCConnection,
        @Value("\${config.rpc.port}") val rpcPort: Int,
        @Value("\${$CONTROLLER_NAME}") private val controllerName: String) {

    private val myLegalName: CordaX500Name = rpc.proxy.nodeInfo().legalIdentities.first().name

    @CrossOrigin
    @GetMapping("/getPeers")
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = rpc.proxy.networkMapSnapshot() //rpcOps.networkMapSnapshot()
        // all peers which is not current organisation, or Controller/Network Map Service
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }

    @CrossOrigin
    @GetMapping("/me")
    fun whoami() = mapOf("me" to myLegalName)

    @CrossOrigin
    @GetMapping("/harrisons")
    fun getHarrisons() : List<Map<String, String>> {
        val stateAndRefs = rpc.proxy.vaultQueryBy<HarrisonState>().states
        val states = stateAndRefs.map { it.state.data }
        return states.map { it.toJson() }
    }

    @CrossOrigin
    @PostMapping(value = "/issue-harrison", consumes=[MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    private fun requestHarrisonIssuance(request: HttpServletRequest): ResponseEntity<String> {
        val bankString = request.getParameter("bank")
        val value = request.getParameter("value").toInt()
        val bankParty: CordaX500Name = CordaX500Name.parse(bankString)
        // Checks
        if(value < 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Value must not be negative.\n")
        }
        if(value % 42 != 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Value must be divisible by 42.\n")
        }
        val bank = rpc.proxy.wellKnownPartyFromX500Name(bankParty) ?:
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Party named $bankParty cannot be found.\n")


        return try {
            val flowHandle = rpc.proxy.startTrackedFlow(::HarrisonIssueRequestFlow, value, bank)
            flowHandle.progress.subscribe{ println(">> $it") }

            val result = flowHandle.returnValue.getOrThrow()

            ResponseEntity.status(HttpStatus.CREATED).body("Transaction id ${result.id} committed to ledger.\n")
        } catch (ex: Throwable) {
            // log me
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.message!!)
        }
    }

    // Converting state to json
    private fun HarrisonState.toJson(): Map<String, String> {
        return mapOf("owner" to owner.name.organisation, "issuer" to issuer.name.toString(), "value" to value.toString())
    }

}
