

:- use_module(pdt_builder_analyzer('metafile_referencer.pl')).
:- use_module(pdt_builder_analyzer(pdt_xref_experimental)).
:- use_module(pdt_builder_analyzer(properties)).

% Search, outline, etc. for SWI-Prolog:
:- use_module(pdt_prolog_library(utils4modules)).
:- use_module(pdt_editor_highlighting).
:- use_module(lib_pdt_console_pl(pdtplugin)).
:- consult(smell_api).
	 
% Search, outline, etc. for Logtalk:
:- consult(lib_pdt_console_pl('lgt/loader.pl')).

:- use_module(lib_pdt_console_pl(pdt_console_server)).

% After loading this file call:                 
% ?- pdt_current_console_server(Port).
% (e.g. in "PrologConsoleView.java")