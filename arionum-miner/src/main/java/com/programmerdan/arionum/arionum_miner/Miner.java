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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Random;
import java.util.Base64.Encoder;
import java.util.Scanner;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import de.mkammerer.argon2.Argon2Factory.Argon2Types;
import de.mkammerer.argon2.jna.Argon2Library;
import de.mkammerer.argon2.jna.JnaUint32;
import de.mkammerer.argon2.jna.Size_t;

/**
 * Miner. Functional equiv of arionum-miner.
 *
 */
public class Miner implements UncaughtExceptionHandler {
	
	public static final long UPDATING_DELAY = 2000l;
	private int maxHashers;
	/**
	 * Non-main thread group that handles submitting nonces.
	 */
	private final ExecutorService submitters;
	
	/*
	 * Worker statistics
	 */
	private final ConcurrentHashMap<String, AtomicLong> workerHashes;
	private final ConcurrentHashMap<String, AtomicLong> workerBlockShares;
	private final ConcurrentHashMap<String, AtomicLong> workerBlockFinds;
	private final ConcurrentHashMap<String, AtomicLong> workerRoundBestDL;
	/**
	 * Clear this every 100 hashes and dump into the avg record 
	 */
	private final ConcurrentHashMap<String, AtomicLong> workerArgonTime;
	/**
	 * Clear this every 100 hashes and dump into the avg record
	 */
	private final ConcurrentHashMap<String, AtomicLong> workerNonArgonTime;
	/**
	 * Computed argon vs nonargon ratio -- ideal is more time spent doing argon hashes, less other things!
	 */
	private final ConcurrentHashMap<String, Double> workerLastRatio;
	private final ConcurrentHashMap<String, AtomicLong> workerRateHashes;
	private final ConcurrentHashMap<String, Double> workerRate;
	private final ConcurrentHashMap<String, Double> workerAvgRate;
	
	/**
	 * This is cummulative record of time the worker is aware of being alive and computing hashes.
	 * 
	 * Core efficiency is a measure of worker's known time vs. overall system time.
	 */
	private final ConcurrentHashMap<String, AtomicLong> workerClockTime;
	private final ConcurrentHashMap<String, AtomicLong> workerLastReport;
	private final ConcurrentHashMap<String, Double> workerCoreEfficiency;
	
	/**
	 * One or more hashing threads.
	 */
	private final ExecutorService hashers;
	protected final AtomicInteger hasherCount;
	private final ConcurrentHashMap<String, Hasher> workers;
	
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
	
	protected boolean colors = false;
	
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

	protected final long wallClockBegin;
	
	
	/* AUTO BOT MODE */
	private Profile activeProfile;
	private TreeSet<Profile> evaluatedProfiles;
	private ConcurrentLinkedQueue<Profile> profilesToEvaluate;
	private int coreCap;
	private long nextProfileSwap;
	private long profilesTested;
	
	protected static final long TEST_PERIOD = 270000; // 4.5 minutes
	protected static final long INIT_DELAY = 30000; // .5 minutes
	
	/* Future todo: reassess periodically */
	private long nextReassess;
	private Profile toReassess;
	
