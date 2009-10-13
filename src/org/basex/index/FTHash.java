package org.basex.index;

import org.basex.util.Array;
import org.basex.util.IntList;
import org.basex.util.Num;
import org.basex.util.Set;

/**
 * This class indexes the tokens in a hash structure.
 * The iterator returns all tokens in a sorted manner.
 *
 * @author Workgroup DBIS, University of Konstanz 2005-09, ISC License
 * @author Christian Gruen
 */
final class FTHash extends Set {
  /** Compressed pre values. */
  byte[][] pre = new byte[CAP][];
  /** Compressed pos values. */
  byte[][] pos = new byte[CAP][];
  /** Number of pre values. */
  int[] ns = new int[CAP];

  /**
   * Indexes the specified token.
   * @param key token
   * @param pr id
   * @param po position
   */
  void index(final byte[] key, final int pr, final int po) {
    int id = add(key);
    if(id < 0) {
      id = -id;
      pre[id] = Num.add(pre[id], pr);
      pos[id] = Num.add(pos[id], po);
      ++ns[id];
    } else {
      pre[id] = Num.newNum(pr);
      pos[id] = Num.newNum(po);
      ns[id] = 1;
    }
  }

  @Override
  protected void rehash() {
    super.rehash();
    pre = Array.extend(pre);
    pos = Array.extend(pos);
    ns = Array.extend(ns);
  }

  /** Integer list. */
  private IntList il;
  /** Current iterator. */
  private int it;

  /**
   * Initializes the iterator, which sorts all hash entries. Note that the
   * hash structure will be destroyed by this process as all keys will
   * be reordered - but this way, memory is saved.
   */
  void init() {
    final int[] ids = new int[size];
    for(int i = 0; i < size; i++) ids[i] = i;
    il = new IntList(ids);
    il.sort(keys, false, true);
    it = 0;
  }

  /**
   * Checks if more nodes can be returned.
   * @return result of check
   */
  boolean more() {
    return ++it < size;
  }

  /**
   * Returns the current key.
   * @return pointer
   */
  byte[] key() {
    return keys[it];
  }

  /**
   * Returns the next pointer.
   * @return pointer
   */
  int next() {
    return il.get(it);
  }
}
