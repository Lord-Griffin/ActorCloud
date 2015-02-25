/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.mephi.griffin.actorcloud.client;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;

/**
 *
 * @author Griffin
 */
public class AuthHandler extends IoHandlerAdapter {
	
	Connection conn;
	
	public AuthHandler(Connection conn) {
		this.conn = conn;
	}
	
	@Override
	public void messageReceived(IoSession session, Object message) {
		if(message instanceof IoBuffer) {
			IoBuffer buf = (IoBuffer) message;
			int error = buf.getInt();
			if(error == 1) {
				byte[] str = new byte[buf.remaining()];
				buf.get(str);
				String reason = new String(str);
				conn.authError(reason);
			}
			else if(error == 0) {
				byte[] bytes = new byte[buf.getInt()];
				buf.get(bytes);
				String token = new String(bytes);
				int size = buf.getInt();
				List<InetSocketAddress> addresses = new ArrayList<>();
				for(int i = 0; i < size; i++) {
					bytes = new byte[buf.getInt()];
					buf.get(bytes);
					int port = buf.getInt();
					try {
						addresses.add(new InetSocketAddress(InetAddress.getByAddress(bytes), port));
					}
					catch(UnknownHostException ex) {}
				}
				conn.setToken(token, addresses);
			}
		}
	}
	
	@Override
	public void exceptionCaught(IoSession session, Throwable cause) {
		cause.printStackTrace(System.err);
		session.close(true);
	}
}