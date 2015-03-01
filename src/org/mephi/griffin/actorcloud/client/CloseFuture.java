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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author Griffin
 */
public class CloseFuture implements Future<Object> {
	private org.apache.mina.core.future.CloseFuture future;
	
	protected CloseFuture(org.apache.mina.core.future.CloseFuture future) {
		this.future = future;
	}

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return false;
	}

	@Override
	public boolean isCancelled() {
		return false;
	}

	@Override
	public boolean isDone() {
		return future.isDone();
	}

	@Override
	public Object get() throws InterruptedException, ExecutionException {
		future.await();
		if(future.isClosed()) return null;
		else throw new ExecutionException("Connection is not closed yet", null);
	}

	@Override
	public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		future.await(timeout, unit);
		if(!future.isDone()) throw new TimeoutException();
		else if(future.isClosed()) return null;
		else throw new ExecutionException("Connection is not closed yet", null);
	}
}
