;; (ns master-djinn.transmuter-test
;;     (:require [clojure.spec.alpha :as spec]
;;                 [clojure.test :refer :all]
;;                 [clojure.spec.test.alpha :as test]
;;                 [master-jinn.incantations.transmute.core :as transmuter]
;;                 [master-jinn.util.types.core :as types]))

;; (->> test/check 'transmuter/multiplexer
;;     test/summarize-results)

;; (deftest github-commit->Action 
;;     (let [commitData {

;;         }]
;;         ;; (todo generate github commit data from type spec)
;;         ;; commitData (generate-github commit)
        
;;     (is (= { } (transmuter "GITHUB" commitData)))))