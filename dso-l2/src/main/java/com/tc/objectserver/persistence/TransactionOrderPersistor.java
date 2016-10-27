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
package com.tc.objectserver.persistence;

import org.terracotta.persistence.IPersistentStorage;
import org.terracotta.persistence.KeyValueStorage;

import com.tc.net.ClientID;
import com.tc.net.protocol.tcm.ChannelID;
import com.tc.object.tx.TransactionID;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;


/**
 * Stores the information mapping the client-local TransactionIDs of in-flight transactions into a global order.
 * This is persisted because reconnect on restart needs to ensure that the transactions being replayed are done so in
 * the same order as their original order.
 */
public class TransactionOrderPersistor {
  private static final String CLIENT_LOCAL_LISTS = "client_local_lists";
  private static final String LOCAL_VARIABLES = "local_variables";
  private static final String RECEIVED_TRANSACTION_COUNT = "local_variables:received_transaction_count";
//  must be a LinkedHashSet to preserve entry order.  Using set for constant time removal
  private final KeyValueStorage<ClientID, List<ClientTransaction>> clientLocals;
  private final KeyValueStorage<String, Long> localVariables;
    
  private List<ClientTransaction> globalList = null;
  
  // Unchecked and raw warnings because we are trying to use Class<List<?>>, which the compiler doesn't like but has no runtime meaning.
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public TransactionOrderPersistor(IPersistentStorage storageManager, Set<ChannelID> clients) {
    // In the future, we probably want a different storage approach since storing the information, this way, doesn't
    // work well with the type system (Lists inside KeyValueStorage) and will perform terribly.
    this.clientLocals = storageManager.getKeyValueStorage(CLIENT_LOCAL_LISTS, ClientID.class, (Class)LinkedHashSet.class);
    this.localVariables = storageManager.getKeyValueStorage(LOCAL_VARIABLES, String.class, (Class)Long.class);
    if (!this.localVariables.containsKey(RECEIVED_TRANSACTION_COUNT)) {
      this.localVariables.put(RECEIVED_TRANSACTION_COUNT, 0L);
    }
  }

  /**
   * Called to handle the changes to persisted transactions, based on a new one.
   * This new transactionID will be enqueued as the most recent transaction for the given source but also globally.
   * Any transactions for this source which are older than oldestTransactionOnClient will be removed from persistence.
   */
  public synchronized void updateWithNewMessage(ClientID source, TransactionID transactionID, TransactionID oldestTransactionOnClient) {
    // We need to ensure that the arguments are sane.
    if ((null == oldestTransactionOnClient) || (null == transactionID)) {
      throw new IllegalArgumentException("Transactions cannot be null");
    }
    if (oldestTransactionOnClient.compareTo(transactionID) > 0) {
      throw new IllegalArgumentException("Oldest transaction cannot come after new transaction");
    }
    
    // This operation requires that the globalList be rebuilt.
    this.globalList = null;
    
    // Get the local list for this client.
    List<ClientTransaction> localList = clientLocals.get(source);
    if (null == localList) {
      localList = new LinkedList<>();
    }
    
    // Increment the number of received transactions.
    long received = (long)this.localVariables.get(RECEIVED_TRANSACTION_COUNT) + 1;
    this.localVariables.put(RECEIVED_TRANSACTION_COUNT, received);
    
    // Create the new pair.
    ClientTransaction transaction = new ClientTransaction();
    transaction.id = transactionID;
    transaction.globalID = received;
    
    Iterator<ClientTransaction> walk = localList.iterator();
    while (walk.hasNext()) {
      ClientTransaction next = walk.next();
      if (-1 == next.id.compareTo(oldestTransactionOnClient)) {
        walk.remove();
      } else {
        break;
      }
    }
    
    // Create this new pair and add it to the global list.
    localList.add(transaction);
    clientLocals.put(source, localList);
  }

  /**
   * Called when we no longer need to track transaction ordering information from source (presumably due to a disconnect).
   */
  public void removeTrackingForClient(ClientID source) {
    // Remove the local list for this client.
    clientLocals.remove(source);
  }

  private static class ClientTransaction implements Serializable {
    private static final long serialVersionUID = 1L;
    public transient ClientID client;
    public TransactionID id;
    public long globalID;

    @Override
    public int hashCode() {
      return (7 * this.client.hashCode()) ^ this.id.hashCode();
    }
    @Override
    public boolean equals(Object obj) {
      boolean isEqual = (obj == this);
      if (!isEqual && (obj instanceof ClientTransaction)) {
        ClientTransaction other = (ClientTransaction) obj;
        isEqual = this.client.equals(other.client)
              & this.id.equals(other.id);
      }
      return isEqual;
    }
  }
  
  @SuppressWarnings("deprecation")
  private List<ClientTransaction> buildGlobalListIfNessessary() {
    if (null == this.globalList) {
      TreeMap<Long, ClientTransaction> sortMap = new TreeMap<>();
      for (ClientID client : clientLocals.keySet()) {
        List<ClientTransaction> transactions = clientLocals.get(client);
        for (ClientTransaction ct : transactions) {
          ct.client = client;
          sortMap.put(ct.globalID, ct);
        }
      }
      globalList = Collections.unmodifiableList(new ArrayList<>(sortMap.values()));
    }
    return globalList;
  }

  /**
   * Called to ask where a given client-local transaction exists in the global transaction list.
   * Returns the index or -1 if it isn't known.
   */
  public int getIndexToReplay(ClientID source, TransactionID transactionID) {
    int index = -1;
    List<ClientTransaction> list = buildGlobalListIfNessessary();
    int seek = 0;
    for (ClientTransaction transaction : list) {
      if (source.equals(transaction.client) && transactionID.equals(transaction.id)) {
        index = seek;
        break;
      }
      seek += 1;
    }
    return index;
  }

  /**
   * Clears all internal state.
   */
  public void clearAllRecords() {
    this.clientLocals.clear();
    this.globalList = null;
  }

  /**
   * @return The number of transactions which have been observed by the persistor (NOT the number persisted).
   */
  public long getReceivedTransactionCount() {
    return this.localVariables.get(RECEIVED_TRANSACTION_COUNT);
  }
}
