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
import java.util.LinkedList;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.PointerByReference;

import net.openhft.affinity.Affinity;
import net.openhft.affinity.AffinityLock;

import com.programmerdan.arionum.arionum_miner.jna.*;

/**
 * This function means serious business.
 * With the Hard Fork in Arionum increasing by nearly two orders of magnitude the per-hash memory requirements, 
 * the allocation of memory became a serious performance bottleneck for most normal CPU miners.
 * This hasher attempts to address that, by generating a pool of massive memory "blocks" that are passed
 * to the argon2 function and reused. It is conservative, keeping a static "scratch" pool of available memory
 * blocks and passing them to new Hashers as needed, who (should) return them to the scratch pool when they are
 * done. New memory blocks are only allocated if the scratch pool is exhausted, but then again, those new
 * blocks are then preserved within the pool.
 * Initial testing showed about 25% performance gain by removing the bulk of memory alloc and dealloc calls in this
 * fashion.
 * 
 * @author ProgrammerDan (Daniel Boston)
 *
 */
public class MappedHasher extends Hasher implements Argon2Library.AllocateFunction, Argon2Library.DeallocateFunction {
	/* Scratch "pool" of massive memory pages. */
	private static LinkedList<Memory> scratches = new LinkedList<>();
	private static Memory getScratch() {
		Memory mem = scratches.poll();
		if (mem == null) {
			return new Memory(524288l*1024l);
		} else {
			return mem;
		}
	}
	private static void returnScratch(Memory mem) {
		scratches.push(mem);
	}

	/*Local Hasher vars*/
	private Memory scratch = null;

	private final Argon2_Context context;

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

