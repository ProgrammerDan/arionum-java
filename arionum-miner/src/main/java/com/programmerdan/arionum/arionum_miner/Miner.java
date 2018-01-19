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
	private long lastUpdate;
	private long lastReport; 
	private int cycles;
	private int skips;
	private int failures;
	private int updates;
	

	public static void main(String[] args) {
		(new Miner(args)).start();
	}
	
	public Miner(String[] args) {
		//TODO: increase
		this.hashers = Executors.newCachedThreadPool();
		this.updaters = Executors.newSingleThreadExecutor();
		this.submitters = Executors.newCachedThreadPool();
		this.hasherCount = new AtomicInteger();
		
		this.hashes = new AtomicLong();
		this.currentHashes = new AtomicLong();
		this.bestDL = new AtomicLong(Long.MAX_VALUE);
		this.sessionSubmits = new AtomicLong();
		this.sessionRejects = new AtomicLong();
		
		try {
			this.type = MinerType.valueOf(args[0]);
			this.node = args[1].trim();
			this.publicKey = args[2].trim();
			if (MinerType.solo.equals(this.type)) {
				this.privateKey = args[3].trim();
				this.maxHashers = args.length > 4 ? Integer.parseInt(args[4].trim()) : 1;
			} else if (MinerType.pool.equals(this.type)) {
				this.privateKey = this.publicKey;
				this.maxHashers = args.length > 3 ? Integer.parseInt(args[3].trim()) : 1;
			} else if (MinerType.test.equals(this.type)) { // internal test mode, transient benchmarking.
				this.maxHashers = args.length > 2 ? Integer.parseInt(args[2].trim()) : 1;
			}
		} catch (Exception e) {
			System.err.println("Invalid configuration: ");
			System.err.println("  type: " + this.type);
			System.err.println("  node: " + this.node);
			System.err.println("  public-key: " + this.publicKey);
			System.err.println("  private-key: " + this.privateKey);
			System.err.println("  hasher-count: " + this.maxHashers);
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
						
						int status = con.getResponseCode();
						
						lastUpdate = System.currentTimeMillis();
						
						if (status != HttpURLConnection.HTTP_OK) {
							System.out.println("Update failure: " + con.getResponseMessage());
							con.disconnect();
							failures ++;
							return Boolean.FALSE;
						}

						JSONObject obj = (JSONObject) (new JSONParser()).parse(new InputStreamReader(con.getInputStream()));

						if (!"ok".equals((String) obj.get("status"))) {
							con.disconnect();
							failures++;
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
									+ "\n  Time since last block update: " + ((System.currentTimeMillis() - lastBlockUpdate) / 1000) + "s"
									+ " Best DL so far: " + bestDL.get() + "\n  Total time mining this session: " + ((System.currentTimeMillis() - wallClockBegin)/1000l) + "s");
							skips = 0;
							failures = 0;
							updates = 0;
							endline = true;
						}
						con.disconnect();
						updates++;
						if (endline) System.out.println();
						return Boolean.TRUE;
					} catch (IOException | ParseException e) {
						lastUpdate = System.currentTimeMillis();
						System.out.println("Non-fatal Update failure: " + e.getMessage());
						//e.printStackTrace(); // TODO hide for release, non-fatal errors
						failures++;
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
				this.hashers.submit(new Hasher(this, php_uniqid()));
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
					System.out.println("So far, found " + sessionSubmits.get() + " nonces, " + 
							sessionRejects.get() + " rejected");
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
						return;
					}
					
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
					
				} catch (IOException | ParseException ioe) {
					System.err.println("Non-fatal but tragic: Failed during construction or receipt of submission: " + ioe.getMessage());
					//ioe.printStackTrace();
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
		return String.format("%f", ((double) this.currentHashes.getAndSet(0)) / (((double) (System.currentTimeMillis() - this.lastUpdate)) / 1000d));
	}

	private String avgSpeed(long clockBegin) {
		return String.format("%f", this.hashes.doubleValue() / (((double) (System.currentTimeMillis() - clockBegin)) / 1000d));
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
		
		System.out.println("Done static testing.");
	}
}
