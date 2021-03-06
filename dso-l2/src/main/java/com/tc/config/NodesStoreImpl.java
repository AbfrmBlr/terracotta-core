/*
 *
 *  The contents of this file are subject to the Terracotta Public License Version
 *  2.0 (the "License"); You may not use this file except in compliance with the
 *  License. You may obtain a copy of the License at
 *
 *  http://terracotta.org/legal/terracotta-public-license.
 *
 *  Software distributed under the License is distributed on an "AS IS" basis,
 *  WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 *  the specific language governing rights and limitations under the License.
 *
 *  The Covered Software is Terracotta Core.
 *
 *  The Initial Developer of the Covered Software is
 *  Terracotta, Inc., a Software AG company
 *
 */
package com.tc.config;

import com.tc.config.schema.ActiveServerGroupConfig;
import com.tc.config.schema.setup.ConfigurationSetupException;
import com.tc.config.schema.setup.L2ConfigurationSetupManager;
import com.tc.net.TCSocketAddress;
import com.tc.net.groups.Node;
import com.tc.object.config.schema.L2Config;
import com.tc.util.Assert;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class NodesStoreImpl implements NodesStore, TopologyChangeListener {
  private final Set<Node>                                   nodes;
  private final CopyOnWriteArraySet<TopologyChangeListener> listeners              = new CopyOnWriteArraySet<>();
  private L2ConfigurationSetupManager                       configSetupManager;
  private volatile Set<String>                              nodeNamesForThisGroup  = new HashSet<>();
  private volatile Map<String, String>                      nodeNamesToServerNames = new HashMap<>();

  /**
   * used for tests
   */
  public NodesStoreImpl(Set<Node> nodes) {
    this.nodes = Collections.synchronizedSet(nodes);
  }

  public NodesStoreImpl(Set<Node> nodes, Set<String> nodeNamesForThisGroup, L2ConfigurationSetupManager configSetupManager) {
    this(nodes);
    this.nodeNamesForThisGroup.addAll(nodeNamesForThisGroup);
    this.configSetupManager = configSetupManager;
    initNodeNamesToServerNames();
  }

  @Override
  public void topologyChanged(ReloadConfigChangeContext context) {
    this.nodes.addAll(context.getNodesAdded());
    this.nodes.removeAll(context.getNodesRemoved());
    initNodeNamesToServerNames();

    for (TopologyChangeListener listener : listeners) {
      listener.topologyChanged(context);
    }
  }

  @Override
  public void registerForTopologyChange(TopologyChangeListener listener) {
    listeners.add(listener);
  }

  @Override
  public Node[] getAllNodes() {
    Assert.assertTrue(this.nodes.size() > 0);
    return this.nodes.toArray(new Node[this.nodes.size()]);
  }

  private void initNodeNamesToServerNames() {
    HashMap<String, String> tempNodeNamesToServerNames = new HashMap<>();
    String[] serverNames = configSetupManager.allCurrentlyKnownServers();
    for (String serverName : serverNames) {
      try {
        L2Config l2Config = configSetupManager.dsoL2ConfigFor(serverName);
        String host = l2Config.tsaGroupPort().getBind();
        if (TCSocketAddress.WILDCARD_IP.equals(host)) {
          host = l2Config.host();
        }
        tempNodeNamesToServerNames.put(host + ":" + l2Config.tsaPort().getValue(), serverName);
      } catch (ConfigurationSetupException e) {
        throw new RuntimeException(e);
      }
    }
    this.nodeNamesToServerNames = tempNodeNamesToServerNames;
  }

  @Override
  public String getServerNameFromNodeName(String nodeName) {

    return nodeNamesToServerNames.get(nodeName);
  }

  /**
   * ServerNamesOfThisGroup methods ...
   */

  @Override
  public boolean hasServerInGroup(String serverName) {
    return nodeNamesForThisGroup.contains(serverName);
  }

  void updateServerNames(ReloadConfigChangeContext context) {
    Set<String> tmp =  new HashSet<>(nodeNamesForThisGroup);

    for (Node n : context.getNodesAdded()) {
      tmp.add(n.getServerNodeName());
    }

    for (Node n : context.getNodesRemoved()) {
      tmp.remove(n.getServerNodeName());
    }

    this.nodeNamesForThisGroup = tmp;
  }

  @Override
  public boolean hasServerInCluster(String name) {
    return nodeNamesForThisGroup.contains(name);
  }


  @Override
  public String getGroupNameFromNodeName(String nodeName) {
    if (configSetupManager == null) { return null; }
    ActiveServerGroupConfig asgc = configSetupManager.getActiveServerGroupForThisL2();
    if (asgc == null) { return null; }
    return asgc.getGroupName();
  }
}
