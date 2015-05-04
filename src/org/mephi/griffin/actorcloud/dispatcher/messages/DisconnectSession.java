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
package org.mephi.griffin.actorcloud.dispatcher.messages;

import java.io.Serializable;

/**
 *
 * @author Griffin
 */
public class DisconnectSession implements Serializable {
	public static final int NOTOKEN = 1;
	public static final int TIMEOUT = 2;
	
	private int sessionId;
	private int reason;
	
	public DisconnectSession() {}
	
	public DisconnectSession(int sessionId, int reason) {
		this.sessionId = sessionId;
		this.reason = reason;
	}
	
	public int getSessionId() {
		return sessionId;
	}
	
	public int getReason() {
		return reason;
	}
	
	@Override
	public String toString() {
		String res = "Session id " + sessionId + ", reason \"";
		switch(reason) {
			case NOTOKEN:
				res += "Invalid token";
				break;
		}
		res += "\"";
		return res;
	}
}
