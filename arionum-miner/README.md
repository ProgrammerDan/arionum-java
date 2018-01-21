Arionum Miner port for Java
====================
by ProgrammerDan, Jan 2018
based on https://github.com/arionum/miner a work by AroDev

Originally a fun evening project to port the PHP miner to Java that has become a bit of a passion.

Confirmed working, has been used to find blocks.

Includes a few improvements over the reference implementation, mostly with threaded handling of update requests and submit requests.

That way, the miner threads can keep on mining, and aren't held up by the update / submit cycles. 

Enjoy!

#### To compile

Get depencies

* Maven

* git

* Java 8 SDK (OpenJDK8 and Oracle Java 8 both work just fine) 

Clone this repository, change directory into ```arionum-miner```

Execute ```mvn clean package``` -- this will build the miner

#### To run

##### For Linux:

Execute ```./run-pool.sh [your address]``` where ```[your address]``` is your Arionum wallet address

##### For Windows:

Execute ```./run-pool.bat [your address]``` where ```[your address]``` is your Arionum wallet address

In either case this will run the pool, pointing at http://aropool.com , using your wallet address. By default only a single hasher will spin up and the php-parity core will be used.


##### For solo mining in Linux:

Execute ```./run-solo.sh [node-address] [public-key] [private-key]```

Please note solo hasn't been tested, but it's 99.9% the same code so should be fine. Pool has been extensively tested. 


##### General advice:

Rule of thumb is no more then 1 miner per 4 cores/vcores.

Pull the ```java -jar``` execution line out of the appropriate script to customize it. The .sh and .bat files are regenerated when you rebuild, so any customizations you make to them will be lost.

----------------------
# Comparison to php-miner

Similarities:

* Can be used for solo or pool mining

* Checks for valid address (on pool) before activating

* Command Line tool

* Same hashing functions, same CPU bounds -- both bind to a C library version of argon2i hashing, so both are fast

* Can run multiple instances if desired -- I recommend you use screen, tmux, or byobu for better monitoring

Differences:

* One JVM can run multiple hashers and share the same "update" requests to the pool, reducing traffic to the pool or node

* The hashers never stop -- the reference PHP implementation pauses the hasher during update requests and nonce submissions. The java hashers never stop.

* (Very slight) optimizations -- the Java miner has easy swap support for alternate "core" types, with "basic", "debug" and "experimental" for now; basic is equivalent to php-reference, debug is chatty giving runtime details, and experimental has some improvements to improve the amount of time the hasher spends computing argon2i hashes instead of other things.

* More information on active hashing activity -- screen updates every 15s with details on active hashers, how many hashes they have checked, and how close to finding shares or blocks

* Visual notice when a new block has started, with the "difficulty" for the new block, and your personal best DL for the prior block. Note that difficulty is inverse -- smaller is harder, larger is easier.

---------------------
### TODO

* ~~Add address checking as here: https://github.com/arionum/miner/commit/e14b696362fb79d60c4ff8bc651185740b8021d9 ~~ Done in commit dd5388c

* Colored screen output to better see labels / stats and keep track of hashers

* Auto-adaptive hashing -- automatically add more hasher instances until peak rate is achieved

* Variable intensity -- allow hashing to take a back seat to other computer activity

---------------------
### Known caveates

* For systems with many, many cores (24, 32) it may be necessary to run multiple instances of this miner. Try using multiple miners with a max of 8 hashers each; this has worked well in initial testing.

* Hash Rate is "best effort", and might be imprecise. Pool Hash Rate is based on self-reporting, so minor inaccuracies are not an issue. Difficulty is based on median time between block discovery.

---------------------
### Testing

If you encounter any problems, open an issue here or email me at programmerdan@gmail.com -- I'm also ProgrammerDan#7586 in the Arionum discord -- feel free to PM me. 

---------------------
### Advanced Use

See DOCS.md for advanced use, compilation, and other instructions.
