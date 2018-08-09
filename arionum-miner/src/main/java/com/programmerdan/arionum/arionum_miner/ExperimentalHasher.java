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
import java.util.Random;
import java.util.BitSet;

import net.openhft.affinity.Affinity;
import net.openhft.affinity.AffinityLock;

import com.programmerdan.arionum.arionum_miner.jna.*;

/**
 * The intent for this hasher is deeper self-inspection of running times of various components. It can be used as a testbed for comparative performance. It is not meant to be used for general use
 *
 * This particular experimental hasher takes advantage of an observation: namely, that we're doing double the randomness minimally necessary for the scheme, since the argon2i implementation here and in the php reference internally salts the
 * "password" with 32 bytes of random data. So the nonce itself can be considered just a payload, fixed entity, and allow the salting of the argon2i to control uniformly random generation of SHA-512 DL outcomes.
 * 
 * This gives me 5-10% H/s speedups on isolated testing with no increase in rejections. Block finds and shares remain as expected for H/s observed.
 * 
 * Another indicator of improved performance is tracking reveals 99.97% of time is spent in argon2i codepath, vs. between 99.8 and 99.9% for other cores. This might sound small but adds up in a big way over time, as its a per-hash
 * improvement.
 * 
 * Once a nonce is submitted, it is discarded and a new one generated, as the pool does not allow resubmission of prior nonces.
 * 
 * This is deprecated; use MappedHasher instead.
 * 
 * @author ProgrammerDan (Daniel Boston)
 *
 */
public class ExperimentalHasher extends Hasher {

	private final JnaUint32 iterations = new JnaUint32(1);
	private final JnaUint32 memory = new JnaUint32(524288);
	private final JnaUint32 parallelism = new JnaUint32(1);
	private final JnaUint32 saltLenI = new JnaUint32(16);
	private final JnaUint32 hashLenI = new JnaUint32(32);
	private final Size_t saltLen = new Size_t(16l);
	private final Size_t hashLen = new Size_t(32l);
	private final Size_t encLen;
	private byte[] encoded;
	private final Argon2Library argonlib;
	private final Argon2_type argonType = new Argon2_type(1l);

	public ExperimentalHasher(Miner parent, String id, long target, long maxTime) {
		super(parent, id, target, maxTime);

		// SET UP ARGON FOR DIRECT-TO-JNA-WRAPPER-EXEC
		argonlib = Argon2Library.INSTANCE;
		encLen = argonlib.argon2_encodedlen(iterations, memory, parallelism, saltLenI, hashLenI,
				argonType);
		encoded = new byte[encLen.intValue()];
	}

	private SecureRandom random = new SecureRandom();
	private Random insRandom = new Random(random.nextLong());
	private Encoder encoder = Base64.getEncoder();
	private String rawHashBase;
	private byte[] nonce = new byte[32];
	private byte[] salt = new byte[16];
	private String rawNonce;
	private byte[] hashBaseBuffer;
	private Size_t hashBaseBufferSize;
	private byte[] fullHashBaseBuffer;

	@Override
	public void update(BigInteger difficulty, String data, long limit, String publicKey, long blockHeight,
			boolean pause, int iters, int mem, int threads) {
		super.update(difficulty, data, limit, publicKey, blockHeight, pause, iters, mem, threads);

		genNonce();
	}
	
	@Override
	public void newHeight(long oldBlockHeight, long newBlockHeight) {
		// no-op, we are locked into 10800 > territory now
	}

	private void genNonce() {
		insRandom = new Random(random.nextLong());
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

		hashBaseBuffer = rawHashBase.getBytes();
		hashBaseBufferSize = new Size_t(hashBaseBuffer.length);
		fullHashBaseBuffer = new byte[hashBaseBuffer.length + encLen.intValue()];

		System.arraycopy(hashBaseBuffer, 0, fullHashBaseBuffer, 0, hashBaseBuffer.length);
	}

