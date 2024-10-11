(ns master-djinn.util.testing-helpers
  (:require [clojure.test :refer :all]
                [master-djinn.util.db.core :as db]
              [master-djinn.util.db.identity :as iddb]
              [neo4j-clj.core :as neo4j]))


(neo4j/defquery clear-db "
    // Get all fake players / test db entries to delete 
    MATCH (a:Avatar) WHERE NOT (a.id =~ '0x.*') OR NOT (a.uuid =~ '0x.*')

    // Get all nodes associated with fake player except p2c jinni.
    // p2p jinni owned by them deleted bc :SUMMONS matches on lables(r)
    OPTIONAL MATCH (a)--(j:Avatar)
    OPTIONAL MATCH (act:Action)
    OPTIONAL MATCH (id:Identity)
    OPTIONAL MATCH (m:Jinni) WHERE NOT (a.id =~ '0x.*') OR NOT (a.uuid =~ '0x.*')

    // Delete actions on summononed jinni or 
    OPTIONAL MATCH (j)--(iv)
    OPTIONAL MATCH (d:MasterDjinn) WHERE NOT (a.id =~ '0x.*') OR NOT (a.uuid =~ '0x.*')

    DETACH DELETE a, act, id, iv, j, d, m
")

(defn clear [] (db/call clear-db {}))

(defn neoqu
    ([cy] (neoqu cy {}))
    ([cy args] (neo4j/execute (neo4j/get-session db/connection) cy args)))
      

(defn get-node-count
    ([] (get-node-count ""))
    ([labels]
    (let [result (neoqu (str "MATCH (n"labels") RETURN count(*) as totalNodes"))]
      (first result)))) ;; Return the first result to avoid IllegalArgumentException
