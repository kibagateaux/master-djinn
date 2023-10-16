(ns master-djinn.incantations.transmute.core
  (:require
    [master-djinn.util.db.core :as db]
    [master-djinn.util.types.core :as types]
    [neo4j-clj.core :as neo4j]
    [master-djinn.incantations.transmute.android-health-connect :as ahc]
  ))


(defn provider->transmuter [provider]
  "takes a keyword for data provider and returns function that will transform incoming player data into game actions"
 (case provider
    :AndroidHealthConnect ahc/transmute
    (fn [args]
      (if (types/valid-data-provider? provider)
        (println "Trans:multiplexer: VALID DATA PROVIDER WITH NO TRANSMUTER" args)
        (println "Trans:multiplexer: INVALID DATA PROVIDER" args) )
      []))) ;; default return empty actions instead of nil/error to prevent code complexity. can check if empty to prevent unneccessary queries

;; @DEV: is defmulti/defmethod more semantic/terse? I prefer current format personally
(defn multiplexer
    [context args value]
    (let [transmute (provider->transmuter (:data_provider args))]
    
    (println "Trans:multiplex: args" args)
    (println "Trans:multiplex: value" value)
    (println "Trans:multiplex: tranny" transmute)
    ;; TODO validate player for action here
    (neo4j/with-transaction db/activity-db tx
    ;; TODO add try block. specifically want to catch duplicate uuid invariant violation
    ;; so we know if data needs to be resubmitted or not. (also figure out if part of data sent was duplicated or all, no actions sent willbe saved since single tx)
      (->> args
          transmute
          ;; ((fn [config] (println "TRANSMUTING " config) config))
          ;; now have normalized format of { :data-provider :player_id :actions [{:name :player_relation :data {}}]}
          ;; TODO add types/actions? validator
          (db/batch-create-actions tx)
          doall ;; eagerly load results before db connection closes
          first ;; doall returns list but only ever one response
          :ids ;; extract list of :Action ids created
          ;; (map str) ;; TODO once uuids implemented should be able to remove
        ))))
    
