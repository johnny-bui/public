%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
% This file is part of the Prolog Development Tool (PDT) for Eclipse
% http://roots.iai.uni-bonn.de/research/pdt
%
% Authors: G�nter Kniesel, Paulo Moura (May 2011)
%          partly based on PDT code by Tobias Rho
%
% All rights reserved. This program is  made available under the terms
% of the Eclipse Public License v1.0 which accompanies this distribution,
% and is available at http://www.eclipse.org/legal/epl-v10.html
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

% Predicates called by the Java part of the PDT outline and search facilities

:- ensure_loaded(pdt_prolog_library(general)).

:- set_prolog_flag(color_term, false).

:- object(logtalk_adapter).

:- public([
	loaded_by/4,	% ?SourceFile, ?ParentSourceFile, -Line, -Directive
	find_reference_to/13, %+Functor,+Arity,DefFile, DefModule,+ExactMatch,RefModule,RefName,RefArity,RefFile,Position,NthClause,Kind,?PropertyList
	find_entity_reference/8,
	find_entity_definition/5,
	find_definitions_categorized/9, % (Term, ExactMatch, Entity, Functor, Arity, DeclOrDef, FullPath, Line, Properties)
	find_definitions_categorized/12, % (EnclFile,Name,Arity,ReferencedModule,Visibility, DefiningModule, File,Line)
	find_primary_definition_visible_in/8, % (EnclFile,ClickedLine,TermString,Name,Arity,ReferencedModule,MainFile,FirstLine)#
	find_definition_contained_in/10,
	get_pred/7,
	find_pred/8,
	predicates_with_property/3,
	manual_entry/3, % still in use, but probably broken, see predicat_manual_entry
	activate_warning_and_error_tracing/0,
	deactivate_warning_and_error_tracing/0,
	errors_and_warnings/4
]).

:- uses(list, [
	length/2, member/2, memberchk/2, selectchk/3
]).
:- uses(numberlist, [
	sum/2
]).
:- uses(meta, [
	map/3::maplist/3
]).
:- uses(utils4entities, [
	source_file_entity/3, entity/1, entity_property/3
]).

%:- use_module(library(pldoc/doc_library)).
%:- use_module(library(explain)).
%:- use_module(library(help)).
%:- use_module(library(make)).
%:- use_module(library(memfile)).
%:- use_module(library('pldoc')).
%:- use_module(library('pldoc/doc_html'), []).
%:- use_module(library('http/html_write'), []).

%:- use_module(pdt_builder_analyzer('metafile_referencer.pl')).

%:- use_module(pdt_prolog_library(utils4modules)).
%:- use_module(pdt_prolog_library(pdt_xref_experimental)).


/* For Load File Dependency Graph: */
loaded_by(LoadedFile, LoadingFile, -1, (initialization)) :-
	logtalk::loaded_file_property(LoadedFile, parent(LoadingFile)).


%% find_reference_to(+Functor,+Arity,DefFile, DefModule,+ExactMatch,RefModule,RefName,RefArity,RefFile,Position,NthClause,Kind,?PropertyList)
find_reference_to(SearchFunctor, Arity, FromFile, From, ExactMatch, Entity, CallerFunctor, CallerArity, EntityFile,Line,_Nth,_Call,[clause_line(Line), called(Called)|PropertyList]) :-
	(	ExactMatch == true
	->	SearchFunctor = Functor
	;	true
	),
	(	entity_property(Entity, _, calls(From::Functor/Arity, Properties)),
		Kind = logtalk
	;	entity_property(Entity, _, calls(From:Functor/Arity, Properties)),
		Kind = prolog
	;	entity_property(From, _, calls(Functor/Arity, Properties)),
		Kind = logtalk
	),
	(	ExactMatch \== true
	->	once(sub_atom(Functor, _, _, _, SearchFunctor))
	;	true
	),	once(member(caller(CallerFunctor/CallerArity), Properties)),
	once(member(line_count(Line), Properties)),
	entity_property(Entity, _, file(EntityBase, EntityDirectory)),
	atom_concat(EntityDirectory, EntityBase, EntityFile),
	(	Kind == logtalk ->
		entity_property(From, _, file(FromBase, FromDirectory)),
		atom_concat(FromDirectory, FromBase, FromFile)
	;	% Kind = prolog
		module_property(From, file(FromFile))
	),
	(	member(as(AliasFunctor/Arity), Properties),
		Functor \== AliasFunctor
	->	format(atom(AliasAtom), '~w aliased to ~w', [Functor/Arity, AliasFunctor/Arity]),
		PropertyList = [is_alias(AliasAtom)]
	;	PropertyList = []
	),
	format(atom(Called), '~w::~w/~w', [From, Functor, Arity]).

