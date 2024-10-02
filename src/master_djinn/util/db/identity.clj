(ns master-djinn.util.db.identity
  (:require [neo4j-clj.core :as neo4j]
            [master-djinn.util.db.core :as db]))


;; Set UUID to not create duplicate avatar that only has id from setting :Widgets
(neo4j/defquery create-player (str "
    // ensure vouching master already exists and API wasnt exploited somehow
    MATCH (m:Provider:"db/MASTER_DJINN_DATA_PROVIDER" {id: $master_id})
    WITH m

    // create new player preventing override if already NPC
    MERGE (p:Avatar:Human { id: $player.id })
    MERGE (p)-[:HAS]->(id:Identity:Ethereum { provider_id: $player.id, provider: 'Ethereum' }) 
    ON CREATE
        SET p = $player

    // (p)--(j) merge assumes only one jinni per player (intentional for now)
    MERGE (p)-[:SUMMONS]->(j:Avatar:p2p)
    MERGE (j)-[rj:BONDS]->(p)

    // if new player, ON CREATE SET to prevent acciental overriding jinni data
    ON CREATE 
        SET j = $jinni,
        rj.since = $now,
        j:Jinni

    // if extant player as NPC, convert to full player preserving their existing game state.
    REMOVE j:NPC

    RETURN $jinni.uuid as jinni
"))

;; creates npc avatar so we can save their widget config when they first download the game
;; they convert from npc to p2p when vouched by master jinn and preserving widgets and action data
(neo4j/defquery create-npc "
    // ensure account doesnt get overwritten 
    MERGE (p:Avatar:Human { id: $player.id })
    
    // only allows one personal avatar (npc or jinni) per player atm
    MERGE (p)<-[rj:BONDS]-(j:Avatar:p2p)
    MERGE (p)-[:SUMMONS]->(j)

    // add their randomly generated identity to player profile
    MERGE (p)-[:HAS]->(id:Identity:Ethereum { provider_id: $player.id, provider: 'Ethereum' })

    // if player never existed then create new avatar for them (non jinni NPC)
    ON CREATE 
        SET p = $player,
            rj.since = $now,
            j = $jinni,
            j:NPC

    RETURN j.id as jid")

;; TODO should? add rel for (:Avatar:Jinni {id: "master-djinn"})-[:ATTESTS]->(:Identity)
;; and add status metadata - requested, verifying, verified, etc. ? Allows other Avatar to attest to identities
(neo4j/defquery init-player-identity "
    MATCH (p:Avatar { id: $pid })
    MERGE (p)-[:HAS]->(id:Identity {provider: $provider})

    WITH id
    CALL apoc.create.addLabels(id, [$provider]) YIELD node
    RETURN ID(node) as id
")

;; set attributes individually to not erase other identity data e.g. username
;; TODO should add scope to identity? diff function so we dont override when refreshing tokens
(neo4j/defquery set-identity-credentials "
    MATCH (p:Avatar { id: $pid })-[:HAS]->(id:Identity {provider: $provider})
    SET id.access_token = $access_token
    SET id.refresh_token = $refresh_token
    
    RETURN ID(id) as id
")

(neo4j/defquery sync-provider-id "
    MATCH (p:Avatar { id: $pid })-[:HAS]->(id:Identity {provider: $provider})
    SET id.provider_id = $provider_id
    RETURN id
")

(neo4j/defquery get-identity "
    MATCH (p:Avatar { id: $pid })-[:HAS]->(id:Identity {provider: $provider})
    RETURN id
")

(defn getid [player_id provider]
    (:id (db/call get-identity {:pid player_id :provider provider})))

;; used for oauth flow to ensure callback is from right provider and authorized by player
(neo4j/defquery match-nonce-to-avatar "
    MATCH (p:Avatar)-[:HAS]->(id:Identity {nonce: $nonce})
    RETURN p as player
")