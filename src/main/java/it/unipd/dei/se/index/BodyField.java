package it.unipd.dei.se.index;

import java.io.Reader;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;

import it.unipd.dei.se.parse.ParsedDocument;

/**
 * Represents a {@link Field} for containing the body of a document.
 * <p>
 * It is a tokenized field, not stored, keeping only document ids and term frequencies (see {@link
 * IndexOptions#DOCS_AND_FREQS} in order to minimize the space occupation.
 */
public class BodyField extends Field {

    /**
     * The type of the document body field
     */
    private static final FieldType BODY_TYPE = new FieldType();

    static {
        BODY_TYPE.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
        BODY_TYPE.setTokenized(true);
        BODY_TYPE.setStored(false);
    }


    /**
     * Create a new field for the body of a document.
     *
     * @param value the contents of the body of a document.
     */
    public BodyField(final Reader value) {
        super(ParsedDocument.FIELDS.BODY, value, BODY_TYPE);
    }

    /**
     * Create a new field for the body of a document.
     *
     * @param value the contents of the body of a document.
     */
    public BodyField(final String value) {
        super(ParsedDocument.FIELDS.BODY, value, BODY_TYPE);
    }

}