	public MappedHasher(Miner parent, String id, long target, long maxTime) {
		super(parent, id, target, maxTime);

		// SET UP ARGON FOR DIRECT-TO-JNA-WRAPPER-EXEC
		argonlib = Argon2Library.INSTANCE;
		context  = new Argon2_Context();
		context.outlen = new JnaUint32(32);
		context.out = new Memory(32l);
		// assigned per hash context.pwdLen 
		context.saltlen = new JnaUint32(16);
		context.secret = null;
		context.secretlen = new JnaUint32(0);
		context.ad = null;
		context.adlen = new JnaUint32(0);

		context.t_cost = iterations;
		context.m_cost = memory;
		context.lanes = parallelism;
		context.threads = parallelism;
		context.flags = Argon2Library.ARGON2_DEFAULT_FLAGS;
		context.version = new JnaUint32(0x13);

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
	private Memory m_salt = new Memory(16l);
	private String rawNonce;
	private byte[] hashBaseBuffer;
	private Memory m_hashBaseBuffer;
	private Size_t hashBaseBufferSize;
	private byte[] fullHashBaseBuffer;

	@Override
	public void update(BigInteger difficulty, String data, long limit, String publicKey, long blockHeight) {
		super.update(difficulty, data, limit, publicKey, blockHeight);

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
		m_hashBaseBuffer = new Memory(hashBaseBuffer.length);
		fullHashBaseBuffer = new byte[hashBaseBuffer.length + encLen.intValue()];

		m_hashBaseBuffer.write(0, hashBaseBuffer, 0, hashBaseBuffer.length);
		System.arraycopy(hashBaseBuffer, 0, fullHashBaseBuffer, 0, hashBaseBuffer.length);
	}

	@Override
	public void go() {
		scratch = MappedHasher.getScratch();
		context.allocate_cbk = this;
		context.free_cbk = this;

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
			boolean bound = false; /*true;
			BitSet affinity = Affinity.getAffinity();
			if (affinity == null || affinity.isEmpty() || affinity.cardinality() > 1) { // no affinity?
				Integer lastChance = AggressiveAffinityThreadFactory.AffineMap.get(Affinity.getThreadId());
				if (lastChance == null || lastChance < 0) {
					bound = false;
				}
			}*/
			while (doLoop && active) {
				statCycle = System.currentTimeMillis();
				statBegin = System.nanoTime();
				try {
					//insRandom.nextBytes(salt); // 47 ns
					random.nextBytes(salt);
					m_salt.write(0, salt, 0, 16);
	
					statArgonBegin = System.nanoTime();
	
					/*argonlib.argon2i_hash_encoded(iterations, memory, parallelism, hashBaseBuffer, hashBaseBufferSize, salt,
							saltLen, hashLen, encoded, encLen); // refactor saves like 30,000-200,000 ns per hash // 34.2 ms
					*/
											// -- 34,200,000 ns
					// set argon params in context..
					context.out = new Memory(32l);//new byte[32];
					context.salt = m_salt;
					context.pwdlen = new JnaUint32(hashBaseBuffer.length);
					m_hashBaseBuffer.write(0, hashBaseBuffer, 0, hashBaseBuffer.length);
					context.pwd = m_hashBaseBuffer;
					int res = argonlib.argon2_ctx(context, argonType);
					if (res != Argon2Library.ARGON2_OK) {
						System.out.println("HASH FAILURE!" + res);
						System.out.println(" hashes: " + hashCount);
						System.exit(res);
					}
					int res2 = argonlib.encode_ctx(encoded, encLen, context, argonType);
					if (res2 != Argon2Library.ARGON2_OK) {
						System.out.println("ENCODE FAILURE! " + res2);
					}
					//byte[] method1 = new byte[encoded.length];
					//System.arraycopy(encoded, 0, method1, 0, encoded.length);
					
					//argonlib.argon2i_hash_encoded(iterations, memory, parallelism, hashBaseBuffer, hashBaseBufferSize, salt,
					//		saltLen, hashLen, encoded, encLen); // refactor saves like 30,000-200,000 ns per hash // 34.2 ms

					statArgonEnd = System.nanoTime();
					/*if (encoded.length == method1.length) {
						System.out.println(String.format("Verification for method \n%d vs baseline \n%d", argonlib.argon2i_verify(method1, m_hashBaseBuffer.getByteArray(0, hashBaseBuffer.length), new Size_t(hashBaseBuffer.length)), argonlib.argon2i_verify(encoded, hashBaseBuffer, new Size_t(hashBaseBuffer.length))));
						for (int i = 0; i < encoded.length; i++) {
							if (encoded[i] != method1[i]) {
								System.out.println(String.format("For same salts \n%s vs \n%s and same pwd \n%s vs \n%s method 1 produced \n%s vs baseline \n%s", 
									encoder.encodeToString(m_salt.getByteArray(0,16)), encoder.encodeToString(salt), 
									new String(m_hashBaseBuffer.getByteArray(0, hashBaseBuffer.length)), new String(hashBaseBuffer),
									new String(method1), new String(encoded)
								));
								System.exit(-1);
							}
						}
						System.out.println("Hash is same");
					} else {
						System.out.println("Encode failure, differing lengths.");
						System.exit(-1);
					}*/
	
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
	
						parent.submit(rawNonce, new String(encoded), finalDuration, this.difficulty.longValue(), this.getType(), this.blockHeight);
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
					/*if (!bound) { // no affinity?
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
					}*/
					if (!bound) {
						//System.out.println("Ending worker " + this.id);
						doLoop = false;
					} else {
						//System.out.println("Ending a session for worker " + this.id);
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
		MappedHasher.returnScratch(scratch);
		this.hashEnd = System.currentTimeMillis();
		this.hashTime = this.hashEnd - this.hashBegin;
		this.parent.hasherCount.decrementAndGet();
	}

	// allocate
	public int invoke(PointerByReference memory, Size_t byte_to_allocate) {
		
		if (byte_to_allocate.intValue() > this.scratch.size()) {
			return -22; // memory allocation error
		}

		memory.setValue(this.scratch);

		return 0;
	}

	// no-op deallcate, wipe?
	public void invoke(Pointer memory, Size_t byte_to_allocate) {
		// we ignore this, since we manage memory on our own.
	}
	
	public String getType() {
		return "CPU";
	}
}
