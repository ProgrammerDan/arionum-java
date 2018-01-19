Arionum Miner port for Java
====================
by ProgrammerDan, Jan 2018
based on https://github.com/arionum/miner a work by AroDev


Fun evening project to port the PHP miner to Java. Confirmed working, has been used to find blocks.

Includes a few improvements over the reference implementation, mostly with threaded handling of update requests and submit requests.

That way, the miner threads can keep on mining, and aren't held up by the update / submit cycles. 

Enjoy!

To run:

java -jar arionum-miner.jar pool http://aropool.com CHANGEADDRESS [# miners]

or

java -jar arionum-miner.jar solo node-address public-key private-key [# miners]

Please note solo hasn't been tested, but it's 99.9% the same code so should be fine. Pool has been extensively tested. Rule of thumb is no more then 1 miner per 4 cores/vcores. Leave [# miners] off to default to 1.


---------------------
### TODO

* Add address checking as here: https://github.com/arionum/miner/commit/e14b696362fb79d60c4ff8bc651185740b8021d9
