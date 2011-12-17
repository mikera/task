(ns task.test-task
  (:use [clojure.test])
  (:use [task.core]))


(deftest test-function-generation
  (testing "Task function generation"
    (is (= (take 2 `(fn [~'task] (assoc ~'task :result 3))) 
           (take 2 (build-task-function 3))))))

(deftest test-running
  (let [task (run "foobar")
        id (:id task)]
    (testing "Task running"
      (is (= id (:id (get-task id)))))))

(deftest test-results
  (testing "Task results"
    (is (= "foobar" (await-result (run "foobar"))))
    (is (= 15 (await-result (run {:result 10 :repeat 5 } (inc (:result task))))))))

(deftest test-while
  (testing "Task results"
    (is (= 15 
           (await-result (run 
                           {:result 0 
                            :repeat 100
                            :while (< (:result task) 15)} 
                           (inc (:result task))))))
    (is (= 10 
           (await-result (run 
                           {:result 0 
                            :repeat 10
                            :while (< (:result task) 15)} 
                           (inc (:result task))))))))

(deftest test-accumulate
  (testing "Result accumulation"
    (is (= [1 2 3 4 5] 
           (await-results (run 
                           {:accumulate-results true
                            :result 0 
                            :repeat 5} 
                           (inc (:result task))))))))