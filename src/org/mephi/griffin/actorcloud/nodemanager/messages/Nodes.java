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
package org.mephi.griffin.actorcloud.nodemanager.messages;

import akka.actor.Address;
import org.mephi.griffin.actorcloud.common.ServerInfo;

/**
 *
 * @author griffin
 */
public class Nodes {
	private final Address generalNode;
	private final Address netNode;
	private final ServerInfo netServer;
	
	public Nodes(Address generalNode, Address netNode, ServerInfo netServer) {
		this.generalNode = generalNode;
		this.netNode = netNode;
		this.netServer = netServer;
	}
	
	public Address getGeneralNode() {
		return generalNode;
	}
	
	public Address getNetNode() {
		return netNode;
	}
	
	public ServerInfo getNetServer() {
		return netServer;
	}
	
	@Override
	public String toString() {
		return "General node: " + generalNode + ", net node: " + netNode + ", net server: " + netServer;
	}
}
