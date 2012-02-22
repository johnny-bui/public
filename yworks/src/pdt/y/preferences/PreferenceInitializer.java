package pdt.y.preferences;

import static pdt.y.preferences.PreferenceConstants.APPEARANCE_BORDER_COLOR;
import static pdt.y.preferences.PreferenceConstants.APPEARANCE_BORDER_STYLE;
import static pdt.y.preferences.PreferenceConstants.APPEARANCE_BORDER_STYLE_DASHED_DOTTED;
import static pdt.y.preferences.PreferenceConstants.APPEARANCE_BORDER_STYLE_SOLID;
import static pdt.y.preferences.PreferenceConstants.APPEARANCE_DYNAMIC_PREDICATE_BORDER_STYLE;
import static pdt.y.preferences.PreferenceConstants.APPEARANCE_EXPORTED_PREDICATE_COLOR;
import static pdt.y.preferences.PreferenceConstants.APPEARANCE_LINE_COLOR;
import static pdt.y.preferences.PreferenceConstants.APPEARANCE_MODULE_FILE_BACKGROUND_COLOR;
import static pdt.y.preferences.PreferenceConstants.APPEARANCE_MODULE_HEADER_COLOR;
import static pdt.y.preferences.PreferenceConstants.APPEARANCE_NONMODULE_HEADER_COLOR;
import static pdt.y.preferences.PreferenceConstants.APPEARANCE_PREDICATE_COLOR;
import static pdt.y.preferences.PreferenceConstants.APPEARANCE_UNUSED_PREDICATE_BORDER_COLOR;
import static pdt.y.preferences.PreferenceConstants.BASE_TEMPLATE;
import static pdt.y.preferences.PreferenceConstants.BASE_TEMPLATES_STORAGE;
import static pdt.y.preferences.PreferenceConstants.BASE_TEMPLATE_DEFAULT;
import static pdt.y.preferences.PreferenceConstants.LAYOUT;
import static pdt.y.preferences.PreferenceConstants.LAYOUT_HIERARCHY;
import static pdt.y.preferences.PreferenceConstants.NAME_CROPPING;
import static pdt.y.preferences.PreferenceConstants.NAME_CROPPING_BRACKET;
import static pdt.y.preferences.PreferenceConstants.NODE_SIZE;
import static pdt.y.preferences.PreferenceConstants.NODE_SIZE_FIXED;
import static pdt.y.preferences.PreferenceConstants.NODE_SIZE_FIXED_HEIGHT;
import static pdt.y.preferences.PreferenceConstants.NODE_SIZE_FIXED_WIDTH;
import static pdt.y.preferences.PreferenceConstants.SHOW_TOOLTIPS;
import static pdt.y.preferences.PreferenceConstants.UPDATE_MODE;
import static pdt.y.preferences.PreferenceConstants.UPDATE_MODE_MANUAL;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Hashtable;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.StringConverter;
import org.eclipse.swt.graphics.RGB;

import pdt.y.main.PluginActivator;

