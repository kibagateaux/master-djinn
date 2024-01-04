(ns master-djinn.incantations.transmute.core
  (:require
    [clojure.spec.alpha :as spec]
    [master-djinn.util.db.core :as db]
    [master-djinn.util.types.core :as types]
    [neo4j-clj.core :as neo4j]
    [steffan-westcott.clj-otel.api.trace.span :as span]
    [master-djinn.incantations.transmute.android-health-connect :as ahc]

  ))


(defn provider->transmuter [provider]
  "takes a keyword for data provider and returns a function that
  will transform incoming player data into game actions"
 (case provider
    :AndroidHealthConnect ahc/transmute
    (fn [args]
      (if (types/is-data-provider? provider) ;; TODO redundant with spec
        (println "Trans:multiplexer: VALID DATA PROVIDER WITH NO TRANSMUTER - " provider)
        (println "Trans:multiplexer: INVALID DATA PROVIDER - " provider)))))

(defn multiplexer
    ;; TODO technically should just be (transmute args) and rest should be handled in portal handler to keep this no side effects
    [context args value]
    (span/with-span! ["incantations.transmute.multiplexer" {:system/provider (:data_provider args)}]
      ;;  {:pre  [spec/valid? types/::action-source-data args] ;; TODO predicate for valid submit_data arg
      ;;   :post [(map string? %)]}
      (if-let [transmute (provider->transmuter (:data_provider args))]
      ;; TODO (spec/check-asserts true)
      ;; TODO validate player for action here
      ;; TODO add try block. specifically want to catch duplicate uuid invariant violation
      ;; so we know if data needs to be resubmitted or not. (also figure out if part of data sent was duplicated or all, no actions sent will be saved since single tx)
      (->> args
        ;; TODO anon function
          ((fn [data] (span/with-span! ["incantations.transmute.processing" {:system/provider (:data_provider args)}]
            (transmute data))))
          ;; now have normalized format of { :data_provider :player_id :actions [{:name :player_relation :data {}}]}
          ;; spec/assert ::Actions
          (db/call db/batch-create-actions)
          doall ;; eagerly load results before db connection closes
          first ;; doall returns list but only ever one response
          :ids)))) ;; extract list of :Action uuids created
        
    
