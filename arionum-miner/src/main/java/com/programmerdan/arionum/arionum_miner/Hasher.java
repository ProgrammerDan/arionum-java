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

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import de.mkammerer.argon2.Argon2Factory.Argon2Types;

public class Hasher implements Runnable {
	private Miner parent;
	private boolean active;
	private String id;

	public Hasher(Miner parent, String id) {
		this.parent = parent;
		this.id = id;
	}

	public void run() {
		this.parent.hasherCount.incrementAndGet();
		active = true;
		long start = System.currentTimeMillis();
		long hashCount = 0l;
		long bestDuration = Long.MAX_VALUE;

		byte[] nonce = new byte[32];
		String encNonce = null;

		StringBuilder hashBase = new StringBuilder();
		String base = null;
		byte[] byteBase = null;
		String argon = null;

		StringBuilder hashedHash = new StringBuilder();

		SecureRandom random = new SecureRandom();

		Argon2 argon2 = Argon2Factory.create(Argon2Types.ARGON2i);

		MessageDigest sha512 = null;
		try {
			sha512 = MessageDigest.getInstance("SHA-512");
		} catch (NoSuchAlgorithmException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			active = false;
		}
		long report = System.currentTimeMillis();
		long reportCount = 0l;
		if (active)
			System.out.println(id + "] Spinning up hashing worker.");
		while (active) {
			try {
				BigInteger difficulty = parent.getDifficulty(); // BigInteger.valueOf(176824189l);

				random.nextBytes(nonce);
				encNonce = Base64.getEncoder().encodeToString(nonce);
				encNonce = encNonce.replaceAll("[^a-zA-Z0-9]", "");
				hashBase = new StringBuilder();
				hashBase.append(parent.getPublicKey()).append("-");
				hashBase.append(encNonce).append("-");
				hashBase.append(parent.getBlockData()).append("-");
				hashBase.append(difficulty);
				argon = argon2.hash(4, 16384, 4, hashBase.toString());
				hashBase.append(argon);

				base = hashBase.toString();
				byteBase = base.getBytes();
				for (int i = 0; i < 5; i++) {
					byteBase = sha512.digest(byteBase);
				}
				byteBase = sha512.digest(byteBase);
				// see https://stackoverflow.com/a/33085670
				hashedHash = new StringBuilder();
				// for (int j = 0; j < byteBase.length; j++) {
				// hashedHash.append(Integer.toString((byteBase[j] & 0xff) +
				// 0x100, 16).substring(1));
				// }
				// or see https://stackoverflow.com/a/19722967
				BigInteger bi = new BigInteger(1, byteBase);
				hashedHash.append(String.format("%0" + (byteBase.length << 1) + "x", bi));
				// System.out.println(hashedHash.toString());

				StringBuilder duration = new StringBuilder();
				duration.append(hexdec_equiv(hashedHash, 10)).append(hexdec_equiv(hashedHash, 15))
						.append(hexdec_equiv(hashedHash, 20)).append(hexdec_equiv(hashedHash, 23))
						.append(hexdec_equiv(hashedHash, 31)).append(hexdec_equiv(hashedHash, 40))
						.append(hexdec_equiv(hashedHash, 45)).append(hexdec_equiv(hashedHash, 55));

				// TODO: Bypass double ended conversion; no reason to go from
				// binary to hex to dec; go straight from bin to dec for max
				// eff.

				long finalDuration = new BigInteger(duration.toString()).divide(difficulty).longValue(); // TODO:
																											// 50000
																											// ==
																											// difficulty
				// duration.toString().replaceFirst("^[0]+", "")
				if (finalDuration > 0 && finalDuration <= parent.getLimit()) {
					/*
					 * TESTINF: matches php reference in closed tests:
					 * StringBuilder duration2 = new StringBuilder();
					 * duration2.append(hashedHash.substring(20, 22))
					 * .append(hashedHash.substring(30, 32))
					 * .append(hashedHash.substring(40, 42))
					 * .append(hashedHash.substring(46, 48))
					 * .append(hashedHash.substring(62, 64))
					 * .append(hashedHash.substring(80, 82))
					 * .append(hashedHash.substring(90, 92))
					 * .append(hashedHash.substring(110, 112));
					 */
					System.out.println(id + "] Found a nonce to submit with DL " + finalDuration + ": " + encNonce
							+ "  /  " + argon);
					// TESTINF: matches php reference in closed tests:
					// System.out.println(duration2.toString() + " - " +
					// duration.toString() + " - " +
					// duration.toString().replaceFirst("^[0]+", "") + " vs " +
					// difficulty + " ---- " + hashedHash);
					parent.submit(encNonce, argon);
				}
				if (finalDuration < bestDuration) {
					bestDuration = finalDuration;
				}
				hashCount++;
				reportCount++;
				parent.hashes.incrementAndGet();
				long reportElapse = (System.currentTimeMillis() - report);
				if (reportElapse > 2000) {
					// TESTINF: System.out.println("Last Data: " + hashBase);
					long elapse = (System.currentTimeMillis() - start) / 1000;

					System.out.println(id + "] All Time Rate: " + (hashCount / elapse) + "H/s Active Rate: "
							+ (reportCount / (reportElapse / 1000)) + "H/s hashes: " + hashCount + " elapsed: " + elapse
							+ "s best DL last round: " + bestDuration);
					report = System.currentTimeMillis();
					reportCount = 0l;
					bestDuration = Long.MAX_VALUE;
				}
			} catch (Exception e) {
				System.err.println(id + "] This worker failed somehow. Killing it.");
				e.printStackTrace();
				active = false;
			}
		}
		System.out.println(id + "] This worker is now inactive.");
		this.parent.hasherCount.decrementAndGet();
	}

	public boolean isActive() {
		return active;
	}

	public int hexdec_equiv(StringBuilder m, int index) {
		return Integer.parseInt(m.substring(index * 2, index * 2 + 2), 16);
	}
}
