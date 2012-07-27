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


:- module(pdt_editor_breakpoints,[
	pdt_set_breakpoint/4,			% used in PDTBreakpointHandler.java
	pdt_breakpoint_properties/4
]).

:- use_module(library(prolog_breakpoints)).
:- use_module(library(debug)).

pdt_set_breakpoint(File, Line, Offset, Id) :-
    debug(pdt_breakpoints, 'before existing breakpoint', []),
    not(existing_breakpoint(File, Offset)),
    debug(pdt_breakpoints, 'after existing breakpoint', []),
    debug(pdt_breakpoints, 'before set_breakpoint', []),
    catch(set_breakpoint(File, Line, Offset, Id),_,fail),
    debug(pdt_breakpoints, 'after set_breakpoint', []).
    
existing_breakpoint(File, Offset) :-   
    breakpoint_property(ExistingId, file(File)),
    breakpoint_property(ExistingId, character_range(StartPos, Length)),
    EndPos is StartPos + Length,
    Offset >= StartPos,
    Offset < EndPos.
    
pdt_breakpoint_properties(Id, File, Line, Offset) :-
    breakpoint_property(Id, file(File)),
    breakpoint_property(Id, line_count(Line)),
    breakpoint_property(Id, character_range(Offset, _)).

:- multifile(user:message_hook/3).
:- dynamic(user:message_hook/3).

user:message_hook(breakpoint(set, Id), _Kind, _Lines) :-
    catch(pif_observe:pif_notify(add_breakpoint,Id),_,true), fail.
    
user:message_hook(breakpoint(delete, Id), _Kind, _Lines) :-
    catch(pif_observe:pif_notify(remove_breakpoint,Id),_,true), fail.


