Extended Documentation for Java port of Arionum-miner
==========================

For pool-based mining, full command line flags:

```  java -jar arionum-miner-[version].jar pool [pool address] [wallet address] [# hashers] [hasher core] [colored output]```

**version**: This is the compiled version, usually something like 0.0.7-SNAPSHOT. See the `run-pool.sh` for the correct one based on your compiled version

**pool address**: If you use the main pool, http://aropool.com -- if you host your own pool, use that pool's address.

**wallet address**: Your Wallet address, or the wallet address you want to gain credit for your mining.

**# hashers**: A number greater then 0 indicating how many hashers to run within this single miner instance. Be warned, more hashers does not always lead to higher Hr/s -- experiment with this value to find the best for your system.

**hasher core**: Currently, one of:

    `basic`: a core that is line for line basically the same as the php-miner's hasher

    `debug`: a core that shows runtimes of various subcomponents of the hashers as they run. Very chatty. Not for long term use.

    `stable`: Stable, but drifting from php-parity. This hasher tries to squeeze even more performance out of your CPU cores. Current Default.

    `experimental`:  Bleeding edge. Might be faster, might have tradeoffs, no warrantees.

**colored output**: Only supported in linux, set to true to add some color to the output of the statistic updates.

--------------------

For solo mining, full command line flags:

```  java -jar arionum-miner-[version].jar solo [node address] [public-key] [private-key] [# hashers] [hasher core] [colored output]```

**version**: This is the compiled version, usually something like 0.0.7-SNAPSHOT. See the `run-pool.sh` for the correct one based on your compiled version

**node address**: The address of the node you want to directly send discovered blocks

**public key**: Your Wallet address, or the wallet address you want to gain credit for your mining.

**private key**: Private key to use to sign the payout transaction.

**# hashers**: A number greater then 0 indicating how many hashers to run within this single miner instance. Be warned, more hashers does not always lead to higher Hr/s -- experiment with this value to find the best for your system.

**hasher core**: Currently, one of:

    `basic`: a core that is line for line basically the same as the php-miner's hasher

    `debug`: a core that shows runtimes of various subcomponents of the hashers as they run. Very chatty. Not for long term use.

    `stable`: Stable, but drifting from php-parity. This hasher tries to squeeze even more performance out of your CPU cores. Current Default.

    `experimental`:  Bleeding edge. Might be faster, might have tradeoffs, no warrantees.

**colored output**: Only supported in linux, set to true to add some color to the output of the statistic updates.

Be sure you trust the node you are using; and be aware that the hasher's host needs to be specifically allowed by the node you are connecting to.

Be aware that at present, all data is sent in the clear -- you might want to use a secure VPN or exclusively mine towards local network nodes.
