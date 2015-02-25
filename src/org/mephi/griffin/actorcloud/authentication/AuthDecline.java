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
package org.mephi.griffin.actorcloud.authentication;

/**
 *
 * @author Griffin
 */
public class AuthDecline {
	
	private int sessionId;
	private String reason;
	
	public AuthDecline() {}
	
	public AuthDecline(int sessionId, String reason) {
		this.sessionId = sessionId;
		this.reason = reason;
	}
	
	public int getSessionId() {
		return sessionId;
	}
	
	public String getReason() {
		return reason;
	}
}
