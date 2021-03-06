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
package org.terracotta.voter;

import java.util.Arrays;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class VetoVote {

  /**
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      throw new IllegalArgumentException(Arrays.toString(args));
    }
    ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("root");
    logger.setLevel(ch.qos.logback.classic.Level.INFO);
    new TCVoterImpl().vetoVote(args[0]);
  }
  
}