	@Override
	public void go() {
		boolean doLoop = true;
		this.hashBegin = System.currentTimeMillis();

		this.parent.hasherCount.getAndIncrement();
		byte[] byteBase = null;

		MessageDigest sha512 = null;
		try {
			sha512 = MessageDigest.getInstance("SHA-512");
		} catch (NoSuchAlgorithmException e1) {
			System.err.println("Unable to find SHA-512 algorithm! Fatal error.");
			e1.printStackTrace();
			active = false;
			doLoop = false;
			System.exit(1);
		}
		if (active) {
			parent.workerInit(id);
		}

		long statCycle = 0l;
		long statBegin = 0l;
		long statArgonBegin = 0l;
		long statArgonEnd = 0l;
		long statShaBegin = 0l;
		long statShaEnd = 0l;
		long statEnd = 0l;

		try {
			boolean bound = Miner.PERMIT_AFINITY;
			if (Miner.PERMIT_AFINITY) {
				BitSet affinity = Affinity.getAffinity();
				if (affinity == null || affinity.isEmpty() || affinity.cardinality() > 1) { // no affinity?
					Integer lastChance = AggressiveAffinityThreadFactory.AffineMap.get(Affinity.getThreadId());
					if (lastChance == null || lastChance < 0) {
						bound = false;
					}
				}
			}
			while (doLoop && active) {
				statCycle = System.currentTimeMillis();
				statBegin = System.nanoTime();
				try {
					insRandom.nextBytes(salt); // 47 ns
	
					statArgonBegin = System.nanoTime();
	
					int res = argonlib.argon2i_hash_encoded(iterations, memory, parallelism, hashBaseBuffer, hashBaseBufferSize, salt,
							saltLen, hashLen, encoded, encLen); // refactor saves like 30,000-200,000 ns per hash // 34.2 ms
																// -- 34,200,000 ns
					if (res != Argon2Library.ARGON2_OK) {
						System.out.println("HASH FAILURE!" + res);
						System.out.println(" hashes: " + hashCount);
						System.exit(res);
					}
					statArgonEnd = System.nanoTime();
	
					System.arraycopy(encoded, 0, fullHashBaseBuffer, hashBaseBufferSize.intValue(), encLen.intValue());
					// 10-20ns (vs. 1200ns of strings in former StableHasher)
	
					statShaBegin = System.nanoTime();
					
					byteBase = sha512.digest(fullHashBaseBuffer);
					for (int i = 0; i < 5; i++) {
						byteBase = sha512.digest(byteBase);
					}
					
					statShaEnd = System.nanoTime();
					// shas total 4900-5000ns for all 6 digests, or < 1000ns ea
	
					StringBuilder duration = new StringBuilder(25);
					duration.append(byteBase[10] & 0xFF).append(byteBase[15] & 0xFF).append(byteBase[20] & 0xFF)
							.append(byteBase[23] & 0xFF).append(byteBase[31] & 0xFF).append(byteBase[40] & 0xFF)
							.append(byteBase[45] & 0xFF).append(byteBase[55] & 0xFF);
	
					long finalDuration = new BigInteger(duration.toString()).divide(this.difficulty).longValue();
					// 385 ns for duration
	
					if (finalDuration > 0 && finalDuration <= this.limit) {
	
						parent.submit(rawNonce, new String(encoded), finalDuration, this.difficulty.longValue(), this.getType(), this.blockHeight, this);
						if (finalDuration <= 240) {
							finds++;
						} else {
							shares++;
						}
						genNonce(); // only gen a new nonce once we exhaust the one we had
					}
	
					hashCount++;
					statEnd = System.nanoTime();
	
					if (finalDuration < this.bestDL) {
						this.bestDL = finalDuration;
					}
	
					this.argonTime += statArgonEnd - statArgonBegin;
					this.shaTime += statShaEnd - statShaBegin;
					this.nonArgonTime += (statArgonBegin - statBegin) + (statEnd - statArgonEnd);
	
				} catch (Exception e) {
					System.err.println(id + "] This worker failed somehow. Killing it.");
					e.printStackTrace();
					doLoop = false;
				}
				this.loopTime += System.currentTimeMillis() - statCycle;
	
				if (this.hashCount > this.targetHashCount || this.loopTime > this.maxTime) {
					if (!bound) { // no affinity?
						if (Miner.PERMIT_AFINITY) {
							// make an attempt to grab affinity.
							AffinityLock lock = AffinityLock.acquireLock(false); //myid);
							if (!lock.isBound()) {
								lock = AffinityLock.acquireLock();
							}
							if (!lock.isBound()) {
								lock = AffinityLock.acquireCore();
							}
							if (!lock.isBound()) {
								bound = false;
							} else {
								bound = true;
							}
						}
					}
					if (!bound) {
						doLoop = false;
					} else {
						this.hashEnd = System.currentTimeMillis();
						this.hashTime = this.hashEnd - this.hashBegin;
						this.hashBegin = System.currentTimeMillis();
						completeSession();
						this.loopTime = 0l;
					}
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		this.hashEnd = System.currentTimeMillis();
		this.hashTime = this.hashEnd - this.hashBegin;
		this.parent.hasherCount.decrementAndGet();
	}
	
	public String getType() {
		return "Legacy";
	}
	
	public void kill() {
		active = false;
	}
}