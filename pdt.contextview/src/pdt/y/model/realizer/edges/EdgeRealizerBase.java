/*****************************************************************************
 * This file is part of the Prolog Development Tool (PDT)
 * 
 * Author: Andreas Becker, Ilshat Aliev
 * WWW: http://sewiki.iai.uni-bonn.de/research/pdt/start
 * Mail: pdt@lists.iai.uni-bonn.de
 * Copyright (C): 2013, CS Dept. III, University of Bonn
 * 
 * All rights reserved. This program is  made available under the terms
 * of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 * 
 ****************************************************************************/

package pdt.y.model.realizer.edges;

import y.view.EdgeRealizer;
import y.view.GenericEdgeRealizer;

public class EdgeRealizerBase extends GenericEdgeRealizer {

	public EdgeRealizerBase() {
	}
	
	public EdgeRealizerBase(EdgeRealizer realizer) {
		super(realizer);
	}

	public String getInfoText() {
		return "";
	}
}
