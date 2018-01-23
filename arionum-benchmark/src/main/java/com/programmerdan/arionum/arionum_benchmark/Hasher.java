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
package com.programmerdan.arionum.arionum_benchmark;

import java.math.BigInteger;

/**
 * Abstraction layer to allow multiple miner definitions.
 * 
 * 
 * @author ProgrammerDan (Daniel Boston)
 *
 */
public class Hasher implements Runnable {

	protected BigInteger difficulty = BigInteger.valueOf(100000000);
	protected String difficultyString = "100000000";
	protected String data = "sampledatafornode";
	protected int hashBufferSize;
	protected long limit = 50000;
	protected String publicKey = "4SxMLLWRCxoL42gqgf2C2x5T5Q1BJhgfQvMsfwrcFAu4owsBYncsvVnBBVqtxVrkqHVZ8nJGrcJB7yWnV92j5Rca";
	
	// local stats stores, retrieved "off thread"
	protected long shares;
	protected long finds;
	
	/* Timing Stats, current */
	protected long hashCount;
	
	protected boolean active;
	
	public static void main(String[] args) {
		if (args == null && args.length < 1) {
			System.err.println("Pass in core to test: stable | experimental | basic");
			System.err.println();
			System.exit(1);
		}
		
		Hasher hasher = null;
		AdvMode which = AdvMode.valueOf(args[0]);
		switch (which) {
		case basic:
			hasher = new BasicHasher();
			break;
		case experimental:
			hasher = new ExperimentalHasher();
			break;
		case stable:
			hasher = new StableHasher();
			break;
		}
		
		hasher.run();
		
	}
	
	@Override
	public void run() {}

	public Hasher() {
		this.hashCount = 0l; 
	}

	public long getHashes() {
		return this.hashCount;
	}

	public boolean isActive() {
		return active;
	}

}