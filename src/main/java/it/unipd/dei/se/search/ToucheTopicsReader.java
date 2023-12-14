package it.unipd.dei.se.search;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import org.apache.lucene.benchmark.quality.QualityQuery;

public class ToucheTopicsReader {

  /**
   * Constructor for Touché's TopicsReader
   */
  public ToucheTopicsReader() {
    super();
  }

  /**
   * Read quality queries from touché format topics file.
   * 
   * @param reader where queries are read from.
   * @return the result quality queries.
   * @throws IOException if cannot read the queries.
   */
  public QualityQuery[] readQueries(BufferedReader reader) throws IOException {

    ArrayList<QualityQuery> res = new ArrayList<>();

    XMLInputFactory f = XMLInputFactory.newFactory();
    XMLStreamReader sr;
    try {
      sr = f.createXMLStreamReader(reader);
      int n = -1;
      Topic t;

      XmlMapper mapper = new XmlMapper();
      n = sr.next(); // to point to <topics>

      while (sr.hasNext()) {
        HashMap<String, String> fields = new HashMap<>();

        n = sr.nextTag(); // to point to <topic> under root
        if (n == XMLStreamConstants.END_ELEMENT)
          break;
        t = mapper.readValue(sr, Topic.class);
        //System.out.println(t);
        // sr now points to matching END_ELEMENT, so move forward
        fields.put("title", ""+t.getTitle());
        fields.put("description", ""+t.getDescription());
        fields.put("narrative", ""+t.getNarrative());
        QualityQuery topic = new QualityQuery(t.getNumber() + "", fields);
        res.add(topic);
      }
      // and more, as needed, then
      sr.close();

    } catch (XMLStreamException e) {
      e.printStackTrace();
    } finally {
      reader.close();
    }
    // sort result array (by ID)
    QualityQuery qq[] = res.toArray(new QualityQuery[0]);
    Arrays.sort(qq);
    return qq;
  }
}
