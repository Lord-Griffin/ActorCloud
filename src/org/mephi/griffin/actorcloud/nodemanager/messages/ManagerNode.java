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
package org.mephi.griffin.actorcloud.nodemanager.messages;

import akka.actor.Address;

/**
 *
 * @author Griffin
 */
public class ManagerNode {
	private final Address address;
	
	public ManagerNode(Address address) {
		this.address = address;
	}
	
	public Address getAddress() {
		return address;
	}
	
	@Override
	public String toString() {
		return "Actor manager node: " + address;
	}
}
