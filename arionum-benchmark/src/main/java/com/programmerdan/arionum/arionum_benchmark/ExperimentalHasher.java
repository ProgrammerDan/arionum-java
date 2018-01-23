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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Base64.Encoder;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import de.mkammerer.argon2.Argon2Factory.Argon2Types;

/**
 * The intent for this hasher is deeper self-inspection of running times of various components.
 * It can be used as a testbed for comparative performance. It is not meant to be used for general use
 *
 * This particular experimental hasher takes advantage of an observation: namely, that 
 * we're doing double the randomness minimally necessary for the scheme, since the argon2i 
 * implementation here and in the php reference internally salts the "password" with 32 bytes of
 * random data. So the nonce itself can be considered just a payload, fixed entity, and allow the
 * salting of the argon2i to control uniformly random generation of SHA-512 DL outcomes.
 * 
 * This gives me 5-10% H/s speedups on isolated testing with no increase in rejections. Block
 * finds and shares remain as expected for H/s observed. 
 * 
 * Another indicator of improved performance is tracking reveals 99.97% of time is spent in argon2i 
 * codepath, vs. between 99.8 and 99.9% for other cores. This might sound small but adds up in a big way
 * over time, as its a per-hash improvement.
 * 
 * Once a nonce is submitted, it is discarded and a new one generated, as the pool does not allow 
 * resubmission of prior nonces.
 * 
 * 
 * @author ProgrammerDan (Daniel Boston)
 *
 */
public class ExperimentalHasher extends Hasher {

	public ExperimentalHasher() {
	}
	
	private SecureRandom random = new SecureRandom();
	private Encoder encoder = Base64.getEncoder();
	private String rawHashBase;
	private byte[] nonce = new byte[32];
	private String rawNonce;
	
	private long bestDL;

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
		bestDL = Long.MAX_VALUE;
	}
	
	
	@Override
	public void run() {
		active = true;
		long start = System.currentTimeMillis();
		long lastUpdate = start;

		StringBuilder hashBase = null;
		byte[] byteBase = null;
		
		String argon = null;
		Argon2 argon2 = Argon2Factory.create(Argon2Types.ARGON2i);

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
			System.out.println("Spun up EXPERIMENTAL hashing worker in " + (System.currentTimeMillis() - start) + "ms");
			start = System.currentTimeMillis();
			genNonce();
		}
		
		long statCycle = 0l;
		long statBegin = 0l;
		long statArgonBegin = 0l;
		long statArgonEnd = 0l;
		long statEnd = 0l;
		long stuck = 0;
		
		int cCount = 0;
		double speed = 0d;
		double avgSpeed = 0d;
		
		statCycle = System.currentTimeMillis();

		while (active) {
			
			if (System.currentTimeMillis()-lastUpdate > 2000) {
				System.out.println("--> Last hash rate: " + speed + " H/s   Average: " + avgSpeed + " H/s  Total hashes: " + hashCount + "  Mining Time: " + ((System.currentTimeMillis() - start) / 1000d) +
						"  Shares: " + shares + " Finds: " + finds);
				lastUpdate = System.currentTimeMillis();
			}
			
			statBegin = System.nanoTime();
			try {
				hashBase = new StringBuilder(this.hashBufferSize);
				statArgonBegin = System.nanoTime();
				argon = argon2.hash(4, 16384, 4, rawHashBase);
				statArgonEnd = System.nanoTime();
				hashBase.append(rawHashBase).append(argon);

				byteBase = hashBase.toString().getBytes();
				for (int i = 0; i < 5; i++) {
					byteBase = sha512.digest(byteBase);
				}
				byteBase = sha512.digest(byteBase);

				StringBuilder duration = new StringBuilder(25);
				duration.append(byteBase[10] & 0xFF).append(byteBase[15] & 0xFF).append(byteBase[20] & 0xFF)
						.append(byteBase[23] & 0xFF).append(byteBase[31] & 0xFF).append(byteBase[40] & 0xFF)
						.append(byteBase[45] & 0xFF).append(byteBase[55] & 0xFF);

				long finalDuration = new BigInteger(duration.toString()).divide(this.difficulty).longValue();
				if (finalDuration > 0 && finalDuration <= this.limit) {
					if (finalDuration <= 240) {
						finds++;
					} else {
						shares++;
					}
					genNonce(); // only gen a new nonce once we exhaust the one we had
				}
				
				hashCount++;
				cCount++;
				statEnd = System.nanoTime();
				
				if (finalDuration < this.bestDL) { // split the difference; if we're not getting movement after a while, just move on
					this.bestDL = finalDuration;
					stuck = 0;
				} else {
					stuck++;
					if (stuck > avgSpeed * 15) {
						genNonce();
						stuck = 0;
					}
				}
				
				
				if (cCount == 100) {
					cCount = 0;
					long cycleEnd = System.currentTimeMillis();
					speed = 100d / ((cycleEnd - statCycle)/ 1000d);
					avgSpeed = (double) hashCount / ((cycleEnd - start) / 1000d);
					statCycle = cycleEnd;
				}
				
			} catch (Exception e) {
				System.err.println("This worker failed somehow. Killing it.");
				e.printStackTrace();
				active = false;
			}
		}
		System.out.println("This worker is now inactive.");
	}
	
}
