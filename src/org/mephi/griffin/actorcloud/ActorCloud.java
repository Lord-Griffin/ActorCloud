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

import akka.actor.ActorSystem;
import akka.actor.Props;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.mephi.griffin.actorcloud.manager.Manager;

/**
 *
 * @author Griffin
 */
public class ActorCloud {

	/**
	 *
	 * @param args
	 * @throws java.net.UnknownHostException
	 */
	public static void main(String[] args) throws IOException {
		Logger.getLogger("org.mephi.griffin.actorcloud").setLevel(Level.ALL);
		Handler handler = new FileHandler("%h/java%g.log", 0, 1);
		handler.setFormatter(new SimpleFormatter());
		Logger.getLogger("org.mephi.griffin.actorcloud").addHandler(handler);
		//Logger.getLogger("").getHandlers()[0].setLevel(Level.FINER);
		final ActorSystem system = ActorSystem.create("actorcloud");
		system.actorOf(Props.create(Manager.class), "actor-manager");
	}
}