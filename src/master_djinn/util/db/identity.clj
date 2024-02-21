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
;; Adds trust network metadata of which master djinn approved the player
(neo4j/defquery create-player "
    MERGE (p:Avatar:Human { id: $player.id })
    SET p = $player
    
    MERGE (p)-[:HAS]->(id:Identity:Ethereum { provider_id: $player.id, provider: 'Ethereum' })
    MERGE (p)<-[rj:BONDS]-(j:Avatar:Jinn:P2P)
    MERGE (p)<-[:ATTESTS]-(Provider {id: $master_id})

    SET j = $jinni
    SET rj.since = $now

    RETURN $jinni.uuid as jinni
")

;; TODO should? add rel for (:Avatar:Jinn {id: "master-djinn"})-[:ATTESTS]->(:Identity)
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