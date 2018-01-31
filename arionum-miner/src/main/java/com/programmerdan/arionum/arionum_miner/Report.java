package com.programmerdan.arionum.arionum_miner;

public class Report {
	/**
	 * When report was run
	 */
	long reportTime = System.currentTimeMillis();
	
	/**
	 * Number of active streams/threads
	 */
	long streams;
	
	/**
	 * Number of microtasks run
	 */
	long runs;
	
	/**
	 * Number of hashes
	 */
	long hashes;
	
	/**
	 * Hashes per run
	 */
	double hashPerRun;
	
	double curHashPerSecond;
	double curTimeInCore;
	long totalTime;
	long argonTime;
	long nonArgontime;
	long shaTime;
	long shares;
	long finds;
	long rejects;
	
}
