:- module(pdt_builder,
	[	pdt_request_target/1, 
		pdt_request_targets/1, 
		pdt_invalidate_target/1,
		pdt_with_targets/1,
		pdt_with_targets/2,		
		pdt_restart_arbiter/0,
		pdt_builder_info/2,
		pdt_builder_info/3,
		pdt_print_builder_info/3,
		pdt_print_builder_info/2,
		pdt_fp_enqueue/2		
	]
).     
  
:- use_module(library(pif_observe2)).
:- use_module(library('pef/pef_base')).
 
/* hooks */
:- dynamic 
	build_hook/1,
	fp_process_hook/1,
	fp_seed_hook/1,
	invalidate_hook/1,
	estimate_hook/3,
	target_file/2,
	target_mutable/2,
	target_mux/2,
	report_progress/1,
	fix_point_target/1.

/* only used for debugging */
:- dynamic touched/1,'$target_event'/2.


:- multifile
	build_hook/1,
	fp_process_hook/1,
	fp_seed_hook/1,	
	invalidate_hook/1, %FIXME: not used anymore.
	estimate_hook/3,
	target_file/2,
	target_mutable/2,
	target_mux/2,
	report_progress/1,
	fix_point_target/1.

/* estimate_hook(Target,Dependency, Weight),
   report_progress(Target)
Used for progress monitoring. 
Clients can request progress monitoring for a particular target. The builder will send progress information
using the pif_observe api. Progress will be reported for a target iff report_progress(Target) succeeds.
Target definition can add clauses to report_progress(Target).

before rebuilding this target, the arbiter will call the report_progress(Target) hook to find out wheter
progress should be reported for this target. If this is the case, it next   calls the estimate_hook to 
get a list of known "sub-problems" of the target. It will automatically filter out the once that are already up-to-date.
For each of the remaining entries, progress will be reported as soon as each entry becomes available. 

*/

/*  '$progress_subproblem'(SubTarget,Target,Weight)
	'$progress_subproblem_inv'(Target,SubTarget)
	'$progress_total'(Target,Sum)
*/
:- dynamic '$progress_subproblem'/3, '$progress_subproblem_inv'/2, '$progress_total'/2.


:- dynamic '$has_lock'/1,'$building'/1, '$fp_building_target'/1,'$fp_running'/0, '$fp_queue'/2.
:- thread_local '$has_lock'/1,'$building'/1, '$fp_building_target'/1,'$fp_running'/0, '$fp_queue'/2.

/* associate state with targets 
These predicates should only be accessed by the arbiter thread.

*/
:- dynamic '$target_state'/2, '$target_depends'/2,'$target_depends_inv'/2,'$thread_waits'/2.
%:- dynamic '$net_depends_cache'/2, '$net_depends_inv_cache'/2.

 
  my_debug(Topic,Msg,Args):-
      append("[~w:~w:~w] ",Msg,Msg2),
      get_time(Stamp),
      stamp_date_time(Stamp,DT,local),
      date_time_value(time,DT,time(H,M,S)),
      debug(Topic,Msg2,[H,M,S|Args]).

:- module_transparent pdt_with_targets/2, pdt_with_targets/1.

%% pdt_with_targets(+Targets,+Goal).
% holds read locks for the specified list of Targets while executing Goal.
%
% IMPORTANT NOTE: This predicate uses call_cleanup/2 to take care of releasing 
% the locks. Bear in mind that as long as there are any choice points left in Goal, 
% the locks are NOT released. 
%


pdt_with_targets(Goal):-
    pdt_with_targets([],Goal).

pdt_with_targets(Ts,Goal):-
    my_debug(builder(debug),"pdt_with_targets(~w, ~w): ~n",[Ts, Goal]),
    pdt_builder:asserta('$has_lock'('$mark'),Ref),
    my_debug(builder(debug),"added mark clause, ref= ~w ~n",[Ref]),    
    call_cleanup(
    	(	pdt_request_targets(Ts),
    		Goal
    	),
    	pdt_builder:release_targets(Ref)
    ).
    
release_targets(Ref):-
    my_debug(builder(debug),"erasing all locks until marker ~w~n",[Ref]),
    clause('$has_lock'(T),_,LockRef),
    (	LockRef==Ref
    ->	my_debug(builder(debug),"~t found marker, erasing it: ~w~n",[Ref]),
    	erase(Ref),
    	!
    ;  	my_debug(builder(debug),"~t found lock ~w, ref=~w, erasing it. ~n",[T,LockRef]),
    	erase(LockRef),
		my_debug(builder(debug),"~t sending release message to arbiter (target: ~w) ~n",[T]),
		thread_self(Me),		
		client_send_message(T,release(Me)),
		fail
    ).
release_targets(Ref):-
    throw(failed(error(release_targets(Ref)))).


spyme.


mutable(Target):-
	'$mutable'(Target).    

% this is called only once per target to infer whether it is mutable.
mutable_X(Target):-
    (	target_mutable(Target,Mutable)
    ->	Mutable==true
    ;   target_file(Target,File),
    	pef_source_path_query([path=Path/*,include_pattern=IP,exclude_pattern=EP*/]),
    	atom_prefix(File,Path)
    ).
    


    



fp_request_target(Target):-
    fp_building_target(Target),
    !,
    client_log(Target,already_building),
     (	building_target(Building),mutable(Target)
    ->	client_send_message(Building,depend(Target))	
    ;	true
    ).
fp_request_target(Target):-    
    '$has_lock'(Target),
    !,
    client_log(Target,already_locked),
    (	building_target(Building),mutable(Target)
    ->	client_send_message(Building,depend(Target))	
    ;	true
    ),    
    my_debug(builder(debug),"target already granted: ~w ~n",[Target]).
