/*
 * All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.
 */

/* Generated By:JJTree: Do not edit this line. ASTCflow.java */

package com.tc.aspectwerkz.expression.ast;

public class ASTCflow extends SimpleNode {
  public ASTCflow(int id) {
    super(id);
  }

  public ASTCflow(ExpressionParser p, int id) {
    super(p, id);
  }

  /**
   * Accept the visitor. *
   */
  public Object jjtAccept(ExpressionParserVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }
}
