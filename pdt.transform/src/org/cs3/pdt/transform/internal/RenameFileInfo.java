/**
 * 
 */
package org.cs3.pdt.transform.internal;

import org.cs3.prolog.common.Option;
import org.cs3.prolog.common.SimpleOption;
import org.cs3.prolog.common.Util;
import org.cs3.prolog.load.PrologLibraryManager;
import org.cs3.prolog.pif.PrologInterface;
import org.cs3.prolog.pif.PrologInterfaceException;
import org.cs3.prolog.session.PrologSession;
import org.eclipse.core.resources.IFile;

public final class RenameFileInfo extends PrologRefactoringInfo {

	private IFile file;
	private final PrologInterface prologInterface;

	/**
	 * @param prologInterface 
	 * @param renameFile
	 */
	RenameFileInfo(IFile file, PrologInterface prologInterface) {
		this.file = file;
		this.prologInterface = prologInterface;
	}
/*
	@Override
	public String getHead() {
		String oldPath = getPreferenceValue("path", "");
		String newName = getPreferenceValue("name","");
		Path p = new Path(oldPath);
		
		String newPath = p.removeLastSegments(1).append(newName).toString();
		
		return "rename_file('"+oldPath+"','"+newPath+"')";
	}
*/
	@Override
	public String getName() {
		return "Rename Prolog File";
	}

	@Override
	public PrologInterface getPrologInterace() {
	
		return prologInterface;
	}

	@Override
	public Option[] getOptions() {

		return new Option[] {
				new SimpleOption("path", "CurrentPath", "", Option.FILE, Util
						.prologFileName(file.getLocation().toFile())) {
					@Override
					public boolean isVisible() {
						return false;
					}

				},
				new SimpleOption("name", "New Name", "the new file name",
						Option.STRING, file.getName()) };
	}
	@Override
	public void configure(PrologLibraryManager libman, PrologSession s)
			throws PrologInterfaceException {
		
	}
	@Override
	public String getParameterTerm() {
		return null;
	}
	@Override
	public String getRefactoringId() {
		return null;
	}
	@Override
	public String getSelectionTerm() {
		return null;
	}
}