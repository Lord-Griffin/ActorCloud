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
package org.mephi.griffin.actorcloud.enqueuer.messages;

import java.io.Serializable;

/**
 *
 * @author Griffin
 */
public class AllowConfirmation implements Serializable {
	
	private String client;
	private String token;
	
	public AllowConfirmation() {}
	
	public AllowConfirmation(String client, String token) {
		this.client = client;
		this.token = token;
	}
	
	public String getClient() {
		return client;
	}
	
	public String getToken() {
		return token;
	}
	
	@Override
	public String toString() {
		return "Client " + client + ", token " + token;
	}
}
