package it.unipd.dei.se.index;

import java.io.Reader;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import it.unipd.dei.se.parse.ParsedDocument;


public class TitleField extends Field {

    /**
     * The type of the document body field
     */
    private static final FieldType TITLE_TYPE = new FieldType();

    static {
        TITLE_TYPE.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
        TITLE_TYPE.setTokenized(true);
        TITLE_TYPE.setStored(true);
    }


    /**
     * Create a new field for the body of a document.
     *
     * @param value the contents of the body of a document.
     */
    public TitleField(final Reader value) {
        super(ParsedDocument.FIELDS.TITLE, value, TITLE_TYPE);
    }

    /**
     * Create a new field for the body of a document.
     *
     * @param value the contents of the body of a document.
     */
    public TitleField(final String value) {
        super(ParsedDocument.FIELDS.TITLE, value, TITLE_TYPE);
    }

}
