/*
 * Copyright 2015 griffin.
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

import java.io.Serializable;
import java.net.InetAddress;
import org.apache.mina.core.session.IoSession;
import org.mephi.griffin.actorcloud.util.Coder;

/**
 *
 * @author griffin
 */
public class AuthData implements Serializable {
	private String login;
	private byte[] hash;
	private String actor;
	private int sessionId;
	private IoSession session;
	private InetAddress address;
	private String messageHandler;
	private String childHandler;
	private boolean authOk;
	
	public AuthData(String login, byte[] hash, String actor, int sessionId, IoSession session) {
		this.login = login;
		this.hash = hash;
		this.actor = actor;
		this.sessionId = sessionId;
		this.session = session;
		this.authOk = false;
	}
	
	public String getLogin() {
		return login;
	}
	
	public byte[] getHash() {
		return hash;
	}
	
	public String getActor() {
		return actor;
	}
	
	public int getSessionId() {
		return sessionId;
	}
	
	public IoSession getSession() {
		return session;
	}
	
	public boolean isAuthOk() {
		return authOk;
	}
	
	public InetAddress getAddress() {
		return address;
	}
	
	public String getMessageHandler() {
		return messageHandler;
	}
	
	public String getChildHandler() {
		return childHandler;
	}
	
	public void setAuthOk(boolean authOk) {
		this.authOk = authOk;
	}
	
	public void setAddress(InetAddress address) {
		this.address = address;
	}
	
	public void setMessageHandler(String messageHandler) {
		this.messageHandler = messageHandler;
	}
	
	public void setChildHandler(String childHandler) {
		this.childHandler = childHandler;
	}
	
	public String getDump() {
		String dump = "";
		dump += "    login " + login + "\n";
		dump += "    hash " + Coder.toHexString(hash) + "\n";
		dump += "    actor " + actor + "\n";
		dump += "    sessionId " + sessionId + "\n";
		dump += "    session " + session + "\n";
		dump += "    address" + address + "\n";
		dump += "    messageHandler" + messageHandler + "\n";
		dump += "    childHandler" + childHandler + "\n";
		dump += "    authOk" + authOk + "\n";
		return dump;
	}
	
	@Override
	public String toString() {
		return "Session id " + sessionId + ", session " + session + ", login: " + login + ", hash: " + Coder.toHexString(hash);
	}
}
