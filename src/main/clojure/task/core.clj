(ns task.core
  (:require [clj-time.core :as time])
  (:require [clojure.pprint]))

;; ====================================================================================
;; Task data structure

(defrecord TaskData [])

(defn task* [options function]
  (TaskData. nil 
    (merge options
           {:function function
            :promise (promise)
            :creation-time (time/now)})))


;; =======================================================================================
;; Task status

(defonce last-task-counter (atom 0))

(defonce tasks (ref {}))

(defn allocate-task-id []
  (swap! last-task-counter inc))

;; =====================================================================================
;; Task query functions

(defn stopped? [task]
  (or
    (nil? task)
    (#{:stopped} (:status task))))


(defn complete? [task]
  (or
    (nil? task)
    (#{:complete :error :stopped} (:status task))))


(defn get-task 
  "Returns the current task associated with the given ID.

  If called with an old task instance, returns the latest task for the same ID"
  [task-or-id] 
  (let [id (cond 
						 (associative? task-or-id) (:id task-or-id)
						 :else task-or-id)
        task (@tasks id)]
    task)) 

(def task-summary-fields [:id :status :source :options :result])

(defn task-summary [task]
  {:id (:id task)
    :status (:status task)
    :source (:source task)
    :options (:options task)
    :result (:result task)})


;; =====================================================================================
;; Task creation

(defmacro wrap-if [condition form]
  `(if ~condition
     (fn [~'code] ~form)
     identity))

(defn build-task-function 
  "Create the source code for the function to be called for each task execution. 
   Code generation is done based on the provided options - you only pay for the 
   options selected." 
 
  ([{repeat :repeat 
     sleep-millis :sleep 
     accumulate :accumulate-results 
     repeat-value :repeat
     while-clause :while 
     until-clause :until  
     :as options} original-code]
	  (-> `(assoc ~'task :result ~original-code)
	    ((wrap-if accumulate 
	              `(let [result# ~code]
	                 (update-in result# :results conj (:result result#)))))
	    ((wrap-if while-clause 
	              `(if ~while-clause 
	                 ~code 
	                 (assoc ~'task :status :complete))))
	    ((wrap-if until-clause 
	              `(let [result# ~code]
                   (if ~until-clause 
	                   result# 
	                   (assoc result# :status :complete)))))
 	    ((wrap-if (number? repeat-value) 
		            `(let [result# ~code
		                   rep# (dec (:repeat result#))]
		               (if (> rep# 0)
		                 (assoc result# :repeat rep#)
		                 (assoc result# :status :complete :repeat 0)))))
 	    ((wrap-if (not repeat-value) 
					      `(let [result# ~code]
					         (assoc result# :status :complete))))
	    ((wrap-if sleep-millis 
	              `(let [result# ~code] 
	                 (if (not (complete? ~'task)) (do (Thread/sleep ~sleep-millis) result#)) 
	                 result#)))
	    ((wrap-if true 
	              `(fn [~'task] ~code)))))
  
  ([code] (build-task-function nil code)))

(defmacro task 
  "Creates a task containing the given code. Valid options are:
  :repeat 
     (Default) If left nil of false, the task will only execute once
     If a numeric value is used, the task will repeat the given number of times. 
     If any other true value is used, the task will repeat infinitely.

  :result
     An initial result value for the task. :result will be set to the value produced
     by the task on each iteration.

  :accumulate-results
     If set to true, all results will be saved in the vector :results in the task.

  :timeout
     A number of milliseconds to run the task for. If the tomeout is reaced during
     execution of the task, it will be allowed to complete.

  :sleep
     A number of milliseconds to sleep between successive executions of the task

  :while
     Code that will be called before each iteration with the task as an argument. 
     If it returns true, then the task will complete.

  :until
     Code that will be evaluated after each iteration with the task as an argument. 
     If it returns true then the task will complete.
     "

  ([options code]
    (let [function (build-task-function options code)
          options (merge options 
                         {:source (str code)
                          :options options})]
      
      `(task* (quote ~options) ~function)))
  ([code] 
    `(task nil ~code)))



;; =====================================================================================
;; Task management functions


(defn ps 
  ([]
    (ps (vals @tasks)))
  ([tasks]
    (ps task-summary-fields tasks))
  ([ks tasks]
    (clojure.pprint/print-table ks (map task-summary tasks))))

(defn stop [task]
  (let [task (get-task task)
        id (:id task)]
    (dosync 
      (alter tasks assoc id (assoc task :status :stopped)))))

(defn await-result [task]
  (let [task (get-task task)]
    @(:promise task)))

(defn clear 
  ([]
    (dosync
      (ref-set tasks {})))
  ([task]
	  (let [task (get-task task)
	        id (:id task)]
	    (dosync 
	      (alter tasks dissoc id)))))

;; ==================================================================================
;; Task execution

(defn finish-task [task]
  (let [task (assoc task :finish-time (time/now))
        promise (:promise task)]
    (if promise (deliver promise (:result task)))
    task))

(defn task-loop [task] 
  (if (complete? task) 
	  (finish-task task)
    (let [id (:id task)
          task (try 
                 ((:function task) task) 
                 (catch Throwable t (assoc task :status :error :error t :result t)))
          new-status (if (stopped? (get-task id)) :stopped (:status task))
          task (assoc task :status new-status)]
	    (dosync (alter tasks assoc id task))
      (recur (@tasks id)))))
	      

(defn run-task [task]
  (let [id (allocate-task-id)
        task (merge task
								    {:id id
                     :status :started
								     :start-time (time/now)})]
	  (dosync (alter tasks assoc id task))
    (future (task-loop task))
    task))

;; =========================================================================================
;; Task launching macros

(defmacro run 
  "Create and run a new task. 

  Calls task with the relevant options."
  ([code]
	 `(run-task (task ~code)))
  
  ([options code]
	 `(run-task (task ~options ~code))))


