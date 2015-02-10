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
package org.mephi.griffin.actorcloud.netserver;

import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Griffin
 */
public class ListWatcher extends TimerTask {
	private static final Logger logger = Logger.getLogger(ListWatcher.class.getName());
	private final Map<String, Date> blackList;
	private final Timer timer;
	
	public ListWatcher(Map<String, Date> blackList, Timer timer) {
		logger.entering("ListWatcher", "Constructor");
		this.blackList = blackList;
		this.timer = timer;
		logger.exiting("ListWatcher", "Constructor");
	}
	
	@Override
	public void run() {
		logger.entering("ListWatcher", "run");
		Date now = new Date();
		int count = 0;
		synchronized(blackList) {
			for(Entry<String, Date> entry : blackList.entrySet()) {
				if(entry.getValue().getTime() < now.getTime() - 300000) {
					count++;
					logger.logp(Level.INFO, "ListWatcher", "run", "Address " + entry.getKey() + " removed from black list");
					blackList.remove(entry.getKey());
				}
			}
		}
		logger.logp(Level.FINE, "ListWatcher", "run", count + " addresses removed from black list");
		if(blackList.isEmpty()) {
			logger.logp(Level.FINE, "ListWatcher", "run", "Black list is empty, ListWatcher stopped");
			timer.cancel();
		}
		logger.exiting("ListWatcher", "run");
	}
}