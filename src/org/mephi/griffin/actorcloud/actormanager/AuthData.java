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
import akka.actor.Address;
import java.net.InetAddress;
import org.mephi.griffin.actorcloud.common.ServerInfo;

/**
 *
 * @author Griffin
 */
public class AuthData {
	public static final int NODE_WAITING = 1;
	public static final int ACTOR_WAITING = 2;
	public static final int NET_NODE_WAITING = 3;
	public static final int READY = 4;
	
	private int state;
	private ActorRef actor;
	private Address actorNode;
	private Address netNode;
	private ServerInfo netServer;
	private String token;
	private InetAddress address;
	private ActorRef authServer;
	private int authSessionId;
	
	public AuthData(InetAddress address, int authSessionId, ActorRef authServer) {
		this.state = NODE_WAITING;
		this.actor = null;
		this.actorNode = null;
		this.netNode = null;
		this.token = null;
		this.address = address;
		this.authSessionId = authSessionId;
		this.authServer = authServer;
	}
	
	public AuthData(InetAddress address, int authSessionId, ActorRef authServer, ActorRef actor, Address actorNode) {
		this.state = NET_NODE_WAITING;
		this.actor = actor;
		this.actorNode = actorNode;
		this.netNode = null;
		this.token = null;
		this.address = address;
		this.authSessionId = authSessionId;
		this.authServer = authServer;
	}
	
	public void setState(int state) {
		this.state = state;
	}
	
	public int getState() {
		return state;
	}
	
	public void setActor(ActorRef actor) {
		this.actor = actor;
	}
	
	public ActorRef getActor() {
		return actor;
	}
	
	public void setActorNode(Address node) {
		actorNode = node;
	}
	
	public Address getActorNode() {
		return actorNode;
	}
	
	public void setNetNode(Address node) {
		netNode = node;
	}
	
	public Address getNetNode() {
		return netNode;
	}
	
	public void setNetServer(ServerInfo netServer) {
		this.netServer = netServer;
	}
	
	public ServerInfo getNetServer() {
		return netServer;
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
	
	public ActorRef getAuthServer() {
		return authServer;
	}
	
	public String getDump() {
		String dump = "";
		switch(state) {
			case NODE_WAITING:
				dump += "      state NODE_WAITING\n";
				break;
			case ACTOR_WAITING:
				dump += "      state ACTOR_WAITING\n";
				break;
			case NET_NODE_WAITING:
				dump += "      state NET_NODE_WAITING\n";
				break;
			case READY:
				dump += "      state READY\n";
				break;
		}
		dump += "      actor " + actor + "\n";
		dump += "      actorNode " + actorNode + "\n";
		dump += "      netNode " + netNode + "\n";
		dump += "      netServer:\n";
		dump += netServer.getDump();
		dump += "      token " + token + "\n";
		dump += "      address " + address + "\n";
		dump += "      authServer " + authServer + "\n";
		dump += "      auhtSessionId " + authSessionId + "\n";
		return dump;
	}
	
	@Override
	public String toString() {
		return "Authentication session id " + authSessionId + ", address " + address + ", token " + token;
	}
}
