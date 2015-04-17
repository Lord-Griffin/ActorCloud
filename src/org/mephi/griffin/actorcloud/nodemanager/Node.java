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
package org.mephi.griffin.actorcloud.nodemanager;

import akka.actor.Address;
import akka.cluster.Metric;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 *
 * @author Griffin
 */
public class Node {	
	private final Address address;
	private boolean reachable;
	Map<String, MyMetric> metrics;
	
	public Node(Address address) {
		this.address = address;
		this.reachable = true;
		metrics = new HashMap<>();
	}
	
//	public Node(Address address, Iterable<Metric> metrics) {
//		this.address = address;
//		this.reachable = true;
//		this.metrics = new HashMap();
//		for(Metric metric : metrics) {
//			double alpha = -1;
//			if(metric.average().nonEmpty()) alpha = metric.average().get().alpha();
//			this.metrics.put(metric.name(), new MyMetric(metric.value().doubleValue(), alpha));
//		}
//		ArrayList<String> requiredMetrics = new ArrayList<>();
//		if(!this.metrics.containsKey("heap-memory-used")) requiredMetrics.add("heap-memory-used");
//		if(!this.metrics.containsKey("heap-memory-committed")) requiredMetrics.add("heap-memory-committed");
//		if(!this.metrics.containsKey("heap-memory-max")) requiredMetrics.add("heap-memory-max");
//		if(!this.metrics.containsKey("heap-memory-free")) requiredMetrics.add("heap-memory-free");
//		if(!requiredMetrics.isEmpty()) throw new IllegalStateException("There are no required metrics for node " + address + ": " + requiredMetrics);
//	}
	
	public Address getAddress() {
		return address;
	}
	
	public Map<String, MyMetric> getMetrics() {
		return metrics;
	}
	
	public MyMetric getMetric(String key) {
		return metrics.get(key);
	}
	
	public boolean isReachable() {
		return reachable;
	}
	
	public void setReachable(boolean reachable) {
		this.reachable = reachable;
	}
	
//	public void setMetrics(Iterable<Metric> metrics) {
//		this.metrics.clear();
//		for(Metric metric : metrics) {
//			double alpha = -1;
//			if(metric.average().nonEmpty()) alpha = metric.average().get().alpha();
//			this.metrics.put(metric.name(), new MyMetric(metric.value().doubleValue(), alpha));
//		}
//		ArrayList<String> requiredMetrics = new ArrayList<>();
//		if(!this.metrics.containsKey("heap-memory-used")) requiredMetrics.add("heap-memory-used");
//		if(!this.metrics.containsKey("heap-memory-committed")) requiredMetrics.add("heap-memory-committed");
//		if(!this.metrics.containsKey("heap-memory-max")) requiredMetrics.add("heap-memory-max");
//		if(!this.metrics.containsKey("heap-memory-free")) requiredMetrics.add("heap-memory-free");
//		if(!requiredMetrics.isEmpty()) throw new IllegalStateException("There are no required metrics for node " + address + ": " + requiredMetrics);
//	}
	
	public void updateMetrics(Iterable<Metric> metrics) {
		ArrayList<String> requiredMetrics = new ArrayList<>();
		requiredMetrics.add("heap-memory-used");
		requiredMetrics.add("heap-memory-committed");
		requiredMetrics.add("heap-memory-max");
//		requiredMetrics.add("heap-memory-free");
		for(Metric metric : metrics) {
			if(this.metrics.get(metric.name()) != null) {
				this.metrics.get(metric.name()).update(metric.value().doubleValue());
			}
			else {
				double alpha = -1;
				if(metric.average().nonEmpty()) alpha = metric.average().get().alpha();
				this.metrics.put(metric.name(), new MyMetric(metric.value().doubleValue(), alpha));
			}
			requiredMetrics.remove(metric.name());
		}
		if(!requiredMetrics.isEmpty()) throw new IllegalStateException("There are no required metrics for node " + address + ": " + requiredMetrics);
	}
	
	public Node getCopy() {
		Node copy = new Node(address);
		copy.reachable = reachable;
		for(Entry<String, MyMetric> entry : metrics.entrySet())
			copy.metrics.put(entry.getKey(), entry.getValue().getCopy());
		return copy;
	}
	
	@Override
	public String toString() {
		String res = address + " is ";
		if(!reachable) res += "not ";
		res += "reachable: ";
		for(Entry<String, MyMetric> metric : metrics.entrySet())
			res += metric.getKey() + " " + metric.getValue();
		return res;
	}
}
