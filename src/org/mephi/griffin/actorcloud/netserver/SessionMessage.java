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

import java.util.ArrayList;
import java.util.List;
import org.mephi.griffin.actorcloud.client.Message;

/**
 *
 * @author Griffin
 */
public class SessionMessage {
	
	private List<Integer> sessionIds;
	private Message message;
	
	public SessionMessage() {}
	
	public SessionMessage(int sessionId, Message message) {
		sessionIds = new ArrayList<>();
		sessionIds.add(sessionId);
		this.message = message;
	}
	
	public SessionMessage(List<Integer> sessionIds, Message message) {
		this.sessionIds = sessionIds;
		this.message = message;
	}
	
	public List<Integer> getSessionIds() {
		return sessionIds;
	}
	
	public Message getMessage() {
		return message;
	}
	
	@Override
	public String toString() {
		String res = "Message to/from sessions with ids ";
		for(int i = 0; i < sessionIds.size() - 1; i++) res += sessionIds.get(i) + ", ";
		res += sessionIds.get(sessionIds.size() - 1) + ": " + message.getClass().getName();
		return res;
	}
}
