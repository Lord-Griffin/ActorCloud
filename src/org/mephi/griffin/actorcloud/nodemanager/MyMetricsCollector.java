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

import akka.actor.ActorSystem;
import akka.actor.Address;
import akka.cluster.Cluster;
import akka.cluster.Metric;
import akka.cluster.MetricsCollector;
import akka.cluster.NodeMetrics;
import com.sun.management.OperatingSystemMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.List;
import scala.Option;
import scala.Some;
import scala.collection.JavaConversions;

/**
 *
 * @author Griffin
 */
public class MyMetricsCollector implements MetricsCollector {
	
	private final Address address;
	private final Some<Object> decayFactorOption;
	private final MemoryMXBean memoryMBean;
	private final OperatingSystemMXBean osMBean;
	
	public MyMetricsCollector(Address address, double decayFactor) {
		this.address = address;
		decayFactorOption = new Some<Object>(decayFactor);
		memoryMBean = ManagementFactory.getMemoryMXBean();
		osMBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
	}
	
	public MyMetricsCollector(ActorSystem system) {
		this(Cluster.get(system));
	}
	
	private MyMetricsCollector(Cluster cluster) {
		this.address = cluster.selfAddress();
		double decayFactor = 1 - Math.exp(-Math.log(2) / cluster.settings().MetricsMovingAverageHalfLife().toMillis() * cluster.settings().MetricsInterval().toMillis());
		decayFactorOption = new Some<Object>(decayFactor);
		memoryMBean = ManagementFactory.getMemoryMXBean();
		osMBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
	}

	@Override
	public NodeMetrics sample() {
		return new NodeMetrics(address, System.currentTimeMillis(), metrics());
	}
	
	public scala.collection.immutable.Set<Metric> metrics() {
		MemoryUsage heap = memoryMBean.getHeapMemoryUsage();
		long free = heap.getMax() - heap.getUsed();
		if(osMBean.getFreeSwapSpaceSize() > 0 && osMBean.getFreeSwapSpaceSize() < free)
			free = osMBean.getFreeSwapSpaceSize() + heap.getCommitted() - heap.getUsed();
		List<Metric> set = new ArrayList<>();
		set.add(Metric.create("processors", osMBean.getAvailableProcessors(), Option.empty()).get());
		set.add(Metric.create("system-cpu-load", osMBean.getSystemCpuLoad(), decayFactorOption).get());
		set.add(Metric.create("heap-memory-used", heap.getUsed(), decayFactorOption).get());
		set.add(Metric.create("heap-memory-committed", heap.getCommitted(), decayFactorOption).get());
		set.add(Metric.create("heap-memory-max", heap.getMax(), Option.empty()).get());
		set.add(Metric.create("heap-memory-free", free, decayFactorOption).get());
		//set.add(Metric.create("system-memory-free", osMBean.getFreePhysicalMemorySize(), decayFactorOption).get());
		//set.add(Metric.create("system-swap-free", osMBean.getFreeSwapSpaceSize(), decayFactorOption).get());
		//set.add(Metric.create("system-memory-total", osMBean.getTotalPhysicalMemorySize(), Option.empty()).get());
		//set.add(Metric.create("system-swap-total", osMBean.getTotalSwapSpaceSize(), Option.empty()).get());
		return JavaConversions.asScalaBuffer(set).toSet();
	}

	@Override
	public void close() throws IOException {}
	
}
