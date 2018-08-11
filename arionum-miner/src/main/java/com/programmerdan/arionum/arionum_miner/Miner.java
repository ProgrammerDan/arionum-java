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
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Random;
import java.util.Base64.Encoder;
import java.util.LinkedList;
import java.util.Scanner;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.fusesource.jansi.Ansi.Color;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.programmerdan.arionum.arionum_miner.jna.*;
import com.sun.jna.Memory;
import com.diogonunes.jcdp.color.api.Ansi.Attribute;
import com.diogonunes.jcdp.color.api.Ansi.FColor;
import com.diogonunes.jcdp.color.api.Ansi.Attribute.*;
import com.diogonunes.jcdp.color.api.Ansi.BColor;
import com.diogonunes.jcdp.color.api.Ansi.FColor.*;
import com.diogonunes.jcdp.color.api.Ansi.BColor.*;

/**
 * Miner wrapper.
 * 
 * Implements hard fork 10800 -- Resistance
 * Implements hard fork 80000 -- triforce
 */
@SuppressWarnings({ "unused" })
public class Miner implements UncaughtExceptionHandler {

	public static final long UPDATING_DELAY = 2000l;
	public static final long UPDATING_REPORT = 45000l;
	public static final long UPDATING_STATS = 7500l;

	public static boolean PERMIT_AFINITY = true;
	public static boolean CHECK_BIND = true;
	
	private CPrint coPrint;
	
	private int maxHashers;
	/**
	 * Non-main thread group that handles submitting nonces.
	 */
	private final ExecutorService submitters;

	/*
	 * Worker statistics
	 */
	private final ConcurrentLinkedQueue<HasherStats> deadWorkerSociety;
	private final AtomicLong deadWorkers;
	private final ConcurrentLinkedDeque<Report> deadWorkerSummaries;
	private final ConcurrentHashMap<String, Long> deadWorkerLives;
	
	/**
	 * One or more hashing threads.
	 */
	private final ExecutorService hashers;
	protected final AtomicInteger hasherCount;
	private final ConcurrentHashMap<String, Hasher> workers;
	/**
	 * Idea here is some soft profiling; hashesPerSession records how many hashes a 
	 * particular worker accomplishes in a session, which starts at some initial value; 
	 * then we tune it based on observed results.
	 */
	private long hashesPerSession = 10l;
	private static final long MIN_HASHES_PER_SESSION = 5l;
	/**
	 * The session length is the target parameter generally tuned against
	 */
	private long sessionLength = 5000l;

	private static final long MIN_SESSION_LENGTH = 5000l;
	private static final long MAX_SESSION_LENGTH = 14000l;
	private static final long REBALANCE_DELAY = 300000l;
	
	private long lastRebalance;
	private double lastRebalanceHashRate = Double.MAX_VALUE;
	private double lastRebalanceTiC = 0.0d;
	private long lastRebalanceSessionLength = 0l;

	/**
	 * Last time we checked with workers
	 */
	private long lastWorkerReport;

	/**
	 * Count of all hashes produced by workers.
	 */
	protected final AtomicLong hashes;

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

	protected final AtomicLong blockShares;
	protected final AtomicLong blockFinds;
	protected final AtomicLong shareRejects;
	protected final AtomicLong findRejects;

	/**
	 * Let's thread off updating. We'll just hash constantly, and offload updating like submitting.
	 */
	private final ExecutorService updaters;

	protected boolean active = false;

	protected boolean colors = false;
	
	protected boolean pause = true;

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
	private long height;
	private long lastBlockUpdate;
	private boolean mineRecommended;
	private int mineIters;
	private int mineMem;
	private int mineThreads;

	/* Update related */
	private AtomicLong lastSpeed;
	private AtomicLong speedAccrue;
	private long lastUpdate;
	private long lastReport;
	private int cycles;
	private int supercycles;
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
	
	/* External stats reporting */
	private String statsHost;
	private String statsInvoke;
	private String statsToken;
	private boolean post;
	private ConcurrentHashMap<String, HasherStats> statsStage;
	private ConcurrentLinkedQueue<HasherStats> statsReport;
	private final ExecutorService stats;
	
	protected static final long REPORTING_INTERVAL = 60000l; // 60 seconds

