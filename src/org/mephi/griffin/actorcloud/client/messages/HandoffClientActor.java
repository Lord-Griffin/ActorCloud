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
package org.mephi.griffin.actorcloud.client.messages;

import akka.actor.ActorRef;
import java.io.Serializable;

/**
 *
 * @author Griffin
 */
public class HandoffClientActor implements Serializable {
	private final String client;
	private final String messageHandler;
	private final String childHandler;
	private final byte[] watcherSnapshot;
	private final ActorRef actorManager;
	
	public HandoffClientActor(String client, String messageHandler, String childHandler, byte[] watcherSnapshot, ActorRef actorManager) {
		this.client = client;
		this.messageHandler = messageHandler;
		this.childHandler = childHandler;
		this.watcherSnapshot = new byte[watcherSnapshot.length];
		System.arraycopy(watcherSnapshot, 0, this.watcherSnapshot, 0, watcherSnapshot.length);
		this.actorManager = actorManager;
	}
	
	public String getClient() {
		return client;
	}
	
	public String getMessageHandler() {
		return messageHandler;
	}
	
	public String getChildHandler() {
		return childHandler;
	}
	
	public byte[] getWatcherSnapshot() {
		return watcherSnapshot;
	}
	
	public ActorRef getActorManager() {
		return actorManager;
	}
}
