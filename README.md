# corda-learning-things
Cool repo.

`gradle deployNodes`

Issue from Corporation to Bank of Harrison<br>
`start HarrisonIssueRequestFlow harrisonValue: 84, otherParty: "O=BankOfHarrison,L=Tokyo,C=JP"`

Check vault on Corporation<br>
`run vaultQuery contractStateType: com.template.HarrisonState`