fp_request_target(Target):-
    thread_self(Me),
    (	building_target(Building),mutable(Target)
    ->	client_send_message(Building,depend(Target))	    	
	;	true
	),
    
    client_send_message(Target,request(Me)),
	my_debug(builder(debug),"sending request to arbiter:~w.~n",[msg(Target,request(Me))]),   	
	client_get_message(Msg),	
	my_debug(builder(debug),"Thread ~w received message ~w.~n",[Me,Msg]),
    (	Msg==grant(Target)
    ->	asserta('$has_lock'(Target),Ref),
	    my_debug(builder(debug),"added lock for target ~w, ref=~w~n",[Target,Ref]) 		    
    ;	Msg==rebuild(Target)
    ->  fp_build_target(Target)
    ;	Msg=error(Target,E)
    ->	throw(error(target_error(Target,E)))
    ;	Msg=obsolete(Target,Targets)
    ->	throw(error(target_obsolete(Target,Targets)))
    ;	Msg=fail(Target)
    ->	throw(error(target_failed(Target)))
    ;	Msg=fp_busy(Target)
    ->	throw(fp_busy(Target))
    ;	Msg=cycle(Target) %only for debugging. this should be checked on the client side.
    ->	(	building_target(Target)
    	->	throw(wie_komm_ich_denn_hier_hin)
    	;	throw(nicht_meins(Target))
    	)
    ;	throw(error(unexpected_message(Msg,wait_for_read_lock(Target))))
    ).





fp_building_target(T):-'$fp_building_target'(T).

fp_build_target(Target):- 
	assert('$fp_building_target'(Target)),
	client_log(Target,add_to_build),   	       
    pef_clear_record(Target),    
    target_key(TargetName,Target),
    forall(fp_seed_hook(TargetName),true),
    fp_run(Target).

pdt_fp_enqueue(Job,TargetName):-    
    target_key(TargetName,Target),
    (	\+ '$fp_building_target'(Target)
    ->	client_log(Target,ignore_not_building(Job)),
    	true
    ;	fp_job_in_queue(Job)
    -> 	client_log(Target,ignore_already_enqueued(Job))  	
    ;	fp_enqueue(Job,Target)
    ).
fp_enqueue(Job,Target):-        
    assert('$fp_queue'(Job,Target)),
    client_log(Target,add_to_queue(Job)).    

fp_dequeue(Job,Target):-
    retract('$fp_queue'(Job,Target)),
    !,    
    client_log(Target,removed_from_queue(Job)).
fp_job_in_queue(Job):-
    '$fp_queue'(Job,_).


fp_run(Target):-
	'$fp_running',
	!,
	client_log(Target,not_starting__already_running).
fp_run(Target):-
	\+ fp_job_in_queue(_),
	!,
	client_log(Target,not_starting__queue_empty),
	thread_self(Me),
	retract('$fp_building_target'(Target)),
    client_log(Target,removed_from_building),
    client_send_message(Target,mark_clean(Me)).
fp_run(Target):-
    assert('$fp_running'),
    call_cleanup(fp_run__loop(Target),Catcher,fp_run__cleanup(Target,Catcher)),
    fp_request_target(Target).

fp_run__loop(Target):-       
	client_log(Target,starting_fp_iteration),
	repeat,
		fp_dequeue(Job,TargetKey),		
		push_target(TargetKey),
		pef_start_recording(TargetKey),
		call_cleanup(
			forall(fp_process_hook(Job),true),
			(	pop_target,
				pef_stop_recording
			)
		),		
    	fp_done,
    !,
    client_log(Target,finished_fp_iteration).
    
fp_done:-
    \+ '$fp_queue'(_,_).
	
fp_run__cleanup(Target,Catcher):-
    client_log(Target,fp_cleanup(Catcher)),
    retract('$fp_running'),
    thread_self(Me),
    (	Catcher==fail
    ->	Msg=fail
    ;	Catcher=exception(E)
    ->	Msg=error(E)
    ;	Msg=mark_clean(Me)
    ),    
    
    forall(
    	retract('$fp_building_target'(Target2)),
    	(	client_log(Target2,removed_from_building),
    		client_block_add_message(Target2,Msg)
    	)
    ),
    client_block_commit,
    !.
fp_run__cleanup(Catcher):-
	throw(failed(fp_run__cleanup(Catcher))).
    
    
    
    


    
request_target(Target):-
    (	\+ ground(Target)
    ->	spyme,throw(error(target_must_be_ground))
    ;	true
    ), 
    '$has_lock'(Target),
    !,
    (	building_target(Building),mutable(Target)
    ->	client_send_message(Building,depend(Target))	
    ;	true
    ),    
    my_debug(builder(debug),"target already granted: ~w ~n",[Target]).
request_target(Target):-
    thread_self(Me),   	 	
    (	building_target(Building),mutable(Target)
    ->	client_send_message(Building,depend(Target))	    	
	;	true
	),
   	client_send_message(Target,request(Me)),
	my_debug(builder(debug),"sending request to arbiter:~w.~n",[msg(Target,request(Me))]),   	
	client_get_message(Msg),
	my_debug(builder(debug),"Thread ~w received message ~w.~n",[Me,Msg]),
    (	Msg==grant(Target)
    ->	asserta('$has_lock'(Target))	    
    ;	Msg==rebuild(Target)
    ->  build_target(Target),
    	request_target(Target)
    ;	Msg=error(Target,E)
    ->	throw(error(target_error(Target,E)))
    ;	Msg=obsolete(Target,Targets)
    ->	throw(error(target_obsolete(Target,Targets)))
    ;	Msg=fail(Target)
    ->	throw(error(target_failed(Target)))
    ;	Msg=cycle(Target)
    ->	throw(error(cycle(Target)))    
    ;	throw(error(unexpected_message(Msg,wait_for_read_lock(Target))))
    ).


%%
% pdt_request_target(+Target)
% request a target.
% 
%
%  Make sure the information associated with Target is present and up to date.
% If necessary, the calling thread will wait until the information is built.
%
% 
% The calling thread obtains a "read lock" on the specified target.
% As long as a target is locked, it can not be built.
% The targets are released once the surrounding pdt_with_targets/1 or pdt_with_targets/2 call exits.
% If no such call exists, this predicate behaves as pdt_with_targets( [Target],true).



