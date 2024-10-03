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

;; TODO implement tx queeu for lighter banwidth, prevent redundancy / race conitions,  and ongoing updates and easier debugging 
;; (def request-queue (atom []))
;; (defn process-queue []
;;   (let [batch (swap! request-queue (constantly []))]
;;     (when (seq batch)
;;       (neo4j/with-transaction session
;;         (doseq [{:keys [q data]} batch]
;;           (q data))))))
;; (defn schedule-tx [query args]
;;   (swap! request-queue conj {:q query :data args})
;;   (when (= 1 (count @request-queue))
;;     (future (Thread/sleep 100) (process-queue))))

(defn call [query args]
  (println "DB:call: q \n" (:id query))
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
(neo4j/defquery define-core-game-invariants "
  // ensure uniqueness across all Avatars for base ids and global uuid
  CREATE CONSTRAINT key_avatar_id IF NOT EXISTS
    FOR (a:Avatar)
    REQUIRE a.id IS NODE KEY;
  
  // we compute uuid deterministically so should never have duplicates 
  // but we dont really use it so no composite node key with (id, uuid)
  CREATE CONSTRAINT req_avatar_uuid IF NOT EXISTS
    FOR (a:Avatar)
    REQUIRE a.uuid IS NOT NULL;
  CREATE CONSTRAINT uniq_avatar_uuid IF NOT EXISTS
      FOR (a:Avatar)
      REQUIRE a.uuid IS UNIQUE;

  // ensure only a single identity node exists and relations added ontop of it for ownership/access
  CREATE CONSTRAINT key_identity_provider_id IF NOT EXISTS
    FOR (id:Identity)
    REQUIRE ( id.provider, id.provider_id ) IS UNIQUE;

  // 
  CREATE CONSTRAINT key_action_uuid IF NOT EXISTS
    FOR (a:Action)
    REQUIRE a.uuid IS NODE KEY;
")

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

(neo4j/defquery get-all-jinni
  "MATCH (j:Jinni) RETURN COLLECT(j.id) as jinni")


(neo4j/defquery get-player-actions "
  MATCH  (u:Avatar {id: $player_id})-[:ACTS]->(a:Action)
  WHERE  datetime(a.start_time) >= datetime($start_time)
    AND datetime(a.end_time) <= datetime($end_time)
  RETURN COLLECT(a) as actions
")

;; returns all jinni, all widgets on that jinni, and only the last divination for that jinni
;; for a given player that is bonded to any jinni, whether npc or not
(neo4j/defquery get-home-config "
  MATCH (p:Avatar {id: $player_id})<-[:BONDS]-(j:Avatar)
  MATCH (summ:Avatar)-[:SUMMONS]->(j)
  OPTIONAL MATCH (j)-[:USES]->(w:Widget)
  OPTIONAL MATCH (j)-[:ACTS]->(d:Action:Divination)
  WITH j, summ, w, d
  ORDER BY d.start_time DESC
  WITH j, summ, COLLECT(w) AS widgets, COLLECT(d)[0] AS last_divination
  WITH {
    jinni: j,
    labels: labels(j),
    summoner: summ,
    widgets: widgets,
    divi: last_divination
  } AS bundle
  RETURN collect(bundle) AS jinni
")

(neo4j/defquery get-player-data "
  MATCH (p:Avatar)-[rj]-(j:Jinni),
    (j)-[rw]-(w:Widget),
    (p)-[ra]-(a:Action),
    (j)-[rd]-(d:Divination)

  RETURN p, j , w, a, rw, rj, ra, d, rd
")

(neo4j/defquery get-jinni-actions "
  MATCH (j:Jinni {id: $jinni_id})-[:BONDS]->(p:Avatar),
        (p)-[:ACTS]->(a:Action) WHERE
            datetime(a.start_time) >= datetime($start_time) AND
            datetime(a.end_time) <= datetime($end_time)
  RETURN COLLECT(a) as actions
")

(neo4j/defquery get-player-widgets "
  MATCH (u:Avatar {id: $player_id})<-[:BONDS]-(j:Jinni:p2p)-[:USES]->(w:Widget)
  RETURN COLLECT(w) as widgets
")

(neo4j/defquery get-player-providers "
  MATCH  (p:Avatar:Human {id: $player_id})-[:ACTS]-(a:Action),
    (a)<-[:ATTESTS]-(d:Provider)
  RETURN COLLECT(DISTINCT d.provider) as providers
")

(neo4j/defquery get-last-action-time "
  MATCH (:Avatar {id: $player_id})-[:ACTS {type: \"DID\"}]->(a:Action)<-[:ATTESTS]-(:Provider {provider: $provider})
  
  WITH a
  ORDER BY a.start_time DESC LIMIT 1
  
  RETURN a.start_time as start_time
")

;; get active divination widget
(neo4j/defquery get-last-divination "
  MATCH (j:Jinni {id: $jinni_id}),
  (j)-[:USES]->(w:Widget {id: 'maliksmajik-avatar-viewer'})
  WHERE w.priority > 0 OR w.priority IS NULL
  
  OPTIONAL MATCH (j)-[:ACTS]->(d:Divination)
  
  WITH d, w
  ORDER BY d.start_time DESC LIMIT 1
  
  RETURN w as widget, d as action
")

;; ensure data is only set if divi is actually created so ont overwrite timing vars
(neo4j/defquery create-divination "
  MATCH (j:Jinni {id: $jinni_id })
  MERGE (p:Provider {provider: $provider})
  MERGE (j)-[:ACTS {type: 'SEES'}]->(a:Action:Divination {uuid: $data.uuid})
  
  ON CREATE
    SET a += $data

  MERGE (p)-[:ATTESTS]->(a)
  
  RETURN a.uuid AS id
")

;; for progressively tracking divination e.g. initially only start-time to know its begun but then need end-time to know process finished
(neo4j/defquery update-divination "
  MATCH (d:Divination {uuid: $uuid})
  SET d += $updates
  RETURN d
")


; TODO new widget node, update relations
  ;; SET w.prompt = $prompt, w.hash = $hash, w.embeds = $embeds
(neo4j/defquery new-divination-settings "
  MATCH (j:Jinni {id: $jid})-[]->(w:Widget {id: 'maliksmajik-avatar-viewer'})

  SET w += $updates

  RETURN w as widget
")



;;; SETTERS

;; works on any jinni even if not player not verified so can set intentions in onboarding
;; must already be created via create-npc before setting widgets
(neo4j/defquery set-widget-settings "
  MATCH (j:Avatar {id: $jinni_id})
  
  WITH j
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