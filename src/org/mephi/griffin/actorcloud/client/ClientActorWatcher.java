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

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.serialization.Serialization;
import akka.serialization.SerializationExtension;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException.Missing;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.mephi.griffin.actorcloud.actormanager.messages.ActorStopped;
import org.mephi.griffin.actorcloud.actormanager.messages.Handoff;
import org.mephi.griffin.actorcloud.client.messages.CheckProgress;
import org.mephi.griffin.actorcloud.client.messages.ClearBackup;
import org.mephi.griffin.actorcloud.client.messages.CloseSession;
import org.mephi.griffin.actorcloud.client.messages.ConnectTimeout;
import org.mephi.griffin.actorcloud.client.messages.DeleteMessage;
import org.mephi.griffin.actorcloud.client.messages.GetSnapshot;
import org.mephi.griffin.actorcloud.client.messages.HandoffClientActor;
import org.mephi.griffin.actorcloud.client.messages.HandoffFail;
import org.mephi.griffin.actorcloud.client.messages.HandoffMessages;
import org.mephi.griffin.actorcloud.client.messages.HandoffPrepareSuccess;
import org.mephi.griffin.actorcloud.client.messages.HandoffSnapshot;
import org.mephi.griffin.actorcloud.client.messages.HandoffSuccess;
import org.mephi.griffin.actorcloud.client.messages.IdleTimeout;
import org.mephi.griffin.actorcloud.client.messages.Message;
import org.mephi.griffin.actorcloud.client.messages.Progress;
import org.mephi.griffin.actorcloud.client.messages.Ready;
import org.mephi.griffin.actorcloud.client.messages.RecoveryFail;
import org.mephi.griffin.actorcloud.client.messages.RecoverySuccess;
import org.mephi.griffin.actorcloud.client.messages.SaveMessage;
import org.mephi.griffin.actorcloud.client.messages.Snapshot;
import org.mephi.griffin.actorcloud.client.messages.SystemMessage;
import org.mephi.griffin.actorcloud.common.InitFail;
import org.mephi.griffin.actorcloud.common.InitSuccess;
import org.mephi.griffin.actorcloud.enqueuer.messages.ClientConnected;
import org.mephi.griffin.actorcloud.enqueuer.messages.ClientDisconnected;
import org.mephi.griffin.actorcloud.enqueuer.messages.DisconnectSession;
import org.mephi.griffin.actorcloud.enqueuer.messages.LastMessage;
import org.mephi.griffin.actorcloud.netserver.messages.SessionMessage;
import org.mephi.griffin.actorcloud.util.MyObjectInputStream;
import scala.concurrent.duration.Duration;

/**
 *
 * @author Griffin
 */
public class ClientActorWatcher extends UntypedActor {
	private static final int DISCONNECTED = 1;
	private static final int CONNECTED = 2;
	private static final int IDLE = 3;
	private static final int WORKING = 4;
	
	private LoggingAdapter logger;
	private final ClassLoader cl;
	private ActorRef backupManager;
	private ActorRef clientActor;
	private ActorRef storage;
	private String messageHandler;
	private String childHandler;
	private ActorRef oldActor;
	private final ActorRef actorManager;
	private final ActorRef authServer;
	private final int authSessionId;
	private final String client;
	private ActorRef netServer;
	private int sessionId;
	private boolean gracefulClose;
	private int workState;
	private int connectState;
	private double currentProgress;
	private double prevProgress;
	private long progressTimeout;
	private final ArrayBlockingQueue<Message> queue;
	private boolean handingOff;
	private ActorRef actorHandoffTo;
	private boolean handingTo;
	private boolean lastMessageSeen;
	private ArrayBlockingQueue<Message> handoffQueue;
	private int queuedMessageId;
	private int currentMessageId;
	private int snapshotId;
	private Cancellable progressSched, connectSched, idleSched;
	
