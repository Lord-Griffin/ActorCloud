/*
 * Copyright 2015 Griffin.
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
package org.mephi.griffin.actorcloud.client;

import java.net.InetSocketAddress;
import java.util.List;

/**
 *
 * @author Griffin
 */
public class AuthResponse implements Message {
	private String token;
	private List<InetSocketAddress> addresses;
	
	public AuthResponse() {}
	
	public AuthResponse(String token, List<InetSocketAddress> addresses) {
		this.token = token;
		this.addresses = addresses;
	}
	
	public String getToken() {
		return token;
	}
	
	public List<InetSocketAddress> getAddresses() {
		return addresses;
	}
	
	@Override
	public String toString() {
		return "Token: " + token + ", addresses: " + addresses;
	}
}