pdt_request_target(T):-
    pdt_request_targets([T]).

pdt_request_targets(Ts):-
	(	'$has_lock'('$mark')
    ->	ensure_builder_is_running,
    	request_targets(Ts)
    ;	pdt_with_targets( Ts,true)
    ).
request_targets([]).
request_targets([T|Ts]):-
	target_key(T,K),
	(	fix_point_target(T)
	->	fp_request_target(K)
	;	request_target(K)
	),
	request_targets(Ts).






push_target(Target):-
    asserta('$building'(Target)).

pop_target:-
    retract('$building'(_)).

building_target(Target):-
    '$building'(Target),
    !.

build_target(Target):-
    target_key(TargetTerm,Target),
    pef_clear_record(Target),
    pef_start_recording(Target),
    push_target(Target),
    /*profile_push(build(Target)),*/    
    (	catch(
    		call_cleanup(
    			(	debug(builder(build(TargetTerm)),"Building target ~w~n.",[TargetTerm]),
    				(	findall(step(S,W),
    						(	estimate_hook(TargetTerm,StepTerm,W),
    							target_key(StepTerm,S)
    						),
    						Steps
    					)
    				->	client_send_message(Target,estimate(Steps))
    				;	true
    				),
    				forall(build_hook(TargetTerm),true),
    				debug(builder(build(TargetTerm)),"Done with target ~w~n.",[TargetTerm])
    			),
    			(	/*profile_pop,*/
    				pop_target,
    				pef_stop_recording
    			)
    		),
    		E,
    		(	
    			client_send_message(Target,error(E)),
    			throw(E)
    		)
    	)    		
    ->	thread_self(Me),
    	client_send_message(Target,mark_clean(Me))
    ;	client_send_message(Target,fail)
    ).



%%
% pdt_invalidate_target(+Target)
% invalidate a target.
% 
% Marks the information associated with Target as obsolete.
pdt_invalidate_target(Target):-
    ensure_builder_is_running,
    target_key(Target,Key),
    thread_self(Me),
	client_send_message(Key,mark_dirty(Me)).



current_target_state(Target,State):-    
    (	'$target_state'(Target,S)
    *->	State=S
    ;	State=state(idle,outdated,[],[],[])
    ).

update_target_state(Target,NewState):-
	thread_self(Me),
	(	Me \== build_arbiter
	->	throw(only_arbiter_should_modify_state(Me,Target,NewState))
	;	NewState==state(idle,outdated,[],[],[])
    ->  retractall('$target_state'(Target,_))
    ;   retractall('$target_state'(Target,_)),
    	assert('$target_state'(Target,NewState))
    ),
    
    get_time(Now),
    recorda(builder_log,log(Now,new_state,Target,NewState)).

   

stop_arbiter:-
    current_thread(build_arbiter,Status),
    !,
    (	Status==running	
    ->  client_send_message(all,stop)
    ;	true
    ),
    thread_join(build_arbiter,ExitStatus),
    get_time(Now),
    flag(build_arbiter_msg_id,SN,SN+1),
	recorda(builder_log,log(Now,stop,meta,stop(ExitStatus),SN)),
    my_debug(builder(info),"build_arbiter stopped with status ~w~n",[ExitStatus]).
stop_arbiter.    
    

ensure_builder_is_running:-
    current_thread(build_arbiter,Status),
    !,
    (	Status==running
    ->	true
    ;	throw(builder_not_running(Status))
    ).
ensure_builder_is_running:-
	start_arbiter.    

start_arbiter:-
    current_thread(build_arbiter,running),
    !.
start_arbiter:-    
    thread_create(run_arbiter,_,[alias(build_arbiter)]).

pdt_restart_arbiter:-
    stop_arbiter,
    start_arbiter.


% used to mark threads that hold obsoleted locks.
:- dynamic '$obsolete'/2.


/* the arbiter uses the "fast lane" to send messages to itself.
   they are processed before any other pending regular messages, 
   but they never interrupt a message block. 
 */
:- dynamic '$fast_lane'/3.

/* clients can request that a number of message should be processed
   in a block, avoiding interleaving with messages from other clients. 
 */
:- dynamic '$message_block'/4,'$message_block_playback'/1.



arbiter_send_message(fast_lane(ToTarget),FromTarget,Msg):-
    !,
    get_time(Now),
    flag(build_arbiter_msg_id,SN,SN+1),
	recorda(builder_log,log(Now,send_to(fast_lane(ToTarget)),FromTarget,Msg,SN)),
	assert('$fast_lane'(SN,ToTarget,Msg)).
arbiter_send_message(Client,Target,Msg):-
    get_time(Now),
    flag(build_arbiter_msg_id,SN,SN+1),
	recorda(builder_log,log(Now,send_to(Client),Target,Msg,SN)),
	thread_send_message(Client,builder_msg(SN,Target,Msg)).

client_get_message(Msg):-
    thread_self(Me),
    get_time(Now),
    recorda(Me,log(Now,wait,Target,Msg,SN)),
    repeat,
	    catch(
	    	call_with_time_limit(2,thread_get_message(builder_msg(SN,Target,Msg))),
	    	time_limit_exceeded,
	    	fail
	    ),
    !,
    get_time(Now2),
    recorda(Me,log(Now2,recieve,Target,Msg,SN)).
    
client_send_message(Target,Msg):-
    flag(build_arbiter_msg_id,SN,SN+1),
	thread_self(Me),
	get_time(Now),
	recorda(Me,log(Now,send,Target,Msg,SN)),
	thread_send_message(build_arbiter,msg(SN,Target,Msg)).  
client_block_add_message(Target,Msg):-
    flag(build_arbiter_msg_id,SN,SN+1),
	thread_self(Me),
	get_time(Now),
	recorda(Me,log(Now,send,meta,record(Me,Target,Msg,SN),SN)),
	thread_send_message(build_arbiter,msg(SN,meta,record(Me,Target,Msg,SN))).
