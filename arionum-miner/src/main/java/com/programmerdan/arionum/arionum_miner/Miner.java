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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * Miner. Functional equiv of arionum-miner.
 *
 */
public class Miner {
	
	public static final long UPDATING_DELAY = 2000l;
	private int maxHashers;
	/**
	 * Non-main thread group that handles submitting nonces.
	 */
	private final ExecutorService submitters;

	/**
	 * One or more hashing threads.
	 */
	private final ExecutorService hashers;
	protected final AtomicInteger hasherCount;
	
	/**
	 * Count of all hashes produced by workers.
	 */
	protected final AtomicLong hashes;
	/**
	 * Count of hashes this reporting period.
	 */
	protected final AtomicLong currentHashes;
	/**
	 * Record of best DL so far this block
	 */
	protected final AtomicLong bestDL;
	/**
	 * Count of all submits attempted
	 */
	protected final AtomicLong sessionSubmits;
	/**
	 * Count of submits rejected (orphan, or old data hashes)
	 */
	protected final AtomicLong sessionRejects;
	
	/**
	 * Let's thread off updating. We'll just hash constantly, and offload updating like submitting.
	 */
	private final ExecutorService updaters;
	
	protected boolean active = false;
	
	/* ==== Now update / init vars ==== */
	
	private MinerType type;
	private AdvMode hasherMode;

	/* Block / Data related */
	private String node;
	private String worker;
	private String publicKey;
	private String privateKey;
	private String data;
	private BigInteger difficulty;
	private long limit;
	
	private long lastBlockUpdate;
	
	/* Update related */
	private AtomicLong lastSpeed;
	private long lastUpdate;
	private long lastReport; 
	private int cycles;
	private int skips;
	private int failures;
	private int updates;
	
	/* stats on update timing */
	private final AtomicLong updateTimeAvg;
	private final AtomicLong updateTimeMax;
	private final AtomicLong updateTimeMin;
	private final AtomicLong updateParseTimeAvg;
	private final AtomicLong updateParseTimeMax;
	private final AtomicLong updateParseTimeMin;
	
	/* stats on submission timing */
	private final AtomicLong submitTimeAvg;
	private final AtomicLong submitTimeMax;
	private final AtomicLong submitTimeMin;
	private final AtomicLong submitParseTimeAvg;
	private final AtomicLong submitParseTimeMax;
	private final AtomicLong submitParseTimeMin;

	public static void main(String[] args) {
		(new Miner(args)).start();
	}
	
	public Miner(String[] args) {
		//TODO: increase
		this.hasherMode = AdvMode.basic;
		
		this.hashers = Executors.newCachedThreadPool();
		this.updaters = Executors.newSingleThreadExecutor();
		this.submitters = Executors.newCachedThreadPool();
		this.hasherCount = new AtomicInteger();
		
		this.hashes = new AtomicLong();
		this.currentHashes = new AtomicLong();
		this.bestDL = new AtomicLong(Long.MAX_VALUE);
		this.sessionSubmits = new AtomicLong();
		this.sessionRejects = new AtomicLong();
		this.lastSpeed = new AtomicLong(System.currentTimeMillis());
		
		this.updateTimeAvg = new AtomicLong();
		this.updateTimeMax = new AtomicLong(Long.MIN_VALUE);
		this.updateTimeMin = new AtomicLong(Long.MAX_VALUE);
		this.updateParseTimeAvg = new AtomicLong();
		this.updateParseTimeMax = new AtomicLong(Long.MIN_VALUE);
		this.updateParseTimeMin = new AtomicLong(Long.MAX_VALUE);

		this.submitTimeAvg = new AtomicLong();
		this.submitTimeMax = new AtomicLong(Long.MIN_VALUE);
		this.submitTimeMin = new AtomicLong(Long.MAX_VALUE);
		this.submitParseTimeAvg = new AtomicLong();
		this.submitParseTimeMax = new AtomicLong(Long.MIN_VALUE);
		this.submitParseTimeMin = new AtomicLong(Long.MAX_VALUE);

		try {
			this.type = MinerType.valueOf(args[0]);
			this.node = args[1].trim();
			this.publicKey = args[2].trim();
			if (MinerType.solo.equals(this.type)) {
				this.privateKey = args[3].trim();
				this.maxHashers = args.length > 4 ? Integer.parseInt(args[4].trim()) : 1;
				if (args.length > 5) {
					this.hasherMode = AdvMode.valueOf(args[5].trim());
				}
			} else if (MinerType.pool.equals(this.type)) {
				this.privateKey = this.publicKey;
				this.maxHashers = args.length > 3 ? Integer.parseInt(args[3].trim()) : 1;
				if (args.length > 4) {
					this.hasherMode = AdvMode.valueOf(args[4].trim());
				}
			} else if (MinerType.test.equals(this.type)) { // internal test mode, transient benchmarking.
				this.maxHashers = args.length > 3 ? Integer.parseInt(args[3].trim()) : 1;
			}
		} catch (Exception e) {
			System.err.println("Invalid configuration: " + (e.getMessage()));
			System.err.println("  type: " + this.type);
			System.err.println("  node: " + this.node);
			System.err.println("  public-key: " + this.publicKey);
			System.err.println("  private-key: " + this.privateKey);
			System.err.println("  hasher-count: " + this.maxHashers);
			System.err.println("  hasher-mode: " + this.hasherMode);
			System.exit(1);
		}
		
		this.limit = 240; // default
		this.worker = php_uniqid();
	}
	
