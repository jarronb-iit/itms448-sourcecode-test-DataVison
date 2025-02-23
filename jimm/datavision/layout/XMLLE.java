package jimm.datavision.layout;
import jimm.datavision.*;
import jimm.datavision.field.*;
import jimm.util.XMLWriter;

/**
 * An XML layout engine.
 *
 * @author Jim Menard, <a href="mailto:jimm@io.com">jimm@io.com</a>
 */
public class XMLLE extends SortedLayoutEngine {

protected XMLWriter iout;
protected String encoding;

/**
 * Constructor.
 *
 * @param out an indent writer
 */
public XMLLE(XMLWriter out) {
    this(out, Report.XML_ENCODING_ATTRIBUTE);
}

/**
 * Constructor. Optionally specify the encoding string to write to the
 * XML file. This string is written as the XMLDecl encoding attribute.
 * Use legal XML values like "UTF-8", not Java values like "UTF8".
 *
 * @param out an indent writer
 * @param enc an XML encoding string; if <code>null</code>, uses
 * {@link Report}<code>.XML_ENCODING_ATTRIBUTE</code>
 */
public XMLLE(XMLWriter out, String enc) {
    super(out);
    iout = out;
    encoding = (enc == null ? Report.XML_ENCODING_ATTRIBUTE : enc);
}

protected void doStart() {
    iout.xmlDecl(encoding);
    iout.comment("Generated by DataVision version " + info.Version);
    iout.comment(info.URL);
    iout.startElement("report");
}

protected void doEnd() {
    iout.endElement();
    iout.flush();
}

protected void doStartPage() {
    iout.comment("============== Page " + pageNumber()
		 + " ==============");
    iout.startElement("newpage");
    iout.attr("number", pageNumber());
    iout.endElement();
}

protected void doOutputSection(Section s) {
    iout.startElement("section");
    iout.attr("type", currentSectionTypeAsString());
    super.doOutputSection(s);
    iout.endElement();
}

protected void doOutputField(Field field) {
    iout.startElement("field");
    iout.attr("id", field.getId());
    iout.attr("type", field.typeString());
    if (field instanceof SpecialField)
	iout.attr("value", field.getValue());
    else if (field instanceof ColumnField)
	iout.attr(" column", ((ColumnField)field).getColumn().fullName());
    else if (field instanceof AggregateField) {
	AggregateField sf = (AggregateField)field;
	if (sf.getGroup() != null)
	    iout.attr("group", sf.getGroup().getSelectable().getDisplayName());
    }

    iout.cdata(field.toString());
    iout.endElement();
}

protected void doOutputImage(ImageField image) {
    doOutputField(image);
}

protected void doOutputLine(Line l) {
    l.writeXML(iout);
}

}

