Extended Documentation for Java port of Arionum-miner
==========================

# Installation

Choose which image you want to install from the downloads below.

If your hardware supports AVX or AVX2 and you are a Windows user, download the correctly named EXE and run. If your system does not support it, you will know on launch as it will fail noisily. If you know your system supports AVX512F, please contact me on Discord. All prior testing of AVX512F builds on Windows were unsuccessful, but I can work with you to see what we can accomplish. 

If you see crazy high hashrates, you are using 32bit java and these programs will _not_ function. Immediately shut off miner and go here:  http://www.oracle.com/technetwork/java/javase/downloads/jre8-downloads-2133155.html and download a 64 bit jre.

# Release details:

* Replaced `standard` and `enhanced` hashers with a hasher that trades a very small (0.1% or so) hashrate for significantly improved accept rate, as the method of creating salts and nonces matches the code that will verify them. In addition, improved affinity and memory handling quickly moves away from memory that results in rejected hashes; only "successful" memory is kept, and its kept for a long time.

* Better handling in general of the differences in affinity and memory handling between Windows and Linux

# General Details:

* Auto-adapt to block 10800-hf-resistance to remain compliance with primary node code.

* Re-architected to use CPU affinity and single-threaded argon, so that each core can be pegged individually.

* Improved stats, including colorized display for Windows and \*nix

* Advanced installation options for maximizing performance.

## Advanced installation:

### Windows:

1. Install Maven, git, and Java 8 JDK (minimum). 

2. Clone this repository locally, and navigate to `arionum-miner` folder.

3. Run `mvn clean package`

4. Run in a Command Prompt: `pick-argon.bat` -- it will ask for Administrator escalation, this is to check for hardware features, please accept.

5. Run miner using `run.bat`.

Alternatively, if you know that your CPU supports AVX2 or AVX instructions, download the appropriate pre-built .exe here.

If you are truly a glutton for punishment, you can download Visual Studio, import the argon2i library that I host here: http://github.com/ProgrammerDan/arionum-argon2 import it into visual studio, and build it locally. No warrantees, but you can use the Static Release profiles.


### Linux:

1. Install Maven, git, 64bit Java 8 JDK, make, and gcc.

2. Clone this repository locally, and navigate to `arionum-miner` folder.

3. Run `mvn clean package`

4. Run `chmod +x build-argon.sh`

5. Run `build-argon.sh`

6. Run miner using `run.sh` -- this will use the locally built high-performance argon2i libraries automatically.

For Linux users, I _strongly_ recommend using these instructions.

---------------------------

# Advanced Runtime Support

New as of 0.1.0 and later versions, just run the miner! Don't mess with command line flags.

If you want to use command line flags, however, follow along:

----------------------------

For multi-preconfigs, simply pass the name of the config file as the first and only command line flag:

``` java -jar arionum-miner-java.jar my-custom-config.cfg```

And if that config doesn't exist, it will create it with the answers you give to the prompts. If it does exist, it will load it and use it.


----------------------------

For pool-based mining, full command line flags:

```  java -jar arionum-miner-java.jar pool [pool address] [wallet address] [# hashers] [hasher core] [colored output]```

**pool address**: If you use the main pool, http://aropool.com -- if you host your own pool, use that pool's address.

**wallet address**: Your Wallet address, or the wallet address you want to gain credit for your mining.

**# hashers**: A number greater then 0 indicating how many hashers to run within this single miner instance. Be warned, more hashers does not always lead to higher Hr/s -- experiment with this value to find the best for your system.

**hasher core**: Currently, one of:

    `standard`: Stable, best, most improved with best speed AND acceptance.

    `experimental`: Less handling for affinity and memory, use standard.

**colored output**: Only supported in linux, set to true to add some color to the output of the statistic updates. Note, this isn't working yet.

--------------------

For solo mining, full command line flags:

```  java -jar arionum-miner-java.jar solo [node address] [public-key] [private-key] [# hashers] [hasher core] [colored output]```

**node address**: The address of the node you want to directly send discovered blocks

**public key**: Your Wallet address, or the wallet address you want to gain credit for your mining.

**private key**: Private key to use to sign the payout transaction.

**# hashers**: A number greater then 0 indicating how many hashers to run within this single miner instance. Be warned, more hashers does not always lead to higher Hr/s -- experiment with this value to find the best for your system.

**hasher core**: Currently, one of:

    `standard`: Stable, best, most improved with best speed AND acceptance.

    `experimental`: Less handling for affinity and memory, use standard.

**colored output**: Only supported in linux, set to true to add some color to the output of the statistic updates.

Be sure you trust the node you are using; and be aware that the hasher's host needs to be specifically allowed by the node you are connecting to.

Be aware that at present, all data is sent in the clear -- you might want to use a secure VPN or exclusively mine towards local network nodes.
