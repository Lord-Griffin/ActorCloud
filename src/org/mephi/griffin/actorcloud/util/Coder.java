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
package org.mephi.griffin.actorcloud.util;

/**
 *
 * @author Griffin
 */
public class Coder {
	private static final char[] hexArray = "0123456789abcdef".toCharArray();
	private static final char[] base64Array = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

	public static String toHexString(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			int v = bytes[i] & 0xFF;
			hexChars[i * 2] = hexArray[v >>> 4];
			hexChars[i * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}
	
	public static String toBase64String(byte[] bytes) {
		int base64length = bytes.length / 3 * 4 + (bytes.length % 3 == 0 ? 0 : 4);
		base64length += base64length / 64 + (base64length % 64 == 0 ? 0 : 1);
		char[] base64Chars = new char[base64length];
		int i = 0, j = 0, k = 0;
		for(; i < bytes.length - 2; i += 3, j += 4) {
			int v = bytes[i] & 0xFF;
			base64Chars[j] = base64Array[v >>> 2];
			v = (v & 0x03) << 8;
			v = v | (bytes[i + 1] & 0xFF);
			base64Chars[j + 1] = base64Array[v >>> 4];
			v = (v & 0x0F) << 8;
			v = v | (bytes[i + 2] & 0xFF);
			base64Chars[j + 2] = base64Array[v >>> 6];
			base64Chars[j + 3] = base64Array[v & 0x3F];
			k += 4;
			if(k == 64) {
				base64Chars[j + 4] = '\n';
				k = 0;
				j++;
			}
		}
		if(bytes.length % 3 == 1) {
			int v = bytes[i] & 0xFF;
			base64Chars[j] = base64Array[v >>> 2];
			base64Chars[j + 1] = base64Array[(v & 0x03) << 4];
			base64Chars[j + 2] = '=';
			base64Chars[j + 3] = '=';
		}
		else if(bytes.length % 3 == 2) {
			int v = bytes[i] & 0xFF;
			base64Chars[j] = base64Array[v >>> 2];
			v = (v & 0x03) << 8;
			v = v | (bytes[i + 1] & 0xFF);
			base64Chars[j + 1] = base64Array[v >>> 4];
			base64Chars[j + 2] = base64Array[(v & 0x0F) << 2];
			base64Chars[j + 3] = '=';
		}
		base64Chars[j + 4] = '\n';
		return new String(base64Chars);
	}
}