find_entity_reference(Entity, ExactMatch, File, Line, ReferencingModule, RefName, RefArity, PropertyList) :-
	search_entity_name(Entity, ExactMatch, SearchEntity),
	find_reference_to(_, _, _, SearchEntity, ExactMatch, ReferencingModule, RefName, RefArity, File, Line, _, _, PropertyList).

search_entity_name(Entity, true, Entity) :- !.
search_entity_name(EntityPart, false, EntityName) :-
	entity(Entity),
	functor(Entity, EntityName, _),
	once(sub_atom(EntityName, _, _, _, EntityPart)).

find_entity_definition(SearchString, ExactMatch, File, Line, Entity) :-
	entity(Entity),
	functor(Entity, Functor, _),
	(	ExactMatch == true
	->	SearchString = Functor
	;	once(sub_atom(Functor, _, _, _, SearchString))
	),
	entity_property(Entity, _, file(Base, Directory)),
	atom_concat(Directory, Base, File),
	entity_property(Entity, _, lines(Line, _)).

find_entity_definition(SearchString, ExactMatch, File, Line, Entity) :-
	current_logtalk_flag(modules, supported),
	current_module(Entity),
	(	ExactMatch == true
	->	SearchString = Entity
	;	once(sub_atom(Entity, _, _, _, SearchString))
	),
	module_property(Entity, file(File)),
	module_property(Entity, line_count(Line)).


find_definitions_categorized(Term, ExactMatch, Entity, Functor, Arity, DeclOrDef, FullPath, Line, Properties) :-
	split_search_pi(Term, Entity, SearchFunctor, SearchArity),
	(	ExactMatch == true ->
		any_predicate_declaration_or_definition(SearchFunctor, SearchArity, Entity, Kind, From, DeclOrDef, Properties),
		Functor = SearchFunctor,
		Arity = SearchArity
	;	any_predicate_declaration_or_definition(Functor, SearchArity, Entity, Kind, From, DeclOrDef, Properties),
		once(sub_atom(Functor, _, _, _, SearchFunctor)),
		Arity = SearchArity
	),
	entity_property(From, _, file(File, Directory)),
	atom_concat(Directory, File, FullPath),
	memberchk(line_count(Line), Properties).

split_search_pi(Module:Functor/Arity, Module, Functor, Arity) :- !.
split_search_pi(       Functor/Arity, _     , Functor, Arity) :- !.
split_search_pi(Module:Functor      , Module, Functor, _    ) :- !.
split_search_pi(       Functor      , _     , Functor, _    ) :- atom(Functor).

any_predicate_declaration_or_definition(Functor, Arity, Entity, Kind, Entity, declaration, Properties) :-
	entity_property(Entity, Kind, declares(Functor/Arity, Properties)).
any_predicate_declaration_or_definition(Functor, Arity, Entity, Kind, From, definition, Properties) :-
	(	entity_property(Entity, Kind, defines(Functor/Arity, Properties0)),
		From = Entity
	;	entity_property(Entity, Kind, includes(Functor/Arity, From, Properties0))
	),
	% we add a scope property to ensure that the correct visibility icon is used when showing search results
	functor(Predicate, Functor, Arity),
	(	catch(decode(Predicate, Entity, _, _, _, _, DeclarationProperties, declaration, _),_,fail) ->
		% found the scope declaration
		(	member((public), DeclarationProperties) ->
			Properties = [(public)| Properties0]
		;	member(protected, DeclarationProperties) ->
			Properties = [protected| Properties0]
		;	Properties = [private| Properties0]
		)
	;	% no scope declaration; local predicate
		Properties = [local| Properties0]
	).


        /***********************************************************************
         * Find Definitions and Declarations and categorize them by visibility *
         * --------------------------------------------------------------------*
         * for "Find All Declarations" (Ctrl+G) action                         *
         ***********************************************************************/

find_definitions_categorized(EnclFile, ClickedLine, Term, Functor, Arity, This, DeclOrDef, Entity, FullPath, Line, Properties, Visibility) :-
	source_file_entity(EnclFile, ClickedLine, This),
	search_term_to_predicate_indicator(Term, Functor/Arity),
	findall(
		item(Term, Functor, Arity, This, DeclOrDef, Entity, FullPath, Line, Properties, Visibility),
		find_definitions_categorized0(Term, Functor, Arity, This, DeclOrDef, Entity, FullPath, Line, Properties, Visibility),
		Items0
	),
	% remove invisible items that are found as visible items
	filter_categorized_definitions(Items0, Items),
	member(item(Term, Functor, Arity, This, DeclOrDef, Entity, FullPath, Line, Properties, Visibility0), Items),
	% construct the actual text label that will be used by the Java side when showing the search results
	visibility_text(DeclOrDef, Visibility0, Visibility).


