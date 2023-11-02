(ns test.master-djinn.gql 
    (:require [clojure.spec.alph :as spec]
            [clojure.spec.test.alpha :as test]
                [master-jinn.incantations.transmute.core :as transmuter]
                [master-jinn.util.types.core :as types]))

;; Test the specification with example data
(spec/valid?
  ::action-source-data
  {::first-name "Jenny" ::last-name "Jetpack" ::email-address "jen@jetpack.org"})


(spec/valid?
  ::Action
  {::first-name "Jenny" ::last-name "Jetpack" })
