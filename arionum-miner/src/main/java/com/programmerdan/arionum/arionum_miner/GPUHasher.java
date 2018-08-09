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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Base64.Encoder;

//import com.programmerdan.arionum.arionum_miner.jna.*;

/**
 * Proof of concept GPU miner. Once I figure out how.
 * Someday...
 * 
 * @author ProgrammerDan (Daniel Boston)
 *
 */
public class GPUHasher extends Hasher {

	public GPUHasher(Miner parent, String id, long target, long maxTime) {
		super(parent, id, target, maxTime);
	}
	
	private SecureRandom random = new SecureRandom();
	private Encoder encoder = Base64.getEncoder();
	private String rawHashBase;
	private byte[] nonce = new byte[32];
	private String rawNonce;
	
	@Override
	public void update(BigInteger difficulty, String data, long limit, String publicKey, long blockHeight,
			boolean pause, int iters, int mem, int threads) {
		super.update(difficulty, data, limit, publicKey, blockHeight, pause, iters, mem, threads);
		
		genNonce();
	}
	
	@Override
	public void newHeight(long oldH, long newH) {}
	
	/**
	 */
	private void genNonce() {
		String encNonce = null;
		StringBuilder hashBase;
		random.nextBytes(nonce);
		encNonce = encoder.encodeToString(nonce);

		char[] nonceChar = encNonce.toCharArray();

		// shaves a bit off vs regex -- for this operation, about 50% savings
		StringBuilder nonceSb = new StringBuilder(encNonce.length());
		for (char ar : nonceChar) {
			if (ar >= '0' && ar <= '9' || ar >= 'a' && ar <= 'z' || ar >= 'A' && ar <= 'Z') {
				nonceSb.append(ar);
			}
		}
		
		// prealloc probably saves us 10% on this op sequence
		hashBase = new StringBuilder(hashBufferSize); // size of key + nonce + difficult + argon + data + spacers
		hashBase.append(this.publicKey).append("-");
		hashBase.append(nonceSb).append("-");
		hashBase.append(this.data).append("-");
		hashBase.append(this.difficultyString);
		
		rawNonce = nonceSb.toString();
		rawHashBase = hashBase.toString();
	}
	
	@Override
	public void go() {
		this.parent.hasherCount.incrementAndGet();
		active = true;
		long start = System.currentTimeMillis();

		StringBuilder hashBase = null;
		byte[] byteBase = null;
		
		String argon = null;
		//Argon2 argon2 = Argon2Factory.create(Argon2Types.ARGON2i);

		MessageDigest sha512 = null;
		try {
			sha512 = MessageDigest.getInstance("SHA-512");
		} catch (NoSuchAlgorithmException e1) {
			System.err.println("Unable to find SHA-512 algorithm! Fatal error.");
			e1.printStackTrace();
			System.exit(1);
			active = false;
		}
		if (active) {
			parent.workerInit(id);
			System.out.println(id + "] Spun up EXPERIMENTAL hashing worker in " + (System.currentTimeMillis() - start) + "ms");
		}
		
		long statCycle = 0l;
		long statBegin = 0l;
		long statArgonBegin = 0l;
		long statArgonEnd = 0l;
		long statShaBegin = 0l;
		long statShaEnd = 0l;
		long statEnd = 0l;
		
		while (active) {
			statCycle = System.currentTimeMillis();
			statBegin = System.nanoTime();
			try {
				hashBase = new StringBuilder(this.hashBufferSize);
				statArgonBegin = System.nanoTime();
				// this is all TODO: argon = argon2.hash(4, 16384, 4, rawHashBase);
				statArgonEnd = System.nanoTime();
				hashBase.append(rawHashBase).append(argon);

				byteBase = hashBase.toString().getBytes();
				statShaBegin = System.nanoTime();
				for (int i = 0; i < 5; i++) {
					byteBase = sha512.digest(byteBase);
				}
				byteBase = sha512.digest(byteBase);
				statShaEnd = System.nanoTime();
				
				StringBuilder duration = new StringBuilder(25);
				duration.append(byteBase[10] & 0xFF).append(byteBase[15] & 0xFF).append(byteBase[20] & 0xFF)
						.append(byteBase[23] & 0xFF).append(byteBase[31] & 0xFF).append(byteBase[40] & 0xFF)
						.append(byteBase[45] & 0xFF).append(byteBase[55] & 0xFF);

				long finalDuration = new BigInteger(duration.toString()).divide(this.difficulty).longValue();
				if (finalDuration > 0 && finalDuration <= this.limit) {
					parent.submit(rawNonce, argon, finalDuration, this.difficulty.longValue(), this.getType(), this.blockHeight, this);
					if (finalDuration <= 240) {
						finds++;
					} else {
						shares++;
					}
					genNonce(); // only gen a new nonce once we exhaust the one we had
				}
				
				hashCount++;
				statEnd = System.nanoTime();
				
				if (finalDuration < this.bestDL) { // split the difference; if we're not getting movement after a while, just move on
					this.bestDL = finalDuration;
				}
				
				
				this.argonTime += statArgonEnd - statArgonBegin;
				this.shaTime += statShaEnd - statShaBegin;
				this.nonArgonTime += (statArgonBegin - statBegin) + (statEnd - statArgonEnd);
			} catch (Exception e) {
				System.err.println(id + "] This worker failed somehow. Killing it.");
				e.printStackTrace();
				active = false;
			}
			this.loopTime += System.currentTimeMillis() - statCycle;
			
			if (this.hashCount > this.targetHashCount || this.loopTime > this.maxTime) {
				this.active = false;
			}
		}
		System.out.println(id + "] This worker is now inactive.");
		this.parent.hasherCount.decrementAndGet();
	}

	public String getType() {
		return "GPU";
	}
	
	public void kill() { 
		active = false;
	}
}
