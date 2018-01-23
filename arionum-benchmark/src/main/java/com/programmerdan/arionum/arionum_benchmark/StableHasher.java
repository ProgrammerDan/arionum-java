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
 * @author ProgrammerDan (Daniel Boston)
 *
 */
public class StableHasher extends Hasher {

	public StableHasher() {
		
	}
	
	
	@Override
	public void run() {
		active = true;
		long start = System.currentTimeMillis();
		long lastUpdate = start;

		byte[] nonce = new byte[32];
		String encNonce = null;

		StringBuilder hashBase = new StringBuilder();
		String base = null;
		byte[] byteBase = null;
		String argon = null;

		Encoder encoder = Base64.getEncoder();
		char[] nonceChar = null;
		StringBuilder nonceSb = null;

		SecureRandom random = new SecureRandom();

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
			System.out.println("Spun up Stable hashing worker in " + (System.currentTimeMillis() - start) + "ms");
			start = System.currentTimeMillis();
		}
		
		long statCycle = 0l;
		long statBegin = 0l;
		long statArgonBegin = 0l;
		long statArgonEnd = 0l;
		long statEnd = 0l;
		
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
				random.nextBytes(nonce);
				encNonce = encoder.encodeToString(nonce);
				
				// shaves a bit off vs regex -- for this operation, about 50% savings
				nonceSb = new StringBuilder(encNonce.length());
				nonceChar = encNonce.toCharArray();
				for (char ar : nonceChar) {
					if (ar >= '0' && ar <= '9' || ar >= 'a' && ar <= 'z' || ar >= 'A' && ar <= 'Z') {
						nonceSb.append(ar);
					}
				}
								
				// prealloc probably saves us 10% on this op sequence
				// TODO: precompute this length when data is received
				hashBase = new StringBuilder(hashBufferSize); // size of key + none + difficult + argon + data + spacers
				hashBase.append(this.publicKey).append("-");
				hashBase.append(nonceSb).append("-");
				hashBase.append(this.data).append("-");
				// TODO: precompute difficulty as string
				hashBase.append(this.difficultyString);
				statArgonBegin = System.nanoTime();
				argon = argon2.hash(4, 16384, 4, hashBase.toString());
				statArgonEnd = System.nanoTime();
				hashBase.append(argon);

				base = hashBase.toString();
				byteBase = base.getBytes();
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
					if (finalDuration < 240) {
						finds++;
					} else {
						shares++;
					}
				}
				
				hashCount++;
				cCount++;
				statEnd = System.nanoTime();
				
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
