(ns master-djinn.incantations.transmute.android-health-connect
  (:require [clojure.data.json :as json]
            [master-djinn.util.types.core :refer [action-type->name action->uuid]]))


(defonce transmuter-data-provider "AndroidHealthConnect")

(defn Step->Action
  [pid provider input]
  "
  @DEV: transmuter-version is UUID namespace to track data provenance as codebase evolves over time.
  Tried adding as clojure func metadata but that was a bitch.
  
  TODO should we generate relations here and return Action + relations?
  OR just Action type and then db/create-actions query creates relations from that data?
  "
  ;; (println "google:transmute:Step" pid provider)
  (let [transmuter-version "0.0.1"
        action-name (action-type->name :Walking)
        start_time (:startTime input)
        origin (or (get-in input [:metadata :dataOrigin]) transmuter-data-provider) ]
    {
    :name action-name
    :data_provider provider
    :player_id pid
    :player_relation "DID"
    :data {
      :uuid (action->uuid pid provider origin action-name start_time transmuter-version)
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
  [player_id data]
  ;; {:pre  [spec/valid? types/::action-source-data args] ;; TODO predicate for valid submit_data arg
  ;;     :post [(map string? %)]}
  (let [provider (name (:data_provider data)) ;; @DEV: remove keyword prefix ":" for neo4j tag
        action_name (:name data)
        aaa (println "player id   " player_id)
        ;; TODO cleaner if vars above are in action data themselves
        ;; BUT also nice that :data is straight from providers and our data is separate
        inputs (:data data)]
  (if (not= provider transmuter-data-provider)
    (throw (Exception. "Trans:AndroidHealthConnect: Invalid data provider" provider))
    {:actions (case action_name
      "Step" (map #(Step->Action player_id provider %) inputs)
      "default" [])})))
  