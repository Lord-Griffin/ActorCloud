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

/**
 *
 * @author Griffin
 */
public class MyMetric {
	private double value;
	private double average;
	private final double alpha;
	
	public MyMetric(double value, double alpha) {
		this.value = value;
		this.average = value;
		this.alpha = alpha;
	}
	
	public double getValue() {
		return value;
	}
	
	public double getAverage() {
		if(alpha != -1) return average;
		return value;
	}
	
	public double getAlpha() {
		return alpha;
	}
	
	public void update(double value) {
		this.value = value;
		if(alpha != -1) {
			average = alpha * value + (1 - alpha) * average;
		}
	}
	
	public MyMetric getCopy() {
		MyMetric copy = new MyMetric(value, alpha);
		copy.average = average;
		return copy;
	}
}
