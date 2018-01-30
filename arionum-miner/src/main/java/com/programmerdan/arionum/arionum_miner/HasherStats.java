package com.programmerdan.arionum.arionum_miner;

public class HasherStats {
	public long argonTime;
	public long shaTime;
	public long nonArgonTime;
	public long hashes;
	public long bestDL;
	public long shares;
	public long finds;
	public long hashTime;
	
	public HasherStats(long argonTime, long shaTime, long nonArgonTime, long hashTime, long hashes, long bestDL, long shares, long finds) {
		this.argonTime = argonTime;
		this.shaTime = shaTime;
		this.nonArgonTime = nonArgonTime;
		this.hashTime = hashTime;
		this.hashes = hashes;
		this.bestDL = bestDL;
		this.shares = shares;
		this.finds = finds;
	}
}
