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
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.mephi.griffin.actorcloud.nodemanager.Node;
import org.mephi.griffin.actorcloud.util.MaxHeap;

/**
 *
 * @author Griffin
 */
public class NetNodesList implements Serializable {
	private final List<Address> list;
	
	public NetNodesList(MaxHeap<Node> netNodes) {
		list = new ArrayList<>();
		for(Node node : netNodes)
			list.add(node.getAddress());
	}
	
	public List<Address> getList() {
		return list;
	}
	
	@Override
	public String toString() {
		return "Network server nodes: " + list;
	}
}
