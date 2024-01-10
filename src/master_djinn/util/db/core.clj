(ns master-djinn.util.db.core
  (:require [neo4j-clj.core :as neo4j]
            [master-djinn.util.types.core :refer [load-config]]
            [steffan-westcott.clj-otel.api.metrics.instrument :as inst]
            [steffan-westcott.clj-otel.api.trace.span :as span])
  (:import (java.net URI)))

(def database-query-counter
  (inst/instrument {:name "db.queries.counter"
                          :instrument-type :counter}))

(defonce connection
  (let [{:keys [activitydb-uri activitydb-user activitydb-pw]} (load-config)]
      (if (nil? activitydb-uri)
        (println "ACTIVITYDB no config :(")
        (neo4j/connect (URI. activitydb-uri) activitydb-user activitydb-pw)
      )
  ))

(defonce MASTER_DJINN_DATA_PROVIDER "MasterDjinn")
(defonce MOBILE_APP_DATA_SOURCE "JinniMobileApp")

(defn call [query args]
  (println "DB:call: uri" (:activitydb-uri (load-config)))
  (println "DB:call: args" args)
  (neo4j/with-transaction connection tx
    (inst/add! database-query-counter {:value 1})
    (span/with-span! ["DB.Query" {:system/profile-id (:id query)}]
      (-> (query tx args) doall first))))

(defn generate-resolver
  "for direct read queries that dont require formatting input data"
  [query]
  (fn [context args value]
    ;; using context:  https://lacinia.readthedocs.io/en/latest/resolve/context.html
    (neo4j/with-transaction connection tx
      ;; (println "DB:resolver: ctx" context)
      (println "DB:resolver: uri" (:activitydb-uri (load-config)))
      (println "DB:resolver: args" args)
      (println "DB:resolver: value" value)
      ;; doall returns list but only ever one response w/ a map (which could contain lists)
      (-> (query tx args) doall first))))

;; TODO figure out when to run this. Should i create a migrations type process?
(neo4j/defquery define-action-invariants
  "CREATE CONSTRAINT unique_action_uuid FOR (a:Action) REQUIRE a.uuid IS UNIQUE")

(neo4j/defquery get-all-players
  "MATCH (p:Avatar) RETURN COLLECT(p) as players")

(neo4j/defquery get-player-actions"
  MATCH  (u:Avatar { id: $player_id })-[]->(a:Action)
  WHERE  a.startTime >= $starttime AND a.endTime <= $endtime
  RETURN COLLECT(a) as actions
")

;; TODO Ideally CREATE in unind could be a merge for automatic dedupe but cant get that query to work with putting object in directly to create
;; if we want multiple data providers/sources to attest to an action, will need to rethink data model and will cause issues with autoMERGEing
;; @DEV: relationships can only ahve 1 type. refactor.setType *overrides*. Create multiple relations if want to express :WANTS and :DID 
  ;; MATCH (p:Avatar     {id: $actions[0].player_id})
  ;; MERGE (d:DataProvider {id: $actions[0].provider})
(neo4j/defquery batch-create-actions "
  UNWIND $actions AS action

  MERGE (p:Avatar     {id: action.player_id})
  MERGE (d:DataProvider {id: action.provider})

  WITH action, p, d
  
  CREATE (p)-[rp:ACTS]->(a:Action)
  SET a = action.data
  CREATE (d)-[rd:ATTESTS]->(a)

  WITH a, action, rp

  CALL apoc.create.addLabels(a, [action.action_type]) YIELD node
  CALL apoc.refactor.setType(rp, action.player_relation) YIELD output AS relation

  RETURN COLLECT(a.uuid) as ids
") ;; FIX this returns all actions on a player with UUID. If there are duplicate UUIDs then
;; Should be fixed by having constraints set but nice to not have it returned just in case


;; TODO batch-create-action-resources {:actions [{:resources {:relation "Consumed" "Curated" "Created" }}]}
;; doesnt create actions, make that initial step in batch-create-actions
;; this just creates resources and relations with actions
;; resources must have actions???
(neo4j/defquery create-action-with-resources "
    MERGE (p:Avatar     {id: action.player_id})
    MERGE (d:DataProvider {id: action.provider})
    CREATE p-[rp:ACTS]->(a:Action)
    SET a = action.data
    CALL apoc.refactor.setType(rp, action.player_relation) YIELD output AS relation
    CREATE (d)-[rd:ATTESTS]->(a)
    
    WITH a, p
    UNWIND $resources AS resource
    MERGE (r:Resource {uuid: $resource.uuid})
")