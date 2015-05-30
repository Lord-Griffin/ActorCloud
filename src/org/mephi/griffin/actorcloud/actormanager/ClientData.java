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
import java.io.Serializable;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Griffin
 */
public class ClientData implements Serializable {
	
	private List<AuthData> authData;
	private List<SessionData> sessions;
	private List<ActorRef> closedSessions;
	private int maxSessions;
	private int maxChilds;
	private Map<String, String> messageHandlers;
	private Map<String, String> childHandlers;
	
	public ClientData(int maxSessions, int maxChilds, Map<String, String> messageHandlers, Map<String, String> childHandlers) {
		authData = new ArrayList<>();
		sessions = new ArrayList<>();
		closedSessions = new ArrayList<>();
		this.maxSessions = maxSessions;
		this.maxChilds = maxChilds;
		this.messageHandlers = messageHandlers;
		this.childHandlers = childHandlers;
	}
	
	public void addAuthData(InetAddress address, int sessionId, ActorRef authServer) {
		authData.add(new AuthData(address, sessionId, authServer));
	}
	
	public void addAuthData(InetAddress address, int sessionId, ActorRef authServer, ActorRef actor, Address actorNode) {
		authData.add(new AuthData(address, sessionId, authServer, actor, actorNode));
	}
	
	public List<AuthData> getAuthData() {
		return authData;
	}
	
	public AuthData getAuthData(String token) {
		for(AuthData authDataEntry : this.authData) {
			if(authDataEntry.getToken().equals(token))
				return authDataEntry;
		}
		return null;
	}
	
	public void addSession(ActorRef actor, Address actorNode, Address netNode) {
		sessions.add(new SessionData(actor, actorNode, netNode));
	}
	
	public void closeSession(ActorRef actor) {
		closedSessions.add(actor);
	}
	
	public List<SessionData> getSessions() {
		return sessions;
	}
	
	public List<ActorRef> getClosedSessions() {
		return closedSessions;
	}
	
	public int getMaxSessions() {
		return maxSessions;
	}
	
	public int getMaxChilds() {
		return maxChilds;
	}
	
	public Map<String, String> getMessageHandlers() {
		return messageHandlers;
	}
	
	public Map<String, String> getChildHandlers() {
		return childHandlers;
	}
	
	public void merge(ClientData data) {
		this.authData.addAll(data.authData);
		this.sessions.addAll(data.sessions);
		Iterator<SessionData> iterator = sessions.iterator();
		while(iterator.hasNext()) {
			SessionData sd = iterator.next();
			if(data.closedSessions.contains(sd.getActor()))
				iterator.remove();
		}
	}
	
	public ClientData getSyncCopy() {
		ClientData cd = new ClientData(maxSessions, maxChilds, messageHandlers, childHandlers);
		for(SessionData sd : sessions)
			cd.addSession(sd.getActor(), sd.getActorNode(), sd.getNetNode());
		for(ActorRef ar : closedSessions)
			cd.closeSession(ar);
		return cd;
	}
	
	public String getDump() {
		String dump = "";
		dump += "    messageHandler " + messageHandlers + "\n";
		dump += "    childHandler " + childHandlers + "\n";
		dump += "    maxSessions " + maxSessions + "\n";
		dump += "    authData:\n";
		int i = 0;
		for(AuthData ad : authData) {
			dump += "      " + i++ + ":\n";
			dump += ad.getDump();
		}
		dump += "    sessions:\n";
		i = 0;
		for(SessionData sd : sessions) {
			dump += "      " + i++ + ":\n";
			dump += sd.getDump();
		}
		dump += "    closedSessions:\n";
		i = 0;
		for(ActorRef ar : closedSessions) {
			dump += "      " + i++ + ":\n";
			dump += ar + "\n";
		}
		return dump;
	}
	
	@Override
	public String toString() {
		String res = "";
		if(!authData.isEmpty()) {
			res += ". Sessions waiting for token:\n";
			for(AuthData data : authData) {
				res += data + "\n";
			}
		}
		return res;
	}
}