	public void start() {
		if (MinerType.test.equals(this.type)) {
			startTest();
			return;
		}
		
		// commit e14b696362fb port to java from arionum/miner
		if (MinerType.pool.equals(this.type)) {
			String decode = Utility.base58_decode(this.publicKey);
			if (decode.length() != 64) {
				System.err.println("ERROR: Invalid Arionum address!");
				System.exit(1);
			}
		}
		// end commit e14b696362fb
		
		active = true;
		final long wallClockBegin = System.currentTimeMillis();
		this.lastUpdate = wallClockBegin;
		boolean firstRun = true;
		cycles = 0;
		skips = 0;
		failures = 0;
		updates = 0;
		while (active) {
			Future<Boolean> update = this.updaters.submit(new Callable<Boolean>() {
				public Boolean call() {
					long executionTimeTracker = System.currentTimeMillis();
					try {
						if (cycles > 0 && (System.currentTimeMillis() - lastUpdate) < (UPDATING_DELAY * .5)) {
							skips++;
							return Boolean.FALSE;
						}
						boolean endline = false;
						
						String cummSpeed = speed();
						StringBuilder extra = new StringBuilder(node);
						extra.append("/mine.php?q=info");
						if (MinerType.pool.equals(type)) {
							extra.append("&worker=").append(worker)
									.append("&address=").append(privateKey)
									.append("&hashrate=").append(cummSpeed);
						}
						
						URL url = new URL(extra.toString());
						HttpURLConnection con = (HttpURLConnection) url.openConnection();
						con.setRequestMethod("GET");
						
						// Having some weird cases on certain OSes where apparently the netstack
						// is by default unbounded timeout, so the single thread executor is
						// stuck forever waiting for a reply that will never come.
						// This should address that. 
						con.setConnectTimeout(1000);
						con.setReadTimeout(1000);
						
						int status = con.getResponseCode();
						
						lastUpdate = System.currentTimeMillis();
						
						if (status != HttpURLConnection.HTTP_OK) {
							System.out.println("Update failure: " + con.getResponseMessage());
							con.disconnect();
							failures ++;
							updateTime(System.currentTimeMillis() - executionTimeTracker);
							return Boolean.FALSE;
						}

						long parseTimeTracker = System.currentTimeMillis();
						
						JSONObject obj = (JSONObject) (new JSONParser()).parse(new InputStreamReader(con.getInputStream()));

						if (!"ok".equals((String) obj.get("status"))) {
							System.out.println("Update failure: " + obj.get("status"));
							con.disconnect();
							failures++;
							updateTime(System.currentTimeMillis(), executionTimeTracker, parseTimeTracker);
							return Boolean.FALSE;
						}
						
						JSONObject jsonData = (JSONObject) obj.get("data");
						String localData = (String) jsonData.get("block");
						if (!localData.equals(data)) {
							System.out.print("Update transitioned to new block. "
									+ (lastBlockUpdate > 0 ? " Last block took: " + ((System.currentTimeMillis() - lastBlockUpdate) / 1000) + "s. " : ""));
							data = localData;
							lastBlockUpdate = System.currentTimeMillis();
							System.out.print("Best DL on last block: " + bestDL.getAndSet(Long.MAX_VALUE) + " ");
							endline = true;
						}
						BigInteger localDifficulty = new BigInteger((String) jsonData.get("difficulty"));
						if (!localDifficulty.equals(difficulty)) {
							System.out.print("Difficulty updated to " + localDifficulty + ". ");
							difficulty = localDifficulty;
							endline = true;
						}
						long localLimit = 0;
						if (MinerType.pool.equals(type)) {
							localLimit = Long.parseLong(jsonData.get("limit").toString());
							publicKey = (String) jsonData.get("public_key");
						} else {
							localLimit = 240;
						}
						
						if (limit != localLimit) {
							limit = localLimit;
						}
						
						long sinceLastReport = System.currentTimeMillis() - lastReport;
						if (sinceLastReport > 15000l) {//localLimit != limit || cycles == 3 || cycles == 18) {
							lastReport = System.currentTimeMillis();
							System.out.print("MinDL: " + limit + " \n  Total Hashes: " + hashes.get() + "H  Overall Avg Speed: " + avgSpeed(wallClockBegin) + "H/s  Last Reported Speed: " + cummSpeed
									+ "H/s\n  Updates last " + (sinceLastReport / 1000) + "s: " + updates + " Skipped: " + skips + " Failed: " + failures
									+ (updates > 0 ? "\n  Updates took avg: " + (updateTimeAvg.getAndSet(0) / (updates+failures)) + "ms  max: " + (updateTimeMax.getAndSet(Long.MIN_VALUE)) + "ms  min: " + (updateTimeMin.getAndSet(Long.MAX_VALUE)) + "ms": "")
									+ (updates > 0 ? "\n  Parsing updates took avg: " + (updateParseTimeAvg.getAndSet(0) / (updates+failures)) + "ms  max: " + (updateParseTimeMax.getAndSet(Long.MIN_VALUE)) + "ms  min: " + (updateParseTimeMin.getAndSet(Long.MAX_VALUE)) + "ms": "")
									+ "\n  Time since last block update: " + ((System.currentTimeMillis() - lastBlockUpdate) / 1000) + "s"
									+ " Best DL so far: " + bestDL.get() + "\n  Total time mining this session: " + ((System.currentTimeMillis() - wallClockBegin)/1000l) + "s");
							skips = 0;
							failures = 0;
							updates = 0;
							endline = true;
						}
						con.disconnect();
						updates++;
						updateTime(System.currentTimeMillis(), executionTimeTracker, parseTimeTracker);
						if (endline) System.out.println();
						return Boolean.TRUE;
					} catch (IOException | ParseException e) {
						lastUpdate = System.currentTimeMillis();
						System.out.println("Non-fatal Update failure: " + e.getMessage());
						//e.printStackTrace(); // TODO hide for release, non-fatal errors
						failures++;
						updateTime(System.currentTimeMillis() - executionTimeTracker);
						return Boolean.FALSE;
					}
				}
			});
			
			if (firstRun) { // Failures after initial are probably just temporary so we ignore them.
				try {
					if (update.get().booleanValue()) {
						firstRun = false;
					}
				} catch (InterruptedException|ExecutionException e) {
					System.err.println("Unable to successfully complete first update: " + e.getMessage());
				} finally {
					if (firstRun) { // failure!
						System.out.println("Unable to contact node in pool, failing.");
						active = false;
						break;
					}
				}
			}
			
			if (this.hasherCount.get() < maxHashers) {
				this.hashers.submit(HasherFactory.createHasher(hasherMode, this, php_uniqid()));
			}
			
			try {
				Thread.sleep(UPDATING_DELAY);
			} catch (InterruptedException ie) {
				active = false;
				// interruption shuts us down.
				System.out.println("Interruption detection, shutting down.");
			}
			
			if (cycles == 30) {
				if (sessionSubmits.get() > 0) {
					System.out.println("So far, found " + sessionSubmits.get() + " nonces, " + sessionRejects.get() + " rejected"
							+ "\n  Submits took avg: " + (submitTimeAvg.get() / (sessionSubmits.get()+sessionRejects.get())) 
							+ "ms  max: " + (submitTimeMax.get()) + "ms  min: " + (submitTimeMin.get())
							+ "\n  Parsing submits replies took avg: " + (submitParseTimeAvg.get() / (sessionSubmits.get()+sessionRejects.get())) 
							+ "ms  max: " + (submitParseTimeMax.get()) + "ms  min: " + (submitParseTimeMin.get()));
				}
				cycles = 0;
			}
			cycles ++;
		}
		
		this.updaters.shutdown();
		this.hashers.shutdown();
		this.submitters.shutdown();
	}
	
