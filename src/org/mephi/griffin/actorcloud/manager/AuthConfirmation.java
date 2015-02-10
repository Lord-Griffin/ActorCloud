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

import java.net.InetAddress;
import java.util.List;

/**
 *
 * @author Griffin
 */
public class AuthConfirmation {
	
	private int channelId;
	private String token;
	private List<InetAddress> addresses;
	private int port;
	
	public AuthConfirmation() {}
	
	public AuthConfirmation(int channelId, String token, List<InetAddress> addresses, int port) {
		this.channelId = channelId;
		this.token = token;
		this.addresses = addresses;
		this.port = port;
	}
	
	public int getChannelId() {
		return channelId;
	}
	
	public String getToken() {
		return token;
	}
	
	public List<InetAddress> getAddresses() {
		return addresses;
	}
	
	public int getPort() {
		return port;
	}
	
	@Override
	public String toString() {
		String res = "For client on channel id " + channelId + ", access token " + token + ", net server ";
		for(int i = 0; i < addresses.size() - 1; i++)
			res += addresses.get(i).getHostAddress() + ":" + port + ", ";
		res += addresses.get(addresses.size() - 1).getHostAddress() + ":" + port;
		return res;
	}
}
