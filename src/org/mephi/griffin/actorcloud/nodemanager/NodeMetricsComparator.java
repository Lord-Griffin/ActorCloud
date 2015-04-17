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
package org.mephi.griffin.actorcloud.nodemanager;

import java.util.Comparator;

/**
 *
 * @author griffin
 */
public class NodeMetricsComparator implements Comparator<Node> {
	@Override
	public int compare(Node metrics1, Node metrics2) {
		if(!metrics1.isReachable() && !metrics2.isReachable()) return 0;
		if(!metrics1.isReachable() && metrics2.isReachable()) return -1;
		if(metrics1.isReachable() && !metrics2.isReachable()) return 1;
		if(metrics1.metrics.isEmpty() && metrics2.metrics.isEmpty()) return 0;
		if(metrics1.metrics.isEmpty() && !metrics2.metrics.isEmpty()) return -1;
		if(!metrics1.metrics.isEmpty() && metrics2.metrics.isEmpty()) return 1;
		if(metrics1.metrics.get("heap-memory-free").getAverage() < metrics2.metrics.get("heap-memory-free").getAverage()) return -1;
		if(metrics2.metrics.get("heap-memory-free").getAverage() < metrics1.metrics.get("heap-memory-free").getAverage()) return 1;
		if(!metrics1.metrics.containsKey("system-cpu-load") || !metrics2.metrics.containsKey("system-cpu-load")) return 0;
		if(metrics2.metrics.get("system-cpu-load").getAverage() < metrics1.metrics.get("system-cpu-load").getAverage()) return -1;
		if(metrics1.metrics.get("system-cpu-load").getAverage() < metrics2.metrics.get("system-cpu-load").getAverage()) return 1;
		return 0;
	}
	
}
