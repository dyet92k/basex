package org.basex.query.xquery.path;

import static org.basex.query.xquery.path.Axis.*;
import static org.basex.query.xquery.path.Test.NODE;
import static org.basex.query.xquery.XQText.*;
import static org.basex.query.xquery.XQTokens.*;
import java.io.IOException;
import org.basex.data.Serializer;
import org.basex.query.xquery.XQContext;
import org.basex.query.xquery.XQException;
import org.basex.query.xquery.expr.Arr;
import org.basex.query.xquery.expr.CAttr;
import org.basex.query.xquery.expr.Expr;
import org.basex.query.xquery.item.DNode;
import org.basex.query.xquery.item.Item;
import org.basex.query.xquery.item.Nod;
import org.basex.query.xquery.item.QNm;
import org.basex.query.xquery.item.Seq;
import org.basex.query.xquery.item.Type;
import org.basex.query.xquery.iter.Iter;
import org.basex.query.xquery.iter.NodIter;
import org.basex.query.xquery.iter.NodeIter;
import org.basex.query.xquery.util.Err;
import org.basex.query.xquery.util.NodeBuilder;
import org.basex.query.xquery.util.SeqBuilder;
import org.basex.util.Array;

/**
 * Path expression.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-08, ISC License
 * @author Christian Gruen
 */
public class Path extends Arr {
  /** Top expression. */
  public Expr root;
  /** Steps flag. */
  private boolean steps = true;
  /** Flag for result caching. */
  private boolean cache;
  /** Cached result. */
  private Item res;
  /** Cached item. */
  private Item item;

  /**
   * Constructor.
   * @param r root expression
   * @param p expression list
   */
  public Path(final Expr r, final Expr[] p) {
    super(p);
    root = r;
  }

  @Override
  public Expr comp(final XQContext ctx) throws XQException {
    root = ctx.comp(root);
    Expr e = root;

    if(expr[0] instanceof Step) {
      final Step s = (Step) expr[0];
      if(e instanceof DNode && (s.axis == ATTR || s.axis == PARENT ||
          s.axis == SELF && s.test != NODE) || e instanceof CAttr &&
          s.axis == CHILD) Err.or(COMPSELF, s);
    }

    for(int i = 0; i != expr.length; i++) {
      expr[i] = ctx.comp(expr[i]);
      steps &= expr[i] instanceof Step;
    }

    if(steps) {
      mergeDesc(ctx);
      checkEmpty();
      // analyze if result set can be cached - no predicates or no variables...
      cache = true;
      boolean noPreds = true;
      for(final Expr ex : expr) {
        // check if we have a predicate
        if(((Step) ex).expr.length != 0) {
          noPreds = false;
          // check if we also find a variable
          if(ex.uses(Using.VAR)) {
            cache = false;
            break;
          }
        }
      }
      // no predicates, one child or descendant step...
      final Axis axis = ((Step) expr[0]).axis;
      // if we've found a variable, cache will be true. But we can't 
      // handle variables in SimpleIterPath yet.
      if(!cache && noPreds && expr.length == 1 && (axis == Axis.DESC || 
          axis == Axis.DESCORSELF || axis == Axis.CHILD)) {
        return new SimpleIterPath(root, expr);
      }      
    }
    return this;
  }

  @Override
  public Iter iter(final XQContext ctx) throws XQException {
    final Item it = ctx.iter(root).finish();

    if(cache && res != null && item == it && it.type == Type.DOC)
      return res.iter();

    item = it;
    final Item c = ctx.item;
    final int cs = ctx.size;
    final int cp = ctx.pos;
    ctx.item = it;
    res = eval(ctx);

    ctx.item = c;
    ctx.size = cs;
    ctx.pos = cp;
    return res.iter();
  }