client_block_commit:-
	flag(build_arbiter_msg_id,SN,SN+1),
	thread_self(Me),
	get_time(Now),
	recorda(Me,log(Now,send,meta,commit(Me),SN)),
	thread_send_message(build_arbiter,msg(SN,meta,commit(Me))).
client_log(Target,Event):-
    flag(build_arbiter_msg_id,SN,SN+1),
	thread_self(Me),
	get_time(Now),
	recorda(Me,log(Now,debug,Target,Event,SN)).
	
next_message(Target,Event):-
    '$message_block_playback'(Client),
    !,
    (	retract('$message_block'(Client,Target,Event,SN))
    ->	get_time(Now),
    	recorda(builder_log,log(Now,block(Client),Target,Event,SN))   
    ;	retract('$message_block_playback'(Client)),    	
    	next_message(Target,Event)
    ).
         
next_message(Target,Event):-	
	retract('$fast_lane'(SN,Target,Event)),
	!,
	get_time(Now),
    recorda(builder_log,log(Now,fast_lane,Target,Event,SN)).
next_message(Target,Event):-    
    thread_get_message(msg(SN,Target,Event)),
    get_time(Now),
    recorda(builder_log,log(Now,regular,Target,Event,SN)).

run_arbiter:-   
    get_time(Now),
    flag(build_arbiter_msg_id,SN,SN+1),
	recorda(builder_log,log(Now,restart,meta,restart,SN)),	
	repeat,
		next_message(Target,Event),
		(	ground(Target)
	    	->	true
	    	;	throw(non_ground_target(Target,Event))
	    ),
	    (	ground(Event)
	    	->	true
	    	;	functor(Event,error,_)
	    	->	true
	    	;	throw(non_ground_event(Target,Event))
	    ),		
		process_message(Target,Event),			
		Event==stop,
	!,
	report_error(arbiter_quits).
	
report_error(Error):-
    forall(
    	current_target_state(_,state(TargetActivity,_,_,Threads)),
    	(	TargetActivity=building(Thread)
    	->	report_error([Thread|Threads],Error)
    	;	report_error(Threads,Error)
    	)
    ).

report_error([],_Error).
report_error([Thread|Threads],Error):-
    (	Thread=target(_,Target)
    ->	throw(cannot_report_to_target(Error,Target))
    ;	arbiter_send_message(Thread,meta,arbiter_error(Error))
    ),
    report_error(Threads,Error).
    
process_message(all,stop):-!.
process_message(profile(Target),Event):-
    !,
    profile(process_message(Target,Event)).
process_message(meta,check_available(Thread,Targets)):-
	!,
	(	check_available(Targets)
	->	arbiter_send_message(Thread,meta,yes)
	;	arbiter_send_message(Thread,meta,no)
	).
process_message(meta,run(Goal)):-
    !,
    once(Goal).
process_message(meta,record(Client,Target,Event,SN)):-
    !,
    assert('$message_block'(Client,Target,Event,SN)).
process_message(meta,commit(Client)):-
	!,    
    assert('$message_block_playback'(Client)),
    recorda(builder_log,block_commited(Client)).


process_message(Target,Event):-
    
    
    current_target_state(Target,State),
    (	ground(State)
    	->	true
    	;	throw(non_ground_state(Target,State,Event))
    ),    
    
    (	target_transition(State,Event,Action,NewState,Target)
    ->  (	ground(NewState)
    	->	true
    	;	throw(transition_to_non_ground_state(State,Event,Action,NewState,Target))
    	),
    	(	ground(Action)
    	->	true
    	;	throw(transition_with_non_ground_action(State,Event,Action,NewState,Target))
    	),
    	my_debug(builder(transition(Target)),"Target: ~w,~n~t Transition: ~w, ~w ---> ~w,~w~n",[Target,State,Event,Action,NewState]),
    	update_target_state(Target,NewState),
	    (	execute_action(Action,Target)
	    ->	true
	    ;	my_debug(builder(transition(Target)),"action failed ~w (target: ~w)~n",[Action,Target]),
	    	throw(error(action_failed(Target,State,Event,Action)))
	    )
	;	my_debug(builder(transition(Target)),"no transition for state ~w, event ~w (target: ~w)~n",[State,Event,Target]),
		throw(error(no_transition(Target,State,Event)))
	).
 
debugme:-
	my_debug(builder(debug),"ouch~n",[]).

execute_action([],_).
execute_action([Action|Actions],Target):-
    execute_action(Action,Target),
    execute_action(Actions,Target).
execute_action(grant([]),Target):-
    progress_report_worked(Target).
execute_action(grant([Thread|Threads]),Target):-
    (	functor(Thread,target,_)
    ->	true
    ;	arbiter_send_message(Thread,Target,grant(Target))
    ),
    execute_action(grant(Threads),Target).
execute_action(report_failure([]),_Target).
execute_action(report_failure([Thread|Threads]),Target):-
    arbiter_send_message(Thread,Target,fail(Target)),
    execute_action(report_failure(Threads),Target).
execute_action(report_error([],_E),_Target).
execute_action(report_error([Thread|Threads],E),Target):-
    arbiter_send_message(Thread,Target,error(Target,E)),
    execute_action(report_error(Threads,E),Target).
execute_action(invalidate,Target):-
	my_debug(builder(debug),"invalidating target: ~w~n",[Target]),
	target_key(TargetName,Target), %FIXME: should not be used by the arbiter!!
    pif_notify(builder(TargetName),invalid),     %FIXME   
    forall(target_depends(Dependent,Target),
    	arbiter_send_message(fast_lane(Dependent),Target,mark_dirty(target(Target)))
    	
    ).
    
execute_action(obsolete([]),_).    
execute_action(obsolete([L|Ls]),Target):-
	(	functor(L,target,_)
	->	true
	;	'$obsolete'(L,Target)
	->	true
	;	assert('$obsolete'(L,Target)),
		forall(
			'$thread_waits'(L,OtherTarget),
			%assert('$fast_lane'(OtherTarget,kick_obsolete(L,Target)))
			arbiter_send_message(fast_lane(OtherTarget),Target,kick_obsolete(L,Target))
		)	
	),
	
	execute_action(obsolete(Ls),Target).
