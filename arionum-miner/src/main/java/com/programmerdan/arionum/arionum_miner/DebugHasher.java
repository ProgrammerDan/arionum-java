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
public class DebugHasher extends Hasher {

	public DebugHasher(Miner parent, String id) {
		super(parent, id);
	}
	
	
	@Override
	public void run() {
		this.parent.hasherCount.incrementAndGet();
		active = true;
		long start = System.currentTimeMillis();

		byte[] nonce = new byte[32];
		String encNonce = null;

		StringBuilder hashBase = new StringBuilder();
		String base = null;
		byte[] byteBase = null;
		String argon = null;

		char[] nonceChar = null;
		StringBuilder nonceSb = null;
		
		SecureRandom random = new SecureRandom();

		Argon2 argon2 = Argon2Factory.create(Argon2Types.ARGON2i);
		
		Encoder encoder = Base64.getEncoder();

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
			System.out.println(id + "] Spun up DEBUG hashing worker in " + (System.currentTimeMillis() - start) + "ms");
		}
		
		long count = 0;
		long randomData = 0l;
		long encodeNonce = 0l;
		long preArgon = 0l;
		long argonHash = 0l;
		long postArgon = 0l;
		long shaHash = 0l;
		long durationMap = 0l;
		long duration2Map = 0l;
		long durationComp = 0l;
		long duration2Comp = 0l;
		long statsUpd = 0l;
		
		long statCycle = 0l;
		long statBegin = 0l;
		long statArgonBegin = 0l;
		long statArgonEnd = 0l;
		long statEnd = 0l;
		
