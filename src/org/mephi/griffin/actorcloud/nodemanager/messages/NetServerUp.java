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
import java.io.Serializable;
import org.mephi.griffin.actorcloud.common.ServerInfo;

/**
 *
 * @author griffin
 */
public class NetServerUp implements Serializable {
	private final Address node;
	private final ServerInfo info;
	
	public NetServerUp(Address node, ServerInfo info) {
		this.node = node;
		this.info = info;
	}
	
	public Address getNode() {
		return node;
	}
	
	public ServerInfo getInfo() {
		return info;
	}
	
	@Override
	public String toString() {
		return "New network server started: " + info;
	}
}
