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
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Griffin
 */
public class ClientData {
	
	private List<AuthData> authData;
	private boolean actorState;
	private ActorRef actor;
	private int connections;
	
	public ClientData(ActorRef actor) {
		authData = new ArrayList<>();
		actorState = false;
		this.actor = actor;
		connections = 0;
	}
	
	public ClientData(InetAddress address, int sessionId, ActorRef actor/*, ActorRef authServer*/) {
		this(actor);
		authData.add(new AuthData(address, sessionId/*, authServer*/));
	}
	
	public void addAuthData(InetAddress address, int sessionId/*, ActorRef authServer*/) {
		authData.add(new AuthData(address, sessionId/*, authServer*/));
	}
	
	public void addAuthData(String token, InetAddress address, int sessionId/*, ActorRef authServer*/) {
		authData.add(new AuthData(token, address, sessionId/*, authServer*/));
	}
	
	public void addConnection() {
		connections++;
	}
	
	public void removeConnection() {
		connections--;
	}
	
	public void setActorState(boolean ready) {
		this.actorState = ready;
	}
	
	public boolean isActorReady() {
		return actorState;
	}
	
	public ActorRef getActor() {
		return actor;
	}
	
	public List<AuthData> getAuthData() {
		return authData;
	}
	
	public AuthData getAuthData(String token) {
		for(AuthData authData : this.authData) {
			if(authData.getToken().equals(token))
				return authData;
		}
		return null;
	}
	
	public int getConnections() {
		return connections;
	}
	
	@Override
	public String toString() {
		String res = "Actor " + actor + " is";
		if(!actorState) res += " not";
		res += " ready";
		if(actorState) res += ", and has " + connections + " connections";
		if(!authData.isEmpty()) {
			res += ". Sessions waiting for token:\n";
			for(AuthData data : authData) {
				res += data + "\n";
			}
		}
		return res;
	}
}