find_definitions_categorized0(Term, _Functor, _Arity, This, DeclOrDef, Entity, FullPath, Line, Properties, Visibility) :-
	decode(Term, This, Entity, _Kind, _Template, Location, Properties, DeclOrDef, Visibility),
	Location = [Directory, File, [Line]],
	atom_concat(Directory, File, FullPath).

find_definitions_categorized0(_Term, Functor, Arity, This, DeclOrDef, Entity, FullPath, Line, [invisible| Properties], Visibility) :-
	entity(Entity),
	Entity \= This,
	(	entity_property(Entity, _Kind, declares(Functor/Arity, Properties)),
		DeclOrDef = declaration,
		Visibility = invisible
	;	entity_property(Entity, _Kind, defines(Functor/Arity, Properties)),
		DeclOrDef = definition,
		Visibility = invisible
	;	entity_property(Entity, _Kind, includes(Functor/Arity, _, Properties)),
		DeclOrDef = definition,
		Visibility = invisible
	),
	entity_property(Entity, _Kind, file(File, Directory)),
	memberchk(line_count(Line), Properties),
	atom_concat(Directory, File, FullPath).


filter_categorized_definitions([], []).
filter_categorized_definitions([Item0| Items0], Items) :-
	Item0 = item(Term, Functor, Arity, This, _, Entity, _, Line, _, Visibility),
	Visibility \= invisible,
	selectchk(item(Term, Functor, Arity, This, _, Entity, _, Line, _, invisible), Items0, Items1),
	!,
	filter_categorized_definitions([Item0| Items1], Items).
filter_categorized_definitions([Item0| Items0], [Item0| Items]) :-
	filter_categorized_definitions(Items0, Items).


search_term_to_predicate_indicator(_::Term, Functor/Arity) :- !, functor(Term, Functor, Arity).
search_term_to_predicate_indicator(::Term, Functor/Arity) :- !, functor(Term, Functor, Arity).
search_term_to_predicate_indicator(^^Term, Functor/Arity) :- !, functor(Term, Functor, Arity).
search_term_to_predicate_indicator(:Term, Functor/Arity) :- !, functor(Term, Functor, Arity).
search_term_to_predicate_indicator(_<<Term, Functor/Arity) :- !, functor(Term, Functor, Arity).
search_term_to_predicate_indicator(_:Term, Functor/Arity) :- !, functor(Term, Functor, Arity).
search_term_to_predicate_indicator(Term, Functor/Arity) :- functor(Term, Functor, Arity).


visibility_text(declaration, local,		'Local' ) :- !.
visibility_text(declaration, super,   	'Inherited' ) :- !.
visibility_text(declaration, sub, 		'Descendant') :- !.
visibility_text(declaration, invisible,	'Invisible') :- !.

visibility_text(definition, local,		'Local' ) :- !.
visibility_text(definition, super,   	'Inherited' ) :- !.
visibility_text(definition, sub, 		'Descendant') :- !.
visibility_text(definition, invisible,	'Invisible') :- !.




        /***********************************************************************
         * Find Primary Definition                                             *
         * --------------------------------------------------------------------*
         * for "Open Primary Declaration" (F3) action                          *
         ***********************************************************************/

%% find_primary_definition_visible_in(+EnclFile,+ClickedLine,+Name,+Arity,?ReferencedModule,?MainFile,?FirstLine)
%
% Find first line of first clause in the *primary* file defining the predicate Name/Arity
% visible in ReferencedModule. In case of multifile predicates, the primary file is either
% the file whose module is the DefiningModule or otherwise (this case only occurs
% for "magic" system modules, (e.g. 'system')) the file containing most clauses.
%
% Used for the open declaration action in
% pdt/src/org/cs3/pdt/internal/actions/FindPredicateActionDelegate.java

find_primary_definition_visible_in(EnclFile, ClickedLine, Term, Functor, Arity, _, FullPath, Line) :-
	source_file_entity(EnclFile, ClickedLine, This),
	search_term_to_predicate_indicator(Term, Functor/Arity),
	(	decode(Term, This, Entity, _Kind, _Template, Location, Properties, definition, Visibility)
	;	decode(Term, This, Entity, _Kind, _Template, Location, Properties, declaration, Visibility)
	),
	Visibility \== invisible,
	Location = [Directory, File, [Line]],
	atom_concat(Directory, File, FullPath),
	memberchk(line_count(Line), Properties),
	Line > 0,	% if no definition, try to find the declaration
	!.

