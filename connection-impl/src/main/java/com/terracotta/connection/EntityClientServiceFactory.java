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
 *  The Covered Software is Entity API.
 *
 *  The Initial Developer of the Covered Software is
 *  Terracotta, Inc., a Software AG company
 *
 */
package com.terracotta.connection;

import org.terracotta.connection.entity.Entity;
import org.terracotta.entity.EntityClientService;
import org.terracotta.entity.EntityMessage;
import org.terracotta.entity.EntityResponse;

import java.util.ServiceLoader;


public class EntityClientServiceFactory {
  public static <T extends Entity, C, U> EntityClientService<T, C, ? extends EntityMessage, ? extends EntityResponse, U> creationServiceForType(Class<T> cls) {
    return creationServiceForType(cls, EntityClientServiceFactory.class.getClassLoader());
  }

  /**
   * Note that this returns the service or null if no service could be found.
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public static <T extends Entity, C, U> EntityClientService<T, C, ? extends EntityMessage, ? extends EntityResponse, U> creationServiceForType(Class<T> cls, ClassLoader classLoader) {
    EntityClientService<T, C, ? extends EntityMessage, ? extends EntityResponse, U> foundService = null;
    ServiceLoader<EntityClientService> implementations = ServiceLoader.load(EntityClientService.class,  classLoader);
    for (EntityClientService instance : implementations) {
      if (instance.handlesEntityType(cls)) {
        foundService = instance;
        break;
      }
    }
    return foundService;
  }
}
