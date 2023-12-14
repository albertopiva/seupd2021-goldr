package it.unipd.dei.se.parse;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Parser for the args.me corpus.
 */
public class ArgsParser extends it.unipd.dei.se.parse.DocumentParser {

    /**
     * The size of the buffer for the body element.
     */
    private static final int BODY_SIZE = 1024 * 8;

    /**
     * The currently parsed document
     */
    private it.unipd.dei.se.parse.ParsedDocument document = null;

    JsonFactory f = new MappingJsonFactory();
    JsonParser jp;
    JsonToken current;

    /**
     * Creates a new args.me corpus document parser.
     *
     * @param in the reader to the document(s) to be parsed.
     * @throws NullPointerException     if {@code in} is {@code null}.
     * @throws IllegalArgumentException if any error occurs while creating the
     *                                  parser.
     */
    public ArgsParser(final Reader in) {
        super(new BufferedReader(in));

        try {
            f = new MappingJsonFactory();
            jp = f.createParser(in);
            current = jp.nextToken();
            if (current != JsonToken.START_OBJECT) {
                System.out.println("Error: root should be object: quitting.");
                return;
            }
            if (jp.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = jp.getCurrentName();
                System.out.println("name : " + fieldName);
                // move from field name to field value
                current = jp.nextToken();

                if (!fieldName.equals("arguments")) {
                    System.out.println("Unprocessed property: " + fieldName);
                    jp.skipChildren();
                } else {
                    if (current == JsonToken.START_ARRAY) {
                        System.out.println("ArgsParser: ready to parse arguments.");
                    } else {
                        System.out.println("Error: records should be an array: skipping.");
                        jp.skipChildren();
                    }
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean hasNext() {

        String id = null;
        String conclusion = null;
        String premiseText = null;
        String stance = null;
        // String discussionTitle = null;
        String topic = null;
        //String sourceText = null;
        //int sConclusionStart = -1;
        //int sConclusionEnd = -1;
        //int sPremiseStart = -1;
        //int sPremiseEnd = -1;

        final StringBuilder body = new StringBuilder(BODY_SIZE);

        try {

            if (jp.nextToken() != JsonToken.END_ARRAY) {
                // read the record into a tree model,
                // this moves the parsing position to the end of it
                JsonNode doc = jp.readValueAsTree();
                // And now we have random access to everything in the object
                id = doc.get("id").asText();
                conclusion = doc.get("conclusion").asText();

                JsonNode premArray = doc.at("/premises");

                if(premArray.isArray()){
                    ArrayNode arrayNode = (ArrayNode) premArray;
                    if (arrayNode.size() > 1) System.out.println("------------------------> FOUND MORE PREMISES : " + arrayNode.size());
                }

                JsonNode premises = doc.at("/premises/0");
                premiseText = premises.get("text").asText();
                stance = premises.get("stance").asText();

                JsonNode context = doc.at("/context");
                if (context.get("discussionTitle") != null)
                    topic = context.get("discussionTitle").asText();
                else
                    topic = context.get("topic").asText();

                //body.append(topic);
                //body.append(" ");
                body.append(conclusion);
                body.append(" ");
                body.append(premiseText);

            } else {
                next = false;
            }

        } catch (IOException e) {
            throw new IllegalStateException("Unable to parse the document.", e);
        }

        if (id != null) {
            //document = new it.unipd.dei.se.parse.ParsedDocument(id, body.length() > 0 ? body.toString().replaceAll("<[^>]*>", " ") : "#", stance);
            document = new it.unipd.dei.se.parse.ParsedDocument(id, topic, body.length() > 0 ? body.toString() : "#", stance);
        }

        return next;
    }

    @Override
    protected final it.unipd.dei.se.parse.ParsedDocument parse() {
        return document;
    }

    /**
     * Main method of the class. Just for testing purposes.
     *
     * @param args command line arguments.
     * @throws Exception if something goes wrong while indexing.
     */
    public static void main(String[] args) throws Exception {

        Reader reader = new FileReader("/home/filippo/se/collections/touche/inputDataset/idebate.json");

        ArgsParser1 p = new ArgsParser1(reader);

        int docs = 0;

        for (it.unipd.dei.se.parse.ParsedDocument d : p) {
            docs++;
            System.out.printf("%n%n------------------------------------%n%s%n%n%n", d.toString());
        }

        System.out.printf("Parsed %d arguments.\n", docs);

    }

}
