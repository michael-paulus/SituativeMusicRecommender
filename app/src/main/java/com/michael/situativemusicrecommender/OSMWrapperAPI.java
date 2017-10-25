package com.michael.situativemusicrecommender;

import android.util.Log;
import android.util.Xml;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class OSMWrapperAPI {

    private static final String OVERPASS_API = "http://www.overpass-api.de/api/interpreter";
    private static final String OPENSTREETMAP_API_06 = "http://www.openstreetmap.org/api/0.6/";
    private static final String ns = null;

    public static OSMNode getNode(String nodeId) throws IOException, ParserConfigurationException, SAXException {
        String string = "http://www.openstreetmap.org/api/0.6/node/" + nodeId;
        URL osm = new URL(string);
        HttpURLConnection connection = (HttpURLConnection) osm.openConnection();

        DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
        Document document = docBuilder.parse(connection.getInputStream());
        List<OSMNode> nodes = getNodes(document);
        if (!nodes.isEmpty()) {
            return nodes.iterator().next();
        }
        return null;
    }

    /**
     *
     * @param lon the longitude
     * @param lat the latitude
     * @param vicinityRange bounding box in this range
     * @return the xml document containing the queries nodes
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     */
    @SuppressWarnings("nls")
    private static Document getXML(double lon, double lat, double vicinityRange) throws IOException, SAXException,
            ParserConfigurationException {

        DecimalFormat format = new DecimalFormat("##0.0000000", DecimalFormatSymbols.getInstance(Locale.ENGLISH)); //$NON-NLS-1$
        String left = format.format(lat - vicinityRange);
        String bottom = format.format(lon - vicinityRange);
        String right = format.format(lat + vicinityRange);
        String top = format.format(lon + vicinityRange);

        String string = OPENSTREETMAP_API_06 + "map?bbox=" + left + "," + bottom + "," + right + ","
                + top;
        URL osm = new URL(string);
        HttpURLConnection connection = (HttpURLConnection) osm.openConnection();

        DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
        return docBuilder.parse(connection.getInputStream());
    }

    public static Document getXMLFile(String location) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
        return docBuilder.parse(location);
    }
    public static void printDocument(Document doc, OutputStream out) throws IOException, TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        transformer.transform(new DOMSource(doc),
                new StreamResult(new OutputStreamWriter(out, "UTF-8")));
    }


    /**
     *
     * @param xmlDocument
     * @return a list of openseamap nodes extracted from xml
     */
    @SuppressWarnings("nls")
    public static List<OSMNode> getNodes(Document xmlDocument) {
        List<OSMNode> osmNodes = new ArrayList<OSMNode>();

        // Document xml = getXML(8.32, 49.001);
        Node osmRoot = xmlDocument.getFirstChild();
        NodeList osmXMLNodes = osmRoot.getChildNodes();
        for (int i = 1; i < osmXMLNodes.getLength(); i++) {
            Node item = osmXMLNodes.item(i);
            if (item.getNodeName().equals("node")) {
                NamedNodeMap attributes = item.getAttributes();
                NodeList tagXMLNodes = item.getChildNodes();
                Map<String, String> tags = new HashMap<String, String>();
                for (int j = 1; j < tagXMLNodes.getLength(); j++) {
                    Node tagItem = tagXMLNodes.item(j);
                    NamedNodeMap tagAttributes = tagItem.getAttributes();
                    if (tagAttributes != null) {
                        tags.put(tagAttributes.getNamedItem("k").getNodeValue(), tagAttributes.getNamedItem("v")
                                .getNodeValue());
                    }
                }
                Node namedItemID = attributes.getNamedItem("id");
                Node namedItemLat = attributes.getNamedItem("lat");
                Node namedItemLon = attributes.getNamedItem("lon");
                Node namedItemVersion = attributes.getNamedItem("version");

                String id = namedItemID.getNodeValue();
                String latitude = namedItemLat.getNodeValue();
                String longitude = namedItemLon.getNodeValue();
                String version = "0";
                if (namedItemVersion != null) {
                    version = namedItemVersion.getNodeValue();
                }

                osmNodes.add(new OSMNode(id, latitude, longitude, version, tags));
            }

        }
        return osmNodes;
    }

    public static List<OSMNode> getOSMNodesInVicinity(double lat, double lon, double vicinityRange) throws IOException,
            SAXException, ParserConfigurationException {
        Document doc = getXML(lon, lat, vicinityRange);
        try {
            printDocument(doc, System.out);
        } catch (TransformerException e) {
            e.printStackTrace();
        }
        return OSMWrapperAPI.getNodes(doc);
    }

    /**
     *
     * @param query the overpass query
     * @return the nodes in the formulated query
     * @throws IOException
     * @throws ParserConfigurationException
     * @throws SAXException
     */
    public static Document getNodesViaOverpass(String query) throws IOException, ParserConfigurationException, SAXException {
        String hostname = OVERPASS_API;
        //String queryString = readFileAsString(query);

        URL osm = new URL(hostname);
        HttpURLConnection connection = (HttpURLConnection) osm.openConnection();
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        DataOutputStream printout = new DataOutputStream(connection.getOutputStream());
        printout.writeBytes("data=" + URLEncoder.encode(query, "utf-8"));
        printout.flush();
        printout.close();

        DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
        return docBuilder.parse(connection.getInputStream());
    }

    /**
     *
     * @param filePath
     * @return
     * @throws java.io.IOException
     */
    private static String readFileAsString(String filePath) throws java.io.IOException {
        StringBuffer fileData = new StringBuffer(1000);
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        char[] buf = new char[1024];
        int numRead = 0;
        while ((numRead = reader.read(buf)) != -1) {
            String readData = String.valueOf(buf, 0, numRead);
            fileData.append(readData);
            buf = new char[1024];
        }
        reader.close();
        return fileData.toString();
    }

    /**
     * main method that simply reads some nodes
     *
     * @throws IOException
     * @throws SAXException
     * @throws ParserConfigurationException
     */
    public static Map<String, String> getLocationType () throws IOException, SAXException, ParserConfigurationException {
        double latitude = MainActivity.getContext().getLatitude();
        double longitude = MainActivity.getContext().getLongitude();
        Log.d("Latitude", String.valueOf(latitude));
        Log.d("Longitude", String.valueOf(longitude));
        //List<OSMNode> osmNodesInVicinity = getOSMNodesInVicinity(latitude, longitude, 0.005);
        DecimalFormat format = new DecimalFormat("##0.0000000", DecimalFormatSymbols.getInstance(Locale.ENGLISH)); //$NON-NLS-1$
        double vicinityRange = 0.0005;
        String left = format.format(latitude - vicinityRange);
        String bottom = format.format(longitude - vicinityRange);
        String right = format.format(latitude + vicinityRange);
        String top = format.format(longitude + vicinityRange);
        String query = "(node("+left+","+bottom+","+right+","+top+");<;);out body;";
        List<OSMNode> osmNodesInVicinity = getNodes(getNodesViaOverpass(query));
        List<Way> ways = getWays(getNodesViaOverpass(query));
        for (OSMNode osmNode : osmNodesInVicinity) {
            System.out.println(osmNode.getId() + ":" + osmNode.getLat() + ":" + osmNode.getLon());
        }
        try {return ways.get(0).tags;} catch (Exception e){return null;}
    }

    private static List<Way> getWays(Document xmlDocument) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Source xmlSource = new DOMSource(xmlDocument);
        Result outputTarget = new StreamResult(outputStream);
        try {
            TransformerFactory.newInstance().newTransformer().transform(xmlSource, outputTarget);
        } catch (TransformerException e) {
            e.printStackTrace();
        }
        InputStream is = new ByteArrayInputStream(outputStream.toByteArray());
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                parser.setInput(is, null);
                parser.nextTag();
                return readFeed(parser);
            } catch (XmlPullParserException | IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
    }

    private static List<Way> readFeed(XmlPullParser parser) throws XmlPullParserException, IOException {
        List<Way> entries = new ArrayList<>();

        parser.require(XmlPullParser.START_TAG, ns, "osm");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the way tag
            if (name.equals("way")) {
                entries.add(readEntry(parser));
            } else {
                skip(parser);
            }
        }
        return entries;
    }

    private static void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    public static class Way {
        public final Map<String, String> tags;

        private Way(Map<String, String> tags) {
            this.tags = tags;
        }
    }

    private static Way readEntry(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, ns, "way");
        String key = null;
        String value = null;
        Map<String, String> tags = new HashMap<>();
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("tag")) {
                key = readTagKey(parser);
                value = readTagValue(parser);
                tags.put(key, value);
            } else {
                skip(parser);
            }
        }
        return new Way(tags);
    }

    private static String readTagKey(XmlPullParser parser) {
        String key =  parser.getAttributeValue(null, "k");
        Log.d("Found key", key);
        return key;
    }

    private static String readTagValue(XmlPullParser parser) {
        String value =  parser.getAttributeValue(null, "v");
        Log.d("Found value", value);
        return value;
    }

}

