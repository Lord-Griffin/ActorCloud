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

/**
 *
 * @author Griffin
 */
public class SessionDisconnected {
	
	private int sessionId;
	
	public SessionDisconnected() {}
	
	public SessionDisconnected(int sessionId) {
		this.sessionId = sessionId;
	}
	
	public int getSessionId() {
		return sessionId;
	}
	
	@Override
	public String toString() {
		return "Session id " + sessionId;
	}
}
