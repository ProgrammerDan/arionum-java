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

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import java.util.concurrent.ThreadFactory;

import net.openhft.affinity.AffinityLock;
import net.openhft.affinity.AffinityStrategies;
import net.openhft.affinity.AffinitySupport;
import net.openhft.affinity.CpuLayout;
import net.openhft.affinity.Affinity;

public class AggressiveAffinityThreadFactory implements ThreadFactory {

	/**
	 * Windows apparently allows the affine but fails to properly report the affine later...
	 */
	public static ConcurrentHashMap<Integer, Integer> AffineMap = new ConcurrentHashMap<>();

	private final String name;
	private final boolean daemon;

	private int id = 1;
	
	private AffinityLock lastLock = null;
	
	private ArrayList<Integer> plan = new ArrayList<Integer>();
	private int planFailures = 0;

	public AggressiveAffinityThreadFactory(String name) {
		this(name, true);
	}

	public AggressiveAffinityThreadFactory(String name, boolean daemon) {
		this.name = name;
		this.daemon = daemon;

		// make a CPU layout plan.
		try {
			CpuLayout layout = AffinityLock.cpuLayout();
			if (layout != null) {
				// we want to fill out each socket's cores, then do virtualized threads if any.
				System.out.println("Using Affinity Plan Socket / Core / Thread by CPU: ");
				for (int t = 0; t < layout.threadsPerCore(); t++) {
					for (int s = 0; s < layout.sockets(); s++) {
						for (int c = 0; c < layout.coresPerSocket(); c++) {
							for (int i = 0; i < layout.cpus(); i++) {
								if (layout.socketId(i) == s &&
										layout.coreId(i) == c &&
										layout.threadId(i) == t) {
									plan.add(i);
									System.out.println(String.format("%d: Socket %d, Core %d, Thread %d", 
											plan.size(), s, c, t));
									break;
								}
							}
						}
					}
				}
			} else {
				plan = null;
			}
		} catch (Exception e) {
			System.err.println("Unable to determine CPU layout, disabling affinity just to be safe.");
			Miner.disableAffinity();
		}
		
	}

	@Override
	public synchronized Thread newThread(final Runnable r) {
		final int myid = id;
		String name2 = myid <= 1 ? name : (name + '-' + myid);
		id++;
		// System.err.println("Creating a new thread: " + name2);
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				if (Miner.PERMIT_AFINITY) {
					try {
						if (plan == null || myid > plan.size() || planFailures * 2 > plan.size()) {
							if (myid < AffinityLock.cpuLayout().cpus()) {
								AffinityLock lock = lastLock == null ? AffinityLock.acquireLock() : lastLock.acquireLock(AffinityStrategies.SAME_SOCKET, AffinityStrategies.DIFFERENT_CORE, AffinityStrategies.ANY);
								if (!lock.isBound()) {
									lock = AffinityLock.acquireCore();
								}
								if (!lock.isBound()) {
									lock = AffinityLock.acquireLock(myid);
								}
								if (!lock.isBound()) {
									System.err.println("Not a problem, but thread " + name2
											+ " could not immediately reserve a core, it may experience decayed performance.");
									AffineMap.put(Affinity.getThreadId(), -1);
								} else {
									System.out.println("Awesome! Thread " + name2 + " affined! CPU ID " + lock.cpuId() + " Process Thread ID "
											+ Affinity.getThreadId());
									AffineMap.put(Affinity.getThreadId(), lock.cpuId());
									lastLock = lock;
								}
		
								r.run();
		
								lock.close();
							} else {
								System.err.println("Not a problem, but thread " + name2
										+ " could not immediately reserve a core, as there are no more cores to assign to. If performance is degraded, attempt with fewer hashers.");
		
								AffineMap.put(Affinity.getThreadId(), -1);
		
								r.run();
							}
						} else {
							System.out.println("The plan:  Affine Thread " + name2 + " to CPU ID " + plan.get(myid - 1) + ". Note: Process Thread ID "
									+ Affinity.getThreadId());
							AffinityLock lock = AffinityLock.acquireLock(plan.get(myid - 1));
							
							if (!lock.isBound()) {
								System.err.println("Our plan failed :(. Thread " + name2 + " did not affine to CPU ID " + plan.get(myid - 1) + " as we had hoped. Note: Process Thread ID "
										+ Affinity.getThreadId());
								AffineMap.put(Affinity.getThreadId(), -1);
							} else {
								System.out.println("Awesome! Thread " + name2 + " affined! CPU ID " + lock.cpuId() + " Process Thread ID "
										+ Affinity.getThreadId());
								AffineMap.put(Affinity.getThreadId(), lock.cpuId());
								lastLock = lock;
							}
	
							r.run();
	
							lock.close();
						}
					} catch (Throwable e) {
						System.err.println("Ouch: thread " + name2 + " died with error:");
						e.printStackTrace();
						System.err.println("Depending on the error, you might consider shutting this down.");
						System.err.println("For now though, we're disabling affinity.");
						Miner.disableAffinity();
					}
				}
				
				if (!Miner.PERMIT_AFINITY) {
					r.run();
					
					System.out.println("Awesome! Thread " + name2 + " running in affinity-free mode!");
				}
			}
		}, name2);
		t.setDaemon(daemon);
		return t;
	}
}
