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
package org.mephi.griffin.actorcloud.client;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mephi.griffin.actorcloud.common.InitFail;
import org.mephi.griffin.actorcloud.common.InitSuccess;

/**
 *
 * @author Griffin
 */
public class ChildActor extends UntypedActor {
	private static final Logger logger = Logger.getLogger(ChildActor.class.getName());
	private ClassLoader cl;
	private String name;
	private ActorRef storage;
	private String handlerName;
	private ChildMessageHandler handler;
	
	public ChildActor(ClassLoader cl, ActorRef storage, String handlerName) {
		logger.entering("ChildActor(" + getContext().parent().path().name() + "/" + getSelf().path().name() + ")", "Constructor");
		this.cl = cl;
		this.name = "ChildActor(" + getContext().parent().path().name() + "/" + getSelf().path().name() + ")";
		this.storage = storage;
		this.handlerName = handlerName;
		logger.logp(Level.FINER, name, "Constructor", "Storage: " + storage + ", messageHandler: " + handlerName);
		logger.exiting(this.name, "Constructor");
	}
	
	@Override
	public void preStart() {
		logger.entering(name, "preStart");
		logger.logp(Level.FINE, name, "preStart", name + " starts");
		String errors = "";
		try {
			logger.logp(Level.FINER, name, "preStart", "Loading message handler class: " + handlerName);
			Class handlerClass = cl.loadClass(handlerName);
			if(!ChildMessageHandler.class.isAssignableFrom(handlerClass)) {
				errors += "Handler class doesn't inherit ChildMessageHandler class\n";
			}
			logger.logp(Level.FINER, name, "preStart", "Acquiring constructor");
			Constructor con = handlerClass.getDeclaredConstructor();
			logger.logp(Level.FINER, name, "preStart", "Initializing instance");
			handler = (ChildMessageHandler) con.newInstance();
			logger.logp(Level.FINER, name, "preStart", "Initializing config");
			handler.setActors(this, storage, getContext().parent());
		}
		catch(ClassNotFoundException cnfe) {
			logger.throwing(name, "preStart", cnfe);
			errors += "Handler class not found: " + cnfe.getMessage() + "\n";
		}
		catch(NoSuchMethodException nsme) {
			logger.throwing(name, "preStart", nsme);
			errors += "Failed to get handler constructor: " + nsme.getMessage() + "\n";
		}
		catch(InvocationTargetException | InstantiationException | ClassCastException | IllegalAccessException e) {
			logger.throwing(name, "preStart", e);
			errors += "Failed to get handler instance: " + e.getMessage() + "\n";
		}
		catch(Exception e) {
			logger.throwing(name, "preStart", e);
			errors += "Main actor initialization error: " + e.getMessage() + "\n";
		}
		if(!errors.equals("")) {
			logger.logp(Level.WARNING, name, "preStart", "There was errors loading message handler:\n" + errors);
			InitFail msg = new InitFail(InitFail.CHILD, getSelf().path().name(), "Error initializing message handler:\n" + errors);
			logger.logp(Level.FINER, name, "preStart", "InitFail -> ClientActor(" + getContext().parent().path().name() + "): " + msg);
			getContext().parent().tell(msg, getSelf());
			getContext().stop(getSelf());
		}
		else {
			logger.logp(Level.INFO, name, "preStart", "Child actor " + getSelf().path().name() + " for client \"" + getContext().parent().path().name() + "\" started");
			InitSuccess msg = new InitSuccess(InitSuccess.CHILD, getSelf().path().name());
			logger.logp(Level.FINER, name, "preStart", "InitSuccess -> ClientActor(" + getContext().parent().path().name() + "): " + msg);
			getContext().parent().tell(msg, getSelf());
			handler.init();
		}
		logger.exiting(name, "preStart");
	}
	
	@Override
	public void postStop() {
		logger.entering(name, "postStop");
		if(handler != null) handler.destroy();
		logger.logp(Level.INFO, name, "postStop", "Child actor " + getSelf().path().name() + " for client \"" + getContext().parent().path().name() + "\" stopped");
		logger.exiting(name, "postStop");
	}

	@Override
	public void onReceive(Object message) {
		logger.entering(name, "onReceive");
		if(message instanceof Message) {
			logger.logp(Level.FINE, name, "onReceive", name + " <- Message: " + message.getClass().getName());
			String log = "Message: " + message.getClass().getName() + "\n";
			for(Field field : message.getClass().getDeclaredFields()) {
				try {
					field.setAccessible(true);
					Class type = field.getType();
					Object value = field.get(message);
					log += type.getName() + " " + field.getName() + " = " + value + "\n";
				}
				catch(IllegalAccessException iae) {}
			}
			logger.logp(Level.FINEST, name, "onReceive", log);
			try {
				handler.execute((Message) message, "parent");
			}
			catch(Exception e) {
				logger.throwing(name, "onReceive", e);
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				String msg = "Exception during message processing: " + sw.toString();
				logger.logp(Level.FINER, name, "onReceive", "String -> ClientActor(" + getContext().parent().path().name() + ": " + msg);
				getContext().parent().tell(msg, getSelf());
			}
		}
		else unhandled(message);
		logger.exiting(name, "onReceive");
	}
}