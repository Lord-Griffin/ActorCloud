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

import org.mephi.griffin.actorcloud.client.messages.CloseSession;
import org.mephi.griffin.actorcloud.client.messages.Message;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.apache.mina.core.session.IoSession;

/**
 *
 * @author Griffin
 */
public class Connection {
	private IoSession session;
	
	Connection(IoSession session) {
		this.session = session;
	}
	
	void setSession(IoSession session) {
		this.session = session;
	}
	
	public SendFuture send(Message message) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(baos);
		oos.writeObject(message);
		return new SendFuture(session.write(baos.toByteArray()));
	}
	
	public CloseFuture close(boolean immediately) {
		session.setAttribute("gracefullyClosed");
		session.write(new CloseSession());
		return new CloseFuture(session.close(immediately));
	}
}
