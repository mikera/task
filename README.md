# Task


Task is a Clojure library for managing long-running tasks at the REPL. It was created
because of the author's frustration with the lack of good ways to handle Java threads
while doing computational experiments in Clojure.

It can be use programatically, but is intended primarily for interactive REPL usage.


### Setup: Use Task by importing it into your namespace:

<pre>
(ns your.name.space
  (:use [task.core]))
</pre>


### Example 1: Running a simple task 5 times over 5 seconds


  (run {:repeat 5 :pause 1000} (.println (System/out) "bvboug"))



### Example 2: Get a listing of current tasks

<pre>
  (ps)
</pre>
  
Which will produce an output something like:

<pre>
====================================================================================
:id | :status   | :source                      | :options                  | :result
====================================================================================
4   | :complete | (do (println "Hello task!")) | {:repeat 5, :sleep 1000}  |
3   | :complete | (do (swap! counter inc))     | {:repeat 5, :pause 1000}  |
2   | :complete | (some-other-function)        | {:repeat 5, :pause 10000} |
1   | :complete | (yet-another-function)       | {:repeat 5, :pause 1000}  |
====================================================================================
</pre>