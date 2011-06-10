package org.cs3.pdt.internal.structureElements;


public class PredicateOccuranceElement implements PDTTreeElement{
	private String label;
	private int line;
	private String type;
	private PDTTreeElement parent;
	
	public PredicateOccuranceElement(String label, int line, String type, PDTTreeElement parent) {
		this.label = label;
		this.line = line;
		this.type = type;
	}
	
	public int getLine() {
		return line;
	}
	
	public String getType() {
		return type;
	}
	
	public PDTTreeElement getParent() {
		return parent;
	}
	@Override
	public boolean hasChildren() {
		return false;
	}

	@Override
	public Object[] getChildren() {
		return new Object[0];
	}

	@Override
	public String getLabel() {
		return label;
	}

}
