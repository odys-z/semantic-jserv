package io.oz.jserv.sync;

import java.util.concurrent.locks.ReentrantLock;

public class DeviceLock {
	final ReentrantLock lock;
	final String device;

	public DeviceLock(String dev) {
		device = dev;
		lock = new ReentrantLock();
	}

	public boolean isLocked() {
		return lock.isLocked();
	}
	
	public DeviceLock lock() {
		lock.lock();
		return this;
	}

	public DeviceLock unlock() {
		lock.unlock();
		return this;
	}
}