% Work regardelessly whether the user selected the entire consult/use_module
% statement or just the file spec. Does NOT work if he only selected a file
% name within an alias but not the complete alias.
%extract_file_spec(consult(FileSpec),FileSpec) :- !.
%extract_file_spec(use_module(FileSpec),FileSpec) :- !.
%extract_file_spec(ensure_loaded(FileSpec),FileSpec) :- !.
%extract_file_spec(Term,Term).
%
%find_definition_visible_in(EnclFile,_Term,Name,Arity,ReferencedModule,DefiningModule,Locations) :-
%	module_of_file(EnclFile,FileModule),
%	(  atom(ReferencedModule)
%	-> true                            % Explicit module reference
%	;  ReferencedModule = FileModule   % Implicit module reference
%	),
%	(  defined_in_module(ReferencedModule,Name,Arity,DefiningModule)
%	-> defined_in_files(DefiningModule,Name,Arity,Locations)
%	;  ( declared_in_module(ReferencedModule,Name,Arity,DeclaringModule),
%	     defined_in_files(DeclaringModule,Name,Arity,Locations)
%	   )
%	).
%
%primary_location(Locations,DefiningModule,File,FirstLine) :-
%	member(File-Lines,Locations),
%	module_of_file(File,DefiningModule),
%	!,
%	Lines = [FirstLine|_].
%primary_location(Locations,_,File,FirstLine) :-
%	findall(
%		NrOfClauses-File-FirstLine,
%		(member(File-Lines,Locations), length(Lines,NrOfClauses), Lines=[FirstLine|_]),
%		All
%		),
%	sort(All, Sorted),
%	Sorted = [ NrOfClauses-File-FirstLine |_ ].


        /***********************************************************************
         * Find Primary Definition                                             *
         * --------------------------------------------------------------------*
         * for "Open Primary Declaration" (F3) action                          *
         ***********************************************************************/

% TODO: This is meanwhile subsumed by other predicates. Integrate!

%% find_definition_contained_in(+File, -Name,-Arity,-Line,-Dyn,-Mul,-Exported) is nondet.
%
% Looks up the starting line of each clause of each
% predicate Name/Arity defined in File. The boolean
% properties Dyn(amic), Mul(tifile) and Exported are
% unified with 1 or 0.
%
% Called from PrologOutlineInformationControl.java

find_definition_contained_in(FullPath, Options, Entity, EntityLine, Kind, Functor, Arity, SearchCategory, Line, Properties) :-
	once((	split_file_path:split_file_path(FullPath, Directory, File, _, lgt)
		;	split_file_path:split_file_path(FullPath, Directory, File, _, logtalk)
	)),
	(	current_logtalk_flag(version, version(3, _, _)) ->
		logtalk::loaded_file(FullPath)
	;	logtalk::loaded_file(File, Directory)
	),
	% if this fails we should alert the user that the file is not loaded!
	entity_property(Entity, Kind, file(File, Directory)),
	entity_property(Entity, Kind, lines(EntityLine, _)),
	(	% entity declarations
		entity_property(Entity, Kind, declares(Functor/Arity, Properties0)),
		% we add a number_of_clauses/1 declaration property just to simplify coding in the Java side
		(	entity_property(Entity, Kind, defines(Functor/Arity, DefinitionProperties)),
			DefinitionProperties \== [] ->
			memberchk(number_of_clauses(N0), DefinitionProperties)
		;	N0 = 0
		),
		findall(
			N1,
			(entity_property(Entity, Kind, includes(Functor/Arity, _, IncludesProperties)),
			 memberchk(number_of_clauses(N1), IncludesProperties)),
			Ns),
		sum([N0| Ns], N),
		Properties = [number_of_clauses(N)| Properties0],
		SearchCategory = declaration
	;	% entity definitions
		entity_property(Entity, Kind, defines(Functor/Arity, Properties0)),
		% we add a scope/0 property just to simplify coding in the Java side
		functor(Predicate, Functor, Arity),
		(	catch(decode(Predicate, Entity, _, _, _, _, DeclarationProperties, declaration, _),_,fail) ->
			% found the scope declaration
			(	member((public), DeclarationProperties) ->
				Properties = [(public)| Properties0]
			;	member(protected, DeclarationProperties) ->
				Properties = [protected| Properties0]
			;	Properties = [private| Properties0]
			)
		;	% no scope declaration; local predicate
			Properties = [local| Properties0]
		),
		SearchCategory = definition
	;	% entity multifile definitions
		memberchk(multifile(true), Options),
		entity_property(Entity, Kind, includes(Functor/Arity, From, Properties0)),
		entity_property(From, _, file(FromFile, FromDirectory)),
		atom_concat(FromDirectory, FromFile, FromPath),
		% we add a from/1 property just to simplify coding in the Java side
		Properties = [(multifile), from(From), defining_file(FromPath)| Properties0],
		SearchCategory = definition %multifile
	;	% entity multifile definitions
		entity_property(Entity, Kind, provides(Functor/Arity, For, Properties0)),
		% we add a for/1 + defining_file/1 properties just to simplify coding in the Java side
		entity_property(Entity, Kind, file(DefiningFile, DefiningDirectory)),
		atom_concat(DefiningDirectory, DefiningFile, DefiningFullPath),
		Properties = [(multifile), for(For), defining_file(DefiningFullPath)| Properties0],
		SearchCategory = definition %multifile
	),
	memberchk(line_count(Line), Properties).


               /***********************************************
                * FIND VISIBLE PREDICATE (FOR AUTOCOMPLETION) *
                ***********************************************/