	@SuppressWarnings("LeakingThisInConstructor")
	public ClientActorWatcher(ClassLoader cl, ActorRef storage, ActorRef backupManager, String messageHandler, String childHandler, String client, ActorRef actorManager, ActorRef authServer, int authSessionId) {
		this.logger = Logging.getLogger(this);
		this.cl = cl;
		this.actorManager = actorManager;
		this.authServer = authServer;
		this.netServer = null;
		this.authSessionId = authSessionId;
		this.client = client;
		this.backupManager = backupManager;
		if(client.equals("admin"))
			clientActor = getContext().actorOf(Props.create(org.mephi.griffin.actorcloud.admin.AdminActor.class, authServer, authSessionId), getSelf().path().name());
		else
			clientActor = getContext().actorOf(Props.create(ClientActor.class, cl, storage, backupManager, messageHandler, childHandler, client), getSelf().path().name());
		gracefulClose = false;
		connectState = DISCONNECTED;
		workState = IDLE;
		queue = new ArrayBlockingQueue<>(100, false);
		Config config = getContext().system().settings().config();
		try {
			String timeoutStr = config.getString("actorcloud.progress-timeout");
			progressTimeout = Long.parseLong(timeoutStr.substring(0, timeoutStr.length() - 1));
			if(timeoutStr.toLowerCase().endsWith("s")) progressTimeout *= 1000;
			else if(timeoutStr.toLowerCase().endsWith("m")) progressTimeout *= 60000;
			else if(timeoutStr.toLowerCase().endsWith("h")) progressTimeout *= 3600000;
		}
		catch(Missing ex) {
			progressTimeout = 1000;
		}
		handingTo = false;
		handingOff = false;
		connectSched = getContext().system().scheduler().scheduleOnce(Duration.create(60000, TimeUnit.MILLISECONDS), getSelf(), new ConnectTimeout(), getContext().system().dispatcher(), getSelf());
	}
	
	@SuppressWarnings("LeakingThisInConstructor")
	public ClientActorWatcher(ClassLoader cl, ActorRef storage, ActorRef backupManager, String messageHandler, String childHandler, String client, ActorRef actorManager, ActorRef oldActor, byte[] watcherSnapshot) {
		this.logger = Logging.getLogger(this);
		this.cl = cl;
		this.messageHandler = messageHandler;
		this.childHandler = childHandler;
		this.oldActor = oldActor;
		this.actorManager = actorManager;
		this.storage = storage;
		this.authServer = null;
		this.netServer = null;
		this.authSessionId = 0;
		this.client = client;
		this.backupManager = backupManager;
		if(watcherSnapshot != null) {
			Serialization serialization = SerializationExtension.get(getContext().system());
			Object obj = serialization.findSerializerFor(List.class).fromBinary(watcherSnapshot);
			if(obj instanceof List) {
				List list = (List) obj;
				netServer = (ActorRef) list.get(0);
				sessionId = (int) list.get(1);
				connectState = (int) list.get(2);
				gracefulClose = (boolean) list.get(3);
				queuedMessageId = (int) list.get(4);
				currentMessageId = (int) list.get(5);
				snapshotId = (int) list.get(6);
				logger.debug(netServer.toString());
				logger.debug(sessionId + "");
				logger.debug(connectState + "");
			}
		}
		else {
			actorManager.tell(new HandoffFail(oldActor), getSelf());
			getContext().stop(getSelf());
		}
		clientActor = null;
		gracefulClose = false;
		workState = IDLE;
		queue = new ArrayBlockingQueue<>(100, false);
		handoffQueue = new ArrayBlockingQueue<>(100, false);
		Config config = getContext().system().settings().config();
		try {
			String timeoutStr = config.getString("actorcloud.progress-timeout");
			progressTimeout = Long.parseLong(timeoutStr.substring(0, timeoutStr.length() - 1));
			if(timeoutStr.toLowerCase().endsWith("s")) progressTimeout *= 1000;
			else if(timeoutStr.toLowerCase().endsWith("m")) progressTimeout *= 60000;
			else if(timeoutStr.toLowerCase().endsWith("h")) progressTimeout *= 3600000;
		}
		catch(Missing ex) {
			progressTimeout = 1000;
		}
		oldActor.tell(new HandoffPrepareSuccess(), getSelf());
		handingTo = true;
	}
	