execute_action(clear_obsolete(Thread),Target):-
	(	functor(Thread,target,_)
	->	true
	;	retract('$obsolete'(Thread,Target))
	).
execute_action(rebuild(Thread),Target):-
	my_debug(builder(debug),"rebuilding target: ~w~n",[Target]),
	target_key(TargetName,Target), %FIXME: should not be used by the arbiter!!
	pif_notify(builder(TargetName),start(Thread)), %FIXME
	clear_dependencies(Target),	
	arbiter_send_message(Thread,Target,rebuild(Target)).
execute_action(progress_prepare(Ts),Target):-
	progress_report_prepare(Target,Ts).	
execute_action(report_cycle(Thread),Target):-
	arbiter_send_message(Thread,Target, cycle(Target)).
execute_action(notify_done,Target):-
	my_debug(builder(debug),"target done: ~w~n",[Target]),
	target_key(TargetName,Target), %FIXME: should not be used by the arbiter!!
    pif_notify(builder(TargetName),done),
    progress_report_cleanup(Target).
execute_action(ackn_remove(W),Target):-
    my_debug(builder(debug),"sending message to ~w:~w~n",[W,builder_msg(removed(Target))]),
	arbiter_send_message(W,Target,removed(Target)).    
execute_action(lock_deps([]),_Target).
execute_action(lock_deps([T|Ts]),Target):-  
	%assert('$fast_lane'(T,request(target(Target)))),
	arbiter_send_message(fast_lane(T),Target,request(target(Target))),        
	execute_action(lock_deps(Ts),Target).
execute_action(unlock_deps([]),_Target).
execute_action(unlock_deps([T|Ts]),Target):-    		
	%assert('$fast_lane'(T,release(target(Target)))),
	arbiter_send_message(fast_lane(T),Target,release(target(Target))),        
	execute_action(unlock_deps(Ts),Target).	
execute_action(unlock_deps([T|Ts]),_Target):-
    writeln([T|Ts]),
    spyme.
execute_action(add_dependency(Dep),Target):-
    add_dependency(Target,Dep).
execute_action(report_fp_busy(Thread),Target):-
    arbiter_send_message(Thread,Target,fp_busy(Target)).

% state(Activity, Status, Locks,SleepLocks,Waits)
% Activity: idle - there are no locks 
%		or reading - there is at least one read lock 
%	    or building(T) - thread T is currently rebuilding the target. 
%
% Status: available - the target is up to date, Timestamp is the build time
%		  outdated - the target is outdated
%		  pending(Thread) - the target is in the process of becoming available
%
% Locks: A list of threads/targets that were granted a read lock on this target
%
% SleepLocks: (not used right now) A list of targets that were granted a read lock, but that are currently asleep. 
%
% Waits: A list of threads that are waiting for the target to become available.


/* this will only add an edge if it is not redundant
   in addition it will look for and erase any other edge that may become
   redundant by adding this one.
   This is rather expensive, I yet have to find out
   whether it is worth the effort.
 */
add_dependency(Target,Dep):-    
    (	net_dependency(Target,Dep)
    ->	true
    ;  	erase_redundant_edge(Target,Dep),
    	assert('$target_depends'(Target,Dep)),
		assert('$target_depends_inv'(Dep,Target))
    ).
  
    
	


target_depends(T1,T2):-
    (	nonvar(T1)
    ->	'$target_depends'(T1,T2)   	
	;	'$target_depends_inv'(T2,T1)
	).


clear_dependencies(Target):-
    forall(
    	clause('$target_depends'(Target,Dep),_,Ref),
    	(	retract('$target_depends_inv'(Dep,Target)),
    		erase(Ref)
    	)
    ).


erase_redundant_edge(K1,K2):-
    gensym('$visited',V),dynamic( V/1),
    gensym('$visited',W),dynamic( W/1),
	call_cleanup(erase_redundant_edge_x(K1,K2,V,W),(abolish(V/1),abolish(W/1))).
	
erase_redundant_edge_x(K1,K2,V,W):-
	%mark all nodes reachable from K2 and K2 itself
	forall(net_dependency_X(K2,_,V),true),
	VV=..[V,K2],
	assert(VV),
	%If N is K1 or a node that can reach K1
	%and if there is an edge N->M where M is marked,
	%then this edge is redundant.
	%There can be at most one such edge, if
	%we checked each time before adding an edge.
	(	N=K1
	;	net_dependency_X_inv(K1,N,W)
	),
	clause('$target_depends'(N,M),_,Ref),
	VVV=..[V,M],
	call(VVV),
	!,
	erase(Ref),
	retract('$target_depends_inv'(M,N)).
erase_redundant_edge_x(_,_,_,_).
	
net_dependency(T1,T2):-
    gensym('$visited',V),
    dynamic( V/1),    
    (	nonvar(T1)
	->	call_cleanup(net_dependency_X(T1,T2,V),abolish(V/1))		
	;	call_cleanup(net_dependency_X_inv(T2,T1,V),abolish(V/1))
	).
net_dependency_X(T1,T3,V):-
	VV=..[V,T2],
	'$target_depends'(T1,T2),
	\+ VV,
	assert(VV),
	(	T3=T2
	;	net_dependency_X(T2,T3,V)
	).

net_dependency_X_inv(T1,T3,V):-
    VV=..[V,T2],
	'$target_depends_inv'(T1,T2),
	\+ VV,
	assert(VV),
	(	T3=T2
	;	net_dependency_X_inv(T2,T3,V)
	).


net_dependencies(L,R):-
    (	nonvar(L)
    ->	findall(M,net_dependency(L,M),R)
    ;	findall(M,net_dependency(M,R),L)
    ).

dependencies(L,R):-
    (	nonvar(L)
    ->	findall(M,target_depends(L,M),R)
    ;	findall(M,target_depends(M,R),L)
    ).