	public static void main(String[] args) {
		Miner miner = null;
		
		try {
			if (args == null || args.length == 0) {
				args = new String[] {"config.cfg"};
			}
			// let's try to load a config file first.
			if (args.length == 1) {
				// config file?
				ArrayList<String> lines = new ArrayList<>();
				File config = new File(args[0]);
				if (config.exists() && config.canRead()) {
					System.out.println(" Attempting to open " + args[0] + " as a config file for arionum-java-miner");
					
					BufferedReader in = new BufferedReader(new FileReader(config));
					String line = null;
					while ((line = in.readLine()) != null) {
						lines.add(line);
					}
					
					miner = new Miner(lines.toArray(new String[] {}));
				} else if (config.exists()) {
					miner = new Miner(args); // will cause error, probably.
				} else {
					Scanner console = new Scanner(System.in);
					System.out.print(" Would you like to generate a config and save it to " + args[0] + "? (y/N) ");
					String input = console.nextLine();
					
					if ("y".equalsIgnoreCase(input)) {
						System.out.print(" Choose type? (solo/pool) ");
						String type = console.nextLine();
						if ("solo".equalsIgnoreCase(type)) {
							lines.add("solo");
							
							System.out.print(" Node to connect to? ");
							lines.add(console.nextLine());

							System.out.print(" Public key to use with node? ");
							String address = console.nextLine();
							lines.add(address);

							System.out.print(" Private key to use with node? ");
							String paddress = console.nextLine();
							lines.add(paddress);
							
							System.out.println(" Would you like autotuning to occur? This will try to maximize your H/s ");
							System.out.println("   over the course of many minutes by adjusting hashers and cores. (y/N)");
							
							if ("y".equalsIgnoreCase(console.nextLine())) {
								lines.add("-1");
								lines.add("auto");
							} else {
								int defaultHashers = (int) Math.ceil(Runtime.getRuntime().availableProcessors() / 4d);
								System.out.print(" Simultaneous hashers to run? (you have " + Runtime.getRuntime().availableProcessors() + 
										" cores, leave blank for default of " + defaultHashers + ") ");
								String iterations = console.nextLine();
								if (iterations == null || iterations.trim().isEmpty()) {
									lines.add(String.valueOf(defaultHashers));
								} else {
									lines.add(iterations);
								}
								
								System.out.print(" Core type? (stable/basic/debug/experimental - default stable) ");
								String core = console.nextLine();
								if (core == null || core.trim().isEmpty()) {
									lines.add("stable");
								} else {
									lines.add(core);
								}
							}
							
							System.out.print(" Activate colors? (y/N) ");
							
							if ("y".equalsIgnoreCase(console.nextLine())) {
								lines.add("true");
							} else {
								lines.add("false");
							}
						} else if ("pool".equalsIgnoreCase(type)) {
							lines.add("pool");
							
							System.out.print(" Pool to connect to? (leave empty for http://aropool.com) ");
							String pool = console.nextLine();
							if (pool == null || pool.trim().isEmpty()) {
								lines.add("http://aropool.com");
							} else {
								lines.add(pool);
							}

							System.out.print(" Wallet address to use to claim shares? ");
							String address = console.nextLine();
							lines.add(address);
							
							System.out.println(" Would you like autotuning to occur? This will try to maximize your H/s ");
							System.out.println("   over the course of many minutes by adjusting hashers and cores. (y/N)");
							
							if ("y".equalsIgnoreCase(console.nextLine())) {
								lines.add("-1");
								lines.add("auto");
							} else {
								int defaultHashers = (int) Math.ceil(Runtime.getRuntime().availableProcessors() / 4d);
								System.out.print(" Simultaneous hashers to run? (you have " + Runtime.getRuntime().availableProcessors() + 
										" cores, leave blank for default of " + defaultHashers + ") ");
								String iterations = console.nextLine();
								if (iterations == null || iterations.trim().isEmpty()) {
									lines.add(String.valueOf(defaultHashers));
								} else {
									lines.add(iterations);
								}
								
								System.out.print(" Core type? (stable/basic/debug/experimental - default stable) ");
								String core = console.nextLine();
								if (core == null || core.trim().isEmpty()) {
									lines.add("stable");
								} else {
									lines.add(core);
								}
							}
							System.out.print(" Activate colors? (y/N) ");
							
							if ("y".equalsIgnoreCase(console.nextLine())) {
								lines.add("true");
							} else {
								lines.add("false");
							}
						} else if ("test".equalsIgnoreCase(type)) {
							lines.add("test");
							lines.add("http://aropool.com");
							
							System.out.print(" address to use in test run if needed by tests? ");
							String address = console.nextLine();
							lines.add(address);
							
							System.out.print(" iterations of tests to run? ");
							String iterations = console.nextLine();
							lines.add(iterations);
						} else {
							System.err.println("I don't recognize that type. Aborting!");
							System.exit(1);
						}

						miner = new Miner(lines.toArray(new String[] {}));
						
						try (PrintWriter os = new PrintWriter(new FileWriter(config))) {
							
							for (String line : lines) {
								os.println(line);
							}
							
							os.flush();
						} catch (IOException ie) {
							System.err.println("Failed to save settings ... continuing. Check permissions? Error message: " + ie.getMessage());
						}
					}
				}
			} else {
				miner = new Miner(args);
			}

		} catch (Exception e) {
			System.err.println("Failed to initialize! Check config? Error message: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
		Thread.setDefaultUncaughtExceptionHandler(miner);	
		miner.start();
	}
	
	public Miner(String[] args) {
		this.hasherMode = AdvMode.stable;
		
		this.updaters = Executors.newSingleThreadExecutor();
		this.submitters = Executors.newCachedThreadPool();
		this.hasherCount = new AtomicInteger();
		this.workers = new ConcurrentHashMap<String, Hasher>();
	
		/*stats*/
		this.workerHashes = new ConcurrentHashMap<String, AtomicLong>();
		this.workerBlockShares = new ConcurrentHashMap<String, AtomicLong>();
		this.workerBlockFinds = new ConcurrentHashMap<String, AtomicLong>();
		this.workerRoundBestDL = new ConcurrentHashMap<String, AtomicLong>();
		/**
		 * Clear this every 100 hashes and dump into the avg record 
		 */
		this.workerArgonTime = new ConcurrentHashMap<String, AtomicLong>();
		/**
		 * Clear this every 100 hashes and dump into the avg record
		 */
		this.workerNonArgonTime = new ConcurrentHashMap<String, AtomicLong>();
		/**
		 * Computed argon vs nonargon ratio -- ideal is more time spent doing argon hashes, less other things!
		 */
		this.workerLastRatio = new ConcurrentHashMap<String, Double>();
		this.workerRateHashes = new ConcurrentHashMap<String, AtomicLong>();
		this.workerRate = new ConcurrentHashMap<String, Double>();
		this.workerAvgRate = new ConcurrentHashMap<String, Double>();
		
		this.workerClockTime = new ConcurrentHashMap<String, AtomicLong>();
		this.workerLastReport = new ConcurrentHashMap<String, AtomicLong>();
		this.workerCoreEfficiency = new ConcurrentHashMap<String, Double>();
		
		/*end stats*/
		
		/* autotune */
		activeProfile = null;
		TreeSet<Profile> evaluatedProfiles = new TreeSet<Profile>();
		ConcurrentLinkedQueue<Profile> profilesToEvaluate = new ConcurrentLinkedQueue<Profile>();
		int coreCap = Runtime.getRuntime().availableProcessors();
		long nextProfileSwap = 0;
		long profilesTested = 0;

		/* end autotune */
		
		this.hashes = new AtomicLong();
		this.currentHashes = new AtomicLong();
		this.bestDL = new AtomicLong(Long.MAX_VALUE);
		this.sessionSubmits = new AtomicLong();
		this.sessionRejects = new AtomicLong();
		this.lastSpeed = new AtomicLong();
		
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
				if (args.length > 6) {
					this.colors = Boolean.parseBoolean(args[6].trim());
				}
			} else if (MinerType.pool.equals(this.type)) {
				this.privateKey = this.publicKey;
				this.maxHashers = args.length > 3 ? Integer.parseInt(args[3].trim()) : 1;
				if (args.length > 4) {
					this.hasherMode = AdvMode.valueOf(args[4].trim());
				}
				if (args.length > 5) {
					this.colors = Boolean.parseBoolean(args[5].trim());
				}

			} else if (MinerType.test.equals(this.type)) { // internal test mode, transient benchmarking.
				this.maxHashers = args.length > 3 ? Integer.parseInt(args[3].trim()) : 1;
			}
			
			if (AdvMode.auto.equals(this.hasherMode)) {
				this.maxHashers = -1;
			}
			
			System.out.println("Active config:");			
			System.out.println("  type: " + this.type);
			System.out.println("  node: " + this.node);
			System.out.println("  public-key: " + this.publicKey);
			System.out.println("  private-key: " + this.privateKey);
			System.out.println("  hasher-count: " + this.maxHashers);
			System.out.println("  hasher-mode: " + this.hasherMode);
			System.out.println("  colors: " + this.colors);
		} catch (Exception e) {
			System.err.println("Invalid configuration: " + (e.getMessage()));
			System.err.println("  type: " + this.type);
			System.err.println("  node: " + this.node);
			System.err.println("  public-key: " + this.publicKey);
			System.err.println("  private-key: " + this.privateKey);
			System.err.println("  hasher-count: " + this.maxHashers);
			System.err.println("  hasher-mode: " + this.hasherMode);
			System.err.println("  colors: " + this.colors);
			System.err.println();
			System.err.println("Usage: ");
			System.err.println("  java -jar arionum-miner.jar pool http://aropool.com address [#hashers] [basic|debug|experimental] [true|false]");
			System.err.println("  java -jar arionum-miner.jar solo node-address pubKey priKey [#hashers] [basic|debug|experimental] [true|false]");
			System.err.println(" where:");
			System.err.println("   [#hashers] is # of hashers to spin up. Default 1.");
			System.err.println("   [stable|basic|debug|experimental|auto] is type of hasher to run -- stable is always stable, basic is php ref, debug is chatty, experimental has no guarantees but is usually faster. Auto lets java miner pick best config. Default stable.");
			System.err.println("   [true|false] is if colored output is enabled -- does not work on Windows systems unless in Cygwin bash shell. Default false.");
					
			System.exit(1);
		}
		
		System.out.println("You have " + Runtime.getRuntime().availableProcessors() + " processors vs. " + this.maxHashers + " hashers. ");
		

		this.hashers = Executors.newFixedThreadPool(this.maxHashers > 0 ? this.maxHashers : Runtime.getRuntime().availableProcessors(),
				Executors.privilegedThreadFactory());
		
		this.limit = 240; // default
		this.worker = php_uniqid();
		this.wallClockBegin = System.currentTimeMillis();
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
		
		if (AdvMode.auto.equals(this.hasherMode)) {
			// BOOTSTRAP
			for (AdvMode mode: AdvMode.values()) {
				if (mode.useThis()) {
					Profile execProfile = new Profile(mode);
					this.profilesToEvaluate.offer(execProfile);
				}
			}
			nextProfileSwap = System.currentTimeMillis();
			activeProfile = null;
			profilesTested = 0;
		}
		
		active = true;
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
							workerRoundBestDL.forEachValue(2, (dl) -> {dl.set(Long.MAX_VALUE);}); // reset best DL 
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

						if (endline) {
							updateWorkers();
						}
						long sinceLastReport = System.currentTimeMillis() - lastReport;
						if (sinceLastReport > 15000l) {
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
						if (endline) {
							System.out.println();
						}
						if (sinceLastReport > 15000l && sinceLastReport < 5000000000l) {
							printWorkerStats();
						}

						return Boolean.TRUE;					
					} catch (IOException | ParseException e) {
						lastUpdate = System.currentTimeMillis();
						if (!(e instanceof SocketTimeoutException)) {
							System.out.println("Non-fatal Update failure: " + e.getMessage());
						}
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
						System.out.println("Unable to contact node in pool, please try again in a moment.");
						active = false;
						break;
					}
				}
			}
			
			if (!AdvMode.auto.equals(this.hasherMode) && this.hasherCount.get() < maxHashers) {
				while (this.workers.size() < maxHashers) {
					String workerId = php_uniqid();
					Hasher hasher = HasherFactory.createHasher(hasherMode, this, workerId);
					updateWorker(hasher);
					this.hashers.submit(hasher);
					addWorker(workerId, hasher);
				}
			} else if (AdvMode.auto.equals(this.hasherMode)) { // auto adjust! 
				Profile newActiveProfile = activeProfile;
				
				// Check, have we any profile active? If not, pick a bootstrap profile.
				if (activeProfile == null) {
					newActiveProfile = profilesToEvaluate.poll();
				// Now check, are we done profiling out current profile?
				} else if (Profile.Status.DONE.equals(activeProfile.getStatus())) {
					// We are done, do we have any left to check?
					if (profilesToEvaluate.isEmpty()) {
						// check how it stacks up; if this profile is highest, check if we have more profiling work to do.
						// Otherwise, if not highest, switch to best.
						evaluatedProfiles.add(activeProfile);
						newActiveProfile = evaluatedProfiles.pollLast();
					// We do have more to check! Let's lodge what we learned and switch profiles.
					} else {
						evaluatedProfiles.add(activeProfile);
						newActiveProfile = profilesToEvaluate.poll();
					}
				}
				
				if (activeProfile == null || !newActiveProfile.equals(activeProfile)) {
					System.out.println("About to evaluate profile " + newActiveProfile.toString());
					
					// reconfigure!
					
					activeProfile = newActiveProfile;
				}
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
			
			if (cycles % 2 == 0) {
				refreshFromWorkers();
			}
			
			cycles ++;
		}
		
		this.updaters.shutdown();
		this.hashers.shutdown();
		this.submitters.shutdown();
	}
	
	protected void addWorker(String workerId, Hasher hasher) {
		workers.put(workerId, hasher);
	}
	
	protected void releaseWorker(String workerId) {
		workers.remove(workerId);
	}
	
	/**
	 * We update all workers with latest information from pool / node
	 */
	protected void updateWorkers() {
		workers.forEach( (workerId, hasher) -> { if (hasher != null && hasher.isActive()) { updateWorker(hasher); } else { releaseWorker(workerId); }});
	}
	
	/**
	 * We update a specific worker with latest information from pool / node.
	 * @param hasher the worker to update
	 */
	protected void updateWorker(Hasher hasher) {
		hasher.update(getDifficulty(), getBlockData(), getLimit(), getPublicKey());
	}

	/**
	 * When a new worker is started, we zero out its stats.
	 * 
	 * @param workerId
	 */
	protected void workerInit(final String workerId) {
		workerHashes.put(workerId, new AtomicLong(0l));
		workerBlockShares.put(workerId, new AtomicLong(0l));
		workerBlockFinds.put(workerId, new AtomicLong(0l));
		workerArgonTime.put(workerId, new AtomicLong(0l));
		workerNonArgonTime.put(workerId, new AtomicLong(0l));
		workerLastRatio.put(workerId, 0.0d);
		workerRateHashes.put(workerId, new AtomicLong(0l));
		workerRoundBestDL.put(workerId,  new AtomicLong(Long.MAX_VALUE));
		workerRate.put(workerId, 0.0d);
		workerAvgRate.put(workerId, 0.0d);
		workerClockTime.put(workerId, new AtomicLong(0l));
		workerCoreEfficiency.put(workerId,  0.0d);
		workerLastReport.put(workerId, new AtomicLong(System.currentTimeMillis()));
	}
	
	/**
	 * Periodically we ask for updated information from all workers.
	 */
	protected void refreshFromWorkers() {
		//long wallTime = System.currentTimeMillis() - lastWorkerReport;
		try {
			AtomicLong newRate = new AtomicLong();
			workers.forEach(this.maxHashers,  (workerId, hasher) -> {
				long allHashes = hasher.getHashes();
				workerHashes.get(workerId).set(allHashes);
				
				long rateHashes = hasher.getHashesRecentExp();
				long recentHashes = hasher.getHashesRecent();
				workerRateHashes.get(workerId).set(rateHashes);
				currentHashes.getAndAdd(recentHashes);
				hashes.getAndAdd(recentHashes);
				
				long localDL = hasher.getBestDL();
				bestDL.getAndUpdate((dl) -> {if (localDL < dl) return localDL; else return dl;});
				workerRoundBestDL.get(workerId).set(localDL);
				
				// shares are nonces < difficulty > 0 and >= 240
				workerBlockShares.get(workerId).set(hasher.getShares());
				// finds are nonces < 240 (block discovery)
				workerBlockFinds.get(workerId).set(hasher.getFinds());
				
				long argonTime = hasher.getArgonTimeExp();
				workerArgonTime.get(workerId).set(argonTime);
				
				long nonArgonTime = hasher.getNonArgonTimeExp();
				workerNonArgonTime.get(workerId).set(nonArgonTime);
				if (argonTime > 0) {
					workerLastRatio.put(workerId, (double) argonTime / ((double) argonTime + nonArgonTime));
				}
				
				long seconds = (argonTime + nonArgonTime) / 1000000000l;
				double rate = (double) rateHashes / ((double) seconds);
				workerRate.put(workerId, rate);
				workerAvgRate.put(workerId, (double) allHashes / ((double) (System.currentTimeMillis() - wallClockBegin) / 1000l) );
	
				long localMilliseconds = hasher.getLoopTime();
				workerCoreEfficiency.put(workerId, (((double) localMilliseconds)
						/ ((double) (System.currentTimeMillis() - workerLastReport.get(workerId).getAndSet(System.currentTimeMillis()) ))));
				
				workerClockTime.get(workerId).addAndGet( localMilliseconds );
				
				hasher.clearTimers();
				
				newRate.addAndGet((long) (rate * 10000d));
			});
			this.lastSpeed.set(newRate.get());
		} catch (Exception e) {
			System.err.println("There was an issue getting stats from the workers. I'll try again in a bit...: " + e.getMessage());
		}
	}
	
	private void printWorkerStats() {
		System.out.println(String.format("  %13s %12s %7s %8s %7s %8s %7s %7s %5s %12s", "Worker ID", "Hashes", "Avg H/s", "TiC%", "Cur H/s", "Cur TiC%", "Argon %", "Shares", "Finds", "Block BestDL"));
		workerRoundBestDL.forEach((workerId, dl) -> {
			if (dl.get() < Long.MAX_VALUE) {
				StringBuilder workerString = new StringBuilder(69);
				workerString.append("  ").append(workerId).append(" ")
					.append(String.format("%12d ", workerHashes.get(workerId).get()))
					.append(String.format("%7.2f ", workerAvgRate.get(workerId)))
					.append(String.format("%8.2f ", (workerClockTime.get(workerId).doubleValue() / (double) (System.currentTimeMillis() - wallClockBegin)) * 100d))
					.append(String.format("%7.2f ", workerRate.get(workerId)))
					.append(String.format("%8.2f ", workerCoreEfficiency.get(workerId) * 100d))
					.append(String.format("%7.3f ", workerLastRatio.get(workerId) * 100d))
					.append(String.format("%7d ", workerBlockShares.get(workerId).get()))
					.append(String.format("%5d ", workerBlockFinds.get(workerId).get()))
					.append(String.format("%12d", dl.get()));
					System.out.println(workerString.toString());
			}
		});
		if (AdvMode.auto.equals(this.hasherMode)) {
			System.out.println("In auto-adjust mode. Next readjustment in " + "" + "s.");
		}
	}
	
	protected void submit(final String nonce, final String argon, final long submitDL) {
		this.submitters.submit(new Runnable() {
			public void run() {
				
				StringBuilder extra = new StringBuilder(node);
				extra.append("/mine.php?q=submitNonce");
				int failures = 0;
				boolean notDone = true;
				while (notDone) {
					long executionTimeTracker = System.currentTimeMillis();
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
							failures ++;
							submitTime(System.currentTimeMillis() - executionTimeTracker);
						} else {
							long parseTimeTracker = System.currentTimeMillis();
							
							JSONObject obj = (JSONObject) (new JSONParser()).parse(new InputStreamReader(con.getInputStream()));
		
							if (!"ok".equals((String) obj.get("status"))) {
								sessionRejects.incrementAndGet();
								System.out.println("Submit of " + nonce + " rejected, nonce did not confirm: " + (String) obj.get("status"));
							} else {
								System.out.println("Submit of " + nonce + " confirmed!");
							}
							notDone = false;
							
							con.disconnect();
		
							submitTime(System.currentTimeMillis(), executionTimeTracker, parseTimeTracker);
						}
					} catch (IOException | ParseException ioe) {
						failures ++;
						System.err.println("Non-fatal but tragic: Failed during construction or receipt of submission: " + ioe.getMessage());
						submitTime(System.currentTimeMillis() - executionTimeTracker);
					}
					if (failures > 5) {
						notDone = false;
						System.err.println("After more then five failed attempts to submit this nonce, we are giving up.");
					}
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
		return String.format("%12.4f", (this.lastSpeed.doubleValue() / 10000d));
	}

	private String avgSpeed(long clockBegin) {
		return String.format("%12.4f", this.hashes.doubleValue() / (((double) (System.currentTimeMillis() - clockBegin)) / 1000d));
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
		
		/*String decode = Utility.base58_decode(refKey);
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
		
		System.out.println("stripFunction Iterations: " + this.maxHashers + " a: " + a + " b: " + b + " c: " + c + " d: " + d);*/
		
		final JnaUint32 iterations = new JnaUint32(4);
		final JnaUint32 memory= new JnaUint32(16384);
		final JnaUint32 parallelism = new JnaUint32(4);
		final JnaUint32 saltLenI = new JnaUint32(16);
		final JnaUint32 hashLenI = new JnaUint32(32);
		final Size_t saltLen = new Size_t(16l);
		final Size_t hashLen = new Size_t(32l);
		
		final Size_t encLen = Argon2Library.INSTANCE.argon2_encodedlen(iterations, memory, parallelism,
                saltLenI, hashLenI, Argon2Types.ARGON2i.getJnaType());
        final byte[] encoded = new byte[encLen.intValue()];
        byte[] salt = new byte[saltLen.intValue()];

        // warmup the cpu
		for (int i = 0; i < this.maxHashers*100; i++) {
			new Random().nextBytes(salt);
		}
        
        new Random().nextBytes(salt);
    	byte[] hashBaseBuffer = "thisisahashofatypicalsizebutwouldntyouknowititshardtothinkofsillystufftotypetotestasystemlikethissinceweassumethingsworkmostofthetimevenwhenwehavenoreasontothinkthatbuttestingiscritical".getBytes();
    	Size_t hashBaseBufferSize = new Size_t(hashBaseBuffer.length);
        
    	difficulty = new BigInteger("100000000");
    	
    	long rawtime = 0l;
    	long nortime = 0l;
    	long cpytime = 0l;
    	long trntime = 0l;
    	long shatime = 0l;
    	long dltime = 0l;
    	
    	Argon2Library argon2lib = Argon2Library.INSTANCE;
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
    	
    	for (int j = 0; j < 10;j ++) {
	    	long hashS = System.nanoTime();
			
			for (int i = 0; i < this.maxHashers/10; i++) {
				argon2lib.argon2i_hash_encoded(
		                iterations, memory, parallelism, hashBaseBuffer, hashBaseBufferSize,
		                salt, saltLen, hashLen, encoded, encLen
		        );
			}
			
			long hashE = System.nanoTime();
			
			//System.out.println(String.format("rawlib %dns ea.", ((hashE - hashS) / (this.maxHashers/1))));
			rawtime += (hashE - hashS);
			
			hashS = System.nanoTime();
			
			for (int i = 0; i < this.maxHashers/10; i++) {
				argon2.hash(4, 16384, 4, hashBaseBuffer.toString());
			}
			
			hashE = System.nanoTime();
			
			//System.out.println(String.format("norlib %dns ea.", ((hashE - hashS) / ( this.maxHashers/10))));
			nortime += (hashE - hashS);
			
			int offset = hashBaseBuffer.length - encoded.length;
			
			hashS = System.nanoTime();
			
			for (int i = 0 ; i < this.maxHashers*100; i++) {
				System.arraycopy(encoded, 0, hashBaseBuffer, offset, encLen.intValue());
			}
			
			hashE = System.nanoTime();
			
			cpytime += (hashE - hashS);
			
			StringBuilder hashBase = null;
			String base;
			byte[] byteBase = new byte[60];
			hashS = System.nanoTime();
			for (int i = 0; i < this.maxHashers*100; i++) {
				hashBase = new StringBuilder(hashBaseBuffer.length + encoded.length); // size of key + none + difficult + argon + data + spacers
				hashBase.append(new String(hashBaseBuffer));
				hashBase.append(new String(encoded));
				base = hashBase.toString();
				byteBase = base.getBytes();
			}
			hashE = System.nanoTime();
			
			trntime += (hashE - hashS);
			
			hashBaseBuffer = "thisisahashofatypicalsizebutwouldntyouknowititshardtothinkofsillystufftotypetotestasystemlikethissinceweassumethingsworkmostofthetimevenwhenwehavenoreasontothinkthatbuttestingiscritical".getBytes();
			
			hashS = System.nanoTime();
			for (int i = 0; i < this.maxHashers/10; i++) {
				byteBase = sha512.digest(hashBaseBuffer);
				for (int q = 0; i < 5; i++) {
					byteBase = sha512.digest(byteBase);
				}
			}
			hashE = System.nanoTime();
			shatime += (hashE - hashS);
			
			hashS = System.nanoTime();
			for (int i = 0; i < this.maxHashers*100; i++) {
				StringBuilder duration = new StringBuilder(25);
				duration.append(byteBase[10] & 0xFF).append(byteBase[15] & 0xFF).append(byteBase[20] & 0xFF)
						.append(byteBase[23] & 0xFF).append(byteBase[31] & 0xFF).append(byteBase[40] & 0xFF)
						.append(byteBase[45] & 0xFF).append(byteBase[55] & 0xFF);
	
				long finalDuration = new BigInteger(duration.toString()).divide(this.difficulty).longValue();
			}
			hashE = System.nanoTime();
			
			dltime += (hashE - hashS);
			
			System.out.println("Round " + j);
    	}
    	
		System.out.println(String.format("rawlib %dns ea.", (rawtime / this.maxHashers)));
		System.out.println(String.format("norlib %dns ea.", (nortime / this.maxHashers)));
		System.out.println(String.format("arraycpy %dns ea.", (cpytime / (this.maxHashers * 1000))));
		System.out.println(String.format("stringtr %dns ea.", (trntime / (this.maxHashers * 1000))));
		System.out.println(String.format("shalib %dns ea.", (shatime / this.maxHashers)));
		System.out.println(String.format("dlcomp %dns ea.", (dltime / (this.maxHashers * 1000))));
		System.exit(0);
		
		SecureRandom random = new SecureRandom();
		Random insRandom = new Random(random.nextLong());
		
		byte[] sale = new byte[16];
		
		long saltS = System.nanoTime();
		
		for (int i = 0; i < this.maxHashers; i++) {
			random.nextBytes(sale);
		}
		
		long saltE = System.nanoTime();
		
		System.out.println(String.format("Secure %dns ea.", ((saltE - saltS) / this.maxHashers)));
		
		
		saltS = System.nanoTime();
		
		for (int i = 0; i < this.maxHashers; i++) {
			insRandom.nextBytes(sale);
		}
		
		saltE = System.nanoTime();
		
		System.out.println(String.format("Insecure %dns ea.", ((saltE - saltS) / this.maxHashers)));
			
		Encoder encoder = Base64.getEncoder();
		String rawHashBase;
		byte[] nonce = new byte[32];
		String rawNonce;
	
		
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
		
		BigInteger difficulty = BigInteger.valueOf(263701626l);
		String difficultyString = difficulty.toString();
		String data = "thisistestdatandnotreallyrepresentativebutletsuseit.";
		// prealloc probably saves us 10% on this op sequence
		hashBase = new StringBuilder(500); // size of key + nonce + difficult + argon + data + spacers
		hashBase.append(refKey).append("-");
		hashBase.append(nonceSb).append("-");
		hashBase.append(data).append("-");
		hashBase.append(difficultyString);
		
		rawNonce = nonceSb.toString();
		rawHashBase = hashBase.toString();
		
		System.out.println("rawNonce: " + rawNonce + " \nrawHashBase: " + rawHashBase);
		
		String argon = null;
		argon2 = Argon2Factory.create(Argon2Types.ARGON2i);

		
		byte[] byteBase = null;
		long[][] findBuckets = new long[8][256];
		System.out.println();
		for (int j = 0; j < this.maxHashers; j ++ ) {
			hashBase = new StringBuilder(500);
			argon = argon2.hash(4, 16384, 4, rawHashBase);
			hashBase.append(rawHashBase).append(argon);

			byteBase = hashBase.toString().getBytes();
			for (int i = 0; i < 5; i++) {
				byteBase = sha512.digest(byteBase);
			}
			byteBase = sha512.digest(byteBase);

			findBuckets[0][byteBase[10] & 0xFF]++;
			findBuckets[1][byteBase[15] & 0xFF]++;
			findBuckets[2][byteBase[20] & 0xFF]++;
			findBuckets[3][byteBase[23] & 0xFF]++;
			findBuckets[4][byteBase[31] & 0xFF]++;
			findBuckets[5][byteBase[40] & 0xFF]++;
			findBuckets[6][byteBase[45] & 0xFF]++;
			findBuckets[7][byteBase[55] & 0xFF]++;
			
			System.out.println("\033[1A\033[2K" + j);
		}
		
		int uniformTarget = this.maxHashers / 256;
		
		for (int k = 0; k < 8; k++) {
			StringBuilder sb = new StringBuilder(256);
			long sum = 0; 
			long[] interior = new long[uniformTarget * 3 + 1];
			for (int q = 0; q < 256; q++) {
				if (findBuckets[k][q] > uniformTarget * 3) {
					interior[uniformTarget * 3]++;
				} else {
					interior[(int) findBuckets[k][q]]++;
				}
			}
			sb.append(" [");
			for (int p = 0; p < uniformTarget * 3 + 1 ; p++) {
				sb.append("[").append(interior[p]);
			}
			System.out.println(k + "] " + sb.toString());
		}
		
		System.out.println();

		findBuckets = new long[8][256];
		for (int j = 0; j < this.maxHashers; j ++ ) {
			random.nextBytes(nonce);
			encNonce = encoder.encodeToString(nonce);

			nonceChar = encNonce.toCharArray();

			// shaves a bit off vs regex -- for this operation, about 50% savings
			nonceSb = new StringBuilder(encNonce.length());
			for (char ar : nonceChar) {
				if (ar >= '0' && ar <= '9' || ar >= 'a' && ar <= 'z' || ar >= 'A' && ar <= 'Z') {
					nonceSb.append(ar);
				}
			}
			
			// prealloc probably saves us 10% on this op sequence
			hashBase = new StringBuilder(500); // size of key + nonce + difficult + argon + data + spacers
			hashBase.append(refKey).append("-");
			hashBase.append(nonceSb).append("-");
			hashBase.append(data).append("-");
			hashBase.append(difficultyString);
			
			rawNonce = nonceSb.toString();
			rawHashBase = hashBase.toString();
			
			hashBase = new StringBuilder(500);
			argon = argon2.hash(4, 16384, 4, rawHashBase);
			hashBase.append(rawHashBase).append(argon);

			byteBase = hashBase.toString().getBytes();
			for (int i = 0; i < 5; i++) {
				byteBase = sha512.digest(byteBase);
			}
			byteBase = sha512.digest(byteBase);

			findBuckets[0][byteBase[10] & 0xFF]++;
			findBuckets[1][byteBase[15] & 0xFF]++;
			findBuckets[2][byteBase[20] & 0xFF]++;
			findBuckets[3][byteBase[23] & 0xFF]++;
			findBuckets[4][byteBase[31] & 0xFF]++;
			findBuckets[5][byteBase[40] & 0xFF]++;
			findBuckets[6][byteBase[45] & 0xFF]++;
			findBuckets[7][byteBase[55] & 0xFF]++;
			
			System.out.println("\033[1A\033[2K" + j);
		}

		for (int k = 0; k < 8; k++) {
			StringBuilder sb = new StringBuilder(256);
			long[] interior = new long[uniformTarget * 3 + 1];
			for (int q = 0; q < 256; q++) {
				if (findBuckets[k][q] > uniformTarget * 3) {
					interior[uniformTarget * 3]++;
				} else {
					interior[(int) findBuckets[k][q]]++;
				}
			}
			sb.append(" [");
			for (int p = 0; p < uniformTarget * 3 + 1 ; p++) {
				sb.append("[").append(interior[p]);
			}
			System.out.println(k + "] " + sb.toString());
		}

		
		System.out.println("Done static testing.");
	}

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		System.err.println("Detected thread " + t.getName() + " death due to error: " + e.getMessage());
		e.printStackTrace();
		
		System.err.println("\n\nThis is probably fatal, so exiting now.");
		System.exit(1);
	}
}
