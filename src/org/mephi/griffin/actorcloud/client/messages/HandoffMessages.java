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
package org.mephi.griffin.actorcloud.client.messages;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Griffin
 */
public class HandoffMessages implements Serializable {
	private final List<byte[]> messages;
	
	public HandoffMessages(List<byte[]> messages) {
		this.messages = new ArrayList<>();
		for(byte[] message : messages) {
			byte[] msg = new byte[message.length];
			System.arraycopy(message, 0, msg, 0, message.length);
			this.messages.add(msg);
		}
	}
	
	public List<byte[]> getMessages() {
		return messages;
	}
}
