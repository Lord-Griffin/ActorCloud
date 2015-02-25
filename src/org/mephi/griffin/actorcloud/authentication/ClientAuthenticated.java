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

import java.net.InetAddress;

/**
 *
 * @author Griffin
 */
public class ClientAuthenticated {
	
	private String login;
	private InetAddress address;
	private int sessionId;
	private String messageHandler;
	private String childHandler;
	
	public ClientAuthenticated() {}
	
	public ClientAuthenticated(String login, InetAddress address, int sessionId, String messageHandler, String childHandler) {
		this.login = login;
		this.address = address;
		this.sessionId = sessionId;
		this.messageHandler = messageHandler;
		this.childHandler = childHandler;
	}
	
	public String getLogin() {
		return login;
	}
	
	public InetAddress getAddress() {
		return address;
	}
	
	public int getSessionId() {
		return sessionId;
	}
	
	public String getMessageHandler() {
		return messageHandler;
	}
	
	public String getChildHandler() {
		return childHandler;
	}
	
	@Override
	public String toString() {
		return "Login \"" + login + "\", address " + address + ", authentication server session id " + sessionId + ", main message handler class " + messageHandler + ", child message handler class " + childHandler;
	}
}