%% find_completion(?EnclosingFile, ?LineInFile, +Prefix, -Kind, -Entity, -Name, -Arity, -Visibility, -IsBuiltin, -ArgNames, -DocKind, -Doc) is nondet.
% 
:- public(find_completion/12).
find_completion(SearchEntity::PredicatePrefix, EnclosingFile, _, predicate, DefiningOrDeclaringEntity, Name, Arity, Visibility, false, _, nodoc, _) :-
	var(EnclosingFile),
	!,
	(	var(SearchEntity)
	->	true
	;	Entity = SearchEntity
	),
	current_object(Entity),
	Entity::current_predicate(Name/Arity),
	atom_concat(PredicatePrefix, _, Name),
	functor(Head, Name, Arity),
	Entity::predicate_property(Head, scope(Visibility)),
	(	var(SearchEntity)
	->	DefiningOrDeclaringEntity = Entity
	;	(	Entity::predicate_property(Head, defined_in(DefiningOrDeclaringEntity))
		->	true
		;	Entity = DefiningOrDeclaringEntity
		)
	).

find_completion(Prefix, _, _, module, _, Entity, _, _, _, _, _, _) :-
	atomic(Prefix),
	entity(Entity),
	(	atomic(Entity)
	->	Name = Entity
	;	functor(Entity, Name, _)
	),
	atom_concat(Prefix, _, Name).

find_completion(Prefix, _EnclosingFile, _Line, predicate, '', Name, Arity, private, true, _, lgt_help_file, FileName) :-
	atomic(Prefix),
	help::completion(Prefix, Name/Arity-FileName).


               /*************************************
                * PROLOG ERROR MESSAGE HOOK         *
                *************************************/

%:- dynamic(traced_messages/3).
%:- dynamic(warning_and_error_tracing/0).
%
%activate_warning_and_error_tracing :-
%	assertz(warning_and_error_tracing).
%
%deactivate_warning_and_error_tracing:-
%	retractall(warning_and_error_tracing),
%	retractall(traced_messages(_,_,_)).
%
%
%%% message_hook(+Term, +Level,+ Lines) is det.
%%
%% intercept prolog messages to collect term positions and
%% error/warning messages in traced_messages/3
%%
%% @author trho
%%
%:- multifile(user::message_hook/3).
%
%user::message_hook(_Term, Level, Lines) :-
%	warning_and_error_tracing,
%	pdt_term_position(StartLine),
%	assertz(traced_messages(Level, StartLine, Lines)),
%	fail.
%
%%% errors_and_warnings(Level,Line,Length,Message) is nondet.
%%
%errors_and_warnings(Level,Line,0,Message) :-
%	traced_messages(Level, Line,Lines),
%%	traced_messages(error(syntax_error(_Message), file(_File, StartLine, Length, _)), Level,Lines),
%	new_memory_file(Handle),
%	open_memory_file(Handle, write, Stream),
%	print_message_lines(Stream,'',Lines),
%	close(Stream),
%	memory_file_to_atom(Handle,Message),
%	free_memory_file(Handle).
%
%pdt_term_position(StartLine) :-
%	logtalk_load_context(term_position, StartLine-_EndLine).
%pdt_term_position(StartLine) :-
%	prolog_load_context(term_position, '$stream_position'(_,StartLine,_,_,_)).


               /*****************************************
                * PREDICATE PROPERTIES FOR HIGHLIGHTING *
                *****************************************/


