(ns master-djinn.util.db.identity
  (:require [neo4j-clj.core :as neo4j]
            [master-djinn.util.db.core :as db]))

(neo4j/defquery define-id-constraints "
    CREATE CONSTRAINT uniq_id_per_provider IF NOT EXISTS
        FOR (id:Identity)
        REQUIRE (id.provider, id.id) IS UNIQUE;

    CREATE CONSTRAINT uniq_avatar_id IF NOT EXISTS
        FOR (a:Avatar)
        REQUIRE a.id IS UNIQUE;

    CREATE CONSTRAINT uniq_avatar_id IF NOT EXISTS
        FOR (a:Avatar)
        REQUIRE a.uuid IS UNIQUE;
")

;; Set UUID to not create duplicate avatar that only has id from setting :Widgets
(neo4j/defquery create-player (str
    ; ensure vouching master already exists and API wasnt exploited somehow
    "MATCH (m:Provider:"db/MASTER_DJINN_DATA_PROVIDER" {id: $master_id})
    WITH m \n"
    ; create new player preventing override if already NPC
    "MERGE (p:Avatar:Human { id: $player.id })
    ON CREATE
        SET p = $player
        MERGE (p)-[:HAS]->(id:Identity:Ethereum { provider_id: $player.id, provider: 'Ethereum' }) \n"
    ; Adds trust network metadata of which master djinn approved the player
    "MERGE (m)-[:ATTESTS]->(p)\n"
    ; (p)--(j) merge assumes only one jinni per player (intentional for now)
    "MERGE (p)-[SUMMONS]->(j:Avatar:p2p)
    MERGE (j)-[rj:BONDS]->(p) \n"
    ; if new player, ON CREATE SET to prevent acciental overriding jinni data
    "ON CREATE 
        SET j = $jinni,
            j:Jinni,
            rj.since = $now \n"
    ; if extant player as NPC, convert to full player preserving their existing game state.
    "ON MATCH 
        REMOVE j:NPC
        SET j:Jinni

    RETURN $jinni.uuid as jinni
"))

;; creates npc avatar so we can save their widget config when they first download the game
;; they convert from npc to p2p when vouched by master jinn and preserving widgets and action data
(neo4j/defquery create-npc (str
    ; ensure account doesnt get overwritten
    "MERGE (p:Avatar:Human { id: $player.id }) \n"
    ; if player never existed then create new avatar for them (non jinni NPC)
    "ON CREATE 
        MERGE (p)<-[rj:BONDS]-(j:Avatar:NPC:p2p)
        MERGE (p)-[:SUMMONS]->(j)
        SET p = $player,
            rj.since = $now,
            j = $jinni \n"
    ; add their randomly generated identity to player profile
    "MERGE (p)-[:HAS]->(id:Identity:Ethereum { provider_id: $player.id, provider: 'Ethereum' }) \n"
    ; if they were vouched for by existing player or jinni then add social graph
    "CASE WHEN $summoner IS NOT NULL
        MERGE (p)<-[:ATTESTS]-(:Provider {id: $summoner})
    END

    RETURN j.id as jid
"))

;; TODO should? add rel for (:Avatar:Jinni {id: "master-djinn"})-[:ATTESTS]->(:Identity)
;; and add status metadata - requested, verifying, verified, etc. ? Allows other Avatar to attest to identities
(neo4j/defquery init-player-identity "
    MATCH (p:Avatar { id: $pid })
    MERGE (p)-[:HAS]->(id:Identity {provider: $provider})

    WITH id
    CALL apoc.create.addLabels(id, [$provider]) YIELD node
    RETURN ID(node) as id
")

(neo4j/defquery get-player-attesters "
    MATCH (p:Avatar { id: $pid })
    MATCH (p)<-[:ATTESTS]->(pr:Provider)
    
    RETURN COLLECT(pr) as attesters
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