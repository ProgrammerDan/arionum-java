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

import java.lang.reflect.Method;
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
 * This version uses a PHP-esque "safe" salt, for easier verification and to avoid verification problems against the
 * reference implementation. As a consequence of the loss of byte space (2/3 reduction possibly) in the salt, I've added
 * in a hopefully clever NONCE rotation scheme, that will mutate the nonce along with the salt. 
 * 
 * @author ProgrammerDan (Daniel Boston)
 *
 */
public class SafeMappedHasher extends Hasher implements Argon2Library.AllocateFunction, Argon2Library.DeallocateFunction {
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
	
	private boolean kill = false;

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
	
	private final JnaUint32 iterations2 = new JnaUint32(4);
	private final JnaUint32 memory2 = new JnaUint32(16384);
	private final JnaUint32 parallelism2 = new JnaUint32(4);
	private final Size_t encLen2;
	private byte[] encoded2;
	
	private int mode = 1;

	public SafeMappedHasher(Miner parent, String id, long target, long maxTime) {
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
		encoded = new byte[encLen.intValue() - 1];
		
		encLen2 = argonlib.argon2_encodedlen(iterations2, memory2, parallelism2, saltLenI, hashLenI,
				argonType);
		encoded2 = new byte[encLen2.intValue() - 1];
	}

	private SecureRandom random = new SecureRandom();
	private Random insRandom = new Random(random.nextLong());
	private Encoder encoder = Base64.getEncoder();
	private String rawHashBase;
	private byte[] nonce = new byte[32];
	private byte[] salt = new byte[16];
	private Memory m_salt = new Memory(16l);
	private String rawNonce;
	private int rawNonceOffset;
	private int rawNonceLen = 42;
	private byte[] hashBaseBuffer;
	private Memory m_hashBaseBuffer;
	private Size_t hashBaseBufferSize;
	private byte[] fullHashBaseBuffer;
	
	private static final char[] safeEncode = {
			'a','b','c','d','e','f','g','h','i','j','k','l','m','n','o','p','q','r','s','t','u','v','w','x','y','z',
			'A','B','C','D','E','F','G','H','I','J','K','L','M','N','O','P','Q','R','S','T','U','V','W','X','Y','Z',
			'0','1','2','3','4','5','6','7','8','9','.','/'};

	@Override
	public void update(BigInteger difficulty, String data, long limit, String publicKey, long blockHeight,
			boolean pause, int iters, int mem, int threads) {
		super.update(difficulty, data, limit, publicKey, blockHeight, pause, iters, mem, threads);

		if (this.mem == 16384) {
			mode = 2;
		} else {
			mode = 1; // assume only the two modes for now.
		}
		
		genNonce();
	}
	
	@Override
	public void newHeight(long oldBlockHeight, long newBlockHeight) {
		// no-op, we are locked into 10800 > territory now
	}

	private byte[] safeBase64Random(int bytes) {
		char[] buffer = new char[bytes];
		for (int i = 0; i < bytes; i++) {
			buffer[i] = safeEncode[random.nextInt(64)];
		}
		return String.valueOf(buffer).getBytes();
	}

	private byte[] safeBase64Random(byte[] buffer) {
		for (int i = 0; i < buffer.length;) {
			byte[] buff = Character.valueOf(safeEncode[random.nextInt(64)]).toString().getBytes();
			buffer[i++] = buff[0];
			if (buff.length>1) buffer[i++] = buff[1];
			if (buff.length>2) buffer[i++] = buff[2];
			if (buff.length>3) buffer[i++] = buff[3];
		}
		return buffer;
	}
	
	private String safeAlphaNumericRandom(int bytes) {
		char[] buffer = new char[bytes];
		for (int i = 0; i < bytes; i++) {
			buffer[i] = safeEncode[random.nextInt(62)];
		}
		return new String(buffer);
	}

	
	private void genNonce() {
		StringBuilder hashBase;

		rawNonce = safeAlphaNumericRandom(rawNonceLen);

		// prealloc probably saves us 10% on this op sequence
		hashBase = new StringBuilder(hashBufferSize); // size of key + nonce + difficult + argon + data + spacers
		hashBase.append(this.publicKey).append("-");
		rawNonceOffset = hashBase.length();
		hashBase.append(rawNonce).append("-");
		hashBase.append(this.data).append("-");
		hashBase.append(this.difficultyString);

		rawHashBase = hashBase.toString();

		hashBaseBuffer = rawHashBase.getBytes();
		hashBaseBufferSize = new Size_t(hashBaseBuffer.length);
		m_hashBaseBuffer = new Memory(hashBaseBuffer.length);
		
		if (mode == 1) {
			fullHashBaseBuffer = new byte[hashBaseBuffer.length + encLen.intValue() - 1];
		} else if (mode == 2) {
			fullHashBaseBuffer = new byte[hashBaseBuffer.length + encLen2.intValue() - 1];
		}

		m_hashBaseBuffer.write(0, hashBaseBuffer, 0, hashBaseBuffer.length);
		System.arraycopy(hashBaseBuffer, 0, fullHashBaseBuffer, 0, hashBaseBuffer.length);
	}
	
