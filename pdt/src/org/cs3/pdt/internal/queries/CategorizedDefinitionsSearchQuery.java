package org.cs3.pdt.internal.queries;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.cs3.pdt.core.PDTCoreUtils;
import org.cs3.pdt.internal.structureElements.PDTMatch;
import org.cs3.pl.common.Util;
import org.cs3.pl.metadata.Goal;
import org.cs3.pl.prolog.PrologInterface;
import org.eclipse.core.resources.IFile;

public class CategorizedDefinitionsSearchQuery extends PDTSearchQuery {
	public CategorizedDefinitionsSearchQuery(PrologInterface pif, Goal goal) {
		super(pif, goal);
		setSearchType("Definitions and declarations of");
	}

	protected String getCategoryDescription(String module, String functor, int arity, String category)  {
		StringBuffer description = new StringBuffer(category);
		return description.toString();
	}

	@Override
	protected String buildSearchQuery(Goal goal, String module) {		
		String file = "'"+goal.getFile()+"'";
		if (goal.getFile().equals(""))
			file = "OrigFile";

		String module2 = module;
		if (module.equals("''"))
			module2 = "Module";
		
		String term = goal.getTermString();
		//String term = Util.quoteAtom(origTerm);
		
		
		String query = "pdt_search:find_definitions_categorized(" 
			            + file + "," + goal.getLine() + "," + term + ", Functor, Arity, "+ module2 + 
			            ", SearchCategory, DefiningModule, File, Line, PropertyList, ResultsCategory)";
		return query;
	}



	@SuppressWarnings("unchecked")
	@Override
	protected PDTMatch constructPrologMatchForAResult(Map<String, Object> m)
	throws IOException {
		String definingModule = (String)m.get("DefiningModule");
		String functor = (String)m.get("Functor");
		int arity = Integer.parseInt(((String)m.get("Arity")));
		IFile file = PDTCoreUtils.getFileForLocationIndependentOfWorkspace((String)m.get("File"));
		int line = Integer.parseInt((String) m.get("Line"));

		Object prop = m.get("PropertyList");
		List<String> properties = null;
		if (prop instanceof Vector<?>) {
			properties = (Vector<String>)prop;
		}	
		String resultsCategory = (String)m.get("ResultsCategory");

		String searchCategory = (String)m.get("SearchCategory");
		
		
		PDTMatch match = createUniqueMatch(definingModule, functor, arity,
				file, line, properties, resultsCategory, searchCategory);
		
		addCategoryEntry(match, getCategoryDescription(definingModule, functor, arity, resultsCategory));			
		return match;
	}
	
}
