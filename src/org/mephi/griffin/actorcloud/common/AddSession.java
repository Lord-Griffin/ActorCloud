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
package org.mephi.griffin.actorcloud.common;

import org.apache.mina.core.session.IoSession;

public class AddSession {
	private int sessionId;
	private IoSession session;
	
	public AddSession() {}
	
	public AddSession(int sessionId, IoSession session) {
		this.sessionId = sessionId;
		this.session = session;
	}
	
	public int getSessionId() {
		return sessionId;
	}
	
	public IoSession getSession() {
		return session;
	}
	
	@Override
	public String toString() {
		return "Session id " + sessionId + ", " + session;
	}
}