	/* Replace nonce in-place instead of regenerating the entirety. */
	private void regenNonce() {
		rawNonce = safeAlphaNumericRandom(rawNonceLen);
		byte[] rawNonceBytes = rawNonce.getBytes();
		m_hashBaseBuffer.write(rawNonceOffset, rawNonceBytes, 0, rawNonceBytes.length);
		System.arraycopy(rawNonceBytes, 0, hashBaseBuffer, rawNonceOffset, rawNonceBytes.length);
		System.arraycopy(rawNonceBytes, 0, fullHashBaseBuffer, rawNonceOffset, rawNonceBytes.length);
	}
	
	@Override
	public void go() {
		try {
			scratch = SafeMappedHasher.getScratch();
		} catch (OutOfMemoryError oome) {
			System.err.println("Please reduce the number of requested hashing workers. Your system lacks sufficient memory to run this many!");
			//System.err.println("Regrettably, this is a fatal error.");
			//oome.printStackTrace();
			active = false;
			//System.exit(1);
			return;
		}
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
			boolean bound = Miner.PERMIT_AFINITY;
			if (Miner.PERMIT_AFINITY && Miner.CHECK_BIND) { // for some systems, this doesn't work, so we don't check.
				int activeCpu = Affinity.getCpu();
				BitSet affinity = Affinity.getAffinity();
				if (affinity == null || affinity.isEmpty() || affinity.cardinality() > 1) { // no affinity?
					Integer lastChance = AggressiveAffinityThreadFactory.AffineMap.get(Affinity.getThreadId());
					if (lastChance == null || lastChance < 0) {
						bound = false;
					} else { // see if lastChance equals actual CPU binding
						if (!lastChance.equals(activeCpu)) {
							// try to alter!
							AffinityLock.acquireLock(lastChance.intValue());
							System.out.println("We had locked on to " + lastChance.intValue() + " but lost it and are running on " + activeCpu);
						}
					}
				} else { // see if BitSet affinity equals actual CPU binding
					//BigInteger singleAffine = new BigInteger(affinity.toByteArray());
					//if (singleAffine.intValue() != activeCpu) {
					if (affinity.nextSetBit(0) != activeCpu) {
						// try to alter!
						AffinityLock.acquireLock(affinity.nextSetBit(0));
						System.out.println("We had locked on to " + affinity.nextSetBit(0) + " but lost it and are running on " + activeCpu);
					}
				}
			}
			while (doLoop && active) {
				if (this.pause) {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						// ok, moving on.
					}
					continue;
				}
				statCycle = System.currentTimeMillis();
				statBegin = System.nanoTime();
				try {
					regenNonce();
					
					this.safeBase64Random(salt); //random.nextBytes(salt);
					m_salt.write(0, salt, 0, 16);

					/*System.out.println(String.format("%s\n%s\n%s\n", new String(hashBaseBuffer),
							new String(fullHashBaseBuffer), encoder.encodeToString(salt)));
					Thread.sleep(500l);*/
					
					statArgonBegin = System.nanoTime();
					
					int smode = mode;
					if (mode == 2) {
						context.t_cost = iterations2;
						context.m_cost = memory2;
						context.lanes = parallelism2;
						context.threads = parallelism2;
					} else {
						context.t_cost = iterations;
						context.m_cost = memory;
						context.lanes = parallelism;
						context.threads = parallelism;
					}

					// set argon params in context..
					context.out = new Memory(32l);
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
					int res2 = 0;
					long finalDuration = 0;
					if (smode == mode) { // check if mode changed
						if (mode == 1) {
							res2 = argonlib.encode_ctx(encoded, encLen, context, argonType);
						} else if (mode == 2) {
							res2 = argonlib.encode_ctx(encoded2, encLen2, context, argonType);
						}
						if (res2 != Argon2Library.ARGON2_OK) {
							System.out.println("ENCODE FAILURE! " + res2);
						}
						statArgonEnd = System.nanoTime();
						
						if (mode == 1) {
							if (encoded[encoded.length - 1] == 0) {
								System.out.print("Encoded length failure.");
							} 

							System.arraycopy(encoded, 0, fullHashBaseBuffer, hashBaseBufferSize.intValue(), encLen.intValue() - 1);
						} else if (mode == 2) {
							if (encoded2[encoded2.length - 1] == 0) {
								System.out.print("Encoded length failure.");
							} 

							System.arraycopy(encoded2, 0, fullHashBaseBuffer, hashBaseBufferSize.intValue(), encLen2.intValue() - 1);
						}

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
		
						finalDuration = new BigInteger(duration.toString()).divide(this.difficulty).longValue();
						// 385 ns for duration
		
						if (finalDuration > 0 && finalDuration <= this.limit) {

							if (mode == 1 ) {
								// why trim? the raw encoded has a trailing \x00 null char. Trim will remove it, same as we do in the arraycopy by doing encLen - 1.
								parent.submit(rawNonce, new String(encoded).trim(), finalDuration, this.difficulty.longValue(), this.getType(), this.blockHeight, this);
							} else if (mode == 2) {
								parent.submit(rawNonce, new String(encoded2).trim(), finalDuration, this.difficulty.longValue(), this.getType(), this.blockHeight, this);
							}

							if (finalDuration <= 240) {
								finds++;
							} else {
								shares++;
							}
							//genNonce(); // only gen a new nonce once we exhaust the one we had
						}
					}
	
					hashCount++;
					statEnd = System.nanoTime();
	
					if (finalDuration < this.bestDL) {
						this.bestDL = finalDuration;
					}
	
					this.argonTime += statArgonEnd - statArgonBegin;
					this.shaTime += statShaEnd - statShaBegin;
					this.nonArgonTime += (statArgonBegin - statBegin) + (statEnd - statArgonEnd);
					
					/*System.out.print(String.format("- r%d  a%d  s%d -", 
							(statArgonBegin - statBegin),
							(statArgonEnd - statArgonBegin),
							(statShaEnd - statShaBegin)));*/
	
				} catch (Exception e) {
					System.err.println(id + "] This worker failed somehow. Killing it.");
					e.printStackTrace();
					System.err.println("Please report this to ProgrammerDan");
					doLoop = false;
					kill = true;
				}
				this.loopTime += System.currentTimeMillis() - statCycle;
	
				if (this.hashCount > this.targetHashCount || this.loopTime > this.maxTime) {
					if (Miner.PERMIT_AFINITY && Miner.CHECK_BIND) { // for some systems, this doesn't work, so we don't check.
						int activeCpu = Affinity.getCpu();
						BitSet affinity = Affinity.getAffinity();
						if (affinity == null || affinity.isEmpty() || affinity.cardinality() > 1) { // no affinity?
							Integer lastChance = AggressiveAffinityThreadFactory.AffineMap.get(Affinity.getThreadId());
							if (lastChance == null || lastChance < 0) {
								bound = false;
							} else { // see if lastChance equals actual CPU binding
								if (!lastChance.equals(activeCpu)) {
									// try to alter!
									AffinityLock.acquireLock(lastChance.intValue());
									System.out.println("We had locked on to " + lastChance.intValue() + " but lost it and are running on " + activeCpu);
								}
							}
						} else { // see if BitSet affinity equals actual CPU binding
							if (affinity.nextSetBit(0) != activeCpu) {
								// try to alter!
								AffinityLock.acquireLock(affinity.nextSetBit(0));
								System.out.println("We had locked on to " + affinity.nextSetBit(0) + " but lost it and are running on " + activeCpu);
							}
						}
					}
					this.hashEnd = System.currentTimeMillis();
					this.hashTime = this.hashEnd - this.hashBegin;
					this.hashBegin = System.currentTimeMillis();
					completeSession();
					this.loopTime = 0l;
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
		if (kill) {
			scratch.clear(); // we don't return it. Let's clear it, and let it be GC'd. The Scratch will gen a new one.
			try {
				Method dispose = scratch.getClass().getDeclaredMethod("dispose");
				dispose.setAccessible(true);
				dispose.invoke(scratch);
			} catch (Throwable t) {
				System.err.println("After a potential memory failure was identified, our attempt to release this memory buffer has failed.");
				t.printStackTrace();
			}
			scratch = null;
			Memory.purge();
		} else {
			SafeMappedHasher.returnScratch(scratch);
		}
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
	
	public void kill() {
		kill = true;
		active = false;
	}
}