	public static void main(String[] args) {
		Miner miner = null;

		try (Scanner console = new Scanner(System.in)) {
			if (args == null || args.length == 0) {
				args = new String[] { "config.cfg" };
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
					in.close();

					miner = new Miner(lines.toArray(new String[] {}));
				} else if (config.exists()) {
					miner = new Miner(args); // will cause error, probably.
				} else {
					System.out.print(" Would you like to generate a config and save it to " + args[0] + "? (y/N) ");
					String input = console.nextLine();

					if ("y".equalsIgnoreCase(input)) {
						String basetype = "pool";
						System.out.print(" Choose type? (solo/pool - leave blank for default of pool) ");
						String type = console.nextLine();
						if (type == null || "".equals(type.trim())) {
							type = basetype;
						}
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

							/*System.out.println(
									" Would you like autotuning to occur? This will try to maximize your H/s ");
							System.out.println(
									"   over the course of many minutes by adjusting hashers and parameters. (y/N)");

							if ("y".equalsIgnoreCase(console.nextLine())) {
								lines.add("-1");
								lines.add("auto");
							} else {*/
								int defaultHashers = Runtime.getRuntime().availableProcessors();
								System.out.print(" Simultaneous hashers to run? (you have "
										+ Runtime.getRuntime().availableProcessors()
										+ " cores, leave blank for default of " + defaultHashers + ") ");
								String iterations = console.nextLine();
								if (iterations == null || iterations.trim().isEmpty()) {
									lines.add(String.valueOf(defaultHashers));
								} else {
									lines.add(iterations);
								}

								/*
								 * System.out.print(" Core type? (standard) "); String core = console.nextLine(); if (core == null || core.trim().isEmpty()) { lines.add("stable"); } else { lines.add(core); }
								 */
								lines.add("standard");
							//}

							System.out.print(" Activate colors? (y/N) ");

							if ("y".equalsIgnoreCase(console.nextLine())) {
								lines.add("true");
							} else {
								lines.add("false");
							}
							
							String workerName = Miner.php_uniqid();
							System.out.println(" Worker name to report to node? (Each worker should have a unique name, leave empty for default: " + workerName + ") ");
							String tWorker = console.nextLine();
							if (tWorker != null && !tWorker.trim().isEmpty()) {
								lines.add(tWorker.trim());
							} else {
								lines.add(workerName);
							}

							// HF 80k
							System.out.print(" Respect mining pause hints? (y/N) ");
							lines.add(null);
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

							/*System.out.println(
									" Would you like autotuning to occur? This will try to maximize your H/s ");
							System.out.println(
									"   over the course of many minutes by adjusting hashers and parameters. (y/N)");

							if ("y".equalsIgnoreCase(console.nextLine())) {
								lines.add("-1");
								lines.add("auto");
							} else {*/
								int defaultHashers = Runtime.getRuntime().availableProcessors();
								System.out.print(" Simultaneous hashers to run? (you have "
										+ Runtime.getRuntime().availableProcessors()
										+ " cores, leave blank for default of " + defaultHashers + ") ");
								String iterations = console.nextLine();
								if (iterations == null || iterations.trim().isEmpty()) {
									lines.add(String.valueOf(defaultHashers));
								} else {
									lines.add(iterations);
								}

								/*
								 * System.out.print(" Core type? (stable/basic/debug/experimental - default stable) "); String core = console.nextLine(); if (core == null || core.trim().isEmpty()) { lines.add("stable"); } else {
								 * lines.add(core); }
								 */
								lines.add("standard");
							//}
							System.out.print(" Activate colors? (y/N) ");

							if ("y".equalsIgnoreCase(console.nextLine())) {
								lines.add("true");
							} else {
								lines.add("false");
							}

							String workerName = Miner.php_uniqid();
							System.out.println(" Worker name to report to node? (Each worker should have a unique name, leave empty for default: " + workerName + ") ");
							String tWorker = console.nextLine();
							if (tWorker != null && !tWorker.trim().isEmpty()) {
								lines.add(tWorker.trim());
							} else {
								lines.add(workerName);
							}

							// HF 80k
							System.out.print(" Respect mining pause hints? (y/N) ");
							lines.add(null);
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
						} else if ("benchmark".equalsIgnoreCase(type)) {
							lines.add("benchmark");

							System.out.println("Apologies, this mode is not available yet");
							System.exit(1);
							
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

							System.out.print(" Location of python binary? ");
							String python = console.nextLine();
							lines.add(python);

							System.out.print(" Location of python hasher? ");
							python = console.nextLine();
							lines.add(python);

							System.out.print(" Location of php binary? ");
							String php = console.nextLine();
							lines.add(php);

							System.out.print(" Location of php hasher? ");
							php = console.nextLine();
							lines.add(php);

							int defaultHashers = Runtime.getRuntime().availableProcessors() ;
							System.out.print(" Max simultaneous hashers to benchmark? (you have "
									+ Runtime.getRuntime().availableProcessors() + " cores, leave blank for default of "
									+ defaultHashers + ") ");
							String iterations = console.nextLine();
							if (iterations == null || iterations.trim().isEmpty()) {
								lines.add(String.valueOf(defaultHashers));
							} else {
								lines.add(iterations);
							}
							
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
							System.err.println(
									"Failed to save settings ... continuing. Check permissions? Error message: "
											+ ie.getMessage());
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
		this.hasherMode = AdvMode.standard;
		this.worker = php_uniqid();
		
		this.updaters = Executors.newSingleThreadExecutor();
		this.submitters = Executors.newCachedThreadPool();
		this.hasherCount = new AtomicInteger();

		this.workers = new ConcurrentHashMap<String, Hasher>();
		
		this.deadWorkerSociety = new ConcurrentLinkedQueue<>();
		this.deadWorkers = new AtomicLong(0l);
		this.deadWorkerLives = new ConcurrentHashMap<String, Long>();

		this.blockFinds = new AtomicLong();
		this.blockShares = new AtomicLong();
		this.findRejects = new AtomicLong();
		this.shareRejects = new AtomicLong();

		this.deadWorkerSummaries = new ConcurrentLinkedDeque<>();

		/* autotune */
		activeProfile = null;
		TreeSet<Profile> evaluatedProfiles = new TreeSet<Profile>();
		ConcurrentLinkedQueue<Profile> profilesToEvaluate = new ConcurrentLinkedQueue<Profile>();
		int coreCap = Runtime.getRuntime().availableProcessors();
		long nextProfileSwap = 0;
		long profilesTested = 0;
		/* end autotune */

		/* stats report */
		this.statsHost = null;
		this.statsInvoke= "report.php";
		this.statsToken = php_uniqid();
		this.post = false;
		this.statsStage = new ConcurrentHashMap<String, HasherStats>();
		this.statsReport = new ConcurrentLinkedQueue<HasherStats>();
		this.stats = Executors.newCachedThreadPool();
		/* end stats report */
		
		this.hashes = new AtomicLong();
		this.bestDL = new AtomicLong(Long.MAX_VALUE);
		this.sessionSubmits = new AtomicLong();
		this.sessionRejects = new AtomicLong();
		this.lastSpeed = new AtomicLong();
		this.speedAccrue = new AtomicLong();

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
				if (args.length > 7) {
					String workerName = args[7] != null ? args[7].trim() : null;
					if (workerName != null && !"".equals(workerName)) {
						this.worker = workerName;
					} // else we leave "new" name.
				}
				if (args.length > 8) {
					String[] statsReport = args[8] != null ? args[8].trim().split(" ") : null;
					if (statsReport != null && statsReport.length > 2) {
						this.statsHost = statsReport[0];
						this.statsInvoke = statsReport[1];
						this.post = statsReport[2].equalsIgnoreCase("y") ? true : statsReport[2].equalsIgnoreCase("n") ? false : Boolean.parseBoolean(statsReport[2]);
					}
					if (statsReport != null && statsReport.length > 3) {
						this.statsToken = statsReport[3];
					}
				}
				if (args.length > 9) {
					this.pause = Boolean.parseBoolean(args[9].trim());
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
				if (args.length > 6) {
					String workerName = args[6] != null ? args[6].trim() : null;
					if (workerName != null && !"".equals(workerName)) {
						this.worker = workerName;
					} // else we leave "new" name.
				}

				if (args.length > 7) {
					String[] statsReport = args[7] != null ? args[7].trim().split(" ") : null;
					if (statsReport != null && statsReport.length > 2) {
						this.statsHost = statsReport[0];
						this.statsInvoke = statsReport[1];
						this.post = statsReport[2].equalsIgnoreCase("y") ? true : statsReport[2].equalsIgnoreCase("n") ? false : Boolean.parseBoolean(statsReport[2]);
					}
					if (statsReport != null && statsReport.length > 3) {
						this.statsToken = statsReport[3];
					}
				}
				if (args.length > 9) {
					this.pause = Boolean.parseBoolean(args[9].trim());
				}
			} else if (MinerType.test.equals(this.type)) { // internal test mode, transient benchmarking.
				this.maxHashers = args.length > 3 ? Integer.parseInt(args[3].trim()) : 1;
			}

			if (AdvMode.auto.equals(this.hasherMode)) {
				this.maxHashers = -1;
			}

			char[] nameChar = worker.toCharArray();
			// sanitize the worker name
			StringBuilder nameSB = new StringBuilder(worker.length());
			for (char ar : nameChar) {
				if (ar >= '0' && ar <= '9' || ar >= 'a' && ar <= 'z' || ar >= 'A' && ar <= 'Z') {
					nameSB.append(ar);
				}
			}
			worker = nameSB.toString(); // sanitize...
			
			coPrint = new CPrint(colors);
			coPrint.a(Attribute.BOLD).f(FColor.CYAN).ln("Running Version 0.2.6.1");
			coPrint.a(Attribute.BOLD).f(FColor.CYAN).ln("Active config:")
				.clr().f(FColor.CYAN).p("  type: ").f(FColor.GREEN).ln(this.type)
				.clr().f(FColor.CYAN).p("  node: ").f(FColor.GREEN).ln(this.node);
			if (MinerType.pool.equals(this.type)) {
				coPrint.clr().f(FColor.CYAN).p("  wallet address: ").f(FColor.GREEN).ln(this.publicKey);
			} else {
				coPrint.clr().f(FColor.CYAN).p("  public-key: ").f(FColor.GREEN).ln(this.publicKey)
					.clr().f(FColor.CYAN).p("  private-key: ").f(FColor.GREEN).ln(this.privateKey);
			}
			coPrint.clr().f(FColor.CYAN).p("  hasher-count: ").f(FColor.GREEN).ln(this.maxHashers)
				.clr().f(FColor.CYAN).p("  hasher-mode: ").f(FColor.GREEN).ln(this.hasherMode)
				.clr().f(FColor.CYAN).p("  colors: ").f(FColor.GREEN).ln(this.colors)
			    .clr().f(FColor.CYAN).p("  worker-name: ").f(FColor.GREEN).ln(this.worker)
			    .clr().f(FColor.CYAN).p("  pause-on-mn: ").f(FColor.GREEN).ln(this.pause).clr();
			
		} catch (Exception e) {
			System.err.println("Invalid configuration: " + (e.getMessage()));
			System.err.println("  type: " + this.type);
			System.err.println("  node: " + this.node);
			System.err.println("  public-key: " + this.publicKey);
			System.err.println("  private-key: " + this.privateKey);
			System.err.println("  hasher-count: " + this.maxHashers);
			System.err.println("  hasher-mode: " + this.hasherMode);
			System.err.println("  colors: " + this.colors);
			System.err.println("  worker-name: " + this.worker);
			System.err.println("  pause-on-mn: " + this.pause);
			System.err.println();
			System.err.println("Usage: ");
			System.err.println("  java -jar arionum-miner-java.jar");
			System.err.println(
					"  java -jar arionum-miner-java.jar pool http://aropool.com address [#hashers] [standard] [true|false] [workername]");
			System.err.println(
					"  java -jar arionum-miner-java.jar solo node-address pubKey priKey [#hashers] [standard] [true|false] [workername]");
			System.err.println(" where:");
			System.err.println("   [#hashers] is # of hashers to spin up. Default 1.");
			System.err.println(
					"   [standard] is type of hasher to run. At present, only standard. More options will come.");
			System.err.println(
					"   [true|false] is if colored output is enabled.");
			System.err.println(
					"   [workername] is name to report to pool or node. Should be unique per worker.");
			System.err.println(
					"Please note, to set pause-on-mn you need to use a config.cfg -- use the auto configurator (run without params)");

			System.exit(1);
		}

		coPrint.f(FColor.WHITE).a(Attribute.DARK).p("You have ")
			.a(Attribute.NONE).p(Runtime.getRuntime().availableProcessors()).a(Attribute.DARK).p(" processors vs. ")
				.a(Attribute.DARK).p(this.maxHashers).a(Attribute.NONE).ln(" hashers. ").clr();

		this.hashers = Executors.newFixedThreadPool(
				this.maxHashers > 0 ? this.maxHashers : Runtime.getRuntime().availableProcessors(),
				new AggressiveAffinityThreadFactory("HashMasher", true));

		this.limit = 240; // default
		this.wallClockBegin = System.currentTimeMillis();
	}

	/**
	 * Kicks off this whole hashing business.
	 * 
	 */
	public void start() {
		if (MinerType.test.equals(this.type)) {
			startTest();
			return;
		}

		// commit e14b696362fb port to java from arionum/miner
		if (MinerType.pool.equals(this.type)) {
			String decode = Utility.base58_decode(this.publicKey);
			if (decode.length() != 64) {
				this.coPrint.a(Attribute.BOLD).f(FColor.RED).ln("ERROR: Invalid Arionum address!").clr();
				System.exit(1);
			}
		}
		// end commit e14b696362fb

		if (AdvMode.auto.equals(this.hasherMode)) {
			// BOOTSTRAP
			for (AdvMode mode : AdvMode.values()) {
				if (mode.useThis()) {
					Profile execProfile = new Profile(mode);
					this.profilesToEvaluate.offer(execProfile);
				}
			}
			nextProfileSwap = System.currentTimeMillis();
			activeProfile = null;
			profilesTested = 0;
		}

		if (isWindows()) {
			disableRebindCheck();
		}

		active = true;
		this.lastUpdate = wallClockBegin;
		final AtomicBoolean firstRun = new AtomicBoolean(true);
		cycles = 0;
		supercycles = 0;
		final AtomicBoolean sentSpeed = new AtomicBoolean(false);
		skips = 0;
		failures = 0;
		updates = 0;
		while (active) {
			boolean updateLoop = true;
			int firstAttempts = 0;
			while (updateLoop) { // this allows us to handle initial update retries and general updates.
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
								extra.append("&worker=").append(URLEncoder.encode(worker, "UTF-8"));
								
								// recommend not to constantly update this either, so send in first update then not again for 10 mins.
								if (firstRun.get() || (!sentSpeed.get() && supercycles > 15)) {
									extra.append("&address=").append(privateKey);
								}
								
								// All the frequent speed sends was placing a large UPDATE burden on the pool, so now
								// first h/s is sent 30s after start, and every 10 mins after that. Should help.
								if (!sentSpeed.get() && supercycles > 15) {
									extra.append("&hashrate=").append(cummSpeed);
									sentSpeed.set(true);
								}
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
								coPrint.updateLabel().p("Update failure: ")
									.a(Attribute.UNDERLINE).f(FColor.RED).b(BColor.NONE).ln(con.getResponseMessage()).clr();
								con.disconnect();
								failures++;
								updateTime(System.currentTimeMillis() - executionTimeTracker);
								return Boolean.FALSE;
							}
	
							long parseTimeTracker = System.currentTimeMillis();
	
							JSONObject obj = (JSONObject) (new JSONParser())
									.parse(new InputStreamReader(con.getInputStream()));
							//System.out.println(obj.toString());
	
							if (!"ok".equals((String) obj.get("status"))) {
								coPrint.updateLabel().p("Update failure: ")
									.a(Attribute.UNDERLINE).f(FColor.RED).b(BColor.NONE).p(obj.get(status))
									.a(Attribute.LIGHT).f(FColor.CYAN).ln(". We will try again shortly!").clr();
								con.disconnect();
								failures++;
								updateTime(System.currentTimeMillis(), executionTimeTracker, parseTimeTracker);
								return Boolean.FALSE;
							}
	
							JSONObject jsonData = (JSONObject) obj.get("data");
							String localData = (String) jsonData.get("block");
							if (!localData.equals(data)) {
								coPrint.updateLabel().p("Update transitioned to new block. ").clr();
								if (lastBlockUpdate > 0) {
									coPrint.updateMsg().p("  Last block took: ")
										.normData().p( ((System.currentTimeMillis() - lastBlockUpdate) / 1000)).unitLabel().p("s ").clr();
								}
								data = localData;
								lastBlockUpdate = System.currentTimeMillis();
								long bestDLLastBlock = bestDL.getAndSet(Long.MAX_VALUE);
								coPrint.updateLabel().p("Best DL on last block: ")
									.dlData().p(bestDLLastBlock).unitLabel().p(" ").clr();
	
								endline = true;
							}
							BigInteger localDifficulty = new BigInteger((String) jsonData.get("difficulty"));
							if (!localDifficulty.equals(difficulty)) {
								coPrint.updateMsg().p("Difficulty updated to ")
									.dlData().p(localDifficulty).unitLabel().p(". ").clr();
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
							
							long localHeight = (Long) jsonData.get("height");
							if (localHeight != height) {
								coPrint.updateMsg().p("New Block Height: ")
									.normData().p(localHeight).unitLabel().p(". ").clr();
								height = localHeight;
								endline = true;
							}
							
							if (pause) {
								try {
									boolean localMineRecommend = ("mine".equalsIgnoreCase((String) jsonData.get("recommendation")));
									if (localMineRecommend != mineRecommended) {
										coPrint.updateMsg().p("Mining will be ").normData().p(localMineRecommend ? "active" : "paused").p(". ").clr();
										endline = true;
									}
									mineRecommended = localMineRecommend;
								} catch (Exception e) {
								}
							}
							
							int localIters = ((Long) jsonData.get("argon_time")).intValue();
							if (mineIters != localIters) {
								mineIters = localIters;
								endline = true;
							}
							int localThreads = ((Long) jsonData.get("argon_threads")).intValue();
							if (mineThreads != localThreads) {
								mineThreads = localThreads;
								endline = true;
							}
							int localMem = ((Long) jsonData.get("argon_mem")).intValue();
							if (mineMem != localMem) {
								mineMem = localMem;
								endline = true;
							}
	
							if (endline) {
								updateWorkers();
							}
							long sinceLastReport = System.currentTimeMillis() - lastReport;
							if (sinceLastReport > UPDATING_REPORT) {
								lastReport = System.currentTimeMillis();
								coPrint.ln().info().p("Node: ").textData().fs(node).p(" ")
									.info().p("MinDL: ").dlData().fd(limit).p("  ")
									.info().p("BestDL: ").dlData().fd(bestDL.get()).p("  ")
									.info().p("Block Height: ").normData().fd(height).ln()
									.p("      ").info().p("Time on Block: ").normData().fd(((System.currentTimeMillis() - lastBlockUpdate) / 1000)).unitLabel().p("s  ")
									.info().p("Inverse Difficulty: ").dlData().fd(difficulty.longValue()).clr();
								coPrint.ln().info().p("  Updates:   Last ").normData().fd((sinceLastReport / 1000))
											.unitLabel().p("s").info().p(": ").normData().fd(updates)
										.info().p("  Failed: ").normData().fd(failures+skips)
										.info().p("  Avg Update RTT: ").normData().fd((updates > 0 ? (updateTimeAvg.get() / (updates + failures)) : 0))
											.unitLabel().p("ms").clr();
								coPrint.ln().info().p("  Nonces:  Attempted: ").normData().fd(sessionSubmits.get())
										.info().p("  Rejected: ").normData().fd(sessionRejects.get())
										.info().p("  Eff: ").normData().fp("%.2f", (sessionSubmits.get() > 0 ? 100d * ((double) (sessionSubmits.get() - sessionRejects.get()) / (double)sessionSubmits.get()) : 100.0d ))
											.unitLabel().p("%")
										.info().p("  Avg Hash/nonce: ").normData().fd((sessionSubmits.get() > 0 ? Math.floorDiv(hashes.get(), sessionSubmits.get()) : hashes.get()))
										.info().p("  Avg Submit RTT: ").normData().fd((sessionSubmits.get() > 0 ? submitTimeAvg.get() / (sessionSubmits.get() + sessionRejects.get()) : 0))
											.unitLabel().p("ms").clr();
								coPrint.ln().info().p("  Overall:  Hashes: ").normData().fd(hashes.get())
										.info().p("  Mining Time: ").normData().fd(((System.currentTimeMillis() - wallClockBegin) / 1000l))
											.unitLabel().p("s")
										.info().p("  Avg Speed: ").normData().fs(avgSpeed(wallClockBegin))
											.unitLabel().p("H/s")
										.info().p("  Reported Speed: ").normData().fs(cummSpeed)
											.unitLabel().p("H/s").clr();
								printWorkerHeader();
								
								updateTimeAvg.set(0);
								updateTimeMax.set(Long.MIN_VALUE);
								updateTimeMin.set(Long.MAX_VALUE);
								updateParseTimeAvg.set(0);
								updateParseTimeMax.set(Long.MIN_VALUE);
								updateParseTimeMin.set(Long.MAX_VALUE);
								submitParseTimeAvg.set(0);
								submitParseTimeMax.set(Long.MIN_VALUE);
								submitParseTimeMin.set(Long.MAX_VALUE);
								skips = 0;
								failures = 0;
								updates = 0;
								endline = true;
								clearSpeed();
							}
							con.disconnect();
							updates++;
							updateTime(System.currentTimeMillis(), executionTimeTracker, parseTimeTracker);
							if (endline) {
								System.out.println();
							}
							if ((sinceLastReport % UPDATING_STATS) < UPDATING_DELAY && sinceLastReport < 5000000000l) {
								printWorkerStats();
							}
	
							return Boolean.TRUE;
						} catch (IOException | ParseException e) {
							lastUpdate = System.currentTimeMillis();
							if (!(e instanceof SocketTimeoutException)) {
								coPrint.updateLabel().p("Non-fatal Update failure: ").textData().p(e.getMessage()).updateMsg().ln(" We will try again in a moment.").clr();
								//e.printStackTrace();
							}
							failures++;
							updateTime(System.currentTimeMillis() - executionTimeTracker);
							return Boolean.FALSE;
						}
					}
				});
				if (firstRun.get()) { // Failures after initial are probably just temporary so we ignore them.
					try {
						if (update.get().booleanValue()) {
							firstRun.set( false );
							updateLoop = false;
						} else {
							firstAttempts++;
						}
					} catch (InterruptedException | ExecutionException e) {
						coPrint.a(Attribute.BOLD).f(FColor.RED).p("Unable to successfully complete first update: ").textData().ln(e.getMessage()).clr();
					} finally {
						if (firstRun.get() && firstAttempts > 15) { // failure!
							coPrint.a(Attribute.BOLD).f(FColor.RED).ln("Unable to contact node in pool, please check connection and try again.").clr();
							active = false;
							firstRun.set( false );
							updateLoop = false;
							break;
						} else if (firstRun.get()) {
							coPrint.a(Attribute.BOLD).f(FColor.RED).ln("Pool did not respond (attempt " + firstAttempts + " of 15). Trying again in 5 seconds.").clr();
	
							try {
								Thread.sleep(5000l);
							} catch (InterruptedException ie) {
								active = false;
								firstRun.set( false );
								updateLoop = false;
								// interruption shuts us down.
								coPrint.a(Attribute.BOLD).f(FColor.RED).ln("Interruption detection, shutting down.").clr();
							}
	
						}
					}
					lastWorkerReport = System.currentTimeMillis();
				} else {
					updateLoop = false;
				}
			}