/*
idea: moving target terms between stack and heep seems to dominate the build
system overhead, in particular as it happens rather often.
Let's try this:
Instead of identifying a target by a term, use some integer. A clause reference maybe.
Resolving targets to references and vice versa is done on the client threads. 
The arbiter is only uses numerical references. No target term ever enters the queue.
This should speed things up noticable.

One thing we need to take care of, though, is target mutability. Currently, the arbiter
calls hooks to find out wether a target is mutable or not. 
But this  could be avoided. The mutability is a constant property. So we can infer it once and
then cache it on the heap.
*/

% argument is the target term.
% clause references are used by the arbiter to refer to targets.
% the arbiter however does not know nore care it is dealing with clause references.
% these facts never accessed by the arbiter.
:- dynamic '$target'/1.

% succeeds if the argument is a reference to a mutable target.
% these facts are created by the client threads.
% The arbiter only reads them.
:- dynamic '$mutable'/1.

% succeeds if the first argument is the key of a target that uses the second argument as mux.
% these facts are created by the client threads.
% The arbiter only reads them.
:- dynamic '$target_mux'/2.

% succeeds if the argument is the key of a target that uses fix point iteration.
% these facts are created by the client threads.
% The arbiter only reads them.
:- dynamic '$fp_target'/1.


target_key(T,K):-
    (	clause('$target'(T),_,K)
    ->	true
    ;	ground(T)
    ->	assert('$target'(T),K),
    	(	mutable_X(T)
    	->	assert('$mutable'(K))
    	;	true
    	),
    	(	target_mux(T,Mux)
    	->	assert('$target_mux'(K,Mux))
    	;	true
    	),
    	(	fix_point_target(T)
    	->	assert('$fp_target'(K))
    	;	true
    	)
    ;	throw(doof(T,K))	
    ).
    

check_available(Targets):-
    forall(member(Target,Targets),current_target_state(Target,state(_,available,_,_,_))).



strong_lock(Client):-
    functor(Client,F,_)
    (	F==weak_target
    ->	fail
    ;	F==strong_target
    ->	true    
    ;	fail
    ).
strong_locks(Clients):-
	strong_locks(Clients,0).

strong_locks([],C):-
	C>1.
strong_locks([Client|Clients],C):-
	(	strong_lock(Client)
	->	CC is C + 1
	;	CC = C
	),
	(	CC > 1
	->	true
	;	strong_locks(Clients,CC)
	).		
    

    
target_transition(state(A, S, Ls,SLs,Ws),		 			request(T), 	report_error([T],obsolete_lock(OT)),		
																									state(A, S, Ls, SLs, Ws),					_Target):-
    '$obsolete'(T,OT).
	    																									

target_transition(state(A, outdated, Ls,SLs,Ws), 			request(T), 	report_cycle(T),		state(A, outdated, Ls, SLs, Ws),			Target):-
    closes_cycle(T,Target).
target_transition(state(building(T1), A, Ls,SLs,Ws),		request(T2), 	report_cycle(T2),		state(building(T1), A, Ls, SLs,Ws) ,		Target):-
    closes_cycle(T2,Target).

%enter reading
target_transition(state(idle, available,[], SLs,Ws), 		request(T), 	[lock_deps(Deps),
																			grant([T])], 			state(reading, available,[T],SLs,Ws) ,		Target):-
	dependencies(Target,Deps).	
target_transition(state(reading, available, Ls, SLs,Ws),	request(T), 	Action,		 			state(reading, available, [T|Ls],SLs,Ws) ,	Target):-
    (	\+ strong_locks(Ls), strong_locks([T|Ls])
    ->	dependencies(Target,Deps),
    	Action=[strong_deps(Deps),grant([T])]
	;	Action=grant([T])
	).
%enter building
target_transition(state(idle, outdated, [],SLs,[]), 		request(T), 	rebuild(T),				state(building(T), pending(T) , [],SLs, []),Target):-
	(	functor(T,target,_)
	->	throw(target_requests_outdated_dep(T,Target))
	;	true
	)/*,
	(	try_mux(T,Mux)
	->	Status=pending(T)
	;	Status=wait_mux(T)
	)*/. 
target_transition(state(reading, outdated, Ls,SLs,Ws), 		request(T), 	Action,					state(reading, outdated , Ls, SLs,Ws2),	Target):-
	(	functor(T,target,_)
	->	throw(target_requests_outdated_dep(T,Target))
	;	'$fp_target'(Target), Ws=[_|_]
	->	Ws2=Ws,
		Action=[report_fp_busy(T)]
	;	assert('$thread_waits'(T,Target)),
		Ws2=[T|Ws],		
		Action=[]
	).
target_transition(state(building(P), A , [],SLs, Ws),		request(W), 	Action,	 				state(building(P), A , [], SLs,Ws2),	Target):-
	(	functor(W,target,_)
	->	throw(target_requests_dep_beeing_build(W,Target))
	;	'$fp_target'(Target)
	->	Ws2=Ws,
		Action=[report_fp_busy(W)]
	;	Ws2=[W|Ws],
		Action=[],
		assert('$thread_waits'(W,Target))
	).

target_transition(state(building(_), _ , Ls,SLs,Ws),		fail,		 	report_failure(Ws),		state(idle, outdated, Ls,SLs,[]),		Target):-
    forall(member(W,Ws),retract('$thread_waits'(W,Target))).
target_transition(state(building(_), _ , Ls,SLs,Ws), 		error(E),	 	report_error(Ws,E),		state(idle, outdated, Ls,SLs,[]),		Target):-
    forall(member(W,Ws),retract('$thread_waits'(W,Target))).
target_transition(state(building(P), A , Ls,SLs,Ws),		estimate(Ts),	progress_prepare(Ts),	state(building(P), A , Ls,SLs,Ws),			_Target).	


