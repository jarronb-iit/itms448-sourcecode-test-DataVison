package jimm.datavision.layout;
import jimm.datavision.*;
import jimm.datavision.field.*;
import jimm.util.StringUtils;
import java.io.*;
import java.util.*;

/**
 * A DocBook col is used to represeent a column in a DocBook table.
 *
 * @author Jim Menard, <a href="mailto:jimm@io.com">jimm@io.com</a>
 */
class DocBookCol {

Field field;
double x;
double width;

/**
 * Constructor.
 */
DocBookCol(Field field, double x, double width) {
    this.field = field;
    this.x = x;
    this.width = width;
}

/**
 * Writes this field's data into a DocBook table cell.
 */
void output(PrintWriter out) {
    if (field == null || !field.isVisible()) {
	out.println("<entry></entry>");
	return;
    }

    String str = field.toString();

    Format format = field.getFormat();
    out.print("<entry align=\"" + Format.alignToString(format.getAlign())
	      + "\">");

//      if (format.size != 0) out.print("<font size=\" + format.size + \">");
    if (format.isBold()) out.print("<emphasis role=\"bold\">");
    if (format.isItalic()) out.print("<replaceable>");
//      if (format.underline) out.print("\\underline{");

    out.print(StringUtils.escapeXML(str));

//      if (format.underline) out.print("}");
    if (format.isItalic()) out.print("</replaceable>");
    if (format.isBold()) out.print("</emphasis>");
//      if (format.size != 0) out.print("}");

    out.println("</entry>");
}

}

/**
 * A DocBook layout engine creates DocBook documents. Field layout is
 * achieved by creating tables.
 */
public class DocBookLE extends SortedLayoutEngine {

protected HashMap sectionCols;

/**
 * Constructor.
 *
 * @param out the output writer
 */
public DocBookLE(PrintWriter out) {
    super(out);
}

/**
 * This override outputs information at the top of the DocBook document.
 */
protected void doStart() {
    sectionCols = new HashMap();
    out.println("<!DOCTYPE informaltable PUBLIC \"-//OASIS//DTD DocBook V3.1//EN\">");
    out.println("<!-- Generated by DataVision version " + info.Version
		+ " -->");
    out.println("<!-- " + info.URL + " -->");
    out.println("<informaltable colsep=\"1\" rowsep=\"1\">");
}

/**
 * This override outputs the end of the document.
 */
protected void doEnd() {
    out.println("</informaltable>");
}

/**
 * This override starts a new page.
 */
protected void doStartPage() {
    // Apparently beginpage isn't allowed just anywhere
    if (pageNumber > 1)
	out.println("<beginpage pagenum=\"" + pageNumber + "\">");
    out.println("<!-- ======== Page " + pageNumber + " ======== -->");
}

/**
 * This override outputs a report section.
 *
 * @param sect the report section
 */
protected void doOutputSection(Section sect) {
    Collection cols = calcSectionCols(sect);
    if (cols.isEmpty())
	return;

    out.println("<tgroup cols=" + cols.size() + ">");

    // Write col specs
    int i = 1;
    for (Iterator iter = cols.iterator(); iter.hasNext(); ++i) {
	DocBookCol col = (DocBookCol)iter.next();
	out.println("<colspec colname=c" + i + " colwidth=\""
		    + col.width + "\">");
    }

    // Output the fields in the section
    out.println("<tbody>");
    out.println("<row>");
    for (Iterator iter = cols.iterator(); iter.hasNext(); ++i) {
	DocBookCol col = (DocBookCol)iter.next();
	col.output(out);
    }
    out.println("</row>");
    out.println("</tbody>");

    out.println("</tgroup>");
}

/**
 * Does nothing, since we output fields in {@link #doOutputSection}.
 */
protected void doOutputField(Field field) {}

/**
 * Does nothing, since we output images in {@link #doOutputSection}.
 */
protected void doOutputImage(ImageField image) {}

/**
 * Does nothing, since we output lines in {@link #doOutputSection}.
 */
protected void doOutputLine(Line line) {}

/**
 * Returns a collection of <code>DocBookCol</code> objects. Each one
 * represents a field that will be output.
 *
 * @param sect a section
 */
protected Collection calcSectionCols(Section sect) {
    Collection cols = null;
    if ((cols = (Collection)sectionCols.get(sect)) != null)
	return cols;

    cols = new ArrayList();
    double x = 0;
// FIX: sort these by their x position.
    for (Iterator iter = sect.fields(); iter.hasNext(); ) {
	Field f = (Field)iter.next();
	Rectangle bounds = f.getBounds();
	if (bounds.x > x) {
	    cols.add(new DocBookCol(null, x, bounds.x - x));
	    x = bounds.x;
	}
	cols.add(new DocBookCol(f, bounds.x, bounds.width));
	x += bounds.width;
    }

    sectionCols.put(sect, cols);
    return cols;
}

}