	protected void submit(final String nonce, final String argon, final long submitDL) {
		this.submitters.submit(new Runnable() {
			public void run() {
				long executionTimeTracker = System.currentTimeMillis();
				
				StringBuilder extra = new StringBuilder(node);
				extra.append("/mine.php?q=submitNonce");
				try {
					URL url = new URL(extra.toString());
					HttpURLConnection con = (HttpURLConnection) url.openConnection();
					con.setRequestMethod("POST");
					con.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
					con.setDoOutput(true);
					DataOutputStream out = new DataOutputStream(con.getOutputStream());
					
					// TODO: make elegant.
					StringBuilder data = new StringBuilder();
					
					// argon, just the hash bit since params are universal
					data.append(URLEncoder.encode("argon", "UTF-8")).append("=")
							.append(URLEncoder.encode(argon.substring(29), "UTF-8")).append("&");
					// nonce
					data.append(URLEncoder.encode("nonce", "UTF-8")).append("=")
							.append(URLEncoder.encode(nonce, "UTF-8")).append("&");
					// private key?
					data.append(URLEncoder.encode("private_key", "UTF-8")).append("=")
							.append(URLEncoder.encode(privateKey, "UTF-8")).append("&");
					// public key
					data.append(URLEncoder.encode("public_key", "UTF-8")).append("=")
							.append(URLEncoder.encode(publicKey, "UTF-8")).append("&");
					// address (which is prikey again?)
					data.append(URLEncoder.encode("address", "UTF-8")).append("=")
							.append(URLEncoder.encode(privateKey, "UTF-8"));
					
					out.writeBytes(data.toString());
					
					System.out.println("Submitting to " + node + " a " + submitDL + " DL nonce: " +  nonce + " argon: " + argon);
					//System.out.println("Sending to " + extra.toString() + " Content:\n" + data.toString());
					
					out.flush();
					out.close();
					
					sessionSubmits.incrementAndGet();
					
					int status = con.getResponseCode();
					if (status != HttpURLConnection.HTTP_OK) {
						System.out.println("Submit of " + nonce + " failure: " + con.getResponseMessage());
						con.disconnect();
						sessionRejects.incrementAndGet();
						submitTime(System.currentTimeMillis() - executionTimeTracker);
						return;
					}
					
					long parseTimeTracker = System.currentTimeMillis();
					
					JSONObject obj = (JSONObject) (new JSONParser()).parse(new InputStreamReader(con.getInputStream()));
					// TESTINF: System.out.println(obj.toJSONString());
					if (!"ok".equals((String) obj.get("status"))) {
						sessionRejects.incrementAndGet();
						System.out.println("Submit of " + nonce + " rejected, nonce did not confirm: " + (String) obj.get("status"));
					} else {
						System.out.println("Submit of " + nonce + " confirmed!");
					}
					
					//System.out.println("Determination based on reply: " + obj.toJSONString());
					
					con.disconnect();
					submitTime(System.currentTimeMillis(), executionTimeTracker, parseTimeTracker);
				} catch (IOException | ParseException ioe) {
					System.err.println("Non-fatal but tragic: Failed during construction or receipt of submission: " + ioe.getMessage());
					//ioe.printStackTrace();
					submitTime(System.currentTimeMillis() - executionTimeTracker);
				}
			}
		});
	}
	