	@SuppressWarnings("LeakingThisInConstructor")
	public ClientActorWatcher(ClassLoader cl, ActorRef storage, ActorRef backupManager, String messageHandler, String childHandler, String client, ActorRef actorManager, ActorRef oldActor, byte[] watcherSnapshot, List<byte[]> messages, byte[] actorSnapshot) {
		this.logger = Logging.getLogger(this);
		this.cl = cl;
		this.actorManager = actorManager;
		this.authServer = null;
		this.netServer = null;
		this.authSessionId = 0;
		this.client = client;
		this.backupManager = backupManager;
		if(watcherSnapshot != null && actorSnapshot != null) {
			Serialization serialization = SerializationExtension.get(getContext().system());
			Object obj = serialization.findSerializerFor(List.class).fromBinary(watcherSnapshot);
			if(obj instanceof List) {
				List list = (List) obj;
				netServer = (ActorRef) list.get(0);
				sessionId = (int) list.get(1);
				connectState = (int) list.get(2);
				logger.debug(netServer.toString());
				logger.debug(sessionId + "");
				logger.debug(connectState + "");
			}
			if(client.equals("admin"))
				clientActor = getContext().actorOf(Props.create(org.mephi.griffin.actorcloud.admin.AdminActor.class, authServer, authSessionId), getSelf().path().name());
			else
				clientActor = getContext().actorOf(Props.create(ClientActor.class, cl, storage, backupManager, messageHandler, childHandler, client, oldActor, actorSnapshot), getSelf().path().name());
		}
		else {
			clientActor = null;
			actorManager.tell(new RecoveryFail(oldActor), getSelf());
			backupManager.tell(new ClearBackup(), getSelf());
			getContext().stop(getSelf());
		}
		gracefulClose = false;
		workState = IDLE;
		queue = new ArrayBlockingQueue<>(100, false);
		queuedMessageId = 0;
		currentMessageId = 0;
		snapshotId = 0;
		for(byte[] bytes : messages) {
			try {
				ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
				MyObjectInputStream ois = new MyObjectInputStream(bais, cl);
				Object obj = ois.readObject();
				if(obj instanceof Message) {
					queue.offer((Message) obj);
				}
			}
			catch(IOException | ClassNotFoundException ex) {}
		}
		Config config = getContext().system().settings().config();
		try {
			String timeoutStr = config.getString("actorcloud.progress-timeout");
			progressTimeout = Long.parseLong(timeoutStr.substring(0, timeoutStr.length() - 1));
			if(timeoutStr.toLowerCase().endsWith("s")) progressTimeout *= 1000;
			else if(timeoutStr.toLowerCase().endsWith("m")) progressTimeout *= 60000;
			else if(timeoutStr.toLowerCase().endsWith("h")) progressTimeout *= 3600000;
		}
		catch(Missing ex) {
			progressTimeout = 1000;
		}
		handingTo = false;
		handingOff = false;
	}
	
	@Override
	public void postStop() {
		actorManager.tell(new ActorStopped(client), getSelf());
	}
	
