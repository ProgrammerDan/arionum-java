package com.programmerdan.arionum.arionum_miner;

import java.util.concurrent.ThreadFactory;

import net.openhft.affinity.AffinityLock;

public class AggressiveAffinityThreadFactory implements ThreadFactory {

    private final String name;
    private final boolean daemon;

    private int id = 1;

    public AggressiveAffinityThreadFactory(String name) {
        this(name, true);
    }

    public AggressiveAffinityThreadFactory(String name, boolean daemon) {
        this.name = name;
        this.daemon = daemon;
    }

    @Override
    public synchronized Thread newThread(final Runnable r) {
    	final int myid = id;
        String name2 = myid <= 1 ? name : (name + '-' + myid);
        id++;
        System.err.println("Creating a new thread: " + name2);
        Thread t = new Thread(new Runnable() {
			@Override
            public void run() {
                try {
                	AffinityLock lock = AffinityLock.acquireLock(myid);
                	if (!lock.isAllocated()) {
                		lock = AffinityLock.acquireLock();
                	}
                	if (!lock.isAllocated()) {
                		lock = AffinityLock.acquireCore();
                	}
                	if (!lock.isAllocated()) {
                		System.err.println("Thread " + name2 + " could not reserve a core, try with fewer hashers.");
                	}
                	
                    r.run();
                    
                    lock.close();
                }catch (Throwable e) {
                	System.err.println("Thread " + name2 + " died with error:");
                	e.printStackTrace();
				}
            }
        }, name2);
        t.setDaemon(daemon);
        return t;
}
	
}
