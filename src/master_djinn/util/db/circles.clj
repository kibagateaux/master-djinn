(ns master-djinn.util.db.circles
  (:require [neo4j-clj.core :as neo4j]
            [master-djinn.util.db.core :as db]))

;; TODO match on jjubmoji
(neo4j/defquery get-summoning-circle "
  OPTIONAL MATCH (j:Avatar:Jinni)-[:HAS]->(:Jubmoji {provider_id: $jmid}),
    (p:Avatar:Human)-[:SUMMONS]->(j:Jinni:p2c)
  RETURN p AS summoner, j AS jinni
")

(neo4j/defquery create-summoning-circle "
  // Only verified players with personal jinni can create circles
  MATCH (p:Avatar:Human {id: $pid})-[:SUMMONS]->(:Jinni:p2p)
  WITH p
  WHERE p IS NOT NULL

  // Give community jinni the jubmoji identity to take actions directly in game
  // on jinni :ID created so only 1 circle/jinni per jubmoji
  
  CALL apoc.do.when(
    size((id:Identity:Ethereum:Jubmoji {
      provider: 'Ethereum',
      provider_id: $signer})) = 0,
    \"CREATE (j:Avatar:Jinni:p2c)-[:HAS]->(id:Identity:Ethereum:Jubmoji {
      provider: 'Ethereum',
      provider_id: $signer})
      
      MERGE (p)<-[rp:BONDS]-[j)
      MERGE (p)-[rj:SUMMONS]->(j)
      SET rj.timestamp = $now
      SET rp.since = $now
      
      RETURN j\",
    \"RETURN 'Identity already exists'\"
) YIELD value
    
    RETURN p AS summoner, value AS jinni
")

;; only create rj if p + j already exist. Do not overide existing rj data
(neo4j/defquery join-summoning-circle "
  MATCH (p:Avatar:Human {id: $pid}), (j:Jinni:p2c {jid: $jid})
  WITH p, j
  MERGE (p)-[rj:BONDS]->(j)
  ON CREATE SET rj.since = $now
")

(neo4j/defquery apply-summoning-circle "
  MATCH (p:Avatar:Human {id: $pid}), (j:Jinni:p2c {jid: $jid})
  WITH p, j
  MERGE (p)-[rj:DESIRES]->(j)
  ON CREATE SET rj.since = $now
")

;; TODO jid as input once multi p2c per player
(neo4j/defquery get-circle-applicants "
  MATCH (:Avatar:Human {id: $pid})-[:SUMMONS]->(j:Jinni:p2c),
    (p:Avatar:Human)-[:DESIRES]->(j)
  
  RETURN COLLECT(p.id)
")