package org.basex.query.up.primitives;

import org.basex.io.*;
import org.basex.query.value.node.*;
import org.basex.util.*;

/**
 * Container for inputs that are to be appended to a database.
 *
 * @author BaseX Team 2005-20, BSD License
 * @author Christian Gruen
 */
public final class NewInput {
  /** Target path. */
  public String path;
  /** Node to be added (can be {@code null}). */
  public ANode node;
  /** Input reference (can be {@code null}). */
  public IO io;

  @Override
  public String toString() {
    return new StringBuilder().append(Util.className(this)).append('[').append("path: \"").
      append(path).append("\", ").append(node != null ? "node" : "io: " + io).
      append(']').toString();
  }
}
