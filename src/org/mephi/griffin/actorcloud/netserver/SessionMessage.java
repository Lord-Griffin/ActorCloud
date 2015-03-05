/*
 * Copyright 2014 Griffin.
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
package org.mephi.griffin.actorcloud.netserver;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.mephi.griffin.actorcloud.client.Message;

/**
 *
 * @author Griffin
 */
public class SessionMessage {
	
	private int sessionId;
	private Set<Integer> sessionIds;
	private Message message;
	
	public SessionMessage() {}
	
	public SessionMessage(int sessionId, Message message) {
		this.sessionId = sessionId;
		this.sessionIds = null;
		this.message = message;
	}
	
	public SessionMessage(Set<Integer> sessionIds, Message message) {
		this.sessionIds = sessionIds;
		this.message = message;
	}
	
	public boolean isInbound() {
		return sessionIds == null;
	}
	
	public int getSessionId() {
		if(!isInbound()) return -1;
		return sessionId;
	}
	
	public Set<Integer> getSessionIds() {
		return sessionIds;
	}
	
	public Message getMessage() {
		return message;
	}
	
	@Override
	public String toString() {
		String res = "Message ";
		if(isInbound()) res += "from ";
		else res += "to ";
		res += "sessions with ids ";
		Iterator<Integer> iter = sessionIds.iterator();
		if(!sessionIds.isEmpty()) {
			res += iter.next();
			do {
				res += ", " + iter.next();
			} while(iter.hasNext());
		}
		res += ": " + message.getClass().getName();
		return res;
	}
}
