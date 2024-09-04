(ns master-djinn.util.db.circles
  (:require [neo4j-clj.core :as neo4j]
            [master-djinn.util.db.core :as db]))

(neo4j/defquery get-summoning-circle "
  OPTIONAL MATCH (p:Avatar:Human)-[:HAS]->(:Jubmoji {provider_id: $pid}),
    (p)-[:SUMMONS]->(j:Jinni:P2C)
  RETURN  p AS summoner, j AS jinni
")

;; TODO should :Identity:Jubmoji be mapped to :Human or :Jinni ???
;; much cooler if on jinni then it can be passed around. 
;; would have to make :Jubmoji the summoner then which feels weird if its not an agent

;; Can only create circle if you have been vouched by master djinn
;; (p)--(j) merge assumes only one P2C per player (intentional for now)
;; ON CREATE ensures 1 player->jubmoji and 1 player-> P2C + prevents accidental overriding
(neo4j/defquery create-summoning-circle "
  MATCH (p:Avatar:Human {id: $pid})<-[:BONDS]-(:Jinni:P2P)
  WITH p
  WHERE p IS NOT NULL
  MERGE (p)-[:HAS]->(id:Identity:Ethereum:Jubmoji)

  ON CREATE
    SET id.provider = 'Ethereum'
    SET id.provider_id = $signer
    WITH p
    MERGE (p)-[rj:SUMMONS]->(j:Jinni:P2C)
    MERGE (j)-[rp:BONDS]->(p)
    SET rj.timesetamp = $now
    SET rp.since = $now
    RETURN p AS summoner, j AS jinni
")

;; only create rj if p + j already exist. Do not overide existing rj data
(neo4j/defquery join-summoning-circle "
  MATCH (p:Avatar:Human {id: $pid}), (j:Jinni:P2C {jid: $jid})
  WITH p, j
  MERGE (p)-[rj:BONDS]->(j)
  ON CREATE SET rj.since = $now
")

(neo4j/defquery apply-summoning-circle "
  MATCH (p:Avatar:Human {id: $pid}), (j:Jinni:P2C {jid: $jid})
  WITH p, j
  MERGE (p)-[rj:DESIRES]->(j)
  ON CREATE SET rj.since = $now
")

;; TODO jid as input once multi P2C per player
(neo4j/defquery get-circle-applicants "
  MATCH (:Avatar:Human {id: $pid})-[:SUMMONS]->(j:Jinni:P2C),
    (p:Avatar:Human)-[:DESIRES]->(j)
  
  RETURN COLLECT(p.id)
")