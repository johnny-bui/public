/*****************************************************************************
 * This file is part of the Prolog Development Tool (PDT)
 * 
 * Author: Lukas Degener (among others)
 * WWW: http://sewiki.iai.uni-bonn.de/research/pdt/start
 * Mail: pdt@lists.iai.uni-bonn.de
 * Copyright (C): 2004-2012, CS Dept. III, University of Bonn
 * 
 * All rights reserved. This program is  made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 * 
 ****************************************************************************/

package org.cs3.pdt.internal.editors;

import static org.cs3.prolog.common.QueryUtils.bT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.cs3.pdt.PDT;
import org.cs3.pdt.PDTPlugin;
import org.cs3.pdt.PDTUtils;
import org.cs3.pdt.common.PDTCommonPlugin;
import org.cs3.pdt.common.PDTCommonPredicates;
import org.cs3.pdt.common.PDTCommonUtil;
import org.cs3.pdt.common.metadata.Goal;
import org.cs3.pdt.internal.ImageRepository;
import org.cs3.pdt.internal.actions.FindDefinitionsActionDelegate;
import org.cs3.pdt.internal.actions.FindPredicateActionDelegate;
import org.cs3.pdt.internal.actions.FindReferencesActionDelegate;
import org.cs3.pdt.internal.actions.RunUnitTestAction;
import org.cs3.pdt.internal.actions.ToggleCommentAction;
import org.cs3.pdt.internal.editors.breakpoints.PDTBreakpointHandler;
import org.cs3.pdt.internal.views.lightweightOutline.NonNaturePrologOutline;
import org.cs3.pdt.metadata.GoalProvider;
import org.cs3.pdt.metadata.PredicateReadingUtilities;
import org.cs3.prolog.common.Util;
import org.cs3.prolog.common.logging.Debug;
import org.cs3.prolog.connector.ui.PrologRuntimeUIPlugin;
import org.cs3.prolog.pif.PrologInterface;
import org.cs3.prolog.pif.PrologInterfaceException;
import org.cs3.prolog.pif.service.ActivePrologInterfaceListener;
import org.cs3.prolog.pif.service.ConsultListener;
import org.cs3.prolog.ui.util.ExternalPrologFilesProjectUtils;
import org.cs3.prolog.ui.util.UIUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.text.contentassist.IContentAssistant;
import org.eclipse.jface.text.information.InformationPresenter;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CaretEvent;
import org.eclipse.swt.custom.CaretListener;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;
import org.eclipse.ui.texteditor.MarkerUtilities;
import org.eclipse.ui.texteditor.SourceViewerDecorationSupport;
import org.eclipse.ui.texteditor.TextEditorAction;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;

public class PLEditor extends TextEditor implements ConsultListener, ActivePrologInterfaceListener {

	public static final String COMMAND_OPEN_PRIMARY_DEFINITION = "org.eclipse.pdt.ui.open.primary.definition";
	
	public static final String COMMAND_FIND_ALL_DEFINITIONS = "org.eclipse.pdt.ui.find.all.definitions";
	
	public static final String COMMAND_FIND_REFERENCES = "org.eclipse.pdt.ui.find.references";
	
	public static final String COMMAND_SHOW_TOOLTIP = "org.eclipse.pdt.ui.edit.text.prolog.show.prologdoc";

	public static final String COMMAND_SHOW_QUICK_OUTLINE = "org.eclipse.pdt.ui.edit.text.prolog.show.quick.outline";

	public static final String COMMAND_SAVE_NO_CONSULT = "org.eclipse.pdt.ui.edit.save.no.reconsult";

	public static final String COMMAND_CONSULT = "org.eclipse.pdt.ui.edit.consult";

	public static final String COMMAND_RUN_UNIT_TEST = "org.eclipse.pdt.ui.edit.run.unit.test";

	public static final String COMMAND_TOGGLE_COMMENTS = "org.eclipse.pdt.ui.edit.text.prolog.toggle.comments";
	
	private ColorManager colorManager;

	private NonNaturePrologOutline fOutlinePage;

	protected final static char[] BRACKETS = { '(', ')', '[', ']' };

	private static final boolean EXPERIMENTAL_ADD_TASKS = true;

	private PLConfiguration configuration;

	private IContentAssistant assistant;

	@Override
	protected void initializeKeyBindingScopes() {
		setKeyBindingScopes(new String[] { PDT.CONTEXT_EDITING_PROLOG_CODE });
	}

	@Override
	public void doSave(IProgressMonitor progressMonitor) {
		if (shouldAbortSaving()) {
			return;
		}
		super.doSave(progressMonitor);
		
		boolean shouldConsult = Boolean.parseBoolean(PDTPlugin.getDefault().getPreferenceValue(PDT.PREF_CONSULT_ON_SAVE, "true"));
		
		if (shouldConsult) {
			Document document = (Document) getDocumentProvider().getDocument(getEditorInput());
			if(EXPERIMENTAL_ADD_TASKS){
				addTasks(((FileEditorInput)getEditorInput()).getFile(),document);
			}
			breakpointHandler.backupMarkers(getCurrentIFile(), document);
			
			PDTCommonPlugin.getDefault().getPreferenceStore().setValue("console.no.focus", true);
			
			IFile currentIFile = getCurrentIFile();
			if (currentIFile != null) {
				PrologRuntimeUIPlugin.getDefault().getPrologInterfaceService().consultFile(currentIFile);
			} else {
				PrologRuntimeUIPlugin.getDefault().getPrologInterfaceService().consultFile(getPrologFileName());
			}
		}
		
		PDTCommonPlugin.getDefault().notifyDecorators();
	}

	private void addTasks(IFile file, Document document) {
			try {
				int offset = 0;
				file.deleteMarkers(IMarker.TASK, true, IResource.DEPTH_ONE);
				while(offset<document.getLength()){
					ITypedRegion region = document.getPartition(offset);
					if(isComment(region)){
						String content=document.get(offset, region.getLength());
						addTaskMarker(file, document, offset, content, "TODO");
						addTaskMarker(file, document, offset, content, "FIXME");
					}
					offset = region.getOffset() + region.getLength();
				}
			} catch (BadLocationException e) {
				Debug.report(e);
			} catch (CoreException e1) {
				Debug.report(e1);
			}

	}

