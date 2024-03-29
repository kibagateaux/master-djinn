(ns master-djinn.incantations.transmute.core
  (:require
    [clojure.spec.alpha :as spec]
    [clojure.spec.test.alpha :as stest]
    [master-djinn.util.db.core :as db]
    [master-djinn.util.types.core :as types]
    [master-djinn.util.types.game-data :as type-gd]
    [neo4j-clj.core :as neo4j]
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
        (println "Trans:multiplexer: INVALID DATA PROVIDER - " provider) )
      #('[])))) ;; default return empty actions instead of nil/error to prevent code complexity. can check if empty to prevent unneccessary queries

;; @DEV: is defmulti/defmethod more semantic/terse? I prefer current format personally
(defn multiplexer
;; TODO technically should just be (transmute args) and rest should be handled in portal handler to keep this no side effects
    [context args value]
    ;;  {:pre  [(spec/valid? ::type-gd/provider-input-actions args)] ;; TODO predicate for valid submit_data arg
    ;;   :post [(spec/valid? (spec/+ string?) %)]}
    (let [transmuter (provider->transmuter (:provider args))]
    ;; TODO (spec/check-asserts true)
    ;; TODO validate player for action here
    (neo4j/with-transaction db/connection tx
    ;; TODO add try block. specifically want to catch duplicate uuid invariant violation
    ;; so we know if data needs to be resubmitted or not. (also figure out if part of data sent was duplicated or all, no actions sent willbe saved since single tx)
      (->> args
          transmuter
          ((fn [config] (println "TRANSMUTING " config) config))
          ;; now have normalized format of { :provider :player_id :actions [{:name :player_relation :data {}}]}
          (db/batch-create-actions tx)
          ;; spec/assert ::Action
          doall ;; eagerly load results before db connection closes
          first ;; doall returns list but only ever one response
          :ids)))) ;; extract list of :Action uuids created
        
(spec/fdef provider->transmuter
        :args (spec/cat :provider ::types/provider)
        :ret (spec/fspec :args (spec/cat :data ::types/provider-input-actions)
                :ret ::type-gd/Actions))

(spec/fdef multiplexer
        :args (spec/cat :context map? :args ::types/provider-input-actions :value any?)
        :ret ::type-gd/Actions)

(stest/instrument `provider->transmuter)
(stest/instrument `multiplexer)
