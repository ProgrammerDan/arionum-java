Arionum-Java
------------------
Arionum is a pretty new cryptocurrency that's largely CPU bound, resisting ASIC and GPU optimizations. This is a perfect coin to mine for people with GPU hardware doing other mining but low utilization of CPU resources.

It caught my eye and as part of code-vetting I figured I might as well do a port to Java and see if I can get some more efficiency out of the code or better interfacing. I'll try as I'm able (pretty rusty with PHP) to also contribute back to the PHP reference implementations.

Learn more here: http://arionum.com

Bitcointalk: https://bitcointalk.org/index.php?topic=2710248.0

Block explorer: https://arionum.info/

Send Arionum donations here: 4SxMLLWRCxoL42gqgf2C2x5T5Q1BJhgfQvMsfwrcFAu4owsBYncsvVnBBVqtxVrkqHVZ8nJGrcJB7yWnV92j5Rca

Send BTC donations here: bc1qucem39g0lamacevmvj7qnkfjh3mqkr0jmlnlt3 (segwit) or 1MXcQyo1jbaTYdWUqeAQumsKUDnytiFAe5 (standard)

Send CureCoin donations here: BFDK84Z29zb4caNMArKBZVBXAxGqGMRMfq (get involved here: https://curecoin.net/ -- works great alongside Arionum)

Send FoldingCoin donations here: 1HH3SQNf1KPfsAGC5BAvDj849oHoAsYEms (get involved here: https://foldingcoin.net/ -- mergefold w/ curecoin!)

Thanks, this looks to be a super fun project.

---------------
# Arionum-miner

This is a slightly optimized miner, coded in Java, and based very strongly on the php reference version.

Major differentiators:

* One JVM can run multiple hashers and share the same "update" requests to the pool, reducing traffic to the core

* The hashers never stop -- the reference PHP implementation pauses the hasher during update requests and nonce submissions. The java hashers never stop.

* Optimizations -- the Java miner has easy swap support for alternate "core" types, with "standard", "experimental" and "legacy" for now; standard (or "enhanced") is the current strong recommendation, it has better adaptive behavior and some optimizations to reduce rejections, identify bad or reduced capability memory sectors and generally focus on accepted hashes, not just hashrate (although hashrate is great too). All other cores represent prior or lower performing cores.

For more details, see the arionum forum thread here:  https://forum.arionum.com/viewtopic.php?f=11&t=28

I hope you enjoy. See the README.md in the arionum-miner subfolder for details on compiling and running.
