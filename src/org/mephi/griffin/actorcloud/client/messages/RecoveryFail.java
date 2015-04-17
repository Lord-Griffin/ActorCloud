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

import akka.actor.ActorRef;
import java.io.Serializable;

/**
 *
 * @author Griffin
 */
public class RecoveryFail implements Serializable {
	private final ActorRef deadActor;
	private final String message;
	
	public RecoveryFail(ActorRef deadActor) {
		this.deadActor = deadActor;
		this.message = "";
	}
	
	public RecoveryFail(ActorRef deadActor, String message) {
		this.deadActor = deadActor;
		this.message = message;
	}
	
	public ActorRef getActor() {
		return deadActor;
	}
	
	public String getMessage() {
		return message;
	}
}
