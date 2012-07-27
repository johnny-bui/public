/*****************************************************************************
 * This file is part of the Prolog Development Tool (PDT)
 * 
 * WWW: http://sewiki.iai.uni-bonn.de/research/pdt/start
 * Mail: pdt@lists.iai.uni-bonn.de
 * Copyright (C): 2004-2012, CS Dept. III, University of Bonn
 * 
 * All rights reserved. This program is  made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 * 
 ****************************************************************************/

package pdt.y.preferences;

import static pdt.y.preferences.PreferenceConstants.REFRESH_MODE;
import static pdt.y.preferences.PreferenceConstants.REFRESH_MODE_AUTOMATIC;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IWorkbench;

import pdt.y.main.PluginActivator;
import pdt.y.preferences.controls.RefreshModeFieldEditor;

/**
 * This class represents a preference page that
 * is contributed to the Preferences dialog. By 
 * subclassing <samp>FieldEditorPreferencePage</samp>, we
 * can use the field support built into JFace that allows
 * us to create a page that is small and knows how to 
 * save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They
 * are stored in the preference store that belongs to
 * the main plug-in class. That way, preferences can
 * be accessed directly via the preference store.
 */

public class MainPreferencePage
	extends PreferencePageBase {
	
	public MainPreferencePage() {
	}
	
	/**
	 * Creates the field editors. Field editors are abstractions of
	 * the common GUI blocks needed to manipulate various types
	 * of preferences. Each field editor knows how to save and
	 * restore itself.
	 */
	@Override
	public void createFieldEditors() {
		
		addField(new RefreshModeFieldEditor(getFieldEditorParent()));
	}

	@Override
	public void init(IWorkbench workbench) {
	}
	
	private static IPreferenceStore getCurrentPreferences() {
		return PluginActivator.getDefault().getPreferenceStore();
	}

	public static boolean isAutomaticUpdate() {
		String mode = getCurrentPreferences().getString(REFRESH_MODE);
		return mode.equals(REFRESH_MODE_AUTOMATIC);
	}
}


