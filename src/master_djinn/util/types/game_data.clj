(ns master-djinn.util.types.game-data
  (:require [master-djinn.util.types.core :as types]
            [master-djinn.util.types.gql :as type-q]
            [clojure.spec.alpha :as spec]
            [clojure.spec.test.alpha :as spec-test])
    )


(spec/def ::Avatar (spec/keys
  :req-un [::types/id ::types/name ::types/birthday]
  ;; dont include uuid as that is secret for now[]
  :opt-un [::types/uuid]))

(spec/def ::Identity (spec/keys
  ;; dont include uuid as that is secret for now[]
  :req-un [::types/uuid ::types/id ::types/provider]
  :opt-un [::types/access_token ::types/refresh_token]))

(spec/def ::action_type types/normalize-action-type)

(spec/def ::data (spec/keys
  :req-un [::types/uuid ::types/data_source ::types/startTime ::types/endTime ::action_type]
  :opt-un [::types/count]))

;; TODO autogenerate from schema.edn
(spec/def ::Action (spec/keys :req-un [
  ::types/action_type ;; :TODO non-namespaced keys = name 
  ::types/provider 
  ::types/player_id
  
  ::types/player_relation
  ::data ;; :TODO non-namespaced keys = data 
]))

;; (spec/def ::Actions (spec/or ::types/empty-array (spec/+ ::Action)))
(spec/def ::Actions (spec/* ::Action))
    ; :distinct true :min-count 1

;; TODO autogenerate from schema.edn
(spec/def ::Resource (spec/keys :req-un [
  ::types/resource_type ;; :TODO non-namespaced keys = name 
  ::types/provider 
  ::types/player_id
  ::types/player_relation
  ::data ;; :TODO non-namespaced keys = data 
]))

(spec/def ::Resources (spec/* ::Resource))

;; TODO i shouldn't need to write transmuters anymore
;; Should just write specs about input data, output :Action, and transmuter func.
;; use spec/generator to create (::transmuter [::action-source-date] ::Action)
;; maybe need a defmacro bc need parameters e.g. "Step" for android-health but "Walk" on strava

;; Common Incantations
(defn gen-transmuter-spec
    "@param func-name - escaped function name for transforming data from provider into standard action e.g. `transmuter not transmuter or 'transmuter
    @param input-spec - clojure spec for provider input data type"
    [func-name input-spec]
    (spec/fdef func-name
        :args (spec/and map? input-spec)  ;; TODO submit-data structure with specific android-health-connect action type
        :ret ::Actions))

;; API Mutations
(spec/def ::submit-data (spec/&
  ::type-q/mutation;; TODO compose: need to nest ::action-source-data inside
  (spec/keys :req-un [::provider-input-actions])))  ;; TODO non-namespaced key = :raw_data

