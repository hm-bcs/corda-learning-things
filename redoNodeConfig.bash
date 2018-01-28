#!/bin/bash
cp ./nodeConfigs/bank.conf build/nodes/BankOfHarrison/node.conf
cp ./nodeConfigs/org.conf build/nodes/Organisation/node.conf
rm -r build/nodes/Controller