  /**
   * Evaluates the location path.
   * @param ctx query context
   * @return resulting item
   * @throws XQException evaluation exception
   */
  protected Item eval(final XQContext ctx) throws XQException {
    // simple location step traversal...
    if(steps) {
      final NodIter ir = new NodIter();
      iter(0, ir, ctx);

      if(ir.size == 0) return Seq.EMPTY;
      if(ir.size == 1) return ir.list[0];

      final NodeBuilder nb = new NodeBuilder(false);
      Nod it;
      while((it = ir.next()) != null) nb.add(it);
      return nb.finish();
    }

    Item it = ctx.item;
    for(final Expr e : expr) {
      if(e instanceof Step) {
        ctx.item = it;
        it = ctx.iter(e).finish();
      } else {
        final SeqBuilder sb = new SeqBuilder();
        final Iter ir = it.iter();
        ctx.size = it.size();
        ctx.pos = 1;
        Item i;
        while((i = ir.next()) != null) {
          if(!i.node()) Err.or(NODESPATH, this, i.type);
          ctx.item = i;
          sb.add(ctx.iter(e));
          ctx.pos++;
        }
        it = sb.finish();
      }
    }

    // either nodes or atomic items are allowed in a result set, but not both
    final Iter ir = it.iter();
    Item i = ir.next();
    if(i != null) {
      if(i.node()) {
        // [CG] XQuery/evaluate path: verify when results might be ordered
        final NodeBuilder nb = new NodeBuilder(false);
        nb.add((Nod) i);
        while((i = ir.next()) != null) {
          if(!i.node()) Err.or(EVALNODESVALS);
          nb.add((Nod) i);
        }
        return nb.finish();
      }
      while((i = ir.next()) != null) if(i.node()) Err.or(EVALNODESVALS);
    }
    return it;
  }

  /**
   * Path Iterator.
   * @param l current step
   * @param ni node builder
   * @param ctx query context
   * @throws XQException query exception
   */
  private void iter(final int l, final NodIter ni, final XQContext ctx)
      throws XQException {

    final NodeIter ir = (NodeIter) (ctx.iter(expr[l]));
    final boolean more = l + 1 != expr.length;
    Nod it;
    while((it = ir.next()) != null) {
      if(more) {
        ctx.item = it;
        iter(l + 1, ni, ctx);
      } else {
        ctx.checkStop();
        ni.add(it);
      }
    }
  }

  /**
   * Merges superfluous descendant-or-self steps.
   * This method implies that all expressions are location steps.
   * @param ctx query context
   */
  private void mergeDesc(final XQContext ctx) {
    int ll = expr.length;
    for(int l = 1; l < ll; l++) {
      if(!((Step) expr[l - 1]).simple(DESCORSELF)) continue;
      final Step next = (Step) expr[l];
      if(next.axis == CHILD && !next.uses(Using.POS)) {
        Array.move(expr, l, -1, ll-- - l);
        next.axis = DESC;
      }
    }
    if(ll != expr.length) {
      ctx.compInfo(OPTDESC);
      final Expr[] tmp = new Expr[ll];
      System.arraycopy(expr, 0, tmp, 0, ll);
      expr = tmp;
    }
  }

  /**
   * Check if any of the steps will always yield no results.
   * This method implies that all expressions are location steps.
   * @throws XQException evaluation exception
   */
  private void checkEmpty() throws XQException {
    final int ll = expr.length;

    for(int l = 1; l < ll; l++) {
      final Step step = (Step) expr[l];
      final Step step0 = (Step) expr[l - 1];

      if(step.axis == SELF) {
        if(step.test == NODE) continue;

        if(step0.axis == ATTR) warning(step);
        if(step0.test.type == Type.TXT && step.test.type != Type.TXT)
          warning(step);

        final QNm name = step.test.name;
        final QNm name0 = step0.test.name;
        if(name0 == null || name == null) continue;
        if(!name.eq(name0)) warning(step);

      } else if(step.axis == DESCORSELF) {
        if(step.test == NODE) continue;
        if(step0.axis == ATTR) warning(step);

        if(step0.test.type == Type.TXT && step.test.type != Type.TXT)
          warning(step);
      } else if(step.axis == DESC || step.axis == CHILD) {
        if(step0.axis == ATTR || step0.test.type == Type.TXT)
          warning(step);
      }
    }
  }

  @Override
  public boolean uses(final Using u) {
    return super.uses(u) || root.uses(u);
  }

  @Override
  public Type returned() {
    return Type.NOD;
  }

  /**
   * Throws a static warning.
   * @param s step
   * @throws XQException evaluation exception
   */
  protected void warning(final Expr s) throws XQException {
    Err.or(COMPSELF, s);
  }

  /**
   * Returns a string representation of the path.
   * @return path as string
   */
  public String path() {
    final StringBuilder sb = new StringBuilder();
    for(int p = 0; p < expr.length; p++) {
      if(p != 0) sb.append("/");
      sb.append(expr[p]);
    }
    return sb.toString();
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    if(root != null) sb.append(root + "/");
    return sb.append(path()).toString();
  }

  @Override
  public void plan(final Serializer ser) throws IOException {
    ser.openElement(this, NS, timer());
    root.plan(ser);
    for(final Expr e : expr) e.plan(ser);
    ser.closeElement();
  }
}
