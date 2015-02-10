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
package org.mephi.griffin.actorcloud.manager;

import akka.actor.ActorRef;

/**
 *
 * @author Griffin
 */
public class AuthData {
	
	private String token;
	private String address;
	private int authChannelId;
	//private ActorRef authServer;
	
	public AuthData() {}
	
	public AuthData(String address, int authChannelId/*, ActorRef authServer*/) {
		this(null, address, authChannelId/*, authServer*/);
	}
	
	public AuthData(String token, String address, int authChannelId/*, ActorRef authServer*/) {
		this.token = token;
		this.address = address;
		this.authChannelId = authChannelId;
		//this.authServer = authServer;
	}
	
	public void setToken(String token) {
		this.token = token;
	}
	
	public String getToken() {
		return token;
	}
	
	public String getAddress() {
		return address;
	}
	
	public int getAuthChannelId() {
		return authChannelId;
	}
	
	/*public ActorRef getAuthServer() {
		return authServer;
	}*/
	
	@Override
	public String toString() {
		return "Authentication channel id " + authChannelId + ", address " + address + ", token " + token;
	}
}