	@Override
	public void onReceive(Object message) throws Exception {
		if(getContext().system().settings().AddLoggingReceive())
			logger.debug(" <- {}: {} from {}", message.getClass().getSimpleName(), message, getSender());
		if(message instanceof HandoffMessages) {
			List<byte[]> messages = ((HandoffMessages) message).getMessages();
			for(byte[] bytes : messages) {
				try {
					ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
					MyObjectInputStream ois = new MyObjectInputStream(bais, cl);
					Object obj = ois.readObject();
					if(obj instanceof Message) {
						queue.offer((Message) obj);
					}
				}
				catch(IOException | ClassNotFoundException ex) {}
			}
		}
		else if(message instanceof HandoffSnapshot) {
			HandoffSnapshot hs = (HandoffSnapshot) message;
			getContext().actorOf(Props.create(ClientActor.class, cl, storage, backupManager, messageHandler, childHandler, client, oldActor, hs.getSnapshot()), getSelf().path().name());
		}
		else if(message instanceof InitSuccess) {
			actorManager.tell(new InitSuccess(InitSuccess.CLIENT, client, authServer, authSessionId), getSelf());
		}
		else if(message instanceof InitFail) {
			InitFail ifd = (InitFail) message;
			actorManager.tell(new InitFail(InitFail.CLIENT, client, authServer, authSessionId, ifd.getError()), getSelf());
			getContext().stop(getSelf());
		}
		else if(message instanceof HandoffFail) {
			handingOff = false;
			actorHandoffTo = null;
			if(queue.isEmpty()) {
				workState = IDLE;
				progressSched.cancel();
				if(connectState == DISCONNECTED && !gracefulClose)
					connectSched = getContext().system().scheduler().scheduleOnce(Duration.create(60000, TimeUnit.MILLISECONDS), getSelf(), new ConnectTimeout(), getContext().system().dispatcher(), getSelf());
				else if(connectState == DISCONNECTED) {
					backupManager.tell(new ClearBackup(), getSelf());
					getContext().stop(getSelf());
				}
				else
					idleSched = getContext().system().scheduler().scheduleOnce(Duration.create(3600000, TimeUnit.MILLISECONDS), getSelf(), new IdleTimeout(), getContext().system().dispatcher(), getSelf());
			}
			else {
				currentMessageId++;
				prevProgress = 1;
				currentProgress = 0;
				clientActor.tell(queue.poll(), getSelf());
			}
		}
		else if(message instanceof RecoverySuccess) {
			if(queue.isEmpty()) {
				workState = IDLE;
				if(connectState == DISCONNECTED && !gracefulClose)
					connectSched = getContext().system().scheduler().scheduleOnce(Duration.create(60000, TimeUnit.MILLISECONDS), getSelf(), new ConnectTimeout(), getContext().system().dispatcher(), getSelf());
				else if(connectState == DISCONNECTED) {
					backupManager.tell(new ClearBackup(), getSelf());
					getContext().stop(getSelf());
				}
				else
					idleSched = getContext().system().scheduler().scheduleOnce(Duration.create(3600000, TimeUnit.MILLISECONDS), getSelf(), new IdleTimeout(), getContext().system().dispatcher(), getSelf());
			}
			else {
				prevProgress = 0;
				currentProgress = 0;
				clientActor.tell(queue.poll(), getSelf());
			}
			if(handingTo) {
				clientActor = getSender();
				actorManager.tell(new HandoffSuccess(client, oldActor), getSelf());
			}
			else {
				actorManager.tell(message, getSelf());
			}
		}
		else if(message instanceof RecoveryFail) {
			if(handingTo) {
				actorManager.tell(new HandoffFail(oldActor), getSelf());
				oldActor.tell(new HandoffFail(oldActor), getSelf());
			}
			else {
				actorManager.tell(message, getSelf());
				backupManager.tell(new ClearBackup(), getSelf());
			}
			getContext().stop(getSelf());
		}
		else if(message instanceof ConnectTimeout) {
			backupManager.tell(new ClearBackup(), getSelf());
			getContext().stop(getSelf());
		}
		else if(message instanceof IdleTimeout) {
			netServer.tell(new DisconnectSession(sessionId, DisconnectSession.TIMEOUT), getSelf());
			backupManager.tell(new ClearBackup(), getSelf());
			getContext().stop(getSelf());
		}
		else if(message instanceof ClientConnected) {
			ClientConnected cc = (ClientConnected) message;
			netServer = getSender();
			sessionId = cc.getSessionId();
			connectState = CONNECTED;
			connectSched.cancel();
			idleSched = getContext().system().scheduler().scheduleOnce(Duration.create(3600000, TimeUnit.MILLISECONDS), getSelf(), new IdleTimeout(), getContext().system().dispatcher(), getSelf());
			clientActor.tell(message, netServer);
			Serialization serialization = SerializationExtension.get(getContext().system());
			List list = new ArrayList<>();
			list.add(netServer);
			list.add(sessionId);
			list.add(connectState);
			byte[] currentSnapshot = serialization.findSerializerFor(list).toBinary(list);
			snapshotId++;
			backupManager.tell(new Snapshot(Snapshot.WATCHER, snapshotId, currentSnapshot), getSelf());
		}
		else if(message instanceof CloseSession) {
			gracefulClose = true;
		}
		else if(message instanceof ClientDisconnected) {
			clientActor.tell(message, netServer);
			connectState = DISCONNECTED;
			if(workState == IDLE && !gracefulClose) {
				connectSched = getContext().system().scheduler().scheduleOnce(Duration.create(60000, TimeUnit.MILLISECONDS), getSelf(), new ConnectTimeout(), getContext().system().dispatcher(), getSelf());
				Serialization serialization = SerializationExtension.get(getContext().system());
				List list = new ArrayList<>();
				list.add(netServer);
				list.add(sessionId);
				list.add(connectState);
				byte[] currentSnapshot = serialization.findSerializerFor(list).toBinary(list);
				snapshotId++;
				backupManager.tell(new Snapshot(Snapshot.WATCHER, snapshotId, currentSnapshot), getSelf());
			}
			else if(workState == IDLE) {
				backupManager.tell(new ClearBackup(), getSelf());
				getContext().stop(getSelf());
			}
		}
		else if(message instanceof LastMessage) {
			if(handingOff) {
				actorHandoffTo.tell(message, getSelf());
				getContext().stop(getSelf());
			}
			if(handingTo) {
				while(!handoffQueue.isEmpty() && queue.size() < 100)
					queue.offer(handoffQueue.poll());
				if(!handoffQueue.isEmpty() || queue.size() == 100) {
					SessionMessage sm = new SessionMessage(sessionId, false, new SystemMessage("Actor queue is " + queue.size() + "% full"));
					netServer.tell(sm, getSelf());
				}
				if(handoffQueue.isEmpty()) {
					handingTo = false;
					lastMessageSeen = false;
					handoffQueue = null;
				}
				else lastMessageSeen = true;
			}
		}
		else if(message instanceof byte[]) {
			byte[] bytes = (byte[]) message;
			try {
				ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
				MyObjectInputStream ois = new MyObjectInputStream(bais, cl);
				Object obj = ois.readObject();
				if(obj instanceof Message) {
					Message msg = (Message) obj;
					if(handingTo) {
						if(getSender().equals(oldActor)) {
							if (clientActor != null && workState == IDLE) {
								currentMessageId++;
								clientActor.tell(msg, getSelf());
								workState = WORKING;
								idleSched.cancel();
								prevProgress = 0;
								currentProgress = 0;
								progressSched = getContext().system().scheduler().schedule(Duration.create(progressTimeout, TimeUnit.MILLISECONDS), Duration.create(progressTimeout, TimeUnit.MILLISECONDS), getSelf(), new CheckProgress(), getContext().system().dispatcher(), ActorRef.noSender());
								queuedMessageId++;
								backupManager.tell(new SaveMessage(queuedMessageId, bytes), getSelf());
							}
							else {
								queue.offer(msg);
								queuedMessageId++;
							}
						}
						else {
							if(!lastMessageSeen) {
								if(queue.size() + handoffQueue.size() >= 100) {
									SessionMessage sm = new SessionMessage(sessionId, false, new SystemMessage("Actor queue is full. Message was dropped"));
									netServer.tell(sm, getSelf());
								}
								else {
									handoffQueue.offer(msg);
									queuedMessageId++;
									int size = queue.size() + handoffQueue.size();
									if(size == 50 || size == 75 || size == 90 || size == 100) {
										SessionMessage sm = new SessionMessage(sessionId, false, new SystemMessage("Actor queue is " + size + "% full"));
										netServer.tell(sm, getSelf());
									}
									backupManager.tell(new SaveMessage(queuedMessageId, bytes), getSelf());
								}
							}
							else {
								SessionMessage sm = new SessionMessage(sessionId, false, new SystemMessage("Actor queue is full. Message was dropped"));
								netServer.tell(sm, getSelf());
							}
						}
					}
					else if(workState == IDLE && !handingOff) {
						currentMessageId++;
						clientActor.tell(msg, getSelf());
						workState = WORKING;
						idleSched.cancel();
						prevProgress = 0;
						currentProgress = 0;
						progressSched = getContext().system().scheduler().schedule(Duration.create(progressTimeout, TimeUnit.MILLISECONDS), Duration.create(progressTimeout, TimeUnit.MILLISECONDS), getSelf(), new CheckProgress(), getContext().system().dispatcher(), ActorRef.noSender());
						queuedMessageId++;
						backupManager.tell(new SaveMessage(queuedMessageId, bytes), getSelf());
					}
					else {
						if(queue.offer(msg)) {
							queuedMessageId++;
							if(queue.size() == 50 || queue.size() == 75 || queue.size() == 90 || queue.size() == 100) {
								SessionMessage sm = new SessionMessage(sessionId, false, new SystemMessage("Actor queue is " + queue.size() + "% full"));
								netServer.tell(sm, getSelf());
							}
							backupManager.tell(new SaveMessage(queuedMessageId, bytes), getSelf());
							if(handingOff && actorHandoffTo != null) {
								actorHandoffTo.tell(message, getSelf());
							}
						}
						else {
							SessionMessage sm = new SessionMessage(sessionId, false, new SystemMessage("Actor queue is full. Message was dropped"));
							netServer.tell(sm, getSelf());
						}
					}
				}
			}
			catch(IOException | ClassNotFoundException ex) {
				SessionMessage msg = new SessionMessage(sessionId, false, new SystemMessage("Internal system error"));
				netServer.tell(msg, getSelf());
			}
		}
		else if(message instanceof Ready) {
			backupManager.tell(new DeleteMessage(currentMessageId), getSelf());
			if(handingOff && actorHandoffTo != null) {
				clientActor.tell(new GetSnapshot(), getSelf());
			}
			else if(handingOff) {
				workState = IDLE;
				progressSched.cancel();
			}
			else {
				if(queue.isEmpty()) {
					workState = IDLE;
					progressSched.cancel();
					if(connectState == DISCONNECTED && !gracefulClose)
						connectSched = getContext().system().scheduler().scheduleOnce(Duration.create(60000, TimeUnit.MILLISECONDS), getSelf(), new ConnectTimeout(), getContext().system().dispatcher(), getSelf());
					else if(connectState == DISCONNECTED) {
						backupManager.tell(new ClearBackup(), getSelf());
						getContext().stop(getSelf());
					}
					else
						idleSched = getContext().system().scheduler().scheduleOnce(Duration.create(3600000, TimeUnit.MILLISECONDS), getSelf(), new IdleTimeout(), getContext().system().dispatcher(), getSelf());
				}
				else {
					currentMessageId++;
					prevProgress = 1;
					currentProgress = 0;
					clientActor.tell(queue.poll(), getSelf());
					if(handoffQueue != null && lastMessageSeen) {
						queue.offer(handoffQueue.poll());
						if(handoffQueue.isEmpty()) {
							handingTo = false;
							lastMessageSeen = false;
							handoffQueue = null;
						}
					}
				}
			}
		}
		else if(message instanceof Handoff) {
			Handoff h = (Handoff) message;
			if(handingOff) {
				actorManager.tell(new HandoffFail(), getSelf());
				return;
			}
			handingOff = true;
			Serialization serialization = SerializationExtension.get(getContext().system());
			List list = new ArrayList<>();
			list.add(netServer);
			list.add(sessionId);
			list.add(connectState);
			list.add(gracefulClose);
			list.add(queuedMessageId);
			list.add(currentMessageId);
			list.add(snapshotId);
			byte[] watcherSnapshot = serialization.findSerializerFor(list).toBinary(list);
			getContext().actorSelection(h.getNode() + "/user/node-manager").tell(new HandoffClientActor(h.getClient(), h.getMessageHandler(), h.getChildHandler(), watcherSnapshot, actorManager), getSelf());
		}
		else if(message instanceof HandoffPrepareSuccess) {
			actorHandoffTo = getSender();
			if(workState == IDLE) {
				clientActor.tell(new GetSnapshot(), getSelf());
			}
			List<byte[]> messages = new ArrayList<>();
			Iterator<Message> iterator = queue.iterator();
			while(iterator.hasNext()) {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ObjectOutputStream oos = new ObjectOutputStream(baos);
				oos.writeObject(iterator.next());
				messages.add(baos.toByteArray());
			}
			actorHandoffTo.tell(new HandoffMessages(messages), getSelf());
		}
		else if(message instanceof Snapshot) {
			Snapshot s = (Snapshot) message;
			actorHandoffTo.tell(new HandoffSnapshot(s.getSnapshot()), getSelf());
		}
		else if(message instanceof Progress) {
			Progress p = (Progress) message;
			currentProgress = p.getValue();
		}
		else if(message instanceof CheckProgress) {
			if(currentProgress == prevProgress) {
//				here must be a code to kill a hung actor...
//				but it is impossible to do that
			}
			else {
				prevProgress = currentProgress;
				SessionMessage msg = new SessionMessage(sessionId, false, new SystemMessage(String.format("%1$2.0f%% completed", currentProgress * 100)));
				netServer.tell(msg, getSelf());
			}
		}
		else unhandled(message);
	}
}