	/**
	 * Reference: http://php.net/manual/en/function.uniqid.php#95001
	 * @return a quasi-unique identifier.
	 */
	private String php_uniqid() {
		double m = ((double) (System.currentTimeMillis() / 10)) / 100d;
		return String.format("%8x%05x", (long) Math.floor(m), (long) ((m - Math.floor(m)) * 1000000) );
	}
	
	private String speed() {
		return String.format("%f", ((double) this.currentHashes.getAndSet(0)) / (((double) (System.currentTimeMillis() - this.lastSpeed.getAndSet(System.currentTimeMillis()))) / 1000d));
	}

	private String avgSpeed(long clockBegin) {
		return String.format("%f", this.hashes.doubleValue() / (((double) (System.currentTimeMillis() - clockBegin)) / 1000d));
	}
	
	private void updateTime(long duration) {
		this.updateTimeAvg.addAndGet(duration);
		this.updateTimeMax.getAndUpdate( (dl) -> {if (duration > dl) return duration; else return dl;} );
		this.updateTimeMin.getAndUpdate( (dl) -> {if (duration < dl) return duration; else return dl;} );
	}
	
	private void updateTime(long instance, long total, long parse) {
		updateTime(instance - total);
		long duration = instance - parse;
		
		this.updateParseTimeAvg.addAndGet(duration);
		this.updateParseTimeMax.getAndUpdate( (dl) -> {if (duration > dl) return duration; else return dl;} );
		this.updateParseTimeMin.getAndUpdate( (dl) -> {if (duration < dl) return duration; else return dl;} );
	}
	