%% predicates_with_property(+Property,-Predicates) is det.
%
% Look up all Predicates with property Property, including atomic
% properties (e.g. dynamic, built_in) AND properties that are
% functions (e.g. meta_predicate(Head)).

% GK, 5. April 2011: Extended the implementation to deal with unary
% functors. The combination of findall and setof is essentail for
% this added functionality. The findall/3 call finds all results
%   (even if the arguments are free variables -- note that setof/3
%   would return results one by one in such a case, not a full list!).
% Then the setof/3 call eliminates the duplicates from the results
% of findall/3.
% DO NOT CHANGE, unless you consider yourself a Prolog expert.

% Property = undefined | built_in | dynamic | transparent | meta_predicate(_)

% Look for undefined predicates only in the local context
% (of the file whose editor has just been opened):
%predicates_with_property(undefined, FileName, Predicates) :-
%    !,
%    module_of_file(FileName,Module),
%	findall(Name, predicate_name_with_property_(Module,Name,undefined), AllPredicateNames),
%	make_duplicate_free_string(AllPredicateNames,Predicates).

predicates_with_property(Property, _, Predicates) :-
	findall(Name, predicate_name_with_property_(_,Name,Property), AllPredicateNames),
	make_duplicate_free_string(AllPredicateNames,Predicates).



predicate_name_with_property_(Module,Name,Property) :-
	current_module(Module),
	current_predicate(Module:Name/Arity),
	Name \= '[]',
	functor(Head,Name,Arity),
	user::predicate_property(Module:Head,Property).

make_duplicate_free_string(AllPredicateNames,Predicates) :-
	setof(Name, member(Name,AllPredicateNames), UniqueNames),
	format(string(S),'~w',[UniqueNames]),
	{string_to_atom(S,Predicates)}.



%% predicates_with_unary_property(+Property,?Predicates,?PropertyParams) is det.
%
% Look up all Predicates with the unary property Property, e.g. meta_predicate(Head)
% The element at position i in Predicates is the name of a predicate that has
% the property Property with the parameter at position i in PropertyParams.
%
% Author: GK, 5 April 2011
% TODO: Integrate into the editor the ability to show the params as tool tips,
% e.g. show the metaargument specifications of a metapredicate on mouse over.
predicates_with_unary_property(Property,Predicates,PropertyArguments) :-
	setof((Name,Arg),
	   predicate_name_with_unary_property_(Name,Property,Arg),
	   PredArgList),
	findall(Pred, member((Pred,_),PredArgList), AllProps),
	findall(Arg,  member((_,Arg), PredArgList), AllArgs),
	format(string(S1),'~w',[AllProps]),
	format(string(S2),'~w',[AllArgs]),
	{string_to_atom(S1,Predicates)},
	{string_to_atom(S2,PropertyArguments)}.

% helper
predicate_name_with_unary_property_(Name,Property,Arg) :-
    Property =.. [__F,Arg],
	user::predicate_property(_M:Head,Property),
	functor(Head,Name,_),
	Name \= '[]'.


% decode(Term, This, Entity, Kind, Template, Location, Properties).

:- private(decode/9).
decode(Object::Predicate, _This, Entity, Kind, Template, [Directory, File, [Line]], Properties, DeclOrDef, Visibility) :-
	!,
	nonvar(Object),
	nonvar(Predicate),
	functor(Predicate, Functor, Arity),
	functor(Template, Functor, Arity),
	Object::current_predicate(Functor/Arity),
	(	% declaration
		Object::predicate_property(Template, declared_in(Entity)),
		entity_property(Entity, _, declares(Functor/Arity, Properties)),
		DeclOrDef = declaration,
		Visibility = invisible
	;	% definition
		Object::predicate_property(Template, defined_in(Primary)),
		(	% local definitions
			Entity = Primary,
			entity_property(Primary, _, defines(Functor/Arity, Properties)),
			DeclOrDef = definition,
			Visibility = invisible
		;	% multifile definitions
			entity_property(Primary, _, includes(Functor/Arity, Entity, Properties)),
			DeclOrDef = definition,
			Visibility = invisible
		)
	;	% local definition
		Entity = This,
		entity_property(This, Kind, defines(Functor/Arity, Properties)),
		DeclOrDef = definition,
		Visibility = local
	),
	entity_property(Entity, Kind, file(File, Directory)),
	memberchk(line_count(Line), Properties).

