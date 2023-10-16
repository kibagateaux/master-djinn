(ns master-djinn.util.db.identity
  (:require [neo4j-clj.core :as neo4j]
            [master-djinn.util.types.core :refer [load-config]])
  (:import (java.net URI)))

(defonce identity-db
    (let [{:keys [identitydb-uri identitydb-user identitydb-pw]} (load-config)]
        (neo4j/connect (URI. identitydb-uri) identitydb-user identitydb-pw)))

(neo4j/defquery create-player "
    CREATE (p:Avatar $player)
")

;; TODO should we make relationship :WANTS instead of :HAS?
;; No real benefit besides making status of oauth more semantic. requires updating it on completion later which is annoying. 
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