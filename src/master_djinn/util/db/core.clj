(ns master-djinn.util.db.core
  (:require [neo4j-clj.core :as neo4j]
            [master-djinn.util.types.core :refer [load-config]]
            [master-djinn.portal.logs :as log]
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

(defonce PORTAL_DAY "2023-06-20T12:30:00.000Z")
(defonce MASTER_DJINN_DATA_PROVIDER "MasterDjinn")
(defonce MOBILE_APP_DATA_SOURCE "JinniMobileApp")


(defn call [query args]
  (if args (do (println "DB:call: args") (clojure.pprint/pprint args)) nil)
  (try (neo4j/with-transaction connection tx
      (inst/add! database-query-counter {:value 1})
      (span/with-span! ["DB.Query" {:system/profile-id (:id query)}]
      (-> (query tx args) doall first)))
    (catch Exception err 
      ;; (println "DB:call:ERROR" err)
      (log/handle-error err "util:db:call:ERROR" {:args args})
      ;; catch just for automated reporting. bubble up error for app to handle as it pleases
      (throw err))))

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

;; Mistral embed API only does 1024 dimension
;; https://docs.mistral.ai/platform/endpoints#embedding-models
;; cosine is better for text content/similarity 
(neo4j/defquery define-divination-invariants
  "CREATE VECTOR INDEX `divination-embeds` IF NOT EXISTS
  FOR (d:Divination)
  ON (d.embeds)
  OPTIONS  {indexConfig: {
    `vector.dimensions`: 1024,
    `vector.similarity_function`: 'cosine'
  }}
")

;;; GETTERS

(neo4j/defquery get-all-players
  "MATCH (p:Avatar) RETURN COLLECT(p) as players")

(neo4j/defquery get-player-actions "
  MATCH  (u:Avatar {id: $player_id})-[:ACTS]->(a:Action)
  WHERE  datetime(a.start_time) >= datetime($start_time)
    AND datetime(a.end_time) <= datetime($end_time)
  RETURN COLLECT(a) as actions
")

(neo4j/defquery get-jinni-actions "
  MATCH (j:Jinni {uuid: $jinni_id})-[:BONDS]->(p:Avatar),
        (p)-[:ACTS]->(a:Action) WHERE
            datetime(a.start_time) >= datetime($start_time) AND
            datetime(a.end_time) <= datetime($end_time)
  RETURN COLLECT(a) as actions
")

(neo4j/defquery get-player-widgets"
  MATCH  (u:Avatar {id: $player_id})-[:USES]->(w:Widget)
  RETURN COLLECT(w) as widgets
")


(neo4j/defquery get-last-action-time "
  MATCH (:Avatar {id: $player_id})-[:ACTS {type: \"DID\"}]->(a:Action)<-[:ATTESTS]-(:Provider {provider: $provider})
  
  WITH a
  ORDER BY a.start_time DESC LIMIT 1
  
  RETURN a.start_time as start_time
")

(neo4j/defquery get-last-divination "
  MATCH (j:Jinn {id: $jinni_id})-[:ACTS]->(d:Action:Divination),
    (j)-[:USES]->(w:Widget {id: 'maliksmajik-avatar-viewer'})
  
  WITH d, w
  ORDER BY d.start_time DESC LIMIT 1

  WITH d, w
  MATCH (d2:Action:Divination)
  WHERE d2.hash = d.hash AND d2.prompt IS NOT NULL
  
  RETURN w as settings, d as action, d2.prompt as prompt
")

;;; SETTERS

;; Creates player+jinni but no details until create-player called
;; TODO delete all existing widgets before setting?
;; MATCH (p)-[wr:USES]->(w:Widget); DELETE wr, w; UNWIND
;; MERGE allows player to create :Avatar during onboarding before verified by 
;; Good growth tactic, can have people confirm integrations/widgets before allowing to play the game.
(neo4j/defquery set-widget-settings "
  MERGE (p:Avatar:Human {id: $player_id})
  MERGE (p)<-[rj:BONDS]-(j:Avatar:Jinn:P2P)
  
  UNWIND $widgets AS widget
  
  MERGE (j)-[wr:USES]->(w:Widget {id: widget.id})
  SET w = widget

  RETURN COLLECT(w.uuid) as widgets
")

;; TODO Ideally CREATE in unind could be a merge for automatic dedupe but cant get that query to work with putting object in directly to create
;; if we want multiple data providers/sources to attest to an action, will need to rethink data model and will cause issues with autoMERGEing
;; @DEV: relationships can only ahve 1 type. refactor.setType *overrides*. Create multiple relations if want to express :WANTS and :DID 
  ;; MATCH (p:Avatar     {id: $actions[0].player_id})
  ;; MERGE (d:Provider {id: $actions[0].provider})
(neo4j/defquery batch-create-actions "
  UNWIND $actions AS action

  MERGE (p:Avatar     {id: action.player_id})
  MERGE (d:Provider   {provider: action.provider})

  WITH action, p, d
  
  MERGE (p)-[rp:ACTS {type: action.player_relation}]->(a:Action {uuid: action.data.uuid})
  SET a = action.data
  MERGE (d)-[rd:ATTESTS]->(a)

  WITH a, action
  CALL apoc.create.addLabels(a, [action.action_type]) YIELD node

  RETURN COLLECT(a.uuid) as ids
") ;; FIX this returns all actions on a player with UUID. If there are duplicate UUIDs then
;; Should be fixed by having constraints set but nice to not have it returned just in case

(neo4j/defquery batch-create-resources "
    UNWIND $resources AS resource

    MERGE (p:Avatar     {id: resource.player_id})
    MERGE (d:Provider   {provider: resource.provider})
    MERGE (p)-[rp:MONITORS {type: resource.player_relation }]->(r:Resource {uuid: resource.data.uuid})
    MERGE (r)<-[:HOSTS]-(d)
    SET r = resource.data
    
    WITH r, d, resource
    CALL apoc.create.addLabels(r, [resource.resource_type]) YIELD node as rNode
    CALL apoc.create.addLabels(d, [resource.provider]) YIELD node as dNode
    
    RETURN COLLECT(r) as resources
")

;; TODO batch-create-action-resources {:actions [{:resources {:relation "Consumed" "Curated" "Created" }}]}
;; doesnt create actions, make that initial step in batch-create-actions
;; this just creates resources and relations with actions
;; resources must have actions???
(neo4j/defquery create-action-resource-relations "
    MATCH (a:Action       {uuid: $action.uuid})
    WITH a
    UNWIND $resources AS resource

    MATCH (r:Resource {uuid: resource.uuid })
    MERGE (a)-[rr]-(r)
    
    WITH rr, resource
    CALL apoc.refactor.setType(rr, resource.action_relation) YIELD output AS relation
")