decode(::Predicate, This, Entity, Kind, Template, [Directory, File, [Line]], Properties, DeclOrDef, Visibility) :-
	!,
	nonvar(Predicate),
	functor(Predicate, Functor, Arity),
	functor(Template, Functor, Arity),
	(	% declaration
		(	current_object(This) ->
			(	\+ instantiates_class(This, _),
				\+ specializes_class(This, _) ->
				This<<predicate_property(Template, declared_in(DeclarationEntity))
			;	create_object(Obj, [instantiates(This)], [], []),
				Obj<<predicate_property(Template, declared_in(DeclarationEntity)),
				abolish_object(Obj)
			)
		;	%current_category(This) ->
			create_object(Obj, [imports(This)], [], []),
			Obj<<predicate_property(Template, declared_in(DeclarationEntity)),
			abolish_object(Obj)
		),
		entity_property(DeclarationEntity, _, declares(Functor/Arity, Properties)),
		Entity = DeclarationEntity,
		DeclOrDef = declaration,
		Visibility = super
	;	% definition
		(	current_object(This) ->
			(	\+ instantiates_class(This, _),
				\+ specializes_class(This, _) ->
				This<<predicate_property(Template, declared_in(DeclarationEntity)),
				This<<predicate_property(Template, defined_in(Primary))
			;	create_object(Obj, [instantiates(This)], [], []),
				Obj<<predicate_property(Template, declared_in(DeclarationEntity)),
				Obj<<predicate_property(Template, defined_in(Primary)),
				abolish_object(Obj)
			)
		;	%current_category(This) ->
			create_object(Obj, [imports(This)], [], []),
			Obj<<predicate_property(Template, declared_in(DeclarationEntity)),
			Obj<<predicate_property(Template, defined_in(Primary)),
			abolish_object(Obj)
		),
		entity_property(Primary, _, defines(Functor/Arity, Properties0)),
		entity_property(DeclarationEntity, _, declares(Functor/Arity, DeclarationProperties)),
		(	member((public), DeclarationProperties) ->
			Properties = [(public)| Properties0]
		;	member(protected, DeclarationProperties) ->
			Properties = [protected| Properties0]
		;	Properties = [private| Properties0]
		),
		Entity = Primary,
		DeclOrDef = definition,
		Visibility = sub
	;	% multifile definitions
		entity_property(Primary, _, includes(Functor/Arity, Entity, Properties)),
		DeclOrDef = definition,
		Visibility = local
	;	% local definition
		Entity = This,
		entity_property(This, Kind, defines(Functor/Arity, Properties)),
		DeclOrDef = definition,
		Visibility = local
	),
	entity_property(Entity, Kind, file(File, Directory)),
	memberchk(line_count(Line), Properties).

decode(:Predicate, This, Entity, Kind, Template, [Directory, File, [Line]], Properties, DeclOrDef, Visibility) :-
	nonvar(Predicate),
	functor(Predicate, Functor, Arity),
	functor(Template, Functor, Arity),
	(	% declaration
		(	\+ instantiates_class(This, _),
			\+ specializes_class(This, _) ->
			This<<predicate_property(Template, declared_in(Entity))
		;	create_object(Obj, [instantiates(This)], [], []),
			Obj<<predicate_property(Template, declared_in(Entity)),
			abolish_object(Obj)
		),
		entity_property(Entity, Kind, declares(Functor/Arity, Properties)),
		DeclOrDef = declaration
	;	% definition
		findall(Category, imports_category(This, Category), Categories),
		create_object(Obj, [imports(Categories)], [], []),
		Obj<<predicate_property(Template, defined_in(Primary)),
		abolish_object(Obj),
		(	% local definitions
			Entity = Primary,
			entity_property(Primary, Kind, defines(Functor/Arity, Properties)),
			DeclOrDef = definition,
			Visibility = local
		;	% multifile definitions
			entity_property(Primary, Kind, includes(Functor/Arity, Entity, Properties)),
			DeclOrDef = definition,
			Visibility = local
		)
	;	% local definition
		Entity = This,
		entity_property(This, Kind, defines(Functor/Arity, Properties)),
		DeclOrDef = definition,
		Visibility = local
	),
	entity_property(Entity, Kind, file(File, Directory)),
	memberchk(line_count(Line), Properties).