		while (active) {
			statCycle = System.currentTimeMillis();
			statBegin = System.nanoTime();
			try {
				long tracking = System.currentTimeMillis();

				random.nextBytes(nonce);

				randomData += System.currentTimeMillis() - tracking;
				tracking = System.currentTimeMillis();
				
				encNonce = encoder.encodeToString(nonce);
				
				// shaves a bit off vs regex -- for this operation, about 50% savings
				nonceSb = new StringBuilder(encNonce.length());
				nonceChar = encNonce.toCharArray();
				for (char ar : nonceChar) {
					if (ar >= '0' && ar <= '9' || ar >= 'a' && ar <= 'z' || ar >= 'A' && ar <= 'Z') {
						nonceSb.append(ar);
					}
				}
				
				encodeNonce += System.currentTimeMillis() - tracking;
				tracking = System.currentTimeMillis();
				
				// prealloc probably saves us 10% on this op sequence
				hashBase = new StringBuilder(280 + this.data.length()); // size of key + none + difficult + argon + data + spacers
				hashBase.append(this.publicKey).append("-");
				hashBase.append(nonceSb).append("-");
				hashBase.append(this.data).append("-");
				hashBase.append(this.difficulty);
				preArgon += System.currentTimeMillis() - tracking;
				tracking = System.currentTimeMillis();
				
				/*if (count > 0 && (count % 100) == 0) {
					System.out.println(" pre-argon length vs. prelength: " + hashBase.length() + " vs " + hashBase.capacity());
				}*/
				statArgonBegin = System.nanoTime();
				argon = argon2.hash(4, 16384, 4, hashBase.toString());
				statArgonEnd = System.nanoTime();
				
				argonHash += System.currentTimeMillis() - tracking;
				tracking = System.currentTimeMillis();
				
				hashBase.append(argon);

				/*if (count > 0 && (count % 100) == 0) {
					System.out.println(" post-argon length vs. prelength: " + hashBase.length() + " vs " + hashBase.capacity());
				}*/
				
				postArgon += System.currentTimeMillis() - tracking;
				tracking = System.currentTimeMillis();
				
				base = hashBase.toString();
				byteBase = base.getBytes();
				for (int i = 0; i < 5; i++) {
					byteBase = sha512.digest(byteBase);
				}
				byteBase = sha512.digest(byteBase);
				
				shaHash += System.currentTimeMillis() - tracking;
				tracking = System.currentTimeMillis();
				
				durationMap += System.currentTimeMillis() - tracking;
				tracking = System.currentTimeMillis();

				StringBuilder duration = new StringBuilder(25);
				duration.append(byteBase[10] & 0xFF).append(byteBase[15] & 0xFF).append(byteBase[20] & 0xFF)
						.append(byteBase[23] & 0xFF).append(byteBase[31] & 0xFF).append(byteBase[40] & 0xFF)
						.append(byteBase[45] & 0xFF).append(byteBase[55] & 0xFF);
				
				duration2Map += System.currentTimeMillis() - tracking;
				tracking = System.currentTimeMillis();
				
				/*if (count > 0 && (count % 100) == 0) {
					System.out.println(" duration length vs. prelength: " + duration.length() + " vs " + (duration.capacity()));
				}*/
			
				// TODO: Bypass double ended conversion; no reason to go from
				// binary to hex to dec; go straight from bin to dec for max
				// eff.

				BigInteger bigDuration = new BigInteger(duration.toString());
				
				durationComp += System.currentTimeMillis() - tracking;
				tracking = System.currentTimeMillis();

				long finalDuration = bigDuration.divide(this.difficulty).longValue();
				
				duration2Comp += System.currentTimeMillis() - tracking;
				tracking = System.currentTimeMillis();

				
				if (finalDuration > 0 && finalDuration <= this.limit) {

					parent.submit(nonceSb.toString(), argon, finalDuration);
					if (finalDuration <= 240) {
						finds++;
					} else {
						shares++;
					}
				}
				
				hashCount++;
				hashesRecent++;
				statEnd = System.nanoTime();
				if (finalDuration < this.bestDL) {
					this.bestDL = finalDuration;
				}
				this.argonTime += statArgonEnd - statArgonBegin;
				this.nonArgonTime += (statArgonBegin - statBegin) + (statEnd - statArgonEnd);
				
				statsUpd += System.currentTimeMillis() - tracking;
				tracking = System.currentTimeMillis();
				
				if (count > 0 && (count % 1000) == 0) {
					System.out.println(count + " Hashes Debug Timing Stats: \n"
							+ "  Random: " + (randomData) + "ms \n"
							+ "  Encode: " + (encodeNonce) + "ms \n"
							+ "  PreArg: " + (preArgon) + "ms \n"
							+ "  Argon2: " + (argonHash) + "ms \n"
							+ "  PstArg: " + (postArgon) + "ms \n"
							+ "  sha512: " + (shaHash) + "ms \n"
							+ "  durmap: " + (durationMap) + "ms \n"
							+ "  durmp2: " + (duration2Map) + "ms \n"
							+ "  durcmp: " + (durationComp) + "ms \n"
							+ "  durat2: " + (duration2Comp) + "ms \n"
							+ "  statup: " + (statsUpd) + "ms \n"
							+ "  Arg %: " + ((double) argonHash / (double) (randomData + encodeNonce + preArgon + postArgon+ shaHash + durationMap + duration2Map + durationComp + duration2Comp + statsUpd + argonHash)) * 100d + "%");
					
					if (count == 1000000l) {
						randomData = 0l;
						encodeNonce = 0l;
						preArgon = 0l;
						argonHash = 0l;
						postArgon = 0l;
						shaHash = 0l;
						durationMap = 0l;
						duration2Map = 0l;
						durationComp = 0l;
						duration2Comp = 0l;
						statsUpd = 0l;
									
						count = 0;
					} else {
						count ++;
					}
				} else {
					count ++;
				}
			} catch (Exception e) {
				System.err.println(id + "] This worker failed somehow. Killing it.");
				e.printStackTrace();
				active = false;
			}
			this.loopTime += System.currentTimeMillis() - statCycle;
		}
		System.out.println(id + "] This worker is now inactive.");
		this.parent.hasherCount.decrementAndGet();
	}
	
	public int hexdec_equiv(StringBuilder m, int index) {
		return Integer.parseInt(m.substring(index * 2, index * 2 + 2), 16);
	}


}
