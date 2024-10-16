(ns master-djinn.util.db.circles
  (:require [neo4j-clj.core :as neo4j]
            [master-djinn.util.db.core :as db]))

;; TODO match on jjubmoji
(neo4j/defquery get-summoning-circle "
  OPTIONAL MATCH (j:Avatar:Jinni:p2c)-[:HAS]->(:Jubmoji:Identity:Ethereum {provider_id: $jmid})
  MATCH (p:Avatar:Human)-[:SUMMONS]->(j)
  RETURN p AS summoner, j AS jinni
")

(neo4j/defquery create-summoning-circle (str "
  // Only verified players with personal jinni can create circles
  MATCH (player:Avatar:Human {id: $pid})-[:SUMMONS]->(:Jinni:p2p)
  // Check if the Identity already exists
  OPTIONAL MATCH (id:Identity:Ethereum:Jubmoji {provider: 'Ethereum', provider_id: $signer})
  
  // TODO feelz this isnt returning right vals
  WITH count(player) as playerCount, count(id) AS jubCount
    // RETURN playerCount, jubCount;

  // only 1 circle/jinni per jubmoji. count must be 0 to create
  CALL apoc.do.when(
    jubCount = 0 and playerCount = 1,
    // Give community jinni the jubmoji identity to take actions directly in game
    \"MERGE (p:Avatar:Human {id: $pid})
      MERGE (j:Avatar:Jinni:p2c {id: $jinni.id, uuid: $jinni.uuid})-[:HAS]->(id:Jubmoji:Identity:Ethereum {
        provider: 'Ethereum',
        provider_id: $signer})
      // Set summoner as player that initiated call. Has certain controls over p2c jinni
      MERGE (p)-[rj:SUMMONS]->(j)
      SET rj.timestamp = $now
      // Also set them as a hocrux of jinni in addition to summoner
      MERGE (p)<-[rp:BONDS]-(j)
      SET rp.since = $now


      RETURN j.id as jid\",
    \"RETURN null as id\",
    {jinni: $jinni, signer: $signer, pid: $pid, now: $now}
  ) YIELD value

  RETURN value.jid as jinni;
"))

;; only create rj if p + j already exist. Do not overide existing rj data
(neo4j/defquery join-summoning-circle "
  MATCH (p:Avatar:Human {id: $pid})
  MATCH (j:Jinni:p2c {id: $jid})
  WITH p, j
  MERGE (j)-[rj:BONDS]->(p)
  ON CREATE SET rj.since = $now
  RETURN j.id as jinni
")

(neo4j/defquery apply-summoning-circle "
  MATCH (p:Avatar:Human {id: $pid})
  MATCH (j:Jinni:p2c {jid: $jid})
  WITH p, j
  MERGE (p)-[rj:DESIRES]->(j)
  ON CREATE SET rj.since = $now
  RETURN j.id as jinni
")

;; TODO jid as input once multi p2c per player
(neo4j/defquery get-circle-applicants "
  MATCH (p:Avatar:Human)-[:DESIRES]->(j:Jinni:p2c {id: $jid})
  
  RETURN COLLECT(p.id) as players
")