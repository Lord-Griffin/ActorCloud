/*
 * Copyright 2015 Griffin.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.mephi.griffin.actorcloud.client;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.security.auth.login.FailedLoginException;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.session.IoSession;

/**
 *
 * @author Griffin
 */
class ConnectionFuture implements Future<Connection> {
	private final Object lock;
	private IoFuture authFuture;
	private Map<InetSocketAddress, IoFuture> netFutures;
	private Map<InetSocketAddress, String> errors;
	private boolean done;
	private boolean cancelled;
	private int waiters;
	private Connection connection;
	private Exception exception;
	
	protected ConnectionFuture() {
		lock = new Object();
		authFuture = null;
		errors = null;
		netFutures = null;
		done = false;
		cancelled = false;
		waiters = 0;
		connection = null;
		exception = null;
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		synchronized(lock) {
			if(done) return false;
			if(mayInterruptIfRunning) {
				if(authFuture != null && authFuture instanceof ConnectFuture && !authFuture.isDone())
					((ConnectFuture) authFuture).cancel();
				if(netFutures != null) {
					for(Entry<InetSocketAddress, IoFuture> entry : netFutures.entrySet()) {
						IoFuture future = entry.getValue();
						if(future instanceof ConnectFuture && !future.isDone()) ((ConnectFuture) future).cancel();
					}
				}
			}
			cancelled = true;
			done = true;
			if(waiters > 0) lock.notifyAll();
			return true;
		}
	}

	@Override
	public boolean isCancelled() {
		synchronized(lock) {
			return cancelled;
		}
	}

	@Override
	public boolean isDone() {
		synchronized(lock) {
			return done;
		}
	}

	@Override
	public Connection get() throws InterruptedException, ExecutionException {
		synchronized (lock) {
			while(!done) {
				waiters++;
				try {
					lock.wait();
				}
				finally {
					waiters--;
				}
            }
        }
		if(cancelled) return null;
		if(exception != null) throw new ExecutionException(exception);
        return connection;
	}

	@Override
	public Connection get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		long timeoutMillis = unit.toMillis(timeout);
		long endTime = System.currentTimeMillis() + timeoutMillis;
		if(endTime < 0) endTime = Long.MAX_VALUE;
		synchronized (lock) {
			if(done) {
				if(cancelled) return null;
				if(exception != null) throw new ExecutionException(exception);
				return connection;
			}
			else if (timeoutMillis <= 0) throw new TimeoutException();
			waiters++;
			try {
                while(true) {
					lock.wait(timeoutMillis);
					if(done) {
						if(cancelled) return null;
						if(exception != null) throw new ExecutionException(exception);
						return connection;
                    }
                    if(endTime < System.currentTimeMillis()) throw new TimeoutException();
                }
            }
			finally {
				waiters--;
            }
        }
	}
	
	protected void setAuthFuture(IoFuture future) {
		synchronized(lock) {
			authFuture = future;
		}
	}
	
	protected void setException(Throwable throwable) {
		synchronized(lock) {
			if(!done) {
				exception = new ConnectException("Failed to connect to authentication service");
				exception.initCause(throwable);
				done = true;
				if(waiters > 0) lock.notifyAll();
			}
		}
	}
	
	protected void setAddresses(List<InetSocketAddress> addresses) {
		synchronized(lock) {
			authFuture = null;
			errors = new ConcurrentHashMap<>();
			netFutures = new ConcurrentHashMap<>();
		}
		for(InetSocketAddress address : addresses) errors.put(address, "");
	}

	protected void setNetFuture(InetSocketAddress address, IoFuture future) {
		netFutures.put(address, future);
	}
	
	protected void sessionClosed(InetSocketAddress address, Throwable cause) {
		synchronized(lock) {
			if(!done) {
				if(authFuture != null) {
					exception = new ConnectException("Connection closed by other side");
					if(cause != null) exception.initCause(cause);
					done = true;
					if(waiters > 0) lock.notifyAll();
				}
				else {
					String msg = "Connection closed by other side";
					if(cause != null) msg += ": " + cause.getMessage();
					errors.put(address, msg);
					msg = "";
					for(Entry<InetSocketAddress, String> entry : errors.entrySet()) {
						if(entry.getValue().equals("")) return;
						msg += entry.getValue() + "\n";
					}
					exception = new ConnectException(msg);
					done = true;
					if(waiters > 0) lock.notifyAll();
				}
			}
		}
	}
	
	protected void connectSuccess(IoSession session) {
		synchronized(lock) {
			if(!done) {
				for(Entry<InetSocketAddress, IoFuture> entry : netFutures.entrySet()) {
					IoFuture future = entry.getValue();
					if(future instanceof ConnectFuture && !future.isDone()) ((ConnectFuture) future).cancel();
				}
				connection = new Connection(session);
				done = true;
				if(waiters > 0) lock.notifyAll();
			}
		}
	}
	
	protected void connectFail(InetSocketAddress address, Throwable throwable) {
		String msg = "Failed to connect to network service: " + throwable.getMessage();
		errors.put(address, msg);
		synchronized(lock) {
			if(!done) {
				msg = "";
				for(Entry<InetSocketAddress, String> entry : errors.entrySet()) {
					if(entry.getValue().equals("")) return;
					msg += entry.getValue() + "\n";
				}
				exception = new ConnectException(msg);
				done = true;
				if(waiters > 0) lock.notifyAll();
			}
		}
	}
	
	protected void connectFail(InetSocketAddress address, ErrorMessage message) {
		if(errors == null) {
			String msg = "Failed to login";
			switch(message.getType()) {
				case ErrorMessage.WRONG_CRED: msg += ": Wrong credentials"; break;
				case ErrorMessage.SYSTEM_ERR: msg += ": System error"; break;
				case ErrorMessage.STOR_ERR: msg += ": Storage error"; break;
			}
			FailedLoginException fle = new FailedLoginException(msg);
			if(message.getException() != null) fle.initCause(message.getException());
			synchronized(lock) {
				if(!done) {
					exception = fle;
					done = true;
					if(waiters > 0) lock.notifyAll();
				}
			}
		}
		else {
			String msg = "Failed to connect to network service: ";
			if(message.getType() == ErrorMessage.INVALID_TOKEN) msg += "invalid token";
			else if(message.getException() != null) msg += message.getException().getMessage();
			errors.put(address, msg);
			synchronized(lock) {
				if(!done) {
					msg = "";
					for(Entry<InetSocketAddress, String> entry : errors.entrySet()) {
						if(entry.getValue().equals("")) return;
						msg += entry.getValue() + "\n";
					}
					exception = new ConnectException(msg);
					done = true;
					if(waiters > 0) lock.notifyAll();
				}
			}
		}
	}
}
