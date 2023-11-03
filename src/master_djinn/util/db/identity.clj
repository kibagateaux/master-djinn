(ns master-djinn.util.db.identity
  (:require [neo4j-clj.core :as neo4j]
            [master-djinn.util.types.core :refer [load-config]])
  (:import (java.net URI)))

(defonce identity-db
    (let [{:keys [identitydb-uri identitydb-user identitydb-pw]} (load-config)]
        (println "IDENTITYDB: " identitydb-uri identitydb-user identitydb-pw)
        ;; (neo4j/connect (URI. identitydb-uri) identitydb-user identitydb-pw)
        ))
    ;; (let [{:keys [identitydb-uri identitydb-user identitydb-pw]} (load-config)]
    ;;     (neo4j/connect (URI. identitydb-uri) identitydb-user identitydb-pw)))

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

(neo4j/defquery create-player "
    MERGE (p:Avatar:Human {id: $player.id, uuid: $player.uuid})
    MERGE (p)-[:HAS]->(id:Identity:Ethereum {id: $player.id})
    MERGE (p)-[:BONDING]->(j:Avatar:Jinn {id: $jinni.id, uuid: $jinni.uuid})
    RETURN $jinni.uuid as jinni
")

;; TODO should? add rel for (:Avatar:Jinn {id: "master-djinn"})-[:ATTESTS]->(:Identity)
;; and add status metadata - requested, verifying, verified, etc. ? Allows other Avatar to attest to identities
(neo4j/defquery init-player-identity "
    MATCH (p:Avatar { pid: $pid })
    MERGE (p)-[:HAS]->(id:Identity {provider: $provider})

    WITH id
    CALL apoc.create.addLabels(id, [$provider]) YIELD node
    RETURN ID(node) as identity
")

(neo4j/defquery match-nonce-to-avatar "
    MATCH (p:Avatar)-[:HAS]->(id:Identity {nonce: $nonce})
    RETURN p as player
")