package org.basex.query.func.fn;

import org.basex.query.*;
import org.basex.query.func.*;
import org.basex.query.value.item.*;
import org.basex.query.value.seq.*;
import org.basex.query.value.type.*;
import org.basex.util.*;

/**
 * Function implementation.
 *
 * @author BaseX Team 2005-19, BSD License
 * @author Christian Gruen
 */
public final class FnPrefixFromQName extends StandardFunc {
  @Override
  public Item item(final QueryContext qc, final InputInfo ii) throws QueryException {
    final QNm qname = toQNm(exprs[0], qc, true);
    return qname == null || !qname.hasPrefix() ? Empty.VALUE :
      AtomType.NCN.cast(Str.get(qname.prefix()), qc, sc, info);
  }
}
