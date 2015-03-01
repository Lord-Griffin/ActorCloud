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

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.concurrent.Future;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.apache.mina.core.filterchain.DefaultIoFilterChainBuilder;
import org.apache.mina.core.future.ConnectFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.future.WriteFuture;
import org.apache.mina.core.service.IoHandlerAdapter;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.serialization.ObjectSerializationCodecFactory;
import org.apache.mina.filter.ssl.SslFilter;
import org.apache.mina.transport.socket.SocketConnector;
import org.apache.mina.transport.socket.nio.NioSocketConnector;

/**
 *
 * @author Griffin
 */
public class Connector extends IoHandlerAdapter {
	private SocketConnector connector;
	
	@SuppressWarnings("LeakingThisInConstructor")
	public Connector(String keyStoreName, String keyStorePass, String privateKeyPass, String trustStoreName, String trustStorePass) throws KeyStoreException, IOException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException, KeyManagementException {
		KeyStore keyStore = KeyStore.getInstance("jks");
		keyStore.load(new FileInputStream(keyStoreName), keyStorePass.toCharArray());
		KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
		kmf.init(keyStore, privateKeyPass.toCharArray());
		KeyStore trustStore = KeyStore.getInstance("jks");
		trustStore.load(new FileInputStream(trustStoreName), trustStorePass.toCharArray());
		TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
		tmf.init(trustStore);
		SSLContext sslContext = SSLContext.getInstance("SSL");
		sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		connector = new NioSocketConnector();
		DefaultIoFilterChainBuilder chain = connector.getFilterChain();
		SslFilter sslFilter = new SslFilter(sslContext);
		sslFilter.setUseClientMode(true);
		chain.addLast("sslFilter", sslFilter);
		chain.addLast("serializationFilter", new ProtocolCodecFilter(new ObjectSerializationCodecFactory()));
		connector.setHandler(this);
	}
	
	public Future<Connection> getConnection(String authHost, int authPort, String login, String pass, final MessageListener messageListener) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA-512");
		byte[] bytesToHash = md.digest(pass.getBytes());
		final AuthRequest request = new AuthRequest(login, md.digest(bytesToHash));
		final ConnectionFuture connectionFuture = new ConnectionFuture();
		ConnectFuture cf = connector.connect(new InetSocketAddress(authHost, authPort));
		connectionFuture.setAuthFuture(cf);
		cf.addListener(new IoFutureListener<ConnectFuture>() {
			@Override
			public void operationComplete(ConnectFuture future) {
				if(!future.isConnected()) {
					connectionFuture.setException(future.getException());
					return;
				}
				final IoSession session = future.getSession();
				session.setAttribute("future", connectionFuture);
				session.setAttribute("listener", messageListener);
				WriteFuture wf = session.write(request);
				connectionFuture.setAuthFuture(wf);
				wf.addListener(new IoFutureListener<WriteFuture>() {
					@Override
					public void operationComplete(WriteFuture future) {
						if(!future.isWritten()) {
							connectionFuture.setException(future.getException());
							session.removeAttribute("future");
							session.removeAttribute("listener");
							session.close(true);
						}
					}
				});
			}
		});
		return connectionFuture;
	}
	
	public void close() {
		connector.dispose();
	}
	
	@Override
	public void sessionOpened(IoSession session) {
		
	}
	
	@Override
	public void sessionClosed(IoSession session) {
		if(session.getAttribute("future") != null)
			((ConnectionFuture) session.getAttribute("future")).sessionClosed((InetSocketAddress) session.getRemoteAddress(), null);
		session.close(true);
	}
	
	@Override
	public void messageReceived(IoSession session, Object message) {
		if(message instanceof AuthResponse) {
			final AuthResponse response = (AuthResponse) message;
			final ConnectionFuture connectionFuture = (ConnectionFuture) session.getAttribute("future");
			final MessageListener messageListener = (MessageListener) session.getAttribute("listener");
			List<InetSocketAddress> addresses = response.getAddresses();
			connectionFuture.setAddresses(addresses);
			session.removeAttribute("future");
			session.removeAttribute("listener");
			session.close(true);
			for(final InetSocketAddress address : addresses) {
				ConnectFuture cf = connector.connect(address);
				connectionFuture.setNetFuture(address, cf);
				cf.addListener(new IoFutureListener<ConnectFuture>() {
					@Override
					public void operationComplete(ConnectFuture future) {
						if(!future.isConnected()) {
							connectionFuture.connectFail(address, future.getException());
							return;
						}
						final IoSession session = future.getSession();
						session.setAttribute("future", connectionFuture);
						session.setAttribute("listener", messageListener);
						WriteFuture wf = session.write(new TokenMessage(response.getToken()));
						connectionFuture.setNetFuture(address, wf);
						wf.addListener(new IoFutureListener<WriteFuture>() {
							@Override
							public void operationComplete(WriteFuture future) {
								if(!future.isWritten()) {
									connectionFuture.connectFail(address, future.getException());
									session.removeAttribute("future");
									session.removeAttribute("listener");
									session.close(true);
								}
							}
						});
					}
				});
			}
		}
		else if(message instanceof TokenResponse) {
			((ConnectionFuture) session.getAttribute("future")).connectSuccess(session);
			session.removeAttribute("future");
		}
		else if(message instanceof ErrorMessage) {
			if(session.getAttribute("future") != null) {
				((ConnectionFuture) session.getAttribute("future")).connectFail((InetSocketAddress) session.getRemoteAddress(), (ErrorMessage) message);
				session.removeAttribute("future");
				session.removeAttribute("listener");
				session.close(true);
			}
			else {
				//TODO
			}
		}
		/*else if(message instanceof LogMessage) {
			
		}*/
		else if(message instanceof Message) {
			if(session.getAttribute("listener") != null) {
				((MessageListener) session.getAttribute("listener")).receive((Message) message);
			}
		}
	}
	
	@Override
	public void exceptionCaught(IoSession session, Throwable cause) {
		if(session.getAttribute("future") != null) {
			((ConnectionFuture) session.getAttribute("future")).sessionClosed((InetSocketAddress) session.getRemoteAddress(), cause);
		}
		session.close(true);
	}
}
