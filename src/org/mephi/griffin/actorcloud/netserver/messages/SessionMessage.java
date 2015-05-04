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
package org.mephi.griffin.actorcloud.netserver.messages;

import java.io.Serializable;

/**
 *
 * @author Griffin
 */
public class SessionMessage implements Serializable {
	
	private int sessionId;
	private boolean inbound;
	private Object message;
	
	public SessionMessage(int sessionId, boolean inbound, Object message) {
		this.sessionId = sessionId;
		this.inbound = inbound;
		this.message = message;
	}
	
	public boolean isInbound() {
		return inbound;
	}
	
	public int getSessionId() {
		return sessionId;
	}
	
	public Object getMessage() {
		return message;
	}
	
	@Override
	public String toString() {
		String res = "Message ";
		if(isInbound()) res += "from session with id " + sessionId;
		else res += "to session with id " + sessionId;
		res += ": " + message.getClass().getName();
		return res;
	}
}
