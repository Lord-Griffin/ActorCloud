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
package org.mephi.griffin.actorcloud.backupmanager.messages;

import akka.actor.ActorRef;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.mephi.griffin.actorcloud.util.Coder;

/**
 *
 * @author Griffin
 */
public class Backup implements Serializable {
	private final ActorRef actor;
	private final byte[] watcherSnapshot;
	private final List<byte[]> messages;
	private final byte[] actorSnapshot;
	
	public Backup(ActorRef actor, byte[] watcherSnapshot, List<byte[]> messages, byte[] actorSnapshot) {
		this.actor = actor;
		if(watcherSnapshot != null) {
			this.watcherSnapshot = new byte[watcherSnapshot.length];
			System.arraycopy(watcherSnapshot, 0, this.watcherSnapshot, 0, watcherSnapshot.length);
		}
		else this.watcherSnapshot = null;
		this.messages = new ArrayList<>();
		for(byte[] message : messages) {
			byte[] msg = new byte[message.length];
			System.arraycopy(msg, 0, message, 0, message.length);
			this.messages.add(msg);
		}
		if(actorSnapshot != null) {
			this.actorSnapshot = new byte[actorSnapshot.length];
			System.arraycopy(actorSnapshot, 0, this.actorSnapshot, 0, actorSnapshot.length);
		}
		else this.actorSnapshot = null;
	}
	
	public ActorRef getActor() {
		return actor;
	}
	
	public byte[] getWatcherSnapshot() {
		return watcherSnapshot;
	}
	
	public List<byte[]> getMessages() {
		return messages;
	}
	
	public byte[] getActorSnapshot() {
		return actorSnapshot;
	}
	
	@Override
	public String toString() {
		return "actor " + actor + ", watcherSnapshot " + Coder.toHexString(watcherSnapshot) + ", actorSnapshot " + Coder.toHexString(actorSnapshot) + " messages count " + messages.size();
	}
}
