/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import org.apache.mina.core.buffer.IoBuffer;
import org.apache.mina.core.filterchain.DefaultIoFilterChainBuilder;
import org.apache.mina.core.future.WriteFuture;
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
public class Connection {
	static SSLContext sslContext = null;
	static SocketConnector connector = null;
	String authHost = null;
	int authPort = 0;
	String login = null;
	byte[] hash = null;
	String token = null;
	final Object lock = new Object();
	List<InetSocketAddress> netAddresses;
	IoSession session = null;
	MessageListener messageListener = null;
	
	public Connection() {
		if(sslContext == null) {
			try {
				KeyStore keyStore = KeyStore.getInstance("jks");
				keyStore.load(new FileInputStream("D:\\client.jks"), "antharas".toCharArray());
				KeyManagerFactory kmf = KeyManagerFactory.getInstance("PKIX");
				kmf.init(keyStore, "antharas".toCharArray());
				KeyStore trustStore = KeyStore.getInstance("jks");
				trustStore.load(new FileInputStream("D:\\ca.jks"), "antharas".toCharArray());
				TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
				tmf.init(trustStore);
				sslContext = SSLContext.getInstance("SSL");
				sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
			}
			catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException | UnrecoverableKeyException | KeyManagementException ex) {
				Logger.getLogger(Connection.class.getName()).log(Level.SEVERE, null, ex);
			}
		}
		if(connector == null) {
			connector = new NioSocketConnector();
			DefaultIoFilterChainBuilder chain = connector.getFilterChain();
			SslFilter sslFilter = new SslFilter(sslContext);
			sslFilter.setUseClientMode(true);
			chain.addLast("sslFilter", sslFilter);
		}
	}
	
	public Connection(String host, int port, String login, String pass, MessageListener messageListener) throws NoSuchAlgorithmException, SSLException {
		this();
		this.messageListener = messageListener;
		authHost = host;
		authPort = port;
		this.login = login;
		MessageDigest md = MessageDigest.getInstance("SHA-512");
		byte[] bytesToHash = md.digest(pass.getBytes());
		hash = md.digest(bytesToHash);
	}
	
	public int connect() throws InterruptedException {
		if(connector == null) {
			throw new IllegalStateException("Couldn't establish connection: Connection initialized with errors");
		}
		if(authHost == null || authPort == 0 || login == null || hash == null) {
			throw new IllegalArgumentException("Not all arguments set. Use connect(host, port, login, pass)");
		}
		connector.setHandler(new AuthHandler(this));
		session = connector.connect(new InetSocketAddress(authHost, authPort)).await().getSession();
		IoBuffer buf = IoBuffer.allocate(login.getBytes().length + hash.length + 8);
		buf.putInt(login.getBytes().length);
		buf.put(login.getBytes());
		buf.putInt(hash.length);
		buf.put(hash);
		buf.flip();
		WriteFuture wf = session.write(buf).await();

		synchronized(lock) {
			lock.wait();
		}
		SocketAddress localAddress = session.getLocalAddress();
		session.close(false).await();
		session = null;
		if(token == null) {
			return 1;
		}
		DefaultIoFilterChainBuilder chain = connector.getFilterChain();
		chain.addLast("serializationFilter", new ProtocolCodecFilter(new ObjectSerializationCodecFactory()));
		connector.setHandler(new NetHandler(messageListener));
		Collections.reverse(netAddresses);
		Iterator<InetSocketAddress> iterator = netAddresses.iterator();
		while(session == null && iterator.hasNext()) {
			try {
				session = connector.connect(iterator.next(), localAddress).await().getSession();
			}
			catch(Exception ex) {
				ex.printStackTrace(System.err);
			}
		}
		if(session == null) {
			return 1;
		}
		session.write(new SystemMessage(token)).await();
		return 0;
	}
	
	public int connect(String host, int port, String login, String pass, MessageListener messageListener) throws SSLException, NoSuchAlgorithmException, InterruptedException {
		authHost = host;
		authPort = port;
		this.login = login;
		MessageDigest md = MessageDigest.getInstance("SHA-512");
		byte[] bytesToHash = md.digest(pass.getBytes());
		hash = md.digest(bytesToHash);
		this.messageListener = messageListener;
		return connect();
	}
	
	public void disconnect() throws InterruptedException {
		if(session != null) {
			session.close(false).await();
			session = null;
		}
		token = null;
	}
	
	public void init() {
		if(connector == null) {
			connector = new NioSocketConnector();
			DefaultIoFilterChainBuilder chain = connector.getFilterChain();
			SslFilter sslFilter = new SslFilter(sslContext);
			sslFilter.setUseClientMode(true);
			chain.addLast("sslFilter", sslFilter);
		}
	}
	
	public void close() throws InterruptedException {
		disconnect();
		if(connector != null) {
			connector.dispose();
		}
		connector = null;
	}
	
	public void send(Message message) {
		session.write(message);
	}
	
	public void sendB(Message message) throws InterruptedException {
		session.write(message).await();
	}
	
	void setToken(String token, List<InetSocketAddress> addresses) {
		synchronized(lock) {
			this.token = token;
			netAddresses = addresses;
			lock.notify();
		}
	}
	
	public void authError(String reason) {
		System.out.println("Authentication error: " + reason);
		synchronized(lock) {
			token = null;
			lock.notify();
		}
	}
}