	private void addTaskMarker(IFile file, Document document, int offset,
			String content, String text) throws BadLocationException {
		if(content.contains(text)){
			int todoOffset = content.indexOf(text);
			int linebreakOffset = content.indexOf("\n",todoOffset);
			if(linebreakOffset<0){
				linebreakOffset = content.length()-1;
			}
			String lineString = content.substring(todoOffset, linebreakOffset);
			HashMap<String, Comparable<?>> attributes = new HashMap<String, Comparable<?>>();
			attributes.put(IMarker.LINE_NUMBER, document.getLineOfOffset(offset+todoOffset));
			attributes.put(IMarker.CHAR_START, offset+todoOffset);
			attributes.put(IMarker.CHAR_END, offset+linebreakOffset);
			attributes.put(IMarker.MESSAGE, lineString);
			if(text.equals("TODO")){
				attributes.put(IMarker.PRIORITY, new Integer(IMarker.PRIORITY_NORMAL));
			} else {
				attributes.put(IMarker.PRIORITY, new Integer(IMarker.PRIORITY_HIGH));
			}
			try {
				MarkerUtilities.createMarker(file, attributes, IMarker.TASK);
			} catch (CoreException e) {
				Debug.report(e);
			}
		}
	}

	/**
	 * @return
	 * 
	 */
	private boolean shouldAbortSaving() {
		if (isExternalInput()) {
			boolean showWarning = Boolean.parseBoolean(PDTPlugin.getDefault().getPreferenceValue(PDT.PREF_EXTERNAL_FILE_SAVE_WARNING, "true"));
			if (showWarning) {
				MessageDialog m = new MessageDialog(
						getEditorSite().getShell(),
						"External file",
						null,
						"The current file in the editor is not contained in the workspace. Are you sure you want to save this file?",
						MessageDialog.QUESTION, new String[] { "Yes",
								"Yes, always", "No" }, 0);
				int answer = m.open();
				switch (answer) {
				case 1:
					PDTPlugin.getDefault().setPreferenceValue(
							PDT.PREF_EXTERNAL_FILE_SAVE_WARNING, "false");
				case 0:
					break;
				case 2:
				case SWT.DEFAULT:
				default:
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void setFocus() {
		super.setFocus();
		informAboutChangedEditorInput();
	}

	public void informAboutChangedEditorInput() {
		ISchedulingRule rule;
		if (!isExternalInput()) {
			rule = ((IFileEditorInput) getEditorInput()).getFile();
		} else {
			rule = null;
			// TODO: TRHO: This is probably too much. The current file is an
			// external file:
			// rule = ResourcesPlugin.getWorkspace().getRoot();
		}

		Job j = new Job("notify PDT views about changed editor input") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				PDTPlugin.getDefault().setSelection(
						new PDTChangedFileInformation(getEditorInput()));
				return Status.OK_STATUS;
			}
		};
		j.setRule(rule);
		j.schedule();
	}

	public PLEditor() {
		super();
		try {
			colorManager = PDTPlugin.getDefault().getColorManager();

			configuration = new PLConfiguration(colorManager, this);
			setSourceViewerConfiguration(configuration);

			breakpointHandler = PDTBreakpointHandler.getInstance();

		} catch (Throwable t) {
			Debug.report(t);
			throw new RuntimeException(t);
		}
	}

	private static final String SEP_PDT_SEARCH = "pdt_search_actions";
	private static final String SEP_PDT_INFO = "pdt_info_actions";
	private static final String SEP_PDT_EDIT = "pdt_edit_actions";
	private static final String SEP_PDT_LAST = "pdt_dummy";

	private IPath filepath;

	private InformationPresenter fOutlinePresenter;

	private TextEditorAction reloadAction;

	private static final String MATCHING_BRACKETS = "matching.brackets";

	private static final String MATCHING_BRACKETS_COLOR = "matching.brackets.color";

	public static long OCCURRENCE_UPDATE_DELAY = 300;

	@Override
	protected void configureSourceViewerDecorationSupport(
			SourceViewerDecorationSupport support) {
		getPreferenceStore().setDefault(MATCHING_BRACKETS, true);
		getPreferenceStore().setDefault(MATCHING_BRACKETS_COLOR, "30,30,200");
		support.setCharacterPairMatcher(new PLCharacterPairMatcher());
		support.setMatchingCharacterPainterPreferenceKeys(MATCHING_BRACKETS,
				MATCHING_BRACKETS_COLOR);

		super.configureSourceViewerDecorationSupport(support);
	}

	@Override
	public void createPartControl(final Composite parent) {
		try {
			createPartControl_impl(parent);
		} catch (Throwable t) {
			Debug.report(t);
			throw new RuntimeException(t.getMessage(), t);
		}
	}

	private void createPartControl_impl(final Composite parent) {
		super.createPartControl(parent);

		MenuManager menuMgr = createPopupMenu();

		createInspectionMenu(menuMgr);

		// TODO: create own bundle
		ResourceBundle bundle = ResourceBundle.getBundle(PDT.RES_BUNDLE_UI);

		createMenuEntryForTooltip(menuMgr, bundle);
		createMenuEntryForOutlinePresenter(menuMgr, bundle);
		createMenuEntryForContentAssist(menuMgr, bundle);

		createMenuEntryForReconsult(menuMgr, bundle);
		createMenuEntryForSaveWithoutReconsult(menuMgr, bundle);
		createMenuEntryForRunUnitTest(menuMgr, bundle);

		createMenuEntryForToggleComments(menuMgr, bundle);

		getSourceViewer().getTextWidget().addCaretListener(new CaretListener() {

			@Override
			public void caretMoved(CaretEvent event) {
				updateOccurrenceAnnotations(event.caretOffset);

			}
		});
		checkBackgroundAndTitleImage(getEditorInput());

		getVerticalRuler().getControl().addMouseListener(new MouseListener() {

			@Override
			public void mouseUp(MouseEvent e) {
			}

			@Override
			public void mouseDown(MouseEvent e) {
			}

			@Override
			public void mouseDoubleClick(MouseEvent e) {
				int currentLine = getVerticalRuler().getLineOfLastMouseButtonActivity() + 1;
				Document doc = (Document) getDocumentProvider().getDocument(getEditorInput());
				int currentOffset = UIUtils.physicalToLogicalOffset(doc, getCurrentLineOffsetSkippingWhiteSpaces(currentLine));
				breakpointHandler.toogleBreakpoint(getCurrentIFile(), currentLine, currentOffset);
			}
		});
		
		PrologRuntimeUIPlugin.getDefault().getPrologInterfaceService().registerActivePrologInterfaceListener(this);
		PrologRuntimeUIPlugin.getDefault().getPrologInterfaceService().registerConsultListener(this);

	}

	/**
	 * @param menuMgr
	 */
	private void createInspectionMenu(MenuManager menuMgr) {
		
		addAction(menuMgr, new FindPredicateActionDelegate(this),
				"Open Primary Definition or Declaration", SEP_PDT_SEARCH,
				COMMAND_OPEN_PRIMARY_DEFINITION);
//				"org.eclipse.pdt.ui.open.primary.definition");
//				IJavaEditorActionDefinitionIds.OPEN_EDITOR);

		addAction(menuMgr, new FindDefinitionsActionDelegate(this),
				"Find all Definitions and Declarations", SEP_PDT_SEARCH,
				COMMAND_FIND_ALL_DEFINITIONS);
//				"org.cs3.pdt.find.definitions");
//				IJavaEditorActionDefinitionIds.SEARCH_DECLARATIONS_IN_WORKSPACE);

		addAction(menuMgr, new FindReferencesActionDelegate(this),
				"Find References", SEP_PDT_SEARCH,
				COMMAND_FIND_REFERENCES);
//				"org.cs3.pdt.find.references");
//				IJavaEditorActionDefinitionIds.SEARCH_REFERENCES_IN_WORKSPACE);

		// addAction(menuMgr, new SpyPointActionDelegate(this),
		// "Toggle Spy Point", SEP_PDT_INSPECT,
		// "org.eclipse.debug.ui.commands.ToggleBreakpoint");
	}

	private void createMenuEntryForTooltip(MenuManager menuMgr,
			ResourceBundle bundle) {
		Action action;
		action = new TextEditorAction(bundle, PLEditor.class.getName()
				+ ".ToolTipAction", this) {
			@Override
			public void run() {
				assistant.showContextInformation();
			}
		};
		addAction(menuMgr, action, "Show Tooltip", SEP_PDT_INFO,
				COMMAND_SHOW_TOOLTIP);
	}

	private void createMenuEntryForOutlinePresenter(MenuManager menuMgr,
			ResourceBundle bundle) {
		Action action;

		fOutlinePresenter = configuration.getOutlinePresenter(this
				.getSourceViewer());
		fOutlinePresenter.install(this.getSourceViewer());

		action = new TextEditorAction(bundle, PLEditor.class.getName()
				+ ".ToolTipAction", this) {
			@Override
			public void run() {
				TextSelection selection = (TextSelection) getEditorSite()
						.getSelectionProvider().getSelection();
				fOutlinePresenter.setOffset(selection.getOffset());

				fOutlinePresenter.showInformation();
			}
		};
		addAction(menuMgr, action, "Show Outline", SEP_PDT_INFO,
				COMMAND_SHOW_QUICK_OUTLINE);
	}

	private void createMenuEntryForContentAssist(MenuManager menuMgr,
			ResourceBundle bundle) {
		assistant = configuration.getContentAssistant(getSourceViewer());

		Action action = new TextEditorAction(bundle, PLEditor.class.getName()
				+ ".ContextAssistProposal", this) {
			@Override
			public void run() {
				assistant.showPossibleCompletions();
			}
		};
		addAction(menuMgr, action, "Context Assist Proposal", SEP_PDT_INFO,
				ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS);
	}

	private void createMenuEntryForToggleComments(MenuManager menuMgr,
			ResourceBundle bundle) {
		ToggleCommentAction tca = new ToggleCommentAction(bundle,
				PLEditor.class.getName() + ".ToggleCommentsAction", this);
		tca.configure(getSourceViewer(), configuration);
		tca.setEnabled(true);
		addAction(menuMgr, tca, "Toggle Comments", SEP_PDT_LAST,
				COMMAND_TOGGLE_COMMENTS);
	}

	private void createMenuEntryForReconsult(MenuManager menuMgr,
			ResourceBundle bundle) {
		reloadAction = new TextEditorAction(bundle, PLEditor.class.getName()
				+ ".ConsultAction", this) {
			@Override
			public void run() {
				IFile currentIFile = getCurrentIFile();
				if (currentIFile != null) {
					PrologRuntimeUIPlugin.getDefault().getPrologInterfaceService().consultFile(currentIFile);
				} else {
					PrologRuntimeUIPlugin.getDefault().getPrologInterfaceService().consultFile(getPrologFileName());
				}
//				new ConsultAction().consultFromActiveEditor();
////				addProblemMarkers();
				informViewsAboutChangedEditor();
////				executeConsult();
			}

			public void informViewsAboutChangedEditor() {
				PDTPlugin.getDefault().setSelection(
						new PDTChangedFileInformation(getEditorInput()));
			}
		};
		addAction(menuMgr, reloadAction, "(Re)consult", SEP_PDT_EDIT,
				COMMAND_CONSULT);
	}

	private void createMenuEntryForSaveWithoutReconsult(MenuManager menuMgr,
			ResourceBundle bundle) {
		Action action;
		action = new TextEditorAction(bundle, PLEditor.class.getName()
				+ ".SaveNoConsultAction", this) {
			@Override
			public void run() {
				// must be super, otherwise doSave will
				// consult the file and update the problem markers, too.
				if (!shouldAbortSaving()) {
					PLEditor.super.doSave(new NullProgressMonitor());
					updateTitleImage(getEditorInput());
					PDTCommonPlugin.getDefault().notifyDecorators();
				}
			}
		};
		addAction(menuMgr, action, "Save without consult", SEP_PDT_EDIT, COMMAND_SAVE_NO_CONSULT);
	}

	private void createMenuEntryForRunUnitTest(MenuManager menuMgr,
			ResourceBundle bundle) {
		Action action;
		action = new RunUnitTestAction();
		addAction(menuMgr, action, "Run Unit Test", SEP_PDT_EDIT,
				COMMAND_RUN_UNIT_TEST);
	}
	
	/**
	 * @param menuMgr
	 */
	private void addAction(MenuManager menuMgr, Action action, String name,
			String separator, String id) {

		action.setActionDefinitionId(id);
		action.setText(name);
		menuMgr.appendToGroup(separator, action);
		setAction(id,
//				IJavaEditorActionDefinitionIds.SEARCH_REFERENCES_IN_WORKSPACE,
				action);
	}

	/**
	 * @return
	 */
	private MenuManager createPopupMenu() {
		MenuManager menuMgr = new MenuManager(
				"org.cs3.pl.editors.PLEditor", "org.cs3.pl.editors.PLEditor"); //$NON-NLS-1$
		menuMgr.addMenuListener(getContextMenuListener());
		menuMgr.add(new Separator(SEP_PDT_SEARCH));
		menuMgr.add(new Separator(SEP_PDT_INFO));
		menuMgr.add(new Separator(SEP_PDT_EDIT));
		menuMgr.add(new Separator(SEP_PDT_LAST));
		// menuMgr.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
		getSourceViewer().getTextWidget().setMenu(
				menuMgr.createContextMenu(getSourceViewer().getTextWidget()));
		getEditorSite().registerContextMenu(menuMgr, getSelectionProvider());
		return menuMgr;
	}

	/**
	 * @param parent
	 */

	@SuppressWarnings("rawtypes")
	@Override
	public Object getAdapter(Class required) {
		try {
			if (IContentOutlinePage.class.equals(required)) {
				if (fOutlinePage == null) {
					// fOutlinePage = new PrologOutline(this);
					fOutlinePage = new NonNaturePrologOutline(this);
					fOutlinePage.setInput(getEditorInput());
				}
				return fOutlinePage;
			}
			return super.getAdapter(required);
		} catch (Throwable t) {
			Debug.report(t);
			throw new RuntimeException(t);
		}
	}

	/**
	 * Informs the editor that its outliner has been closed.
	 */
	public void outlinePageClosed() {
		if (fOutlinePage != null) {

			fOutlinePage = null;
			resetHighlightRange();
		}
	}

	public ContentOutlinePage getOutlinePage() {
		return fOutlinePage;
	}

	protected int getCurrentLineOffset(int line) {
		Document document = (Document) getDocumentProvider().getDocument(
				getEditorInput());
		int offset = 0;
		try {
			offset = document.getLineInformation(line - 1).getOffset();
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
		return offset;
	}

	protected int getCurrentLineOffsetSkippingWhiteSpaces(int line) {
		int offset = 0;
		Document document = (Document) getDocumentProvider().getDocument(
				getEditorInput());
		try {
			IRegion lineInformation = document.getLineInformation(line - 1);
			String lineContent = document.get(lineInformation.getOffset(),
					lineInformation.getLength());
			int additionalOffset = 0;
			while (additionalOffset < lineContent.length()) {
				char character = lineContent.charAt(additionalOffset);
				if (character == '\t' || character == ' ') {
					additionalOffset++;
				} else {
					break;
				}
			}
			return lineInformation.getOffset() + additionalOffset;
		} catch (BadLocationException e) {
			Debug.report(e);
		}
		return offset;
	}

	/**
	 * @param i
	 */
	public void gotoLine(int line) {
		Document document;
		document = (Document) getDocumentProvider().getDocument(
				getEditorInput());
		int offset;
		try {
			offset = document.getLineInformation(line - 1).getOffset();
			TextSelection newSelection = new TextSelection(document, offset, 0);
			getEditorSite().getSelectionProvider().setSelection(newSelection);
		} catch (BadLocationException e) {
			Debug.report(e);
		}
	}

	/**
	 * @param i
	 * @param i
	 */
	public void gotoOffset(int offset, int length) {
		Document document = (Document) getDocumentProvider().getDocument(
				getEditorInput());
		TextSelection newSelection;
		// try {
		// if(isLineOffset) {
		// offset = document.getLineOffset(offset);
		// }
		newSelection = new TextSelection(document, offset, length);
		getEditorSite().getSelectionProvider().setSelection(newSelection);
		// } catch (BadLocationException e) {
		// e.printStackTrace();
		// }
	}

	/**
	 * @return
	 */
	public String getSelectedLine() {

		Document document = (Document) getDocumentProvider().getDocument(
				getEditorInput());

		String line = null;
		try {
			TextSelection selection = (TextSelection) getEditorSite()
					.getSelectionProvider().getSelection();
			IRegion info = document.getLineInformationOfOffset(selection
					.getOffset());
			int start = PredicateReadingUtilities.findEndOfWhiteSpace(document,
					info.getOffset(), info.getOffset() + info.getLength());

			line = document.get(start, info.getLength() + info.getOffset()
					- start);
		} catch (BadLocationException e) {
			Debug.report(e);

		}
		return line;
	}

	/**
	 * @return
	 */
	public Goal getSelectedPrologElement() throws BadLocationException {
		Document document = (Document) getDocumentProvider().getDocument(
				getEditorInput());

		TextSelection selection = (TextSelection) getEditorSite()
				.getSelectionProvider().getSelection();
		int length = selection.getLength();
		int offset = selection.getOffset();

		return GoalProvider.getPrologDataFromOffset(getPrologFileName(),
				document, offset, length);
	}

	@Override
	protected void rulerContextMenuAboutToShow(IMenuManager menu) {
		super.rulerContextMenuAboutToShow(menu);
		Action toggleBreakpointAction = new Action("Toggle breakpoint") {
			@Override
			public void run() {
				int currentLine = getVerticalRuler().getLineOfLastMouseButtonActivity() + 1;
				Document doc = (Document) getDocumentProvider().getDocument(getEditorInput());
				int currentOffset = UIUtils.physicalToLogicalOffset(doc, getCurrentLineOffset(currentLine));

				breakpointHandler.toogleBreakpoint(getCurrentIFile(), currentLine, currentOffset);
			}
		};
		Action removeBreakpointsAction = new Action("Remove all breakpoints") {
			@Override
			public void run() {
				breakpointHandler.removeBreakpointFactsForFile(getPrologFileName());
			}
		};

		menu.add(toggleBreakpointAction);
		menu.add(removeBreakpointsAction);

		if (getCurrentIFile() == null) {
			toggleBreakpointAction.setEnabled(false);
			removeBreakpointsAction.setEnabled(false);
		}
	}

	private IFile getCurrentIFile() {
		if (getEditorInput() instanceof IFileEditorInput) {
			IFileEditorInput input = (IFileEditorInput) getEditorInput();
			return input.getFile();
		}
		return null;
	}

	public String getPrologFileName() {
		return Util.prologFileName(filepath.toFile());
	}

	public TextSelection getSelection() {
		return (TextSelection) getEditorSite().getSelectionProvider()
				.getSelection();
	}

	/**
	 * @param document
	 * @param l
	 * @return
	 */
	public static boolean predicateDelimiter(IDocument document, int l)
			throws BadLocationException {
		if (isDelimitingDot(document, l)) {
			if (l > 0 && isDelimitingDot(document, l - 1))
				return false;
			if (l < document.getLength() - 1
					&& isDelimitingDot(document, l + 1))
				return false;
			return true;
		}
		return false;
	}

	/**
	 * Checks if the character at position l is a delimiting dot, meaning it is
	 * not enclosed by
	 * <ul>
	 * <li>parentheses</li>
	 * <li>numbers</li>
	 * </ul>
	 * 
	 * @param document
	 * @param l
	 * @return
	 * @throws BadLocationException
	 */
	private static boolean isDelimitingDot(IDocument document, int l)
			throws BadLocationException {
		char c = document.getChar(l);
		if (c != '.') {
			return false;
		}
		if (l == 0 || l == document.getLength() - 1) {
			return false;
		}
		if (isNumber(document.getChar(l - 1))
				&& isNumber(document.getChar(l + 1))) {
			return false;
		}
		if (document.getChar(l - 1) == '(' && document.getChar(l + 1) == ')') {
			return false;
		}
		return true;
	}

	private static boolean isNumber(char c) {
		return c >= '0' && c <= '9';
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.ui.editors.text.TextEditor#doSetInput(org.eclipse.ui.IEditorInput
	 * )
	 */
	@Override
	protected void doSetInput(IEditorInput input) throws CoreException {

		setDocumentProvider(createDocumentProvider(input));
		
		if (fOutlinePage != null) {
			fOutlinePage.setInput(input);
		}
		super.doSetInput(input);

		filepath = new Path(UIUtils.getFileNameForEditorInput(input));
		checkBackgroundAndTitleImage(input);
	}

	private void checkBackgroundAndTitleImage(IEditorInput input) {
		ISourceViewer sourceViewer = getSourceViewer();
		if (sourceViewer == null)
			return;
		StyledText textWidget = sourceViewer.getTextWidget();
		if (textWidget == null)
			return;
		if (!isExternalInput(input)) {
			textWidget.setBackground(new Color(textWidget.getDisplay(), colorManager.getBackgroundColor()));
		} else {
			textWidget.setBackground(new Color(textWidget.getDisplay(), colorManager.getExternBackgroundColor()));
		}
		updateTitleImage(input);
	}
	
	private void updateTitleImage(IEditorInput input) {
		if (input instanceof IFileEditorInput) {
			Map<String, Object> result = null;
			try {
				result = PDTUtils.getActivePif().queryOnce(bT(PDTCommonPredicates.PDT_SOURCE_FILE, Util.quoteAtom(PDTCommonUtil.prologFileName(input)), "State"));
			} catch (PrologInterfaceException e) {
				Debug.report(e);
			}
			if (ExternalPrologFilesProjectUtils.isExternalFile(((IFileEditorInput) input).getFile())) {
				if (result == null) {
					setTitleImage(ImageRepository.getImage(ImageRepository.PROLOG_FILE_EXTERNAL));
				} else {
					String state = result.get("State").toString();
					if ("current".equals(state)) {
						setTitleImage(ImageRepository.getImage(ImageRepository.PROLOG_FILE_EXTERNAL_CONSULTED));
					} else {
						setTitleImage(ImageRepository.getImage(ImageRepository.PROLOG_FILE_EXTERNAL_CONSULTED_OLD));
					}
				}
			} else {
				if (result == null) {
					setTitleImage(ImageRepository.getImage(ImageRepository.PROLOG_FILE_UNCONSULTED));
				} else {
					String state = result.get("State").toString();
					if ("current".equals(state)) {
						setTitleImage(ImageRepository.getImage(ImageRepository.PROLOG_FILE_CONSULTED));
					} else {
						setTitleImage(ImageRepository.getImage(ImageRepository.PROLOG_FILE_CONSULTED_OLD));
					}
				}
			}
		} else {
			setTitleImage(ImageRepository.getImage(ImageRepository.PROLOG_FILE_EXTERNAL_NOT_LINKED));
		}
	}

	private IDocumentProvider createDocumentProvider(IEditorInput input) {
//		 if(input instanceof FileStoreEditorInput){
             return new ExternalDocumentProvider();
//		 } else{
//			 return new PLDocumentProvider();
//		 }
	}

	private boolean isExternalInput() {
		return isExternalInput(getEditorInput());
	}

	private boolean isExternalInput(IEditorInput input) {
		if (!(input instanceof IFileEditorInput)) {
			return true;
		}
		IFileEditorInput fileEditorInput = (IFileEditorInput) input;
		return ExternalPrologFilesProjectUtils.isExternalFile(fileEditorInput
				.getFile());
	}

	private Object annotationModelMonitor = new Object();

	public Annotation[] fOccurrenceAnnotations;

	private OccurrencesFinderJob fOccurrencesFinderJob;

	private boolean fMarkOccurrenceAnnotations = true;

	private TextSelection oldSelection;

	private boolean hightlightOccurrences = true;
	private boolean hightlightSingletonsErrors = true;

	class OccurrencesFinderJob extends Job {

		private final IDocument fDocument;
		// private final ISelectionValidator fPostSelectionValidator;
		private boolean fCanceled = false;
		private Object cancelMonitor = new Object();
		private int fCaretOffset;

		public OccurrencesFinderJob(IDocument document, int caretOffset) {
			super("update occurrences");
			fDocument = document;
			fCaretOffset = caretOffset;

			// if (getSelectionProvider() instanceof ISelectionValidator)
			// fPostSelectionValidator=
			// (ISelectionValidator)getSelectionProvider();
			// else
			// fPostSelectionValidator= null;
		}

		// cannot use cancel() because it is declared final
		void doCancel() {
			fCanceled = true;
			cancel();
			synchronized (cancelMonitor) {
				cancelMonitor.notify();
			}
		}

		private boolean isCanceled(IProgressMonitor progressMonitor) {
			return fCanceled || progressMonitor.isCanceled()
			// || fPostSelectionValidator != null &&
			// !(fPostSelectionValidator.isValid(fSelection)
					// TRHO || fForcedMarkOccurrencesSelection == fSelection

					|| LinkedModeModel.hasInstalledModel(fDocument);
		}

		/*
		 * @see Job#run(org.eclipse.core.runtime.IProgressMonitor)
		 */
		@Override
		public IStatus run(IProgressMonitor progressMonitor) {
			// System.out.println(Thread.currentThread().getName()+ " wait");
			try {
				synchronized (cancelMonitor) {
					cancelMonitor.wait(OCCURRENCE_UPDATE_DELAY);
				}
			} catch (InterruptedException e) {
				Debug.report(e);
			}
			if (isCanceled(progressMonitor)) {
				// System.out.println(Thread.currentThread().getName()+
				// " cancelled");
				return Status.CANCEL_STATUS;
			}
			// System.out.println(Thread.currentThread().getName()+
			// " not cancelled");

			ITextViewer textViewer = getSourceViewer();
			if (textViewer == null)
				return Status.CANCEL_STATUS;

			IDocument document = textViewer.getDocument();
			if (document == null)
				return Status.CANCEL_STATUS;

			IDocumentProvider documentProvider = getDocumentProvider();
			if (documentProvider == null)
				return Status.CANCEL_STATUS;

			IAnnotationModel annotationModel = documentProvider
					.getAnnotationModel(getEditorInput());
			if (annotationModel == null)
				return Status.CANCEL_STATUS;

			// Add occurrence annotations
			ArrayList<OccurrenceLocation> locationList = parseOccurrences(
					fCaretOffset, document);
			if (locationList == null) {
//				removeOccurrenceAnnotations();
				return Status.CANCEL_STATUS;
			}

			int length = locationList.size();
			Map<Annotation, Position> annotationMap = new HashMap<Annotation, Position>(
					length);
			for (int i = 0; i < length; i++) {

				if (isCanceled(progressMonitor))
					return Status.CANCEL_STATUS;

				OccurrenceLocation location = locationList.get(i);
				Position position = new Position(location.getOffset(),
						location.getLength());

				String description = location.getDescription();
				String annotationType;
				switch (location.getFlags()) {
				case 0:
					annotationType = "org.cs3.pdt.occurrences";
					break;
				case 1:
					annotationType = "org.cs3.pdt.occurrences.singleton";
					break;
				case 2:
					annotationType = "org.cs3.pdt.occurrences.non.singleton";
					break;
				case 3:
					annotationType = "org.cs3.pdt.occurrences.singleton.wrong.prefix";
					break;
				default:
					annotationType = "org.cs3.pdt.occurrences";
				}

				annotationMap.put(new Annotation(annotationType, false,
						description), position);
			}

			if (isCanceled(progressMonitor))
				return Status.CANCEL_STATUS;
			synchronized (annotationModelMonitor) {

				// removeOccurrenceAnnotations();

				if (annotationModel instanceof IAnnotationModelExtension) {
					// ((IAnnotationModelExtension)annotationModel).removeAllAnnotations();
					((IAnnotationModelExtension) annotationModel)
							.replaceAnnotations(fOccurrenceAnnotations,
									annotationMap);
				} else {
					removeOccurrenceAnnotations();
					Iterator<Map.Entry<Annotation, Position>> iter = annotationMap
							.entrySet().iterator();
					while (iter.hasNext()) {
						Map.Entry<Annotation, Position> mapEntry = iter.next();
						annotationModel.addAnnotation(
								(Annotation) mapEntry.getKey(),
								(Position) mapEntry.getValue());
					}
				}
				fOccurrenceAnnotations = annotationMap.keySet().toArray(
						new Annotation[annotationMap.keySet().size()]);
			}

			return Status.OK_STATUS;
		}
	}

	void removeOccurrenceAnnotations() {
		// fMarkOccurrenceModificationStamp=
		// IDocumentExtension4.UNKNOWN_MODIFICATION_STAMP;
		// fMarkOccurrenceTargetRegion= null;

		IDocumentProvider documentProvider = getDocumentProvider();
		if (documentProvider == null)
			return;

		IAnnotationModel annotationModel = documentProvider
				.getAnnotationModel(getEditorInput());
		if (annotationModel == null || fOccurrenceAnnotations == null)
			return;

		synchronized (annotationModelMonitor) {
			if (annotationModel instanceof IAnnotationModelExtension) {
				((IAnnotationModelExtension) annotationModel)
						.replaceAnnotations(fOccurrenceAnnotations, null);
			} else {
				for (int i = 0, length = fOccurrenceAnnotations.length; i < length; i++)
					annotationModel.removeAnnotation(fOccurrenceAnnotations[i]);
			}
			fOccurrenceAnnotations = null;
		}
	}

	protected void updateOccurrenceAnnotations(int caretOffset) {

		if (!hightlightOccurrences) {
			return;
		}

		if (fOccurrencesFinderJob != null)
			fOccurrencesFinderJob.doCancel();

		if (!fMarkOccurrenceAnnotations)
			return;

		IDocument document = getSourceViewer().getDocument();
		if (document == null)
			return;

		// if (document instanceof IDocumentExtension4) {
		// int offset= selection.getOffset();
		// long currentModificationStamp=
		// ((IDocumentExtension4)document).getModificationStamp();
		// IRegion markOccurrenceTargetRegion= fMarkOccurrenceTargetRegion;
		// hasChanged= currentModificationStamp !=
		// fMarkOccurrenceModificationStamp;
		// if (markOccurrenceTargetRegion != null && !hasChanged) {
		// if (markOccurrenceTargetRegion.getOffset() <= offset && offset <=
		// markOccurrenceTargetRegion.getOffset() +
		// markOccurrenceTargetRegion.getLength())
		// return;
		// }
		// fMarkOccurrenceTargetRegion= JavaWordFinder.findWord(document,
		// offset);
		// fMarkOccurrenceModificationStamp= currentModificationStamp;
		// }

		// if (locations == null) {
		// // if (!fStickyOccurrenceAnnotations)
		// // removeOccurrenceAnnotations();
		// // else
		// if (hasChanged) // check consistency of current annotations
		// removeOccurrenceAnnotations();
		// return;
		// }

		synchronized (annotationModelMonitor) {
			fOccurrencesFinderJob = new OccurrencesFinderJob(document,
					caretOffset);
			fOccurrencesFinderJob.setPriority(Job.DECORATE);
			fOccurrencesFinderJob.setSystem(true);
			fOccurrencesFinderJob.schedule();
		}
		// fOccurrencesFinderJob.run(new NullProgressMonitor());
	}

	/**
	 * Looks up all occurrences of (singleton) variables at the current
	 * location.
	 * 
	 * @param caretOffset
	 * @param document
	 * @return
	 */
	private ArrayList<OccurrenceLocation> parseOccurrences(int caretOffset,
			IDocument document) {
		ArrayList<OccurrenceLocation> locationList = new ArrayList<OccurrenceLocation>();
		Map<String, List<OccurrenceLocation>> singletonOccurs = new HashMap<String, List<OccurrenceLocation>>();
		Map<String, List<OccurrenceLocation>> nonSingletonOccurs = new HashMap<String, List<OccurrenceLocation>>();

		try {
			ITypedRegion partition = document.getPartition(caretOffset);
			if (partition == null || isComment(partition)) {
				oldSelection = null;
				return locationList;
			}
			TextSelection var = getVariableAtOffset(document, caretOffset);
			String varName = var.getText();
			if (oldSelection != null && oldSelection.equals(var)) {
				return null;
			} else {
				oldSelection = var;
			}
			int begin = var.getOffset();
			int l = begin == 0 ? begin : begin - 1;
			String proposal = null;
			while (l > 0) {
				ITypedRegion region = document.getPartition(l);
				if (isComment(region))
					l = region.getOffset();
				else {
					if (PLEditor.predicateDelimiter(document, l)) {
						proposal = processProposal(singletonOccurs,
								nonSingletonOccurs, locationList, var, l, true,
								proposal);
						break;
					}
					proposal = processProposal(singletonOccurs,
							nonSingletonOccurs, locationList, var, l, true,
							proposal);
				}
				l--;
			}

			// searching downwards
			l = begin = var.getOffset() + var.getLength();
			proposal = null;
			while (l < document.getLength()) {
				ITypedRegion region = document.getPartition(l);
				if (isComment(region)) {
					l = region.getOffset() + region.getLength();
				} else {
					if (PLEditor.predicateDelimiter(document, l)) {
						proposal = processProposal(singletonOccurs,
								nonSingletonOccurs, locationList, var, l,
								false, proposal);
						break;
					}
					proposal = processProposal(singletonOccurs,
							nonSingletonOccurs, locationList, var, l, false,
							proposal);
				}
				l++;
			}
			if (hightlightSingletonsErrors) {
				for (String varToCheck : singletonOccurs.keySet()) {
					List<OccurrenceLocation> varLocations = singletonOccurs
							.get(varToCheck);
					if (varLocations.size() > 1) {
						for (OccurrenceLocation varLocation : varLocations) {
							locationList.add(varLocation);
						}
					}
				}
				for (String varToCheck : nonSingletonOccurs.keySet()) {
					List<OccurrenceLocation> varLocations = nonSingletonOccurs
							.get(varToCheck);
					if (varLocations.size() == 1) {
						for (OccurrenceLocation varLocation : varLocations) {
							locationList.add(varLocation);
						}
					}
				}

			}

			if (Util.isVarPrefix(varName)) {
				if (locationList.size() > 0) {
					locationList.add(new OccurrenceLocation(var.getOffset(),
							var.getLength(), 0, "desc"));
				} else {
					locationList.add(new OccurrenceLocation(var.getOffset(),
							var.getLength(), 1, "desc"));
				}
			} else {
				ITypedRegion region = document.getPartition(var.getOffset());
				if (!isComment(region)) {
					locationList.add(new OccurrenceLocation(var.getOffset(), var.getLength(), 0, "desc"));
				}
			}
		} catch (BadLocationException e) {
		}
		return locationList;
	}

	private String processProposal(
			Map<String, List<OccurrenceLocation>> singletonOccurs,
			Map<String, List<OccurrenceLocation>> nonSingletonOccurs,
			ArrayList<OccurrenceLocation> locationList, TextSelection var,
			int l, boolean up, String proposal) throws BadLocationException {
		char c = getSourceViewer().getDocument().getChar(l);

		if (Util.isVarChar(c)) {
			if (proposal == null)
				proposal = "";
			if (up) {
				proposal = c + proposal;
			} else {
				proposal = proposal + c;
			}
		} else if (proposal != null) {
			int length = proposal.length();
			if (var.getText().equals(proposal)) {
				locationList.add(new OccurrenceLocation(l + (up ? 1 : -length),
						length, 0, "desc"));
			} else if (Util.isVarPrefix(proposal) && !proposal.equals("_")) {
				List<OccurrenceLocation> probOccs;
				int kind = 2;
				if (isSingletonName(proposal) == VAR_KIND_SINGLETON) {
					probOccs = singletonOccurs.get(proposal);
					if (probOccs == null) {
						probOccs = new ArrayList<OccurrenceLocation>();
						singletonOccurs.put(proposal, probOccs);
					}
					kind = 3;
				} else {
					probOccs = nonSingletonOccurs.get(proposal);
					if (probOccs == null) {
						probOccs = new ArrayList<OccurrenceLocation>();
						nonSingletonOccurs.put(proposal, probOccs);
					}
				}
				probOccs.add(new OccurrenceLocation(l + (up ? 1 : -length),
						length, kind, "desc"));
			}
			proposal = null;
		}
		return proposal;
	}

	public static final int VAR_KIND_ANONYMOUS = 0;
	public static final int VAR_KIND_SINGLETON = 1;
	public static final int VAR_KIND_NORMAL = 2;

	private PDTBreakpointHandler breakpointHandler;

	/**
	 * @param a
	 *            valid Prolog variable
	 */
	private static int isSingletonName(String proposal) {
		if (proposal.equals("_")) {
			return VAR_KIND_ANONYMOUS;
		}
		if (proposal.length() == 1) {
			return VAR_KIND_NORMAL;
		}
		if (proposal.charAt(0) == '_'
				&& Util.isSingleSecondChar(proposal.charAt(1))) {
			return VAR_KIND_SINGLETON;
		}
		return VAR_KIND_NORMAL;
	}

	protected boolean isComment(ITypedRegion region) {
		return region.getType().equals(PLPartitionScanner.PL_COMMENT)
				|| region.getType().equals(PLPartitionScanner.PL_MULTI_COMMENT)
				|| region.getType().equals(
						PLPartitionScanner.PL_SINGLE_QUOTED_STRING)
				|| region.getType().equals(
						PLPartitionScanner.PL_DOUBLE_QUOTED_STRING);
	}

	/**
	 * Element representing a occurrence
	 */
	public static class OccurrenceLocation {
		private final int fOffset;
		private final int fLength;
		private final int fFlags;
		private final String fDescription;

		public OccurrenceLocation(int offset, int length, int flags,
				String description) {
			fOffset = offset;
			fLength = length;
			fFlags = flags;
			fDescription = description;
		}

		public int getOffset() {
			return fOffset;
		}

		public int getLength() {
			return fLength;
		}

		public int getFlags() {
			return fFlags;
		}

		public String getDescription() {
			return fDescription;
		}

		@Override
		public String toString() {
			return "[" + fOffset + " / " + fLength + "] " + fDescription; //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$
		}

	}

	// private class VarPos {
	// int begin;
	// int length;
	// String prefix;
	//
	// VarPos(IDocument document, int begin, String prefix) {
	// this.begin=begin;
	// this.prefix=prefix;
	// this.length=prefix.length();
	// }
	// }

	private TextSelection getVariableAtOffset(IDocument document, int offset)
			throws BadLocationException {
		int begin = offset;
		if (!Util.isNonQualifiedPredicateNameChar(document.getChar(begin))
				&& begin > 0) {
			begin--;
		}
		while (Util.isNonQualifiedPredicateNameChar(document.getChar(begin))
				&& begin > 0)
			begin--;
		if (begin < offset)
			begin++;
		int end = offset;
		while (Util.isNonQualifiedPredicateNameChar(document.getChar(end))
				&& begin > 0)
			end++;
		int length = end - begin;
		// String pos = document.get(begin, length);

		return new TextSelection(document, begin, length);
	}

	@Override
	public void beforeConsult(PrologInterface pif, List<IFile> files, IProgressMonitor monitor) throws PrologInterfaceException {
		monitor.beginTask("", 1);
		monitor.done();
	}

	@Override
	public void afterConsult(PrologInterface pif, List<IFile> files, List<String> allConsultedFiles, IProgressMonitor monitor) throws PrologInterfaceException {
		monitor.beginTask("", 1);
		String editorFile = getPrologFileName();
		if (allConsultedFiles.contains(editorFile)) {
			getSite().getShell().getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					PLEditor.this.updateTitleImage(getEditorInput());
					try {
						PLEditor.this.configuration.getPLScanner().initHighlighting();
						PLEditor.this.getSourceViewer().invalidateTextPresentation();
					} catch (PrologInterfaceException e) {
						Debug.report(e);
					} catch (CoreException e) {
						Debug.report(e);
					};
				}
			});
		}

		monitor.done();
	}

	@Override
	public void activePrologInterfaceChanged(PrologInterface pif) {
		getSite().getShell().getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				PLEditor.this.updateTitleImage(getEditorInput());
				try {
					PLEditor.this.configuration.getPLScanner().initHighlighting();
					PLEditor.this.getSourceViewer().invalidateTextPresentation();
				} catch (PrologInterfaceException e) {
					Debug.report(e);
				} catch (CoreException e) {
					Debug.report(e);
				};
			}
		});
	}
	
	@Override
	public void dispose() {
		super.dispose();
		PrologRuntimeUIPlugin.getDefault().getPrologInterfaceService().unRegisterActivePrologInterfaceListener(this);
		PrologRuntimeUIPlugin.getDefault().getPrologInterfaceService().unRegisterConsultListener(this);
	}

}


