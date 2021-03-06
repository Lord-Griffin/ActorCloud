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
package org.mephi.griffin.actorcloud.admin;

import java.util.List;
import org.mephi.griffin.actorcloud.client.messages.Message;

/**
 *
 * @author Griffin
 */
public class ListMessage implements Message {
	private final List<ShortInfo> list;
	private final int maxPage;
		
	public ListMessage(List<ShortInfo> list, int maxPage) {
		this.list = list;
		this.maxPage = maxPage;
	}
	
	public List<ShortInfo> getList() {
		return list;
	}
	
	public int getMaxPage() {
		return maxPage;
	}
}