	private void submitTime(long duration) {
		this.submitTimeAvg.addAndGet(duration);
		this.submitTimeMax.getAndUpdate( (dl) -> {if (duration > dl) return duration; else return dl;} );
		this.submitTimeMin.getAndUpdate( (dl) -> {if (duration < dl) return duration; else return dl;} );
	}
	
	private void submitTime(long instance, long total, long parse) {
		submitTime(instance - total);
		long duration = instance - parse;
		this.submitParseTimeAvg.addAndGet(duration);
		this.submitParseTimeMax.getAndUpdate( (dl) -> {if (duration > dl) return duration; else return dl;} );
		this.submitParseTimeMin.getAndUpdate( (dl) -> {if (duration < dl) return duration; else return dl;} );		
	}
	
	protected BigInteger getDifficulty() {
		return this.difficulty;
	}
	
	protected String getBlockData() {
		return this.data;
	}
	
	protected long getLimit() {
		return this.limit;
	}
	
	protected String getPublicKey() {
		return this.publicKey;
	}
	
	private void startTest() {
		System.out.println("Static tests using " + this.maxHashers + " iterations as cap");
		
		System.out.println("Utility Test on " + this.publicKey);
		String refKey = this.publicKey;
		
		String decode = Utility.base58_decode(refKey);
		BigInteger decInt = Utility.base58_decodeInt(refKey);
		System.out.println("  base58_decode: (" + decode.length() + ") " + decode );
		System.out.println("  base58_decodeInt: " + decInt.toString() + " (" + decInt.doubleValue() + ") ");
		

		byte[] nonce = new byte[32];
		String encNonce = null;
		String encNonce2 = null;
		String encNonce3 = null;
		String encNonce4 = null;
		SecureRandom random = new SecureRandom();
		StringBuilder sb = new StringBuilder();
		Encoder enc = Base64.getEncoder();
		char[] arr = null;
		
		
		long a = 0l;
		long b = 0l;
		long c = 0l;
		long d = 0l;
		
		for (int i = 0; i < this.maxHashers; i++) {
			random.nextBytes(nonce);
			encNonce = enc.encodeToString(nonce);
			encNonce2 = enc.encodeToString(nonce);
			encNonce3 = enc.encodeToString(nonce);
			encNonce4 = enc.encodeToString(nonce);
			
			long s = System.currentTimeMillis();
			encNonce = encNonce.replaceAll("[^a-zA-Z0-9]", ""); // TODO: static test vs other impls
			a += System.currentTimeMillis() - s;
			
			s = System.currentTimeMillis();
			sb = new StringBuilder(encNonce2.length());
			arr = encNonce2.toCharArray();
			for (char ar : arr) {
				if (ar >= '0' && ar <= '9' || ar >= 'a' && ar <= 'z' || ar >= 'A' && ar <= 'Z') {
					sb.append(ar);
				}
			}
			encNonce2 = sb.toString();
			b += System.currentTimeMillis() - s;
			assert encNonce.equals(encNonce2);
			
			s = System.currentTimeMillis();
			StringBuilder sb2 = new StringBuilder(encNonce3.length());
			char ar = encNonce3.charAt(0);
			for (int j = 0; j < encNonce3.length() - 1; ar = encNonce3.charAt(++j)) {
				if (ar >= '0' && ar <= '9' || ar >= 'a' && ar <= 'z' || ar >= 'A' && ar <= 'Z') {
					sb2.append(ar);
				}
			}
			encNonce3 = sb2.toString();
			c += System.currentTimeMillis() - s;
			assert encNonce.equals(encNonce3);
			
			s = System.currentTimeMillis();
			StringBuilder sb3 = new StringBuilder(encNonce4.length());
			encNonce4.chars().forEach( (aj) -> {
				if (aj >= '0' && aj <= '9' || aj >= 'a' && aj <= 'z' || aj >= 'A' && aj <= 'Z') {
					sb3.append((char) aj);
				}
			});
			encNonce4 = sb3.toString();
			d += System.currentTimeMillis() - s;
			assert encNonce.equals(encNonce4);
		}
		
		System.out.println("stripFunction Iterations: " + this.maxHashers + " a: " + a + " b: " + b + " c: " + c + " d: " + d);
		
		System.out.println("Done static testing.");
	}
}
