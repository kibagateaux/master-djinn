(ns master-djinn.util.types.actions
  (:require [master-djinn.util.types.core :as types]
            [master-djinn.util.types.gql :as type-q]
            [clojure.spec.alpha :as spec]
            [clojure.spec.test.alpha :as spec-test]))


(spec/def ::type types/is-action-type?)
(spec/def ::name types/is-action-name?)
(spec/def ::player_relation types/is-action-relation?)
(spec/def ::output-data (spec/keys
  :req-un [::types/uuid ::types/data_source ::types/startTime ::types/endTime]))
(spec/def ::metadata map?)

;; input data for each provider. TODO match ::data_provider to ::data input
(spec/def ::android-health-connect-action
  (spec/keys :req-un [::types/startTime ::types/endTime ::metadata]
            :opt-un [::types/count]))
(spec/def ::ios-health-action
  (spec/keys :req-un [::types/startTime ::types/endTime]))
(spec/def ::input-data
  (spec/* (spec/or ::android-health-connect-action ::ios-health-action)))

(spec/def ::provider-input-actions (spec/keys :req-un [
    ::types/data_provider
    ::types/player_id
    ::name
    ::input-data]))
    ; :distinct true :min-count 1

;; TODO i shouldn't need to write transmuters anymore
;; Should just write specs about input data, output :Action, and transmuter func.
;; use spec/generator to create (::transmuter [::action-source-date] ::Action)
;; maybe need a defmacro bc need parameters e.g. "Step" for android-health but "Walk" on strava


;; TODO feel like :Action.:Actions need a lot of work on how they are structured so
;; API + playgroun data system is scalable while making DB queries simp[le/efficient]
(spec/def ::Action (spec/keys :req-un [
  ::name ;; :TODO non-namespaced keys = name 
  ::data_provider 
  ::player_id
  ::player_relation
  ::output-data ;; :TODO non-namespaced keys = data 
]))

;; (spec/def ::Actions (spec/or ::types/empty-array (spec/+ ::Action)))
(spec/def ::Actions (spec/* ::Action))

;; 
(spec/def ::submit-data (spec/&
  ::type-q/mutation;; TODO compose: need to nest ::action-source-data inside
  (spec/keys :req-un [::provider-input-actions])))  ;; TODO non-namespaced key = :raw_data

