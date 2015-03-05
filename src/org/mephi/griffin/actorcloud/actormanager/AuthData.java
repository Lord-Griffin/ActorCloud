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
package org.mephi.griffin.actorcloud.actormanager;

import akka.actor.ActorRef;
import java.net.InetAddress;

/**
 *
 * @author Griffin
 */
public class AuthData {
	
	private String token;
	private InetAddress address;
	private int authSessionId;
	//private ActorRef authServer;
	
	public AuthData() {}
	
	public AuthData(InetAddress address, int authSessionId/*, ActorRef authServer*/) {
		this(null, address, authSessionId/*, authServer*/);
	}
	
	public AuthData(String token, InetAddress address, int authSessionId/*, ActorRef authServer*/) {
		this.token = token;
		this.address = address;
		this.authSessionId = authSessionId;
		//this.authServer = authServer;
	}
	
	public void setToken(String token) {
		this.token = token;
	}
	
	public String getToken() {
		return token;
	}
	
	public InetAddress getAddress() {
		return address;
	}
	
	public int getAuthSessionId() {
		return authSessionId;
	}
	
	/*public ActorRef getAuthServer() {
		return authServer;
	}*/
	
	@Override
	public String toString() {
		return "Authentication session id " + authSessionId + ", address " + address + ", token " + token;
	}
}
