/**
The MIT License (MIT)
Copyright (c) 2018 AroDev, adaptation portions (c) 2018 ProgrammerDan (Daniel Boston)

www.arionum.com

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of
the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
OR OTHER DEALINGS IN THE SOFTWARE.

 */
package com.programmerdan.arionum.arionum_miner;

import java.math.BigInteger;

/**
 * Abstraction layer to allow multiple miner definitions.
 * 
 * @author ProgrammerDan (Daniel Boston)
 */
public abstract class Hasher implements Runnable{

	protected Miner parent;

	public void run() {
		try {
			active = true;
			go();
			active = false;
		} catch (Throwable e) {
			System.err.println("Detected thread " + Thread.currentThread().getName() + " death due to error: " + e.getMessage());
			e.printStackTrace();
			
			System.err.println("\n\nThis is probably fatal, so exiting now.");
			System.exit(1);
		}
		HasherStats stats = new HasherStats(id, argonTime, shaTime, nonArgonTime, hashTime, hashCount, bestDL, shares, finds, getType());
		parent.workerFinish(stats, this);
	}
	
	/**
	 * Instead of run, go -- since we now wrap run() into a catch block since our Executors don't support UncaughtExceptions in an intuitive way
	 */
	public abstract void go();
	
	/**
	 * If some condition exists where this should die, kill it.
	 */
	public abstract void kill();

	protected boolean active;
	protected String id;
	protected long hashCount;
	protected long targetHashCount;
	protected long hashBegin;
	protected long hashEnd;
	protected long hashTime;
	protected long maxTime; 
	protected long blockHeight;
	
	// local copy of data, updated "off thread"
	protected BigInteger difficulty;
	protected String difficultyString;
	protected String data;
	protected int hashBufferSize;
	protected long limit;
	protected String publicKey;
	protected String argonString;
	protected boolean pause;
	protected int iters;
	protected int mem;
	protected int threads;
	
	// local stats stores, retrieved "off thread"
	protected long bestDL;
	protected long shares;
	protected long finds;
	
	/* Timing Stats, current */
	protected long loopTime;
	protected long argonTime;
	protected long shaTime;
	protected long nonArgonTime;

	public Hasher(Miner parent, String id, long target, long maxTime) {
		super();
		this.parent = parent;
		this.id = id;
		this.active = false;
		this.hashCount = 0l;
		this.targetHashCount = target;
		this.maxTime = maxTime;
	}

	public void completeSession() {
		// emulate shutdown / restart, but on dedi thread so don't.
		HasherStats stats = new HasherStats(id, argonTime, shaTime, nonArgonTime, hashTime, hashCount, bestDL, shares, finds, getType());
		argonTime = 0l;
		shaTime = 0l;
		nonArgonTime = 0l;
		hashTime = 0l;
		hashCount = 0l;
		bestDL = Long.MAX_VALUE;
		shares = 0l;
		finds = 0l;
		long[] sessionUpd = parent.sessionFinish(stats, this);
		this.targetHashCount = sessionUpd[0];
		this.maxTime = sessionUpd[1];
	}

	public String getID() {
		return this.id;
	}
	
	public long getBestDL() {
		return bestDL;
	}
	
	public long getShares() {
		return shares;
	}
	
	public long getFinds() {
		return finds;
	}
	
	public long getArgonTime() {
		return argonTime;
	}
	
	public long getShaTime() {
		return shaTime;
	}
	
	public long getNonArgonTime() {
		return nonArgonTime;
	}
	
	public long getLoopTime() {
		return loopTime;
	}
	
	public long getHashTime() {
		return this.hashTime;
	}
	
	public void update(BigInteger difficulty, String data, long limit, String publicKey, long blockHeight,
			boolean pause, int iters, int mem, int threads) {
		this.difficulty = difficulty;
		this.difficultyString = difficulty.toString();
		if (!data.equals(this.data)) {
			bestDL = Long.MAX_VALUE;
		}
		this.data = data;
		this.hashBufferSize = 280 + this.data.length();
		this.limit = limit;
		this.publicKey = publicKey;
		if (blockHeight != this.blockHeight) {
			newHeight(this.blockHeight, blockHeight);
		}
		this.blockHeight = blockHeight;
		
		boolean doreinit = false;
		
		if (this.iters != iters || this.mem != mem || this.threads != threads) {
			//System.out.println("hasher: reinit to " + mem);
			doreinit = true;
		}
		this.pause = pause;
		this.iters = iters;
		this.mem = mem;
		this.threads = threads;
	}
	
	/**
	 * Called to let hashers handle new block heights in a flexible fashion. This will allow
	 * us to accommodate hard forks easily.
	 * 
	 * @param oldBlockHeight
	 * @param newBlockHeight
	 * 
	 */
	public abstract void newHeight(long oldBlockHeight, long newBlockHeight);

	/**
	 * Lets the hasher return a "type" classification. Should NOT be null.
	 * @return
	 */
	public abstract String getType();
	
	public long getHashes() {
		return this.hashCount;
	}

	public boolean isActive() {
		return active;
	}

}
