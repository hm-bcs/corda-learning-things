package com.harrison

import net.corda.testing.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class ContractTests {

    @Before
    fun setup() {
        setCordappPackages("com.harrison")
    }

    @After
    fun tearDown() {
        unsetCordappPackages()
    }

    @Test
    fun emptyLedger() {
        ledger {  }
    }

    @Test
    fun `Issuance Transaction must have a create command`() {
        val money = 5
        ledger {
            // transactions can hold input, output, command, and attachment
            transaction {
                //                                                    value    owner     issuer
                output(HARRISON_ISSUANCE_CONTRACT_ID) { HarrisonState(money, MINI_CORP, MEGA_CORP )}
                fails()
                command(MINI_CORP_PUBKEY, MEGA_CORP_PUBKEY) {HarrisonIssueContract.Create()}
                verifies()
            }
        }
    }

    @Test
    fun `Issuance Transaction must have no inputs`() {
        val money = 5
        ledger {
            transaction {
                input(HARRISON_ISSUANCE_CONTRACT_ID) { HarrisonState(money, MINI_CORP, MEGA_CORP)}
                output(HARRISON_ISSUANCE_CONTRACT_ID) { HarrisonState(money, MINI_CORP, MEGA_CORP)}
                command(MINI_CORP_PUBKEY, MEGA_CORP_PUBKEY) { HarrisonIssueContract.Create() }
                `fails with`("No inputs should be consumed")
            }
        }
    }

    @Test
    fun `Issuance Transaction must have one output`() {
        val money = 5
        ledger {
            transaction {
                output(HARRISON_ISSUANCE_CONTRACT_ID) { HarrisonState(money, MINI_CORP, MEGA_CORP) }
                output(HARRISON_ISSUANCE_CONTRACT_ID) { HarrisonState(money, MINI_CORP, MEGA_CORP)}
                command(MINI_CORP_PUBKEY, MEGA_CORP_PUBKEY) { HarrisonIssueContract.Create() }
                `fails with`("Only one output")
            }
        }
    }

    @Test
    fun `Issuance Transaction value must not be negative`() {
        val money = -5
        ledger {
            transaction {
                output(HARRISON_ISSUANCE_CONTRACT_ID) { HarrisonState(money, MINI_CORP, MEGA_CORP)}
                command(MINI_CORP_PUBKEY, MEGA_CORP_PUBKEY) { HarrisonIssueContract.Create() }
                `fails with`("Non negative value")
            }
        }
    }

    @Test
    fun `Issuance Transaction requires two signers`() {
        val money = 5
        ledger {
            transaction {
                output(HARRISON_ISSUANCE_CONTRACT_ID) { HarrisonState(money, MINI_CORP, MEGA_CORP)}
                command(MINI_CORP_PUBKEY) { HarrisonIssueContract.Create() }
                `fails with`("Must be two signers")
            }
            transaction {
                output(HARRISON_ISSUANCE_CONTRACT_ID) { HarrisonState(money, MINI_CORP, MEGA_CORP)}
                command(MEGA_CORP_PUBKEY) { HarrisonIssueContract.Create() }
                `fails with`("Must be two signers")
            }
        }
    }

    @Test
    fun `Issuance Transaction requires issuer and owner to be signers`() {
        val money = 5
        ledger {
            transaction {
                output(HARRISON_ISSUANCE_CONTRACT_ID) { HarrisonState(money, MINI_CORP, MEGA_CORP)}
                command(MINI_CORP_PUBKEY, ALICE_PUBKEY) { HarrisonIssueContract.Create() }
                `fails with`("Signers must be issuer and receiver")
            }
            transaction {
                output(HARRISON_ISSUANCE_CONTRACT_ID) { HarrisonState(money, MINI_CORP, MEGA_CORP)}
                command(ALICE_PUBKEY, MEGA_CORP_PUBKEY) { HarrisonIssueContract.Create() }
                `fails with`("Signers must be issuer and receiver")
            }
            transaction {
                output(HARRISON_ISSUANCE_CONTRACT_ID) { HarrisonState(money, MINI_CORP, MEGA_CORP)}
                command(ALICE_PUBKEY, BOB_PUBKEY) { HarrisonIssueContract.Create() }
                `fails with`("Signers must be issuer and receiver")
            }
        }
    }
}