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

import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.ClusterEvent.ClusterDomainEvent;
import akka.cluster.ClusterEvent.ClusterMetricsChanged;
import akka.cluster.ClusterEvent.MemberExited;
import akka.cluster.ClusterEvent.MemberRemoved;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.ClusterEvent.UnreachableMember;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 *
 * @author Griffin
 */
public class NodeManager extends UntypedActor {
	LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	Cluster cluster = Cluster.get(getContext().system());
	
	@Override
	public void preStart() {
		cluster.subscribe(getSelf(), ClusterEvent.initialStateAsEvents(), ClusterDomainEvent.class);
	}
	
	@Override
	public void postStop() {
		cluster.unsubscribe(getSelf());
	}
	
	@Override
	public void onReceive(Object message) {
		if (message instanceof MemberUp) {
			MemberUp mUp = (MemberUp) message;
			log.info("Member is Up: {}", mUp.member());
			log.info("{}", mUp.member().status());
		}
		else if (message instanceof MemberExited) {
			MemberExited mExited = (MemberExited) message;
			log.info("Member is Removed: {}", mExited.member());
			log.info("{}", mExited.member().status());
		}
		else if (message instanceof MemberRemoved) {
			MemberRemoved mRemoved = (MemberRemoved) message;
			log.info("Member is Removed: {}", mRemoved.member());
			log.info("{}", mRemoved.member().status());
		}
		else if (message instanceof UnreachableMember) {
			UnreachableMember mUnreachable = (UnreachableMember) message;
			log.info("Member detected as unreachable: {}", mUnreachable.member());
			log.info("{}", mUnreachable.member().status());
		}
		/*else if (message instanceof ClusterMetricsChanged) {
			ClusterMetricsChanged cmc = (ClusterMetricsChanged) message;
			log.info("Cluster metrics changed: {}", cmc.getNodeMetrics());
		}
		else if (message instanceof ClusterDomainEvent) {
			log.info("Other: {}", message);
		}*/
		else {
			unhandled(message);
		}
	}
}
