# corda-learning-things
Cool repo.

`gradle deployNodes`<br>
`gradle test integrationTest smokeTest` with any combination of `test`, `integrationTest` and `smokeTest`

Issue from Corporation to Bank of Harrison<br>
`start HarrisonIssueRequestFlow harrisonValue: 84, otherParty: "O=BankOfHarrison,L=Tokyo,C=JP"`

Check vault on Corporation<br>
`run vaultQuery contractStateType: com.template.HarrisonState`

![Transaction](./images/transaction.png)

### Learning
https://github.com/corda/cordapp-example/blob/release-V2/kotlin-source/src/test/kotlin/com/example/contract/IOUContractTests.kt