			if (!AdvMode.auto.equals(this.hasherMode) && this.hasherCount.get() < maxHashers) {
				String workerId = this.deadWorkers.getAndIncrement() + "]" + php_uniqid();
				this.deadWorkerLives.put(workerId, System.currentTimeMillis());
				Hasher hasher = HasherFactory.createHasher(hasherMode, this, workerId, this.hashesPerSession, (long) this.sessionLength * 2l);
				updateWorker(hasher);
				this.hashers.submit(hasher);
				addWorker(workerId, hasher);
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
					coPrint.updateMsg().p("About to evaluate profile ").textData().ln(newActiveProfile.toString()).clr();

					// reconfigure!

					activeProfile = newActiveProfile;
				}
			}
		
			try {
				Thread.sleep(UPDATING_DELAY);
			} catch (InterruptedException ie) {
				active = false;
				// interruption shuts us down.
				coPrint.a(Attribute.BOLD).f(FColor.RED).ln("Interruption detection, shutting down.").clr();
			}

			if (cycles == 30) {
				cycles = 0;
			}
			
			if (supercycles == 300) { // 10 minutes
				supercycles = 0;
				sentSpeed.set(false);
			}

			if (cycles % 2 == 0) {
				refreshFromWorkers();
			}
			
			updateStats();

			cycles++;
			supercycles++;
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
		workers.forEach((workerId, hasher) -> {
			if (hasher != null && hasher.isActive()) {
				updateWorker(hasher);
			}
		});
	}

	/**
	 * We update a specific worker with latest information from pool / node.
	 * 
	 * @param hasher
	 *            the worker to update
	 */
	protected void updateWorker(Hasher hasher) {
		hasher.update(getDifficulty(), getBlockData(), getLimit(), getPublicKey(), getHeight(), getPause(), getIters(), getMem(), getThreads());
	}

	/**
	 * When a new worker is started, no-op for now.
	 * 
	 * @param workerId
	 */
	protected void workerInit(final String workerId) {
	}

	/**
	 * When a worker is done, tosses its stats on the pile and initiates a new worker with updated runlength goals.
	 * 
	 * @param stats outgoing worker stats
	 * @param worker outgoing worker
	 */
	protected void workerFinish(HasherStats stats, Hasher worker) {
		this.deadWorkerSociety.offer(stats);
		releaseWorker(worker.getID());
		try {
			stats.scheduledTime = System.currentTimeMillis() - this.deadWorkerLives.remove(stats.id);
		} catch (NullPointerException npe) {
			coPrint.a(Attribute.BOLD).f(FColor.RED).ln("Failed to determine full scheduled time for a worker").clr();
			stats.scheduledTime = stats.hashTime;
		}
		String workerId = this.deadWorkers.getAndIncrement() + "]" + php_uniqid();
		this.deadWorkerLives.put(workerId, System.currentTimeMillis());
		Hasher hasher = HasherFactory.createHasher(hasherMode, this, workerId, this.hashesPerSession, (long) this.sessionLength * 2l);
		updateWorker(hasher);
		this.hashers.submit(hasher);
		addWorker(workerId, hasher);
	}


	/**
	 * When a worker's session is done, tosses its stats on the pile and updates the worker with updated runlength goals.
	 * 
	 * @param stats outgoing session stats
	 * @param worker outgoing worker
	 */
	protected long[] sessionFinish(HasherStats stats, Hasher worker) {
		this.deadWorkerSociety.offer(stats);
		try {
			stats.scheduledTime = System.currentTimeMillis() - this.deadWorkerLives.put(stats.id, System.currentTimeMillis());
		} catch (NullPointerException npe) {
			coPrint.a(Attribute.BOLD).f(FColor.RED).ln("Failed to determine full scheduled time for a worker").clr();
			stats.scheduledTime = stats.hashTime;
		}
		return new long[]{this.hashesPerSession, (long) this.sessionLength * 2l};
	}
	
	/**
	 * Periodically we tally up reports from finished worker tasks
	 */
	protected void refreshFromWorkers() {
		long wallTime = System.currentTimeMillis() - lastWorkerReport;
		lastWorkerReport = System.currentTimeMillis();
		try {
			AtomicLong newHashes = new AtomicLong();
			AtomicLong adjust = new AtomicLong();
			AtomicLong offload = new AtomicLong();
			HasherStats worker = null;
			Report report = new Report();
			while ( (worker = this.deadWorkerSociety.poll()) != null) {
				try {
					report.runs ++;
					
					long allHashes = worker.hashes;
					newHashes.addAndGet(allHashes);
					hashes.getAndAdd(allHashes);
					
					report.hashes += allHashes;

					long localDL = worker.bestDL;
					bestDL.getAndUpdate((dl) -> {
						if (localDL < dl)
							return localDL;
						else
							return dl;
					});
					
					report.shares += worker.shares;
					report.finds += worker.finds;
					
					// shares are nonces < difficulty > 0 and >= 240
					blockShares.addAndGet(worker.shares);
					// finds are nonces < 240 (block discovery)
					blockFinds.addAndGet(worker.finds);

					long argonTime = worker.argonTime;
					long shaTime = worker.shaTime;
					long nonArgonTime = worker.nonArgonTime;
					long fullTime = argonTime + nonArgonTime;
					double lastRatio = 0.0d;
					if (fullTime > 0) {
						lastRatio = (double) argonTime / (double) fullTime;
					}
					
					double lastShaRatio = 0.0;
					if (shaTime > 0 || fullTime > 0) {
						lastShaRatio = (double) shaTime / (double) fullTime;
					}
					report.argonEff += lastRatio;
					report.shaEff += lastShaRatio;
					
					report.argonTime += argonTime;
					report.nonArgontime += nonArgonTime;
					report.shaTime += shaTime;

					long totalTime = worker.hashTime;

					report.totalTime += totalTime;
					
					report.curWaitLoss += (double) (worker.scheduledTime - worker.hashTime) / (double) worker.scheduledTime;

					long seconds = fullTime / 1000000000l;
					
					report.curHashPerSecond += (double) allHashes / ((double) (totalTime / 1000d));
					
					report.curTimeInCore += ((double) seconds * 1000d) / ((double) totalTime);

					if (totalTime < this.sessionLength) {
						double gap = (double) (this.sessionLength - totalTime) / (double) this.sessionLength;
						long recom = (long) (((double) allHashes) * .5 * gap);

						adjust.addAndGet(recom);
						offload.incrementAndGet();
					} else if (totalTime > this.sessionLength) {
						double gap = (double) (totalTime - this.sessionLength) / (double) totalTime;
						long recom = (long) -(((double) allHashes) * .5 * gap);

						adjust.addAndGet(recom);
						offload.incrementAndGet();
					} else {
						offload.incrementAndGet();
					}
					
					/* reporting stats */
					if (this.statsHost != null) {
						HasherStats contrib = this.statsStage.computeIfAbsent(worker.type, w -> 
								{ return new HasherStats(this.worker, 0, 0, 0, System.currentTimeMillis(), 0, 0, 0, 0, w); });
						
						contrib.hashes += allHashes;
						
						if (System.currentTimeMillis() - contrib.hashTime > REPORTING_INTERVAL) {
							contrib.hashTime = System.currentTimeMillis() - contrib.hashTime;
							this.statsReport.offer(this.statsStage.remove(worker.type));
						}
					}

				} catch (Throwable e) {
					coPrint.a(Attribute.BOLD).f(FColor.RED).p("Resolving stats from finished worker ").textData().p(worker.id)
						.a(Attribute.BOLD).f(FColor.RED).p(" failed! Message: ").textData().ln(e.getMessage()).clr();
					e.printStackTrace();
				}
			};
			if (offload.get() > 0) {
				this.hashesPerSession += (long) (adjust.doubleValue() / offload.doubleValue());
				if (this.hashesPerSession < MIN_HASHES_PER_SESSION) {
					this.hashesPerSession = MIN_HASHES_PER_SESSION;
				}
			}
			this.lastSpeed.addAndGet((long) (((newHashes.doubleValue() * 10000000d) / (double) (wallTime))));
			this.speedAccrue.incrementAndGet();
			this.deadWorkerSummaries.push(report);
		} catch (Exception e) {
			coPrint.a(Attribute.BOLD).f(FColor.RED).p("There was an issue getting stats from the workers. I'll try again in a bit...: ")
					.textData().ln(e.getMessage()).clr();
		}
	}

	private void printWorkerHeader() {
		coPrint.ln("").headers().fp(" %7s", "Streams").clr()
			.p(" ").headers().fp("%5s", "Runs").clr()
			.p(" ").headers().fp("%5s", "H/run").clr()
			.p(" ").headers().fp("%7s", "Cur H/s").clr()
			.p(" ").headers().fp("%8s", "Cur TiC%").clr()
			.p(" ").headers().fp("%8s", "Cur WL%").clr()
			.p(" ").headers().fp("%8s", "Argon %").clr()
			.p(" ").headers().fp("%8s", "Sha %").clr()
			.p(" ").headers().fp("%7s", "Nonces").clr()
			.p(" ").headers().fp("%5s", "Blocks").clr()
			.p(" ").headers().fp("%6s", "Reject").clr();
	}
		
	private void printWorkerStats() {
		int recentSize = 0;
		long runs = 0;
		double avgRate = 0.0d;
		double coreEff = 0.0d;
		double waitEff = 0.0d;
		double argEff = 0.0d;
		double shaEff = 0.0d;
		long shares = this.blockShares.get() - this.shareRejects.get();
		long finds = this.blockFinds.get() - this.findRejects.get();
		long failures = this.sessionRejects.get();
		int grabReports = (int) (15000.0 / (double) (Miner.UPDATING_DELAY * 2d));
		if (grabReports < 3) grabReports = 3;
		
		try {
			LinkedList<Report> recent = new LinkedList<Report>();
			while (recentSize++ < grabReports && deadWorkerSummaries.peekFirst() != null) {
				recent.addFirst(deadWorkerSummaries.pop());
			}
			
			for (Report report : recent) {
				runs += report.runs;
				avgRate += report.curHashPerSecond;
				waitEff += report.curWaitLoss * 100d;
				coreEff += report.curTimeInCore * 100d;
				argEff += report.argonEff * 100d;
				shaEff += report.shaEff * 100d;
				deadWorkerSummaries.push(report);
			}

			if (waitEff < 0.0d) waitEff = 0.0d; // -% is meaningless...

			coPrint.p(" ").normData().fp("%7d", this.hasherCount.get()).clr()
				.p(" ").normData().fp("%5d", runs).clr()
				.p(" ").normData().fp("%5d", this.hashesPerSession).clr()
				.p(" ").normData().fp("%7.2f", avgRate / (double) ((double) runs / (double) this.hasherCount.get())).clr()
				.p(" ").normData().fp("%8.2f", coreEff / (double) runs).clr()
				.p(" ").normData().fp("%8.3f", waitEff / (double) runs).clr()
				.p(" ").normData().fp("%8.3f", argEff / (double) runs).clr()
				.p(" ").normData().fp("%8.3f", shaEff / (double) runs).clr()
				.p(" ").dlData().fp("%7d", shares).clr()
				.p(" ").dlData().fp("%5d", finds).clr()
				.p(" ").dlData().fp("%6d", failures).clr().ln().clr();

		} catch (Throwable t) {
			t.printStackTrace();
		}

		if (AdvMode.auto.equals(this.hasherMode)) {
			coPrint.a(Attribute.BOLD).f(FColor.RED).p("In auto-adjust mode. Next readjustment in ").normData().p("").unitLabel().ln("s").clr();
		}
	}

	protected void updateStats() {
		this.stats.submit( new Runnable() {
			public void run() {
				HasherStats latest = null;
				while((latest = statsReport.poll()) != null) {
					try {
						StringBuilder to = new StringBuilder(statsHost);
						to.append("/").append(statsInvoke).append("?q=report");
						to.append("&token=").append(URLEncoder.encode(statsToken, "UTF-8"));
						to.append("&id=").append(URLEncoder.encode(latest.id, "UTF-8")).append("&type=").append(latest.type);
						if (!post) {
							to.append("&hashes=").append(latest.hashes)
								.append("&elapsed=").append(latest.hashTime);
						}
						
						//System.out.println("Reporting stats: " + to.toString());
	
						URL url = new URL(to.toString());
						HttpURLConnection con = (HttpURLConnection) url.openConnection();
						if (post) {
							con.setRequestMethod("POST");
							con.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
							con.setDoOutput(true);
							DataOutputStream out = new DataOutputStream(con.getOutputStream());
		
							StringBuilder data = new StringBuilder();
		
							// argon, just the hash bit since params are universal
							data.append(URLEncoder.encode("hashes", "UTF-8")).append("=")
									.append(URLEncoder.encode(Long.toString(latest.hashes), "UTF-8")).append("&");
							data.append(URLEncoder.encode("elapsed", "UTF-8")).append("=")
									.append(URLEncoder.encode(Long.toString(latest.hashTime), "UTF-8"));
							
							out.writeBytes(data.toString());
							
							out.flush();
							out.close();
						} else {
							con.setRequestMethod("GET");
						}
						
						int status = con.getResponseCode();
						if (status != HttpURLConnection.HTTP_OK) {
							// quietly fail..?
							System.err.println("Failed to report stats: " + status);
						}
					} catch (IOException ioe) {
						// quietly fail.
						System.err.println("Failed to report stats: " + ioe.getMessage());
					}
				}
			}
		});
	}

	protected void submitStats(final String nonce, final String argon, final long submitDL, final long difficulty, final String type, final int retries, final boolean accepted) {
		this.stats.submit( new Runnable() {
			public void run() {
				try {
					StringBuilder to = new StringBuilder(statsHost);
					to.append("/").append(statsInvoke).append("?q=discovery");
					to.append("&token=").append(URLEncoder.encode(statsToken, "UTF-8"));
					to.append("&id=").append(URLEncoder.encode(worker, "UTF-8")).append("&type=").append(type);
					if (!post) {
						to.append("&nonce=").append(URLEncoder.encode(nonce, "UTF-8"))
							.append("&argon=").append(URLEncoder.encode(argon, "UTF-8"))
							.append("&difficulty=").append(difficulty)
							.append("&dl=").append(submitDL)
							.append("&retries=").append(retries);
						if (accepted) {
							to.append("&confirmed");
						}
					}

					URL url = new URL(to.toString());
					HttpURLConnection con = (HttpURLConnection) url.openConnection();
					if (post) {
						con.setRequestMethod("POST");
						con.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
						con.setDoOutput(true);
						DataOutputStream out = new DataOutputStream(con.getOutputStream());
	
						StringBuilder data = new StringBuilder();
	
						data.append(URLEncoder.encode("nonce", "UTF-8")).append("=")
							.append(URLEncoder.encode(nonce, "UTF-8")).append("&");
						data.append(URLEncoder.encode("argon", "UTF-8")).append("=")
							.append(URLEncoder.encode(argon, "UTF-8")).append("&");
						data.append(URLEncoder.encode("difficulty", "UTF-8")).append("=")
							.append(URLEncoder.encode(Long.toString(difficulty), "UTF-8")).append("&");
						data.append(URLEncoder.encode("dl", "UTF-8")).append("=")
							.append(URLEncoder.encode(Long.toString(submitDL), "UTF-8")).append("&");
						data.append(URLEncoder.encode("retries", "UTF-8")).append("=")
							.append(URLEncoder.encode(Long.toString(retries), "UTF-8")).append("&");
						if (accepted) {
							data.append(URLEncoder.encode("confirmed", "UTF-8"));
						}
						
						out.writeBytes(data.toString());
						
						out.flush();
						out.close();
					} else {
						con.setRequestMethod("GET");
					}
					
					int status = con.getResponseCode();
					if (status != HttpURLConnection.HTTP_OK) {
						// quietly fail..?
						System.err.println("Failed to report submit: " + status);
					}
				} catch (IOException ioe) {
					// quietly fail.
					System.err.println("Failed to report submit: " + ioe.getMessage());
				}
			}
		});
	}

	protected void reportFailure(final String packet, final String type) {
		this.stats.submit( new Runnable() {
			public void run() {
				try {
					StringBuilder to = new StringBuilder(statsHost);
					to.append("/").append("reporterr.php");
					to.append("?token=").append(URLEncoder.encode(statsToken, "UTF-8"));
					to.append("&id=").append(URLEncoder.encode(worker, "UTF-8")).append("&type=").append(type);
					
					URL url = new URL(to.toString());
					HttpURLConnection con = (HttpURLConnection) url.openConnection();

					con.setRequestMethod("POST");
					con.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
					con.setDoOutput(true);
					DataOutputStream out = new DataOutputStream(con.getOutputStream());

					StringBuilder data = new StringBuilder();

					data.append(packet);
					out.writeBytes(data.toString());
						
					out.flush();
					out.close();
				
					int status = con.getResponseCode();
					if (status != HttpURLConnection.HTTP_OK) {
						// quietly fail..?
						System.err.println("Failed to report error: " + status);
					}
				} catch (IOException ioe) {
					// quietly fail.
					System.err.println("Failed to report error: " + ioe.getMessage());
				}
			}
		});
	}
	
	protected void submit(final String nonce, final String argon, final long submitDL, final long difficulty, final String workerType, final long height, final Hasher discover) {
		final String sDiff = getDifficulty().toString();
		final String sBlockId = getBlockData();
		
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
						
						String sentArgon = argon.substring(getMem() == 16384 ? 29 : 30);

						StringBuilder data = new StringBuilder();

						// argon, just the hash bit since params are universal
						data.append(URLEncoder.encode("argon", "UTF-8")).append("=")
								.append(URLEncoder.encode(sentArgon, "UTF-8")).append("&");
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
								.append(URLEncoder.encode(privateKey, "UTF-8")).append("&");
						
						// block height
						data.append(URLEncoder.encode("height", "UTF-8")).append("=")
								.append(height);

						out.writeBytes(data.toString());

						coPrint.updateLabel().a(Attribute.LIGHT).p("Submitting to ").textData().fs(node).p(" ")
							.updateLabel().a(Attribute.LIGHT).p(" a ").dlData().p(submitDL)
							.updateLabel().a(Attribute.LIGHT).ln(" DL nonce.").p("  nonce: ").textData().p(nonce)
							.updateLabel().a(Attribute.LIGHT).p(" argon: ").textData().ln(sentArgon).clr();

						out.flush();
						out.close();

						sessionSubmits.incrementAndGet();

						int status = con.getResponseCode();
						if (status != HttpURLConnection.HTTP_OK) {
							coPrint.updateLabel().a(Attribute.LIGHT).p("Submit of ").textData().p(nonce)
								.updateLabel().a(Attribute.LIGHT).p(" failure: ").textData().f(FColor.RED).p(con.getResponseMessage())
								.updateLabel().a(Attribute.LIGHT).ln(". We will retry.").clr();
							con.disconnect();
							failures++;
							submitTime(System.currentTimeMillis() - executionTimeTracker);
						} else {
							long parseTimeTracker = System.currentTimeMillis();

							JSONObject obj = (JSONObject) (new JSONParser())
									.parse(new InputStreamReader(con.getInputStream()));

							if (!"ok".equals((String) obj.get("status"))) {
								sessionRejects.incrementAndGet();
								
								if (submitDL <= 240) {
									findRejects.incrementAndGet();
								} else {
									shareRejects.incrementAndGet();
								}

								coPrint.updateLabel().a(Attribute.LIGHT).p("Submit of ").textData().p(nonce).p(" ")
									.clr().a(Attribute.BOLD).a(Attribute.UNDERLINE).f(FColor.RED).p("rejected")
									.clr().updateLabel().a(Attribute.LIGHT).p(", nonce did not confirm: ")
									.textData().f(FColor.RED).ln((String) obj.get("status")).clr();
								System.out.println(" Raw Failure: " + obj.toJSONString());
								submitStats(nonce, argon, submitDL, difficulty, workerType, failures, false);

								if (!"stale block".equalsIgnoreCase((String) obj.get("data"))) {
									coPrint.updateMsg().p(" Triggering local check of submission validity: ");
									
									String base = publicKey + "-" + nonce + "-" + sBlockId + "-" + sDiff;
									String argon2 = (getMem() == 16384 ? "$argon2i$v=19$m=16384,t=4,p=4" : "$argon2i$v=19$m=524288,t=1,p=1") + URLDecoder.decode(URLEncoder.encode(sentArgon, "UTF-8"), "UTF-8");
									byte[] baseBytes = base.getBytes();
									
									int ret = Argon2Library.INSTANCE.argon2i_verify(argon2.getBytes(), baseBytes, new Size_t(baseBytes.length));
									if (ret == Argon2Library.ARGON2_OK) {
										coPrint.f(FColor.RED).ln("VALID -- please report this to AroDev: ").clr();
										coPrint.normData().p("  password_verify(\"").textData().p(base)
											.normData().p("\",\"").textData().p(argon2).normData().ln("\");").clr();
										if (MinerType.pool.equals(type)) {
											coPrint.normData().p("  _POST Data: ").textData().ln(data.toString()).clr();
										}
										
									} else {
										coPrint.f(FColor.RED).ln("INVALID -- please report this to ProgrammerDan: ").clr();
										coPrint.normData().p("  argon2i_verify(\"").textData().p(argon2)
											.normData().p("\",\"").textData().p(base).normData().p("\",\"")
											.textData().p(baseBytes.length).normData().p("\") = ")
											.dlData().ln(ret);
										coPrint.normData().p("  urlEncoded nonce: ").textData().ln(URLEncoder.encode(nonce, "UTF-8"));
										coPrint.normData().p("  urlEncoded argon: ").textData().ln(URLEncoder.encode(sentArgon, "UTF-8")).clr();
										if (MinerType.pool.equals(type)) {
											coPrint.normData().p("  _POST Data: ").textData().ln(data.toString()).clr();
										}
										
										discover.kill(); // this kills the hasher, throws away the memory page, and starts it over.
									}
									
									if (statsHost != null) {
										reportFailure(
												"raw=" + URLEncoder.encode(data.toString(), "UTF-8") + "&verify=" + ret + "&base=" + URLEncoder.encode(base, "UTF-8")
													+ "&baseLen=" + baseBytes.length + "&argon=" + URLEncoder.encode(argon2, "UTF-8"), workerType
												);
									}
								} else {
									coPrint.updateMsg().ln(" Rejection due to stale block -- regretably, work during block transition.").clr();
								}
								
							} else {
								coPrint.updateLabel().a(Attribute.LIGHT).p("Submit of ").textData().p(nonce)
									.updateLabel().a(Attribute.LIGHT).ln(" confirmed!").clr();
								submitStats(nonce, argon, submitDL, difficulty, workerType, failures, true);
							}
							notDone = false;

							con.disconnect();

							submitTime(System.currentTimeMillis(), executionTimeTracker, parseTimeTracker);
						}
					} catch (IOException | ParseException ioe) {
						failures++;

						coPrint.updateLabel().a(Attribute.LIGHT).p("Non-fatal but tragic: Failed during construction or receipt of submission: ")
							.textData().f(FColor.RED).p(ioe.getMessage())
							.updateLabel().a(Attribute.LIGHT).ln(". We will retry.").clr();

						submitTime(System.currentTimeMillis() - executionTimeTracker);
					}
					if (failures > 5) {
						notDone = false;
						coPrint.textData().f(FColor.RED).ln("After more then five failed attempts to submit this nonce, we are giving up.").clr();
						sessionRejects.incrementAndGet();
						submitStats(nonce, argon, submitDL, difficulty, workerType, failures, false);
					} else if (failures > 0) {
						try {
							Thread.sleep(50l*failures);
						} catch (InterruptedException ioe) {
						}
					}
				}

			}
		});
	}

	/**
	 * Reference: http://php.net/manual/en/function.uniqid.php#95001
	 * 
	 * @return a quasi-unique identifier.
	 */
	public static String php_uniqid() {
		double m = ((double) (System.nanoTime() / 10)) / 10000d;
		return String.format("%8x%05x", (long) Math.floor(m), (long) ((m - Math.floor(m)) * 1000000)).trim();
	}

	private String speed() {
		return String.format("%.3f", (((double) this.lastSpeed.get() / 10000d) / (double) this.speedAccrue.get()));
	}
	private void clearSpeed() {
		this.lastSpeed.set(0);
		this.speedAccrue.set(0);
	}

	private String avgSpeed(long clockBegin) {
		return String.format("%.3f",
				this.hashes.doubleValue() / (((double) (System.currentTimeMillis() - clockBegin)) / 1000d));
	}

	private void updateTime(long duration) {
		this.updateTimeAvg.addAndGet(duration);
		this.updateTimeMax.getAndUpdate((dl) -> {
			if (duration > dl)
				return duration;
			else
				return dl;
		});
		this.updateTimeMin.getAndUpdate((dl) -> {
			if (duration < dl)
				return duration;
			else
				return dl;
		});
	}

	private void updateTime(long instance, long total, long parse) {
		updateTime(instance - total);
		long duration = instance - parse;

		this.updateParseTimeAvg.addAndGet(duration);
		this.updateParseTimeMax.getAndUpdate((dl) -> {
			if (duration > dl)
				return duration;
			else
				return dl;
		});
		this.updateParseTimeMin.getAndUpdate((dl) -> {
			if (duration < dl)
				return duration;
			else
				return dl;
		});
	}

	private void submitTime(long duration) {
		this.submitTimeAvg.addAndGet(duration);
		this.submitTimeMax.getAndUpdate((dl) -> {
			if (duration > dl)
				return duration;
			else
				return dl;
		});
		this.submitTimeMin.getAndUpdate((dl) -> {
			if (duration < dl)
				return duration;
			else
				return dl;
		});
	}

	private void submitTime(long instance, long total, long parse) {
		submitTime(instance - total);
		long duration = instance - parse;
		this.submitParseTimeAvg.addAndGet(duration);
		this.submitParseTimeMax.getAndUpdate((dl) -> {
			if (duration > dl)
				return duration;
			else
				return dl;
		});
		this.submitParseTimeMin.getAndUpdate((dl) -> {
			if (duration < dl)
				return duration;
			else
				return dl;
		});
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
	
	protected long getHeight() {
		return this.height;
	}
	
	protected boolean getPause() {
		return !this.mineRecommended;
	}
	
	protected int getIters() {
		return this.mineIters;
	}
	
	protected int getMem() {
		return this.mineMem;
	}
	
	protected int getThreads() {
		return this.mineThreads;
	}

	private void startTest() {
		System.out.println("Static tests using " + this.maxHashers + " iterations as cap");

		System.out.println("Utility Test on " + this.publicKey);
		String refKey = this.publicKey;

		// validation framework for error reports.
		JnaUint32 iterations = new JnaUint32(1);
		JnaUint32 memory = new JnaUint32(524288);
		JnaUint32 parallelism = new JnaUint32(1);
		JnaUint32 saltLenI = new JnaUint32(16);
		JnaUint32 hashLenI = new JnaUint32(32);
		Size_t saltLen = new Size_t(16l);
		final Size_t hashLen = new Size_t(32l);
		Argon2_type argonType = new Argon2_type(1l);
		Argon2Library argonlib = Argon2Library.INSTANCE;
		Size_t encLen = argonlib.argon2_encodedlen(iterations, memory, parallelism, saltLenI, hashLenI,
				argonType);
		byte[] encoded = new byte[encLen.intValue()];
		/*String hashBase =  "PZ8Tyr4Nx8MHsRAGMpZmZ6TWY63dXWSCy7AEg3h9oYjeR74yj73q3gPxbxq9R3nxSSUV4KKgu1sQZu9Qj9v2q2HhT5H3LTHwW7HzAA28SjWFdzkNoovBMncD-JUdHXFcYVcIAChuZwCEAOKF8C9MFcWf6TF26dJnh5D-MtZvKY2VEbYzVJywaNJYxJb8TmFMV86Rg7Hovv5PnkrEWTYmz6B76TvrKBdCVvbFzHT4eWHhkrzY4WhxyCVo3PD-33037203" ; // put base here
		String saltBase =  "Wmp4d0lpcUYvWVZza0hZLw" ; // put base64 salt here
		String argon2iE =  "$argon2i$v=19$m=524288,t=1,p=1$Wmp4d0lpcUYvWVZza0hZLw$YhKKYUruDkVVdCVpFr6qJWBE4IfLNfvOvxAKvCGUcPc" ; // put expected argon2i here*/
		/*String hashBase =  "PZ8Tyr4Nx8MHsRAGMpZmZ6TWY63dXWSCy7AEg3h9oYjeR74yj73q3gPxbxq9R3nxSSUV4KKgu1sQZu9Qj9v2q2HhT5H3LTHwW7HzAA28SjWFdzkNoovBMncD-OpcR797PK9DUEpJdSMSveFyUSdGc9BlR7YCtF5WLc-3zY6Pf5jXc1RQvHr8qYhow8YW1NYin3WS8N17HYM4wqrmtNeBQJbuFCoqHUwjodp9ZA7ycThvCHjsdQW3ffSukmV-65817790" ; // put base here
		String saltBase =  "kORRUeHLjB3YiCEhKYuiDw" ; // put base64 salt here
		String argon2iE =  "$argon2i$v=19$m=524288,t=1,p=1$kORRUeHLjB3YiCEhKYuiDw$uQ/unQvCQDBePyjknp8qrwOdu3XVvRzivjUE4x5Dp1M" ; // put expected argon2i here*/
		/*String hashBase =  "PZ8Tyr4Nx8MHsRAGMpZmZ6TWY63dXWSCy7AEg3h9oYjeR74yj73q3gPxbxq9R3nxSSUV4KKgu1sQZu9Qj9v2q2HhT5H3LTHwW7HzAA28SjWFdzkNoovBMncD-IqAGeCJDeYEeAJEswJ5YBTE8RAKkaNE8kgKkkYL8oc8-4s2vXCw69f4rfobWmDgARK3SBDt1uGXYC8SpMyUpW6RrfLfn77v7vmeU8bZ79k54GEnNZtyCvWcLhgt16XxxG1qB-38578751" ; // put base here
		String saltBase =  "DlqvSaKJvnb/Ec7aKnOoVQ" ; // put base64 salt here
		String argon2iE =  "$argon2i$v=19$m=524288,t=1,p=1$DlqvSaKJvnb/Ec7aKnOoVQ$qCm/zbWj7oYAwkWeFTojrTTlaSxlwSmNELk4+KZ4EpY" ; // put expected argon2i here*/
		String hashBase =  "PZ8Tyr4Nx8MHsRAGMpZmZ6TWY63dXWSCy7AEg3h9oYjeR74yj73q3gPxbxq9R3nxSSUV4KKgu1sQZu9Qj9v2q2HhT5H3LTHwW7HzAA28SjWFdzkNoovBMncD-A7tdOTOeAuvqmVtgH6YgVm83h0GWJ9vuMCYrtsE6pd-4XjCsnXntRfnskAyMST7AzzsPWCcXC7oxupE9qSMmmj7TkEyd1ZLYoonmMY57qEQqhP5uvksTh9gq8n22WpiVMoZ-24583881" ; // put base here
		String saltBase =  "SFlCVUtSSXhac3Y5S09EVg" ; // put base64 salt here
		String argon2iE =  "$argon2i$v=19$m=524288,t=1,p=1$SFlCVUtSSXhac3Y5S09EVg$WiGhW0/kYCNXSL3jeRxPIM+oD3Mhnm0S3PVbXUJwCzc" ; // put expected argon2i here
		
		
		// now we process
		System.out.println("Processing using Experimental Hasher method");
		byte[] hashBaseBuffer = hashBase.getBytes();
		Size_t hashBaseBufferSize = new Size_t(hashBaseBuffer.length);
		byte[] salt = Base64.getDecoder().decode(saltBase);
		
		int res = argonlib.argon2i_hash_encoded(iterations, memory, parallelism, hashBaseBuffer, hashBaseBufferSize, salt,
				saltLen, hashLen, encoded, encLen); // refactor saves like 30,000-200,000 ns per hash // 34.2 ms
													// -- 34,200,000 ns
		if (res != Argon2Library.ARGON2_OK) {
			System.out.println("HASH FAILURE!" + res);
		} else {
			System.out.println("Expected Argon: " + argon2iE);
			System.out.println("Generated Argon: " + new String(encoded));
		}
		
		
		System.out.println("Processing using Mapped Hasher method");
		Argon2_Context context  = new Argon2_Context();
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
		
		Memory m_salt = new Memory(16l);
		m_salt.write(0, salt, 0, 16);
		
		// set argon params in context..
		context.out = new Memory(32l);
		context.salt = m_salt;
		context.pwdlen = new JnaUint32(hashBaseBuffer.length);
		Memory m_hashBaseBuffer = new Memory(hashBaseBuffer.length);
		m_hashBaseBuffer.write(0, hashBaseBuffer, 0, hashBaseBuffer.length);
		context.pwd = m_hashBaseBuffer;

		res = argonlib.argon2_ctx(context, argonType);
		if (res != Argon2Library.ARGON2_OK) {
			System.out.println("HASH FAILURE!" + res);
		}
		
		encoded = new byte[encLen.intValue() - 1];
		
		int res2 = argonlib.encode_ctx(encoded, encLen, context, argonType);
		if (res2 != Argon2Library.ARGON2_OK) {
			System.out.println("ENCODE FAILURE! " + res2);
		}
		
		if (encoded[encoded.length - 1] == 0) {
			System.out.println("Encoded length failure.");
		}
		
		else {
			System.out.println("Expected Argon: " + argon2iE);
			System.out.println("Generated Argon: " + new String(encoded));
		}

		
		System.out.println("Done static testing.");
	}

	@Override
	public void uncaughtException(Thread t, Throwable e) {
		coPrint.a(Attribute.BOLD).f(FColor.RED)
			.p("Detected thread ").f(FColor.WHITE).p(t.getName())
			.f(FColor.RED).p(" death due to error: ").a(Attribute.LIGHT).ln(e.getMessage()).clr();
		e.printStackTrace();

		System.err.println("\n\nThis is probably fatal, so exiting now.");
		System.exit(1);
	}
	
	public static void disableAffinity() {
		Miner.PERMIT_AFINITY = false;
	}

	private static String OS = System.getProperty("os.name").toLowerCase();

	public static boolean isWindows() {
		return (OS.indexOf("win") >= 0);
	}

	public static void disableRebindCheck() {
		Miner.CHECK_BIND = false;
	}
}
