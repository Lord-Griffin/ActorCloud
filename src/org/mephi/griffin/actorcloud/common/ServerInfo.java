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
package org.mephi.griffin.actorcloud.common;

import akka.actor.ActorRef;
import java.net.InetSocketAddress;
import java.util.List;

/**
 *
 * @author Griffin
 */
public class ServerInfo {
	ActorRef reference;
	List<InetSocketAddress> addresses;
	
	public ServerInfo(ActorRef reference, List<InetSocketAddress> addresses) {
		this.reference = reference;
		this.addresses = addresses;
	}
	
	public ActorRef getRef() {
		return reference;
	}
	
	public List<InetSocketAddress> getAddresses() {
		return addresses;
	}
	
	@Override
	public String toString() {
		String res = reference + "; ";
		for(int i = 0; i < addresses.size() - 1; i++)
			res += addresses.get(i).getAddress().getHostAddress() + ":" + addresses.get(i).getPort() + ", ";
		res += addresses.get(addresses.size() - 1).getAddress().getHostAddress() + ":" + addresses.get(addresses.size() - 1).getPort();
		return res;
	}
}
