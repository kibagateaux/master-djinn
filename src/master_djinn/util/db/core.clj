(ns master-djinn.util.db.core
  (:require [neo4j-clj.core :as neo4j]
            [master-djinn.util.types.core :refer [load-config]])
  (:import (java.net URI)))

(defonce connection
  (let [{:keys [activitydb-uri activitydb-user activitydb-pw]} (load-config)]
      (if (nil? activitydb-uri)
        (println "ACTIVITYDB no config :(")
        (neo4j/connect (URI. activitydb-uri) activitydb-user activitydb-pw)
      )
  ))

(defn generate-resolver
  "for direct read queries that dont require formatting input data"
  [query]
  (fn [context args value]
    (println "DB:resolver: ctx" context)
    (println "DB:resolver: ctx" args)
  ;; using context:  https://lacinia.readthedocs.io/en/latest/resolve/context.html
    (neo4j/with-transaction connection tx
      ;; (println "DB:resolver: ctx" context)
      (println "DB:resolver: args" args)
      (println "DB:resolver: value" value)
      ;; doall returns list but only ever one response w/ a map (which could contain lists)
      (-> (query tx args) doall first))))

;; TODO figure out when to run this. Should i create a migrations type process?
(neo4j/defquery define-action-invariants
  "CREATE CONSTRAINT unique_action_uuid FOR (a:Action) REQUIRE a.uuid IS UNIQUE")

(neo4j/defquery create-player
  "CREATE (u:Avatar $player)")

(neo4j/defquery get-all-players
  "MATCH (p:Avatar) RETURN COLLECT(p) as players")

;; TODO Ideally CREATE in unind could be a merge for automatic dedupe but cant get that query to work with putting object in directly to create
;; if we want multiple data providers/sources to attest to an action, will need to rethink data model and will cause issues with autoMERGEing
;; @DEV: relationships can only ahve 1 type. refactor.setType *overrides*. Create multiple relations if want to express :WANTS and :DID 
  ;; MATCH (p:Avatar     {id: $actions[0].player_id})
  ;; MERGE (d:DataProvider {id: $actions[0].data_provider})
(neo4j/defquery batch-create-actions "
  UNWIND $actions AS action

  MERGE (p:Avatar     {id: action.player_id})
  MERGE (d:DataProvider {id: action.data_provider})

  WITH action, p, d
  
  CREATE (p)-[rp:ACTS]->(a:Action)
  SET a = action.data
  CREATE (d)-[rd:ATTESTS]->(a)

  WITH a, action, rp

  CALL apoc.create.addLabels(a, [action.name]) YIELD node
  CALL apoc.refactor.setType(rp, action.player_relation) YIELD output AS relation

  RETURN COLLECT(action.data.uuid) as ids
")

(neo4j/defquery get-player-actions"
  MATCH  (u:Avatar { id: $player_id })-[]->(a:Action)
  WHERE  a.startTime >= $starttime AND a.endTime <= $endtime
  RETURN a as actions
")