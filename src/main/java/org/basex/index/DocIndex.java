package org.basex.index;

import static org.basex.util.Token.*;

import java.io.File;
import java.io.IOException;

import org.basex.core.Prop;
import org.basex.data.Data;
import org.basex.io.in.DataInput;
import org.basex.io.out.DataOutput;
import org.basex.util.Array;
import org.basex.util.Util;
import org.basex.util.list.IntList;
import org.basex.util.list.ObjList;

/**
 * This index contains references to all document nodes in a database.
 * It is incrementally updated if the database is modified.
 *
 * @author BaseX Team 2005-11, BSD License
 * @author Christian Gruen
 */
public final class DocIndex implements Index {
  /** Data reference. */
  private final Data data;
  /** Pre values of document nodes. */
  private IntList docs;
  /** Sorted document paths. */
  private byte[][] paths;
  /** Mapping between document paths and pre values. */
  private int[] order;

  /**
   * Constructor.
   * @param d data reference
   */
  public DocIndex(final Data d) {
    data = d;
  }

  /**
   * Reads the document index.
   * @param in input stream
   * @return success flag
   * @throws IOException I/O exception
   */
  public synchronized boolean read(final DataInput in) throws IOException {
    docs = in.readDiffs();
    return true;
  }

  /**
   * Writes the document index.
   * @param out output stream
   * @throws IOException I/O exception
   */
  public void write(final DataOutput out) throws IOException {
    out.writeDiffs(docs());
  }

  /**
   * Returns the pre values of all document nodes.
   * A single dummy node is returned if the database is empty.
   * @return document nodes
   */
  public synchronized IntList docs() {
    if(docs == null) {
      update();
      docs = new IntList();
      final int is = data.meta.size;
      for(int i = 0; i < is; i += data.size(i, Data.DOC)) docs.add(i);
      data.meta.dirty = true;
    }
    return docs;
  }

  /**
   * Adds entries to the index and updates subsequent nodes.
   * @param pre insertion position
   * @param d data reference to be inserted
   */
  public void insert(final int pre, final Data d) {
    // find all document nodes
    final int dsize = d.meta.size;
    final IntList pres = new IntList();
    for(int dpre = 0; dpre < dsize;) {
      final int k = d.kind(dpre);
      if(k == Data.DOC) pres.add(pre + dpre);
      dpre += d.size(dpre, k);
    }

    final IntList il = docs();
    int i = il.sortedIndexOf(pre);
    if(i < 0) i = -i - 1;
    il.insert(i, pres.toArray());
    il.move(dsize, i + pres.size());
    update();
  }

  /**
   * Deletes the specified entry and updates subsequent nodes.
   * @param pre pre value
   * @param size number of deleted nodes
   */
  public void delete(final int pre, final int size) {
    final IntList il = docs();
    int i = il.sortedIndexOf(pre);
    if(i < 0) i = -i - 1;
    else il.delete(i);
    il.move(-size, i);
    update();
  }

  /**
   * Replaces entries in the index.
   * @param pre insertion position
   * @param d data reference to be copied
   */
  public void replace(final int pre, final Data d) {
    delete(pre, d.meta.size);
    insert(pre, d);
  }

  /**
   * Discards the document paths.
   */
  public synchronized void update() {
    paths = null;
    order = null;
  }

  /**
   * Returns the pre values of all document nodes matching the specified path.
   * @param path input path
   * @return root nodes
   */
  public synchronized IntList docs(final String path) {
    // no documents: return empty list
    if(data.empty()) return new IntList(0);

    // empty path: return all documents
    final IntList doc = docs();
    if(path.isEmpty()) return doc;

    // initialize and sort document paths
    final int ds = doc.size();
    if(paths == null) {
      paths = new byte[ds][];
      for(int d = 0; d < ds; d++) {
        final byte[] txt = data.text(doc.get(d), true);
        paths[d] = concat(SLASH, Prop.WIN ? lc(txt) : txt);
      }
      order = Array.createOrder(paths, false, true);
    }

    // normalize paths
    final String np = path.replaceAll("[\\\\//]+", "/").replaceAll("^/|/$", "");
    final byte[] exact = concat(SLASH, Prop.WIN ? lc(token(np)) : token(np));
    final byte[] start = endsWith(exact, SLASH) ? exact : concat(exact, SLASH);

    // relevant paths: start from the first hit and return all subsequent hits
    final IntList il = new IntList();
    for(int p = find(exact); p < paths.length; p++) {
      if(eq(paths[p], exact) || startsWith(paths[p], start))
        il.add(doc.get(order[p]));
    }
    return il.sort();
  }


  /**
   * Returns the references to all binary files matching the specified path.
   * @param path input path
   * @return root nodes
   */
  public synchronized ObjList<File> files(final String path) {
    final File bin = data.meta.binaries();
    final ObjList<File> files = new ObjList<File>();
    final File file = new File(bin, path);
    if(file.exists()) {
      if(file.isDirectory()) {
        add(file, files);
      } else {
        files.add(file);
      }
    }
    return files;
  }

  /**
   * Adds binary files to the specified list.
   * @param dir current directory
   * @param files file list
   */
  private void add(final File dir, final ObjList<File> files) {
    for(final File f : dir.listFiles()) {
      if(f.isDirectory()) {
        add(f, files);
      } else {
        files.add(f);
      }
    }
  }

  /**
   * Returns the first position matching the specified path.
   * @param v value to be found
   * @return position or negative insertion value - 1
   */
  private int find(final byte[] v) {
    // binary search
    int l = 0, h = order.length - 1;
    while(l <= h) {
      int m = l + h >>> 1;
      final int c = diff(paths[m], v);
      if(c == 0) {
        // find first entry
        while(m > 0 && eq(paths[m - 1], v)) --m;
        return m;
      }
      if(c < 0) l = m + 1;
      else h = m - 1;
    }
    return l;
  }

  @Override
  public void close() { }

  // Unsupported methods ======================================================

  @Override
  public IndexIterator ids(final IndexToken token) {
    throw Util.notexpected();
  }

  @Override
  public int nrIDs(final IndexToken token) {
    throw Util.notexpected();
  }

  @Override
  public byte[] info() {
    throw Util.notexpected();
  }
}
