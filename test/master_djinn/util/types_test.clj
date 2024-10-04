(ns master-djinn.util.types-test
  (:require [clojure.spec.alpha :as spec]
            [clojure.spec.test.alpha :as test]
            [master-djinn.util.types.core :as types]))

;; Test the specification with example data
;; (spec/valid?
;;   ::action-source-data
;;   {::first-name "Jenny" ::last-name "Jetpack" ::email-address "jen@jetpack.org"})

;; (spec/valid?
;;   ::Action
;;   {::first-name "Jenny" ::last-name "Jetpack" })