target_transition(state(building(P),outdated,Ls,SLs,Ws),	depend(_Dep),	[], 					state(building(P), outdated, Ls,SLs,Ws),	_Target).
target_transition(state(building(P),pending(P),Ls,SLs,Ws),	depend(Dep),	add_dependency(Dep), 	state(building(P), Status , Ls,SLs,Ws),		_Target):-
    /* normally it adding a dependency to an outdated target is no problem, since the dependency will be requested and rebuild during the pending build.
       We only have a problem, if the building thread already holds an (obsoleted!) lock on the dependency, because this may stay unnoticed.
       
       n.b.: it is imperative that the dependency is added BEFORE the lock is requested!
    */
    (	'$obsolete'(P,Dep)
    ->	Status=outdated
    ;	Status=pending(P)
    ).


target_transition(state(building(P), pending(P), Ls,SLs,Ws),mark_dirty(_),	[invalidate,obsolete(Ls)],state(building(P), outdated , Ls,SLs,Ws),	_Target).
target_transition(state(A, available, Ls,SLs,[]), 			mark_dirty(_), 	[invalidate,obsolete(Ls)],state(A, outdated , Ls,SLs,[]),			_Target).
target_transition(state(A, outdated, Ls,SLs,Ws),			mark_dirty(_), 	[], 					state(A, outdated , Ls,SLs,Ws),				_Target).

target_transition(state(idle, available , [],SLs,[]),		mark_clean(_), 	[],						state(idle, available, [],SLs,[]),			_Target).

% exit building
target_transition(state(building(P), pending(P),[],SLs,[]),	mark_clean(_), 	[notify_done],			state(idle, available, [],SLs,[]),			_Target).

% exit building
target_transition(state(building(_), outdated,[],SLs,Ws),	mark_clean(_), 	[],						state(idle, outdated, [],SLs,Ws),			_Target).

%enter reading
% exit building
target_transition(state(building(_), pending(_),[],SLs,Ts),	mark_clean(_), 	[lock_deps(Deps,X),
																			notify_done,
																			grant(Ts)],				state(reading, available, Ts,SLs,[]),		Target):-
	length(Ts,Ln),
	(	Ln > 1
	->	DoDeps = [lock_deps(Deps),strong_deps(Deps)]
	;	X = weak
	),    																			
	dependencies(Target,Deps),
	forall(member(T,Ts),retract('$thread_waits'(T,Target))).    																			

% exit reading
target_transition(state(reading, available, Ls,SLs,Ws),		release(T), 	Do, 					state(Act, available, Ls2,SLs,Ws),			Target):-
    /*(	functor(T,target,1)
	->	Deps=[]    																			
	;	net_dependencies(Target,Deps)
	),*/
	dependencies(Target,Deps),
    select(T,Ls,Ls2), 
    (	Ls2 == []
    ->	Act=idle,Do=[clear_obsolete(T),unlock_deps(Deps)]
    ;	strong_locks(Ls), \+ strong_locks(Ls2)
    ->	Act=reading,Do=[clear_obsolete(T),weaken_locks(Deps)]
    ;	Act=reading,Do=[clear_obsolete(T)]
    ).
% exit reading
% enter building
target_transition(state(reading, outdated, [T],SLs,[W|Ws]),	release(T),		[clear_obsolete(T),
																			unlock_deps(Deps),
																			rebuild(W)],			state(building(W), pending(W) , [],SLs, Ws),Target):-
    /*(	functor(T,target,1)
	->	Deps=[]    																			
	;	net_dependencies(Target,Deps)
	).*/
	dependencies(Target,Deps),
	retract('$thread_waits'(W,Target)).
target_transition(state(reading, outdated, Ls,SLs,Ws),		release(T),	 	Do,						state(reading, outdated , Ls2,SLs,Ws),		_Target):-    
    select(T,Ls,Ls2), Ls2 \== [],
    (	strong_locks(Ls), \+ strong_locks(Ls2)
    ->	Do=[clear_obsolete(T),weaken_locks(Deps)]
    ;	Do=[clear_obsolete(T)]
    ).
% exit reading
target_transition(state(reading, outdated, [L],SLs,[]),		release(L),	 	[clear_obsolete(L),
																			unlock_deps(Deps)],		state(idle, outdated , [],SLs,[]),			Target):-
    /*(	functor(L,target,1)
	->	Deps=[]    																			
	;	net_dependencies(Target,Deps)
	).*/
	dependencies(Target,Deps).
target_transition(state(Act, St, Ls,SLs,Ws),				remove(W),	 	[ackn_remove(W)],		state(Act, St , Ls,SLs,Ws2),				Target):-
    (	retract('$thread_waits'(W,Target))
    ->	select(W,Ws,Ws2)	
    ;	Ws2=Ws
    ).
target_transition(state(Act, St, Ls,SLs,Ws),				kick_obsolete(W,T),	
																			[report_error([W],obsolete_lock(T))],		
																									state(Act, St , Ls,SLs,Ws2),				Target):-
    retract('$thread_waits'(W,Target)),
    select(W,Ws,Ws2).



/*

Cycle checkking:
A thread depends on a target if it waits for it, or if it requests it.
A target depends on a thread if it is pending, and if the thread is working on providing the target.

Invariant: the graph induced by the above relations is always acyclic.
requesting a target constitutes adding an edge. If that edge would close a cylce, an error is reported to the requesting thread.
*/

target_depends_thread(Target,Thread):-
    current_target_state(Target,state(building(Thread2),_,_,_,_)),
    thread_depends_thread(Thread2,Thread).    
    
thread_depends_target(Thread,Target):-
    current_target_state(Target2,state(_,_,_,_,Waiting)),
    member(Thread,Waiting),
    target_depends_target(Target2,Target).


target_depends_target(Target,Target).
target_depends_target(Target1,Target2):-
    target_depends_thread(Target1,Thread),
    thread_depends_target(Thread,Target2).

thread_depends_thread(Thread,Thread).
thread_depends_thread(Thread1,Thread2):-
    thread_depends_target(Thread1,Target),
    target_depends_thread(Target,Thread2).

    
closes_cycle(Thread,Target):-  
    target_depends_thread(Target,Thread),
    !.


