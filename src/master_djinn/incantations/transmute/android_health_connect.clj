(ns master-djinn.incantations.transmute.android-health-connect
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as spec]
            [clojure.spec.test.alpha :as stest]
            [master-djinn.util.types.core :as type]
            [master-djinn.util.types.actions :as type-a]))


(defonce transmuter-data-provider "AndroidHealthConnect")

(spec/fdef transmute
        :args (spec/and map? ::type-a/provider-input-actions)  ;; TODO submit-data structure with specific android-health-connect action type
        :ret ::type-a/Actions)

(stest/instrument `transmute)

;; (spec/fdef Step->Action
;;         :args (spec/and :pid :::type/address? :provider type/is-data-provider? :input :::type/android-health-connect-action)
;;         :ret :::type/Actions)


(defn Step->Action
  ;; {:pre  [spec/valid? ::android-health-connect-action args] ;; TODO predicate for valid submit-data arg
  ;;     :post ::db.submit-data}
  [pid provider input]
  "
  @DEV: transmuter-version is UUID namespace to track data provenance as codebase evolves over time.
  Tried adding as clojure func metadata but that was a bitch.
  
  TODO should we generate relations here and return Action + relations?
  OR just Action type and then db/create-actions query creates relations from that data?
  "
  ;; (println "google:transmute:Step" pid provider)
  (let [transmuter-version "0.0.1"
        action-name (type/action-type->name :Walking)
        start_time (:startTime input)
        origin (or (get-in input [:metadata :dataOrigin]) transmuter-data-provider)]
    {
    :name action-name
    :data_provider provider
    :player_id pid
    :player_relation "DID"
    :data {
      :uuid (type/action->uuid pid provider origin action-name start_time transmuter-version)
      :start_time start_time
      :end_time (:endTime input)
      :count (:count input)
      :data_source origin}}))
      ;;; These fields exist but not sure what these mean or what the benfits of saving them are yet
      ;;; Should probs normalize across other data providers too but dont have data from them yet
      ;; :foreign-uuid (get-in [:metadata :id] input)
      ;; :collection-method (get-in [:metadata :id] input)
      ;; :client-record-id (get-in [:metadata :clientRecordId] input)
      ;; :client-record-version (get-in [:metadata :clientRecordVersion] input)
    

(defn transmute
  "Transform raw data collected from phone into game :Action types"
  [data]
  ;; {:pre [(spec/explain ::type-a/provider-input-actions data) (spec/valid? ::type-a/provider-input-actions data)] ;; TODO submit-data structure with specific android-health-connect action type
  ;;     :post [(spec/valid? ::type-a/Actions %)]}
  (let [provider (name (:data_provider data)) ;; @DEV: remove keyword prefix ":" for neo4j tag
        action_name (:name data)
        pid (:player_id data)
        aaa (println "player id   " pid)
        ;; TODO cleaner if vars above are in action data themselves
        ;; BUT also nice that :data is straight from providers and our data is separate
        inputs (:data data)]
  (if (not= provider transmuter-data-provider)
    (throw (Exception. "Trans:AndroidHealthConnect: Invalid data provider" provider))
    {:actions (case action_name
      "Step" (map #(Step->Action pid provider %) inputs)
      "default" [])})))
