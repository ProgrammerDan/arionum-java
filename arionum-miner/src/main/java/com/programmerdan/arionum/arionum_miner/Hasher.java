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
	
	public Hasher(Miner parent) {
		this.parent = parent;
	}
	
	public void run() {
		this.parent.hasherCount.incrementAndGet();
		active = true;
		long start = System.currentTimeMillis();
		long hashcount = 0l;
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
		
		while (active) {
			try {
				BigInteger difficulty = parent.getDifficulty(); // BigInteger.valueOf(176824189l);

				random.nextBytes(nonce);
				encNonce = Base64.getEncoder().encodeToString(nonce);
				encNonce = encNonce.replaceAll("[^a-zA-Z0-9]", "");
				// TESTINF: match this to nonce in php reference: encNonce = "GjVM7alc4twSoSKobE9GEW4xYuSapcYVr5mM8z8";
				hashBase = new StringBuilder();
				hashBase.append(parent.getPublicKey()).append("-");
				hashBase.append(encNonce).append("-");
				hashBase.append(parent.getBlockData()).append("-");
				hashBase.append(difficulty);
				argon = argon2.hash(4, 16384, 4, hashBase.toString());
				// TESTINF: match this to argon hash in php reference: argon = "$argon2i$v=19$m=16384,t=4,p=4$eFhjNmp4ejhtMTNsVWdaSQ$AD9Y0Ym7W/VZIRC8V76l0dPhTbEZr1+HDe0Zd+U9cZ8";
				// TESTINF: used for matching. System.out.println(hashBase.toString() + " --- " + argon);
				hashBase.append(argon);
				
				base = hashBase.toString();
				byteBase = base.getBytes();
				for (int i = 0; i < 5; i++) {
					byteBase = sha512.digest(byteBase);
				}
				byteBase = sha512.digest(byteBase);
				// see https://stackoverflow.com/a/33085670
				hashedHash = new StringBuilder();
				//for (int j = 0; j < byteBase.length; j++) {
				//	hashedHash.append(Integer.toString((byteBase[j] & 0xff) + 0x100, 16).substring(1));
				//}
				// or see https://stackoverflow.com/a/19722967
				BigInteger bi = new BigInteger(1, byteBase);
				hashedHash.append(String.format("%0" + (byteBase.length << 1) + "x", bi));
				//System.out.println(hashedHash.toString());
				
				StringBuilder duration = new StringBuilder();
				duration.append(hexdec_equiv(hashedHash, 10))
						.append(hexdec_equiv(hashedHash, 15))
						.append(hexdec_equiv(hashedHash, 20))
						.append(hexdec_equiv(hashedHash, 23))
						.append(hexdec_equiv(hashedHash, 31))
						.append(hexdec_equiv(hashedHash, 40))
						.append(hexdec_equiv(hashedHash, 45))
						.append(hexdec_equiv(hashedHash, 55));
				
				// TODO: Bypass double ended conversion; no reason to go from binary to hex to dec; go straight from bin to dec for max eff.
				
				long finalDuration = new BigInteger(duration.toString()).divide(difficulty).longValue(); // TODO: 50000 == difficulty
				// duration.toString().replaceFirst("^[0]+", "")
				if (finalDuration > 0 && finalDuration <= parent.getLimit()) {
					/* TESTINF: matches php reference in closed tests: StringBuilder duration2 = new StringBuilder();
					duration2.append(hashedHash.substring(20, 22))
							.append(hashedHash.substring(30, 32))
							.append(hashedHash.substring(40, 42))
							.append(hashedHash.substring(46, 48))
							.append(hashedHash.substring(62, 64))
							.append(hashedHash.substring(80, 82))
							.append(hashedHash.substring(90, 92))
							.append(hashedHash.substring(110, 112));*/
					System.out.println("Found a nonce to submit with DL " + finalDuration + ": " + encNonce + "  /  " + argon);
					// TESTINF: matches php reference in closed tests: System.out.println(duration2.toString() + " - " + duration.toString() + " - " + duration.toString().replaceFirst("^[0]+", "") + " vs " + difficulty + " ---- " + hashedHash);
					parent.submit(encNonce, argon);
				}
				if (finalDuration < bestDuration) {
					bestDuration = finalDuration;
				}
				hashcount ++;
				parent.hashes.incrementAndGet();
				if ((System.currentTimeMillis() - report) > 2000) {
					// TESTINF: System.out.println("Last Data: " + hashBase);
					long elapse = (System.currentTimeMillis() - start) / 1000;
					System.out.println("Rate : " + (hashcount / elapse) + "H/s hashes: " + hashcount + " elapsed: " + elapse + "s best DL last round: " + bestDuration);
					report = System.currentTimeMillis();
					bestDuration = Long.MAX_VALUE;
				}
			} catch (Exception e) {
				System.err.println("This worker failed somehow. Killing it.");
				e.printStackTrace();
				active = false;
			}
		}
		this.parent.hasherCount.decrementAndGet();
	}
	
	public boolean isActive() {
		return active;
	}
	
	public int hexdec_equiv(StringBuilder m, int index) {
		return Integer.parseInt(m.substring(index*2, index*2+2), 16);
	}
}
