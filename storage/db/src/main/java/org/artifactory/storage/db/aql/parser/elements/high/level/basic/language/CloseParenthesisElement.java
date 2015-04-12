package org.artifactory.storage.db.aql.parser.elements.high.level.basic.language;

import org.artifactory.storage.db.aql.parser.elements.ParserElement;
import org.artifactory.storage.db.aql.parser.elements.low.level.InternalSignElement;
import org.artifactory.storage.db.aql.parser.elements.low.level.LazyParserElement;

/**
 * @author Gidi Shabat
 */
public class CloseParenthesisElement extends LazyParserElement {
    @Override
    protected ParserElement init() {
        return new InternalSignElement("]");
    }

    @Override
    public boolean isVisibleInResult() {
        return true;
    }
}
