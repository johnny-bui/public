package org.cs3.pl.common;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

public class FileUtils {

	public static IPath normalize(IPath path) {
		IPath testLocation = null;
		try {
	
			testLocation = new Path(path.toFile().getCanonicalFile().toString());
		} catch (IOException e1) {
			Debug.report(e1);
			throw new RuntimeException(e1);
		}
		return testLocation;
	}

	/**
	 * adapted from
	 * org.eclipse.core.internal.localstore.FileSystemResourceManager. This is
	 * the "corrected" version: it does normalize the locations before comparing
	 * them. propably hurts performance, but i cant help it. --lu
	 */
	public static List<IPath> allPathsForLocation(IPath l) {
		IPath location = normalize(l);
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot()
				.getProjects();
		final ArrayList<IPath> results = new ArrayList<IPath>();
		for (IProject project2 : projects) {
			IProject project = project2;
			// check the project location
	
			IPath testLocation = normalize(project.getLocation());
			IPath suffix;
			if ((testLocation != null) && testLocation.isPrefixOf(location)) {
				suffix = location.removeFirstSegments(testLocation
						.segmentCount());
				results.add(project.getFullPath().append(suffix));
			}
			if (!project.isAccessible()) {
				continue;
			}
			IResource[] children = null;
			try {
				children = project.members();
			} catch (CoreException e) {
				// ignore projects that cannot be accessed
			}
			if (children == null) {
				continue;
			}
			for (IResource child : children) {
				if (child.isLinked()) {
					testLocation = normalize(child.getLocation());
					if ((testLocation != null)
							&& testLocation.isPrefixOf(location)) {
						// add the full workspace path of the corresponding
						// child of the linked resource
						suffix = location.removeFirstSegments(testLocation
								.segmentCount());
						results.add(child.getFullPath().append(suffix));
					}
				}
			}
		}
		return results;
	}

	public static IFile[] findFilesForLocation(IPath location) {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();
	
		List<IPath> list = allPathsForLocation(location);
		ArrayList<IResource> result = new ArrayList<IResource>(list.size());
		for (IPath p : list) {
			IResource r = root.findMember(p);
			if ((r != null) && (r.getType() == IResource.FILE)) {
				result.add(r);
			}
		}
		IFile[] files = result.toArray(new IFile[result.size()]);
		return files;
	
	}

	public static IFile[] findFilesForLocation(String path) {
		IPath fpath = new Path(path);
		return findFilesForLocation(fpath);
	}

	public static IFile findFileForLocation(Path path) {
		IFile file = null;
		IFile[] files = findFilesForLocation(path);
		if ((files == null) || (files.length == 0)){
			try {
				return ExternalPrologFilesProjectUtils.linkFile(path);
			} catch (CoreException e) {
				throw new IllegalArgumentException("Not in Workspace: " + path);
			}
		}
		if (files.length > 1) {
			Debug.warning("Mapping into workspace is ambiguous:" + path);
			Debug.warning("i will use the first match found: " + files[0]);
		}
		file = files[0];
		if (!file.isAccessible())
			throw new RuntimeException("The specified file \"" + file
					+ "\" is not accessible.");
		return file;
	}

	public static IFile findFileForLocation(String path) throws IOException {
		return findFileForLocation(new Path(path));
	}
	
}
