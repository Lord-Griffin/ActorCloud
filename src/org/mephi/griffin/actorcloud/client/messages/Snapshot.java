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

/**
 *
 * @author Griffin
 */
public class Snapshot {
	public static final int ACTOR = 1;
	public static final int WATCHER = 2;
	
	private final int type;
	private final int id;
	private final byte[] snapshot;
	
	public Snapshot(int type, int id, byte[] snapshot) {
		this.type = type;
		this.id = id;
		this.snapshot = snapshot;
	}
	
	public int getType() {
		return type;
	}
	
	public int getId() {
		return id;
	}
	
	public byte[] getSnapshot() {
		return snapshot;
	}
}