decode(^^Predicate, This, Entity, Kind, Template, [Directory, File, [Line]], Properties, DeclOrDef, Visibility) :-
	!,
	nonvar(Predicate),
	functor(Predicate, Functor, Arity),
	functor(Template, Functor, Arity),
	(	% declaration
		(	current_object(This) ->
			(	\+ instantiates_class(This, _),
				\+ specializes_class(This, _) ->
				This<<predicate_property(Template, declared_in(Entity))
			;	create_object(Obj, [instantiates(This)], [], []),
				Obj<<predicate_property(Template, declared_in(Entity)),
				abolish_object(Obj)
			)
		;	%current_category(This) ->
			create_object(Obj, [imports(This)], [], []),
			Obj<<predicate_property(Template, declared_in(Entity)),
			abolish_object(Obj)
		),
		entity_property(Entity, _, declares(Functor/Arity, Properties)),
		DeclOrDef = declaration,
		Visibility = super
	;	% inherited
		(	current_object(This) ->
			(	\+ instantiates_class(This, _),
				\+ specializes_class(This, _) ->
				This<<predicate_property(Template, declared_in(DeclarationEntity)),
				This<<predicate_property(Template, redefined_from(Entity))
			;	create_object(Obj, [instantiates(This)], [], []),
				Obj<<predicate_property(Template, declared_in(DeclarationEntity)),
				Obj<<predicate_property(Template, redefined_from(Entity)),
				abolish_object(Obj)
			)
		;	%current_category(This) ->
			create_object(Obj, [imports(This)], [], []),
			Obj<<predicate_property(Template, declared_in(DeclarationEntity)),
			Obj<<predicate_property(Template, redefined_from(Entity)),
			abolish_object(Obj)
		),
		entity_property(Entity, _, defines(Functor/Arity, Properties0)),
		entity_property(DeclarationEntity, _, declares(Functor/Arity, DeclarationProperties)),
		(	member((public), DeclarationProperties) ->
			Properties = [(public)| Properties0]
		;	member(protected, DeclarationProperties) ->
			Properties = [protected| Properties0]
		;	Properties = [private| Properties0]
		),
		DeclOrDef = definition,
		Visibility = super
	),
	entity_property(Entity, Kind, file(File, Directory)),
	memberchk(line_count(Line), Properties).

decode(Predicate, This, Entity, Kind, Template, [Directory, File, [Line]], Properties, DeclOrDef, Visibility) :-
	nonvar(Predicate),
	functor(Predicate, Functor, Arity),
	(	entity_property(This, _, calls(Object::Functor/Arity, _)) ->
		OriginalFunctor = Functor
	;	entity_property(This, _, calls(Object::OriginalFunctor/Arity, Properties)),
		member(as(Functor/Arity), Properties)
	),
	!,
	functor(Template, OriginalFunctor, Arity),
	decode(Object::Template, This, Entity, Kind, Template, [Directory, File, [Line]], Properties, DeclOrDef, Visibility).

decode(Predicate, This, Entity, Kind, Template, [Directory, File, [Line]], Properties, DeclOrDef, Visibility) :-
	nonvar(Predicate),
	functor(Predicate, Functor, Arity),
	(	entity_property(This, _, calls(Module:Functor/Arity, _)) ->
		OriginalFunctor = Functor
	;	entity_property(This, _, calls(Module:OriginalFunctor/Arity, Properties)),
		member(as(Functor/Arity), Properties)
	),
	!,
	functor(Template, OriginalFunctor, Arity),
	decode(Module:Template, This, Entity, Kind, Template, [Directory, File, [Line]], Properties, DeclOrDef, Visibility).

decode(Predicate, This, Entity, Kind, Template, [Directory, File, [Line]], Properties, DeclOrDef, Visibility) :-
	% local predicate
	nonvar(Predicate),
	functor(Predicate, Functor, Arity),
	functor(Template, Functor, Arity),
	(	% declaration
		(	current_object(This) ->
			(	\+ instantiates_class(This, _),
				\+ specializes_class(This, _) ->
				This<<predicate_property(Template, declared_in(Entity))
			;	create_object(Obj, [instantiates(This)], [], []),
				Obj<<predicate_property(Template, declared_in(Entity)),
				abolish_object(Obj)
			)
		;	%current_category(This) ->
			create_object(Obj, [imports(This)], [], []),
			Obj<<predicate_property(Template, declared_in(Entity)),
			abolish_object(Obj)
		),
		entity_property(Entity, Kind, declares(Functor/Arity, Properties)),
		DeclOrDef = declaration,
		(	\+ Entity \= This -> 
			Visibility = local
		;	Visibility = super
		)
	;	% definition
		Entity = This,
		entity_property(Entity, _, defines(Functor/Arity, Properties)),
		DeclOrDef = definition,
		Visibility = local
	;	% multifile definitions
		entity_property(This, _, includes(Functor/Arity, Entity, Properties)),
		DeclOrDef = definition,
		Visibility = local
	),
	entity_property(Entity, Kind, file(File, Directory)),
	memberchk(line_count(Line), Properties).

:- end_object.
