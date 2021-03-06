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
package org.mephi.griffin.actorcloud;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.mephi.griffin.actorcloud.nodemanager.NodeManager;

/**
 *
 * @author Griffin
 */
public class ActorCloud {
	static boolean run;
	/**
	 *
	 * @param args
	 * @throws java.io.IOException
	 */
	public static void main(String[] args) throws IOException {
		run = true;
		try {
			LogManager.getLogManager().readConfiguration(new FileInputStream("logging.properties"));
			final ActorSystem system = ActorSystem.create("actorcloud");
			system.log().info("Starting system...");
			system.actorOf(Props.create(NodeManager.class), "node-manager");
			Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
				@Override
				public void run() {
					run = false;
					system.actorSelection("/user/node-manager").tell(new Shutdown(), ActorRef.noSender());
					system.awaitTermination();
				}
			}));
		}
		catch (IOException | SecurityException ex) {
			Logger.getLogger(ActorCloud.class.getName()).log(Level.SEVERE, null, ex);
		}
	}
}