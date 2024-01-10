(ns master-djinn.incantations.transmute.android-health-connect
  (:require [clojure.data.json :as json]
            [clojure.spec.alpha :as spec]
            [clojure.spec.test.alpha :as stest]
            [clojure.test.check.generators :as gen]
            [expound.alpha :as expound]
            [master-djinn.util.types.core :as types]
            [master-djinn.util.types.game-data :as type-gd]))


(defonce transmuter-data-provider :AndroidHealthConnect)
(set! spec/*explain-out* expound/printer)

(spec/fdef Step->Action
        :args (spec/cat :pid ::types/signer :provider ::types/provider :input ::types/android-health-connect-action)
        :ret ::type-gd/Action)

(spec/fdef transmute
        :args (spec/cat :data (spec/and map? ::types/provider-input-actions))  ;; TODO submit-data structure with specific android-health-connect action type
        ;; basic :args above throw error w=on stest/check unable to generate data so tell it how to generate data manually
        ;; :args (spec/with-gen
        ;;   (spec/and
        ;;     (spec/cat :data (spec/and map? ::types/provider-input-actions))
        ;;     #(= (distinct (map :startTime (:raw_data %))) (map :startTime (:raw_data %)))
        ;;     ) ;; should not have duplicate data
        ;;   #(gen/let [data (spec/gen ::types/provider-input-actions)]
        ;;      [data]))
        :ret ::type-gd/Actions)

(defn Step->Action
  [pid provider input]
  ;; {:pre  [(expound/expound ::types/android-health-connect-action input)] ;; TODO predicate for valid submit-data arg
  ;;     :post [(expound/expound ::type-gd/Action %)]}
  "
  @DEV: transmuter-version is UUID namespace to track data provenance as codebase evolves over time.
  Tried adding as clojure func metadata but that was a bitch.
  
  TODO should we generate relations here and return Action + relations?
  OR just Action type and then db/create-actions query creates relations from that data?
  "
  ;; (println "google:transmute:Step" pid provider)
  (let [transmuter-version "0.0.1"
        p (name provider)
        action-type (types/normalize-action-type :Walking)
        start_time (:startTime input)
        origin (or (get-in input [:metadata :dataOrigin]) p)]
    {
    :action_type action-type
    :provider p
    :player_id pid
    :player_relation "DID"
    :data {
      :uuid (types/action->uuid pid p origin action-type start_time transmuter-version)
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
  ;;; @DEV: these fail at runtime from API but running in repl manually succeeds 
  ;; {:pre [(spec/conform ::types/provider-input-actions data) (spec/explain ::types/provider-input-actions data)] ;; TODO submit-data structure with specific android-health-connect action type
  ;;     :post [(spec/conform ::type-gd/Actions (:raw_data %)) (spec/explain ::type-gd/Actions (:raw_data %))]}
  ;; (clojure.pprint/pprint "Trans:Andr:transmute" data)
  (let [provider (:provider data) ;; @DEV: remove keyword prefix ":" for neo4j tag
        action_type (:action_type data)
        pid (:player_id data)
        aaa (println "player id   " pid)
        ;; TODO cleaner if vars above are in action data themselves
        ;; BUT also nice that :data is straight from providers and our data is separate
        inputs (:raw_data data)]
  (if (not= provider transmuter-data-provider)
    (throw (Exception. "Trans:AndroidHealthConnect: Invalid data provider" provider))
    {:actions (case action_type
      "Step" (map #(Step->Action pid provider %) inputs)
      "default" [])})))



;;; @DEV: (instrument) only checks inputs at runtime, does not check outputs!
;; This passes fine even tho :pre/:post doesnt. spec/fdef + stest/instrument MAY be broken giving false postives :pre/:post definitely throws false negatives
;; can verify with repl commands below
  ;; => (require '[expound.alpha :as exp] '[clojure.spec.alpha :as s] '[master-djinn.util.types.core :as t])
  ;; => (exp/expound ::t/provider-input-actions {:raw_data [{:count 531, :startTime "2030-04-07T09:44:16.818Z", :endTime "2031-09-07T09:45:16.819Z", :metadata {:clientRecordId nil, :clientRecordVersion "0", :dataOrigin "com.google.android.apps.fitness", :device "0", :id "079e8187-15f2-421d-8024-7c4b2f5fda06", :lastModifiedTime "2023-09-07T09:57:52.715Z", :recordingMethod "0"}}], :provider :AndroidHealthConnect, :action_type "Step", :player_id "0x0AdC54d8113237e452b614169469b99931cF094e"})
(stest/instrument `Step->Action)
(stest/instrument `transmute)

;; (defspec cant-transmute-normalized-Actions 100
;;   (prop/for-all [acts (map (gen/vector ::type-gd/Action)]
;;     (transmute (transmute acts))))

;; (defspec unknown-action-type-returns-empty-list 100
;;   (prop/for-all [acts (map #(assoc % :action_type "UNKOWN_TYPE")(gen/vector ::type-gd/Action)]
;;     (= [] (transmute acts))))

;; (expound/explain-results (stest/check `transmute))
;; (expound/explain-results (stest/check `Step->Action))
