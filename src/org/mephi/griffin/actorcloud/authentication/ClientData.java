/*
 * Copyright 2015 griffin.
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
package org.mephi.griffin.actorcloud.authentication;

import io.netty.channel.Channel;
import org.mephi.griffin.actorcloud.util.Hex;

/**
 *
 * @author griffin
 */
public class ClientData {
	
	private final String login;
	private final byte[] hash;
	private final int channelId;
	private final Channel channel;
	
	public ClientData(String login, byte[] hash, int channelId, Channel channel) {
		this.login = login;
		this.hash = hash;
		this.channelId = channelId;
		this.channel = channel;
	}
	
	public String getLogin() {
		return login;
	}
	
	public byte[] getHash() {
		return hash;
	}
	
	public int getChannelId() {
		return channelId;
	}
	
	public Channel getChannel() {
		return channel;
	}
	
	@Override
	public String toString() {
		return "Login \"" + login + "\", hash " + Hex.toHexString(hash) + ", channel id " + channelId;
	}
}