/**
 * Class used to initialize default preference values.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

	public static final String[][] defaultPreferences = new String[][] {
			{ BASE_TEMPLATE, BASE_TEMPLATE_DEFAULT },
			{ UPDATE_MODE, UPDATE_MODE_MANUAL },
			{ SHOW_TOOLTIPS, Boolean.toString(true) },
			{ NAME_CROPPING, NAME_CROPPING_BRACKET },
			{ NODE_SIZE, NODE_SIZE_FIXED },
			{ NODE_SIZE_FIXED_HEIGHT, "40" },
			{ NODE_SIZE_FIXED_WIDTH, "100" },
			{ LAYOUT, LAYOUT_HIERARCHY },
			{ APPEARANCE_NONMODULE_HEADER_COLOR, getColorString(Color.WHITE) },
			{ APPEARANCE_MODULE_HEADER_COLOR, getColorString(new Color(203, 215, 226)) },
			{ APPEARANCE_MODULE_FILE_BACKGROUND_COLOR, getColorString(new Color(240, 240, 240)) },
			{ APPEARANCE_PREDICATE_COLOR, getColorString(Color.YELLOW) },
			{ APPEARANCE_EXPORTED_PREDICATE_COLOR, getColorString(Color.GREEN) },
			{ APPEARANCE_BORDER_COLOR, getColorString(Color.BLACK) },
			{ APPEARANCE_UNUSED_PREDICATE_BORDER_COLOR, getColorString(Color.RED) },
			{ APPEARANCE_BORDER_STYLE, Integer.toString(APPEARANCE_BORDER_STYLE_SOLID) },
			{ APPEARANCE_DYNAMIC_PREDICATE_BORDER_STYLE, Integer.toString(APPEARANCE_BORDER_STYLE_DASHED_DOTTED) },
			{ APPEARANCE_LINE_COLOR, getColorString(Color.DARK_GRAY) }
	};
	
	private static String getColorString(Color color) {
		RGB rgb = new RGB(color.getRed(), color.getGreen(), color.getBlue());
		return StringConverter.asString(rgb);
	}
	
	@SuppressWarnings("unchecked")
	public static Hashtable<String, String[][]> getTemplates(IPreferenceStore store) {
		
		Hashtable<String, String[][]> templates = null;
		try {
			String storage = store.getString(BASE_TEMPLATES_STORAGE);
			
			if (storage.length() == 0) {
				return null;
			}
			
			ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(storage.getBytes()));
			
			templates = (Hashtable<String, String[][]>)stream.readObject();
		}
		catch (Exception e) {
			return null;
		}
		return templates;
	}

	public void initializeDefaultPreferences() {
		
		IPreferenceStore store = PluginActivator.getDefault().getPreferenceStore();
		
		String baseTemplate = store.getString(BASE_TEMPLATE);
		if (baseTemplate.length() == 0) {
			baseTemplate = BASE_TEMPLATE_DEFAULT;
		}
			
		Hashtable<String,String[][]> templates = getTemplates(store);
		String[][] preferences;
		if (templates == null
				|| (preferences = templates.get(baseTemplate)) == null) {
			preferences = defaultPreferences;
		}
		for (String[] p : preferences) {
			store.setDefault(p[0], p[1]);
		}
	}

	public static void saveCurrentTemplate(IPreferenceStore store, String name) throws IllegalArgumentException {
		
		store.setValue(BASE_TEMPLATE, name);
		
		Hashtable<String,String[][]> templates = getTemplates(store);
		
		if (templates == null)
			templates = new Hashtable<String,String[][]>();
		
		if (name.length() == 0 || templates.containsKey(name)) {
			throw new IllegalArgumentException("Template name should be non-empty and unique");
		}
		
		String[][] newPreferences = getCurrentPreferences(store);
		
		templates.put(name, newPreferences);
		
		ByteArrayOutputStream mem = new ByteArrayOutputStream(4096);
		writeObjectToStream(templates, mem);
		store.setValue(BASE_TEMPLATES_STORAGE, mem.toString());
	}

	public static void applyTemplate(IPreferenceStore store, String name) {
		
		Hashtable<String,String[][]> templates = getTemplates(store);
		
		String[][] preferences;
		if (templates == null 
				|| (preferences = templates.get(name)) == null) {
			preferences = defaultPreferences;
		}
		
		applyPreferences(store, preferences);
	}

	protected static void applyPreferences(IPreferenceStore store, String[][] preferences) {
		for (String[] p : preferences) {
			store.setDefault(p[0], p[1]);
			store.setToDefault(p[0]);
		}
		PluginActivator.getDefault().preferencesUpdated();
	}

	public static void removeAllTemplates(IPreferenceStore store) {
		store.setValue(BASE_TEMPLATES_STORAGE, "");
		store.setValue(BASE_TEMPLATE, BASE_TEMPLATE_DEFAULT);
	}

	public static void saveCurrentPreferencesToFile(IPreferenceStore store, String path) throws FileNotFoundException {
		
		String[][] newPreferences = getCurrentPreferences(store);
		
		writeObjectToStream(newPreferences, new FileOutputStream(path));
	}

	protected static String[][] getCurrentPreferences(IPreferenceStore store) {
		String[][] newPreferences = new String[defaultPreferences.length][2];
		for (int i = 0; i < defaultPreferences.length; i++) {
			newPreferences[i][0] = defaultPreferences[i][0];
			newPreferences[i][1] = store.getString(defaultPreferences[i][0]);
		}
		return newPreferences;
	}

	protected static void writeObjectToStream(Object dataObject, OutputStream stream) {
		ObjectOutputStream objStream;
		try {
			objStream = new ObjectOutputStream(stream);
			objStream.writeObject(dataObject);
			stream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void loadPreferencesFromFile(IPreferenceStore store, String path) throws IOException, ClassNotFoundException {
		
		FileInputStream stream = new FileInputStream(path);
		ObjectInputStream objStream = new ObjectInputStream(stream);
		String[][] preferences = (String[][])objStream.readObject();
		applyPreferences(store, preferences);
	}

}
