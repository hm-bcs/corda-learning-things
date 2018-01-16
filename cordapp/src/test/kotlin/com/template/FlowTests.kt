package com.template

import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.flows.FlowException
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetwork.MockNode
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import net.corda.core.transactions.SignedTransaction
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FlowTests {
    lateinit var network: MockNetwork
    lateinit var a: StartedNode<MockNetwork.MockNode>
    lateinit var b: StartedNode<MockNetwork.MockNode>

    @Before
    fun setup() {
        setCordappPackages("com.template")
        network = MockNetwork()
        val nodes = network.createSomeNodes(2)
        a = nodes.partyNodes[0]
        b = nodes.partyNodes[1]
        nodes.partyNodes.forEach {
            it.registerInitiatedFlow(HarrisonIssueResponderFlow::class.java)
        }
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
        unsetCordappPackages()
    }

    @Test
    fun `Flow should reject negative value`() {
        val money = -5
        val flow = HarrisonIssueRequestFlow(money, b.info.chooseIdentity())
        val future = a.services.startFlow(flow).resultFuture
        network.runNetwork()

        assertFailsWith<TransactionVerificationException> { future.getOrThrow() }
    }

    @Test
    fun `Flow should reject values not divisible by 42`() {
        val money = 40
        val flow = HarrisonIssueRequestFlow(money, b.info.chooseIdentity())
        val future = a.services.startFlow(flow).resultFuture
        network.runNetwork()

        assertFailsWith<FlowException> { future.getOrThrow() }
    }

    @Test
    fun `Signed Transaction returned by flow is signed by the Initiator`() {
        val money = 42
        val flow = HarrisonIssueRequestFlow(money, b.info.chooseIdentity())
        val future = a.services.startFlow(flow).resultFuture
        network.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignaturesExcept(b.info.chooseIdentity().owningKey)
    }

    @Test
    fun `Signed Transaction returned by flow is signed by the Acceptor`() {
        val money = 42
        val flow = HarrisonIssueRequestFlow(money, b.info.chooseIdentity())
        val future = a.services.startFlow(flow).resultFuture
        network.runNetwork()

        val signedTx = future.getOrThrow()
        signedTx.verifySignaturesExcept(a.info.chooseIdentity().owningKey)
    }

    @Test
    fun `Transaction is stored in both parties transaction storage`() {
        val money = 42
        val flow = HarrisonIssueRequestFlow(money, b.info.chooseIdentity())
        val future = a.services.startFlow(flow).resultFuture
        network.runNetwork()

        val signedTx = future.getOrThrow()

        assertEquals(signedTx, a.services.validatedTransactions.getTransaction(signedTx.id))
        assertEquals(signedTx, b.services.validatedTransactions.getTransaction(signedTx.id))
    }

    @Test
    fun `Transaction has no inputs, and one output`() {
        val money = 42
        val flow = HarrisonIssueRequestFlow(money, b.info.chooseIdentity())
        val future = a.services.startFlow(flow).resultFuture
        network.runNetwork()

        val signedTx = future.getOrThrow()

        for(node in listOf(a, b)) {
            val recordedTx = node.services.validatedTransactions.getTransaction(signedTx.id)
            val txOutputs = recordedTx!!.tx.outputs
            assert(txOutputs.size == 1)

            val recordedState = txOutputs[0].data as HarrisonState
            assertEquals(recordedState.value, money)
            assertEquals(recordedState.owner, a.info.chooseIdentity())
            assertEquals(recordedState.issuer, b.info.chooseIdentity())
        }
    }

    @Test
    fun `Correct HarrisonState recorded in vault`() {
        val money = 42
        val flow = HarrisonIssueRequestFlow(money, b.info.chooseIdentity())
        val future = a.services.startFlow(flow).resultFuture
        network.runNetwork()
        future.getOrThrow()

        for(node in listOf(a, b)) {
            node.database.transaction {
                val states = node.services.vaultService.queryBy<HarrisonState>().states
                assertEquals(1, states.size)
                val recordedState = states.single().state.data
                assertEquals(recordedState.value, money)
                assertEquals(recordedState.owner, a.info.chooseIdentity())
                assertEquals(recordedState.issuer, b.info.chooseIdentity())
            }
        }
    }
}
