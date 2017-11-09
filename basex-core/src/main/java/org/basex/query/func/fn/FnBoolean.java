package org.basex.query.func.fn;

import org.basex.query.*;
import org.basex.query.expr.*;
import org.basex.query.func.*;
import org.basex.query.value.item.*;
import org.basex.query.value.type.*;
import org.basex.util.*;

/**
 * Function implementation.
 *
 * @author BaseX Team 2005-17, BSD License
 * @author Christian Gruen
 */
public final class FnBoolean extends StandardFunc {
  @Override
  public Item item(final QueryContext qc, final InputInfo ii) throws QueryException {
    return Bln.get(exprs[0].ebv(qc, info).bool(info));
  }

  @Override
  protected Expr opt(final CompileContext cc) throws QueryException {
    final Expr ex = exprs[0].optimizeEbv(cc);

    // boolean($node/text()) -> exists($node/text())
    final SeqType st = ex.seqType();
    if(st.type instanceof NodeType) return cc.function(Function.EXISTS, info, exprs);

    // simplify, e.g.: boolean(true())) -> true()
    if(st.eq(SeqType.BLN)) return ex;

    exprs[0] = ex;
    return this;
  }

  @Override
  public Expr optimizeEbv(final CompileContext cc) {
    // if A is not numeric: expr[boolean(A)] -> expr[A]
    final Expr ex = exprs[0];
    return ex.seqType().mayBeNumber() ? this : cc.replaceEbv(this, ex);
  }
}