%pdt_thread_activity(Thread,status(A)):-
%    current_thread(Thread,A).
pdt_builder_info(Thread,build,Target):-
	current_target_state(Target,state(building(Thread),_,_,_,_)).    
pdt_builder_info(Thread,lock,Target):-
	current_target_state(Target,state(_,_,Ls,_,_)),
	memberchk(Thread,Ls).
pdt_builder_info(Thread,wait,Target):-
	current_target_state(Target,state(_,_,_,_,Ws)),
	memberchk(Thread,Ws).
pdt_builder_info(A,depend,B):-
	target_depends(A,B).
pdt_builder_info(Target,Activity+Status):-
	current_target_state(Target,state(Activity, Status, _, _, _)).
	
pdt_print_builder_info(slice(T),File):-
    pdt_print_builder_info(FT,
    	(	T=FT
    	;	net_dependency(T,FT)
    	;	net_dependency(FT,T)	
    	),
    	File
    ).
pdt_print_builder_info(FT,F,File):-
    thread_self(Me),
    (	Me==build_arbiter
    ->	print_builder_info(FT,F,File)
    ;	thread_send_message(build_arbiter,msg(meta,run(print_builder_info(FT,F,File))))
    ).

print_builder_info(FT,F,File):-   
	tell(File),
	call_cleanup(print_builder_info(FT,F),told).

print_builder_info(FT,F):-   	 
    format("digraph G {~nnode [shape = \"record\"]~n",[]),
    forall(
    	current_thread(Thread,Status),
    	format("\"~w\" [label=\"{~w|~w}\"]~n",[Thread,Thread,Status])
    ),
    forall(
    	(	pdt_builder_info(Target,Activity+Status), 
    		mutable(Target),
    		\+ \+ (Target=FT,once(F))
    	),
    	format("\"~w\" [label=\"{~w|~w|~w}\"]~n",[Target,Target,Activity,Status])
    ),
    forall(
    	(	pdt_builder_info(Thread,Edge,Node1),
    		(	Thread=target(Node0)
    		->	\+ \+ (Node0=FT,once(F))
    		;	Node0=Thread
    		),
    		\+ \+ (Node1=FT,once(F))
    	),
    	(	(	Edge==depend
    		->	Style=dashed,Color=black
    		;	Edge==lock
    		->	Style=solid,Color=green
    		;	Edge==build
    		->	Style=solid,Color=blue
    		;	Edge==wait
    		->	Style=solid,Color=red
    		;	Style=solid,Color=black
    		),
    		format("\"~w\" -> \"~w\" [label=\"~w\",style=~w,color=~w]~n",[Node0,Node1,Edge,Style,Color])
    	)
    ),
	format("}~n",[]).
available(Target):-  
    current_target_state(Target,state(_, available, _, _, _)).
    
progress_report_prepare(Target,Steps):-    
    progress_report_prepare_X(Steps,Target,0,Sum),
    assert('$progress_total'(Target,Sum)),
    target_key(TargetName,Target), %FIXME: should not be used by the arbiter!!
    pif_notify(builder(TargetName),estimate(Sum)).
    
progress_report_prepare_X([],_Target,Sum,Sum).
progress_report_prepare_X([step(S,W)|Steps],Target,Sum0,Sum):-
    Sum1 is Sum0 + W,
    assert('$progress_subproblem'(S,Target,W),Ref),
    assert('$progress_subproblem_inv'(Target,Ref)),
    progress_report_prepare_X(Steps,Target,Sum1,Sum).



progress_report_worked(SubTarget):-
	forall(
		'$progress_subproblem'(SubTarget,Target,W),
		
		(	spyme,
			target_key(TargetName,Target), %FIXME: should not be used by the arbiter!!
			pif_notify(builder(TargetName),worked(W))
		)
	).
	
	

progress_report_cleanup(Target):-
	forall(
		clause('$progress_subproblem_inv'(Target,Ref),_,InvRef),
		(	erase(Ref),
			erase(InvRef)
		)
	),
	retractall('$progress_total'(Target,_)).
	


:-thread_local '$profile'/2,'$profile_contains'/2,'$profile_starts'/2,'$profile_ends'/2,'$profile_current'/1.
:-dynamic '$profile'/2,'$profile_contains'/2,'$profile_starts'/2,'$profile_ends'/2,'$profile_current'/1.
	
profile_push(Target):-
    get_time(Now),
    pef_reserve_id('$profile',T),
    assert('$profile'(Target,T)),
    (	'$profile_current'(CurrentT)
    ->	assert('$profile_contains'(CurrentT,T))
    ;	true
    ),
    assert('$profile_start'(T,Now)),    
    asserta('$profile_current'(T)).
    
profile_pop:-
	get_time(Now),
	retract('$profile_current'(T)),
	assert('$profile_end'(T,Now)).

profile_clear:-
	retractall('$profile'(_,_)),
	retractall('$profile_current'(_)),
	retractall('$profile_contains'(_,_)),
	retractall('$profile_start'(_,_)),
	retractall('$profile_end'(_,_)).
	
target_profile(Target,Time,Netto):-
    '$profile'(Target,T),
    '$profile_start'(T,Start),
    '$profile_end'(T,End),
    Time is End - Start,
    
    findall(CTime,
    	(	'$profile_contains'(T,C),
    		'$profile_start'(C,CStart),
    		'$profile_end'(C,CEnd),
    		CTime is CEnd - CStart
    	),
    	CTimes
    ),
    sum(CTimes,TimeChildren),
    Netto is Time - TimeChildren.

target_profile(Target,Count,STime,SNetto):-
    findall(Time,target_profile(Target,Time,_),Times),
    findall(Netto,target_profile(Target,_,Netto),Nettos),
    length(Times,Count),
    sum(Times,STime),
    sum(Nettos,SNetto).
    
sum(Times,Brutto):-
	sum(Times,0,Brutto).

sum([],Sum,Sum).
sum([Time|Times],Sum0,Sum):-
    Sum1 is Time + Sum0,
    sum(Times,Sum1,Sum).
