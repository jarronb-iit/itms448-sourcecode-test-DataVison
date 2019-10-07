package jimm.datavision.source.sql;
import jimm.datavision.*;
import jimm.datavision.source.*;
import jimm.util.StringUtils;
import java.util.*;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Queries build SQL query strings. They contain tables, joins, and
 * where clauses.
 *
 * @author Jim Menard, <a href="mailto:jimm@io.com">jimm@io.com</a>
 * @see ParserHelper
 */
public class SQLQuery extends Query {

protected Set tables;
protected ArrayList preparedStmtValues;

/**
 * Constructor.
 *
 * @param report the report for which this query will generate SQL
 */
public SQLQuery(Report report) {
    super(report);
    tables = new HashSet();
}

/**
 * Returns the where clause string; may be <code>null</code>. If there
 * are any parameter values, we return '?' in their place and save the
 * values for later use.
 * <p>
 * This code may also modify the clause. For example, a parameter can
 * change the previous comparison operator ("=", "is") based on its arity.
 *
 * @return the where clause string; may be <code>null</code>
 * @see #getWhereClause
 */
protected String getWhereClauseForPreparedStatement() {
   if (whereClause == null)
	return null;
    return prepare(whereClause);
}

/**
 * Given a clause (really any string), replace all formulas and parameters
 * with their values. Anything else in curly braces must be a column; we
 * remove the curly braces and quote the name.
 * <p>
 * Implementation note: we can't use <code>StringUtils.replaceDelimited</code>
 * because we modify the symbol that appears <i>before</i> some of the
 * delimited items.
 */
public String prepare(String clause) {
    if (clause == null || clause.indexOf("{") == -1)
	return clause;

    StringBuffer buf = new StringBuffer();

    int pos, endPos;
    for (pos = 0, endPos = -1;
	 (pos = clause.indexOf("{", endPos + 1)) >= 0;
	 pos = endPos + 1)
    {
	int oldEndPos = endPos;
	endPos = clause.indexOf("}", pos);
	if (endPos == -1) {
	    buf.append(clause.substring(pos));
	    break;
	}

	switch (clause.charAt(pos + 1)) {
	case '@':		// Formula
	    String idAsString = clause.substring(pos + 2, endPos);
	    preparedStmtValues.add(report.findFormula(idAsString).eval());

	    buf.append(clause.substring(oldEndPos + 1, pos));
	    buf.append(" ? ");
	    break;
	case '?':		// Parameter
	    idAsString = clause.substring(pos + 2, endPos);

	    // Find previous word
	    ParserHelper ph = new ParserHelper(clause, pos);

	    // Append prev text without previous token
	    buf.append(clause.substring(oldEndPos + 1,
					ph.getEndBeforeToken()));

	    // Append possibly modified previous token and parameter
	    addParameter(buf, ph.getPrevToken(), idAsString);
	    break;
	default:	    // Column field; remove curlies and quote value
	    buf.append(clause.substring(oldEndPos + 1, pos));
	    buf.append(' ');
	    buf.append(quoted(clause.substring(pos + 1, endPos)));
	    buf.append(' ');
	    break;
	}
	pos = endPos + 1;
    }

    if ((endPos + 1) < clause.length())
	buf.append(clause.substring(endPos + 1));

    return buf.toString();
}

/**
 * Given a parameter id string, add its value(s) to the parameter list
 * and add prepared statement placeholders in the query buffer. Appends
 * the previous word to the buffer. The previous word may be modified
 * if the circumstances call for it. For example, we want to turn
 * "foo in {?Range Parameter}" into "foo between ? and ?". The value
 * of <var>prevWord</var> here would be "in". We would append "between"
 * to the buffer and return the "word" "? and ".
 *
 * @param buf a string buffer containing the SQL query so far
 * @param prevWord the previous word
 * @param idAsString the parameter id
 */
protected void addParameter(StringBuffer buf, String prevWord,
			    String idAsString)
{
    String word = null;
    Long paramId = new Long(idAsString);
    Parameter param = report.findParameter(paramId);

    // Ask report for parameter value so the report can ask the user to
    // fill in the parameter's value.
    Object val = report.getParameterValue(paramId);
    if (val instanceof List) {
	List list = (List)val;
	if (param.getArity() == Parameter.ARITY_RANGE) {
	    // Modify prev word
	    if ("!=".equals(prevWord) || "<>".equals(prevWord))
		buf.append(" not between ");
	    else if ("=".equals(prevWord)
		     || "in".equals(prevWord.toLowerCase()))
		buf.append(" between ");
	    else {
		buf.append(' ');
		buf.append(prevWord);
		buf.append(' ');
	    }

	    word = "? and ?";
	    preparedStmtValues.add(list.get(0));
	    preparedStmtValues.add(list.get(1));
	}
	else {			// Build "(a,b,c)" list
	    switch (list.size()) {
	    case 0:		// No items in list; "is null"
		buf.append(" is null");
		break;
	    case 1:		// One item in list; simple equality
		if ("in".equals(prevWord) || "<>".equals(prevWord))
		    buf.append(" = ");
		else {
		    buf.append(' ');
		    buf.append(prevWord);
		    buf.append(' ');
		}
		word = "?";
		preparedStmtValues.add(list.get(0));
		break;
	    default:
		if ("!=".equals(prevWord) || "<>".equals(prevWord))
		    buf.append(" not in ");
		else if ("=".equals(prevWord)
			 || "in".equals(prevWord.toLowerCase()))
		    buf.append(" in ");
		else {
		    buf.append(' ');
		    buf.append(prevWord);
		    buf.append(' ');
		}

		StringBuffer wordBuf = new StringBuffer("(");
		boolean first = true;
		int len = list.size();
		for (int i = 0; i < len; ++i) {
		    if (first) first = false;
		    else wordBuf.append(',');
		    wordBuf.append('?');
		}
		wordBuf.append(")");
		word = wordBuf.toString();
		preparedStmtValues.addAll(list);
	    }
	}
    }
    else {
	buf.append(' ');
	buf.append(prevWord);	// Previous word
	preparedStmtValues.add(val);
	word =" ?";		// For prepared statement
    }

    buf.append(word);
}

/**
 * Given a parameter id string, add it and a possible modified previous
 * word to <var>buf</var>. Does not modify <var>preparedStmtValues</var>
 * list.
 *
 * @param buf a string buffer containing the SQL query so far
 * @param prevWord the previous word
 * @param idAsString the parameter id
 * @see #addParameter
 */
protected void addParameterForDisplay(StringBuffer buf, String prevWord,
				      String idAsString)
{
    String word = null;
    Long paramId = new Long(idAsString);
    Parameter param = report.findParameter(paramId);
    String name = param.designLabel();

    // Ask report for parameter value so the report can ask the user to
    // fill in the parameter's value.
    switch (param.getArity()) {
    case Parameter.ARITY_RANGE:
	// Modify prev word
	if ("!=".equals(prevWord) || "<>".equals(prevWord))
	    buf.append(" not between ");
	else if ("=".equals(prevWord)
		 || "in".equals(prevWord.toLowerCase()))
	    buf.append(" between ");
	else {
	    buf.append(' ');
	    buf.append(prevWord);
	    buf.append(' ');
	}

	word = name + " and " + name;
	break;
    case Parameter.ARITY_LIST_MULTIPLE:
	if ("!=".equals(prevWord) || "<>".equals(prevWord))
	    buf.append(" not in ");
	else if ("=".equals(prevWord)
		 || "in".equals(prevWord.toLowerCase()))
	    buf.append(" in ");
	else {
	    buf.append(' ');
	    buf.append(prevWord);
	    buf.append(' ');
	}

	word = "(" + name + ")";
	break;
    default:
	buf.append(' ');
	buf.append(prevWord);	// Previous word
	word = " " + name;
	break;
    }

    buf.append(word);
}

/**
 * Builds collections of the report tables and selectable fields actually used
 * in the report.
 */
public void findSelectablesUsed() {
    super.findSelectablesUsed();
    tables.clear();
    for (Iterator iter = selectables.iterator(); iter.hasNext(); )
	addTable(((Selectable)iter.next()).getTable());

    // Add all tables used in joins.
    for (Iterator iter = joins.iterator(); iter.hasNext(); ) {
	Join join = (Join)iter.next();
	addTable(((Column)join.getFrom()).getTable());
	addTable(((Column)join.getTo()).getTable());
    }

    // Add all selectables' tables used by subreports' joins.
    for (Iterator iter = report.subreports(); iter.hasNext(); ) {
	Subreport sub = (Subreport)iter.next();
	for (Iterator subIter = sub.parentColumns(); subIter.hasNext(); ) {
	    // maybe parentColumns should use same the Table-Object as the
	    // parent report...
	    addTable(((Column)subIter.next()).getTable());
	}
    }
}

/**
 * Adds the table <var>t</var> to <var>tables</var>, but only if <var>t</var>
 * is not <code>null</code> and is not already in <var>tables</var>. We
 * compare tables by name instead of value (pointer) because different table
 * object may refer to the same table, for example if one is from the report
 * and the other is from a subreport.
 *
 * @param t a Table
 */
protected void addTable(Table t) {
    if (t == null)
	return;

    // Look for the same table name
    String tableName = t.getName();
    for (Iterator iter = tables.iterator(); iter.hasNext(); )
        if (((Table)iter.next()).getName().equals(tableName))
            return;		// Don't add if we have the same table name

    tables.add(t);
}
/**
 * Returns the number of tables in the query. Does not recalculate the
 * columns or tables used; we assume this is being called after the query
 * has been run, or at least after <code>findSelectablesUsed</code> has
 * been called.
 * <p>
 * This method is only used for testing, so far.
 */
public int getNumTables() { return tables.size(); }

/**
 * Returns a collection containing the tables used by this query.
 *
 * @return the collection of tables used by this query
 */
public Collection getTablesUsed() {
    findSelectablesUsed();
    return tables;
}

/**
 * Returns the where clause string; may be <code>null</code>. If there are
 * any column names contained in curly braces, we remove the curly braces.
 * Formulas, parameters, and user colums remain as-is.
 * <p>
 * Implementation note: we can't use <code>StringUtils.replaceDelimited</code>
 * because we modify the symbol that appears <i>before</i> some of the
 * delimited items.
 *
 * @return the where clause string; may be <code>null</code>
 * @see #getWhereClause
 */
protected String getWhereClauseForDisplay() {
   if (whereClause == null)
	return null;
    if (whereClause.indexOf("{") == -1)
	return whereClause;

    StringBuffer buf = new StringBuffer();

    int pos, endPos;
    for (pos = 0, endPos = -1;
	 (pos = whereClause.indexOf("{", endPos + 1)) >= 0;
	 pos = endPos + 1)
    {
	int oldEndPos = endPos;
	endPos = whereClause.indexOf("}", pos);
	if (endPos == -1) {
	    buf.append(whereClause.substring(pos));
	    break;
	}

	switch (whereClause.charAt(pos + 1)) {
	case '@':		// Formula
	    String idAsString = whereClause.substring(pos + 2, endPos);

	    buf.append(whereClause.substring(oldEndPos + 1, pos));
	    buf.append(" {@");
	    buf.append(report.findFormula(idAsString).getName());
	    buf.append("} ");
	    break;
	case '?':		// Parameter
	    idAsString = whereClause.substring(pos + 2, endPos);

	    // Find previous word
	    ParserHelper ph = new ParserHelper(whereClause, pos);

	    // Append prev text without previous token
	    buf.append(whereClause.substring(oldEndPos + 1,
					     ph.getEndBeforeToken()));

	    // Append possibly modified previous token and parameter
	    addParameterForDisplay(buf, ph.getPrevToken(), idAsString);
	    break;
	default:		// Column field; remove curlies
	    buf.append(whereClause.substring(oldEndPos + 1, pos));
	    buf.append(' ');
	    buf.append(quoted(whereClause.substring(pos + 1, endPos)));
	    buf.append(' ');
	    break;
	}
	pos = endPos + 1;
    }

    if ((endPos + 1) < whereClause.length())
	buf.append(whereClause.substring(endPos + 1));

    return buf.toString();
}

/**
 * Returns the query as a human-readable SQL statement, including parameter,
 * formula, and user column display strings.
 *
 * @return a SQL query string
 */
public String toString() {
    return queryAsString(true);
}

/**
 * Returns the query as a SQL string suitable for building a prepared
 * statement.
 *
 * @return a SQL query string
 */
public String toPreparedStatementString() {
    preparedStmtValues = new ArrayList();
    return queryAsString(false);
}

/**
 * Returns the query as either a human-readable SQL statement or a SQL
 * string suitable for building a prepared statement.
 *
 * @param forDisplay if <code>true</code> return a human-readable string,
 * else return a SQL string suitable for building a prepared statement
 * @return a SQL string
 */
protected String queryAsString(boolean forDisplay) {
    // Rebuild collections of tables, columns, and user columns every time
    // (not just first time) since the list of columns we want to use may
    // have changed since last time.
    findSelectablesUsed();

    if (tables.size() == 0 || selectables.size() == 0)
	return "";

    StringBuffer str = new StringBuffer();
    buildSelect(str);
    buildFrom(str);
    buildWhereClause(str, forDisplay);
    buildOrderBy(str);
    return str.toString();
}

protected void buildSelect(StringBuffer str) {
    str.append("select ");

    // Build list of database columns and user columns
    ArrayList selectCols = new ArrayList();
    for (Iterator iter = selectables.iterator(); iter.hasNext(); ) {
	String sel = ((Selectable)iter.next()).getSelectString(this);
	if (sel != null)
	    selectCols.add(sel);
    }
    str.append(StringUtils.join(selectCols, ", "));
}

protected void buildFrom(StringBuffer str) {
    str.append(" from ");
    boolean first = true;
    for (Iterator iter = tables.iterator(); iter.hasNext(); ) {
	if (first) first = false;
	else str.append(", ");
	str.append(quoted(((Table)iter.next()).getName()));
    }
}

protected void buildWhereClause(StringBuffer str, boolean forDisplay) {
    if (joins.isEmpty() && (whereClause == null || whereClause.length() == 0))
	return;

    str.append(" where ");
    if (!joins.isEmpty())
	buildJoins(str);
    if (whereClause != null && whereClause.length() > 0) {
	if (!joins.isEmpty())
	    str.append(" and ");
	buildUserWhereClause(str, forDisplay);
    }
}

protected void buildJoins(StringBuffer str) {
    ArrayList quotedJoins = new ArrayList();
    for (Iterator iter = joins.iterator(); iter.hasNext(); ) {
	Join j = (Join)iter.next();
	StringBuffer buf = new StringBuffer();
	buf.append(quoted(((Column)j.getFrom()).fullName()));
	buf.append(' ');
	buf.append(j.getRelation());
	buf.append(' ');
	buf.append(quoted(((Column)j.getTo()).fullName()));
	quotedJoins.add(buf.toString());
    }

    str.append("(");
    str.append(StringUtils.join(quotedJoins, ") and ("));
    str.append(")");
}

protected void buildUserWhereClause(StringBuffer str, boolean forDisplay) {
    str.append("(");
    if (forDisplay)
	str.append(getWhereClauseForDisplay());
    else {
	// Call getWhereClauseForPreparedStatement so parameter
	// values are substituted and saved.
	str.append(getWhereClauseForPreparedStatement());
    }
    str.append(")");
}

protected void buildOrderBy(StringBuffer str) {
    if (report.hasGroups() || !sortSelectables.isEmpty()) {
	str.append(" order by ");
	ArrayList orders = new ArrayList();
	for (Iterator iter = report.groups(); iter.hasNext(); ) {
	    Group g = (Group)iter.next();
	    StringBuffer buf =
		new StringBuffer(g.getSelectable().getSortString(this));
	    buf.append(' ');
	    buf.append(g.getSortOrder() == Group.SORT_DESCENDING
			    ? "desc" : "asc");
	    orders.add(buf.toString());
	}
	for (Iterator iter = sortedSelectables(); iter.hasNext(); ) {
	    Selectable s = (Selectable)iter.next();
	    StringBuffer buf = new StringBuffer(s.getSortString(this));
	    buf.append(' ');
	    buf.append(sortOrderOf(s) == Query.SORT_DESCENDING ? "desc" : "asc");
	    orders.add(buf.toString());
	}

	str.append(StringUtils.join(orders, ", "));
    }
}

/**
 * Given a prepared statement created with the text returned by
 * <code>toPreparedStatementString</code>, plug in all the parameter
 * and formula values.
 *
 * @see #toPreparedStatementString
 */
public void setParameters(PreparedStatement stmt) throws SQLException {
    int i = 1;
    for (Iterator iter = preparedStmtValues.iterator(); iter.hasNext(); ++i) {
	// In Oracle, Java Dates are turned into timestamps, or something
	// like that. This is an attempt to fix this problem.
	Object val = iter.next();
	if (val instanceof java.util.Date)
	    stmt.setDate(i,
			 new java.sql.Date(((java.util.Date)val).getTime()));
	else
	    stmt.setObject(i, val);
    }
}

/**
 * Quotes those parts of a table or column name that need to be quoted.
 * <p>
 * Different databases and JDBC drivers treat case sensitively differently.
 * We use the database metadata case sensitivity values to determine which
 * parts of the name need to be quoted.
 *
 * @param name a table or column name
 * @return a quoted version of the name
 */
public String quoted(String name) {
    Database db = (Database)report.getDataSource();

    List components = StringUtils.split(name, ".");
    int len = components.size();
    for (int i = 0; i < len; ++i) {
	String component = (String)components.get(i);
	// Put quotes around the component if (a) there is a space in the
	// component, (b) the JDBC driver translates all names to lower
	// case and we have non-lower-case letters, or (c) the JDBC driver
	// translates all names to upper case and we have non-upper-case
	// letters.
	//
	// The database has a method that lets us know if the user wants
	// to skip quoting. We always quote fields with spaces in the name,
	// though.
	if (component.indexOf(" ") >= 0	// Always quote if spaces
	    || (report.caseSensitiveDatabaseNames() // Don't quote unless asked
		&& ((db.storesLowerCaseIdentifiers()
		     && !component.equals(component.toLowerCase()))
		    || (db.storesUpperCaseIdentifiers()
			&& !component.equals(component.toUpperCase())))
		)
	    )
	    components.set(i, db.quoteString() + component + db.quoteString());
    }
    return StringUtils.join(components, ".");
}

}
