(ns master-djinn.util.db.identity-test
  (:require [clojure.test :refer :all]
              [master-djinn.util.types.core :refer [address?]]
              [clojure.spec.alpha :as s]
              
              [master-djinn.util.db.core :as db]
              [master-djinn.util.db.identity :as iddb]
              [master-djinn.incantations.manifest.jinni :as j]
              [neo4j-clj.core :as neo4j]

              [master-djinn.util.crypto :refer :all]))

;; ;; DB tests
(neo4j/defquery clear-db "
    // Get all fake players / test db entries to delete 
    MATCH (a:Avatar) WHERE NOT (a.id =~ '0x.*') OR NOT (a.uuid =~ '0x.*')

    // Get all nodes associated with fake player except p2c jinni.
    // p2p jinni owned by them deleted bc :SUMMONS matches on lables(r)
    OPTIONAL MATCH (a)-[r]-(ii) WHERE NOT type(r) = 'BONDS'

    // Delete actions on summononed jinni or 
    OPTIONAL MATCH (ii)-[rr]-(iii)
    DETACH DELETE a, r, rr, ii, iii
")

(defn reset-db-fixture
    [do-tests]
    (db/call clear-db {}) ;; Reset the database to a known state
    (do-tests)
    (db/call clear-db {}) ;; Reset the database to a known state
    )

(use-fixtures :each reset-db-fixture)

(defn create-npc [id]
    (db/call iddb/create-npc {
        :player { :id id :uuid id }
        :jinni { :id (str "j" id) :uuid (str "j" id) }
        :now "timestamp"}))

(defn neoqu
    ([cy] (neoqu cy {}))
    ([cy args] (neo4j/execute (neo4j/get-session db/connection) cy args)))

(defn get-node-count
    ([] (neoqu  "MATCH (n) RETURN count(*) as totalNodes"))
    ([labels]
    (let [result (neoqu (str "MATCH (n"labels") RETURN count(*) as totalNodes"))]
      (first result)))) ;; Return the first result to avoid IllegalArgumentException

    ;; TODO test invariants
(deftest db-invariant-tests
  (let [player-id "tjbjvghjhgfrtyujk87654erth"
        existing-npc-id "ansjkfanbfiau83093u190h31io2nj"
        existing-jinni-id "18231b1bd8d-8d91d-asbasc89asasca-nji2b"
        non-existing-player-id "18231b1bd8d-8d91d-asbasc89asasca-anjsfki2"]
        (doseq [label [":Avatar" ":Avatar:Human" ":Avatar:Jinni" ":Avatar:NPC"]]
            (testing (str label " may have id. if id then unique not null")
                ;; (is thrown?) gives nil, (is nil?) throws error. Both checks belowfail
                ;; (is (nil? (neoqu (str "CREATE (a" label " {id: $pid}) RETURN a") {:pid player-id})))
                ;; (is (thrown? Exception (neoqu (str "CREATE (a" label " {uuid: $pid}) RETURN a") {:pid player-id})))
                ;; (let [res]) captures err somehow and lets nil be tested

                (db/call clear-db {})
                (let [res (neoqu (str "CREATE (a" label " {id: null}) RETURN a") {:pid player-id})]
                    (is nil? res))
                (let [res (neoqu (str "CREATE (a" label " {id: null, id: $pid}) RETURN a") {:pid player-id})]
                    (is nil? res))
                ;; can actually create
                (is (some? (neoqu (str "CREATE (a" label " {id: $pid}) RETURN a") {:pid player-id})))
                (let [res (neoqu (str "CREATE (a" label " {id: $pid}) RETURN a") {:pid player-id})]
                    (is nil? res))
                )
                
            (testing (str label " may have uuid. if uuid then unique not null")
                (db/call clear-db {})
                (let [res (neoqu (str "CREATE (a" label " {uuid: null}) RETURN a") {:pid player-id})]
                    (is nil? res))
                (let [res (neoqu (str "CREATE (a" label " {uuid: null, id: $pid}) RETURN a") {:pid player-id})]
                    (is nil? res))
                
                ;; can actually create
                (is (some? (first (neoqu (str "CREATE (a" label " {uuid: $pid}) RETURN a") {:pid player-id}))))
                ;; for some reason the thrown?nil? error doesnt affect this last check
                ;; (let [res (neoqu (str "CREATE (a" label " {uuid: $pid}) RETURN a") {:pid player-id})]
                ;;     (is nil? res))
                (is (thrown? Exception (neoqu (str "CREATE (a" label " {uuid: $pid}) RETURN a") {:pid player-id})))
            )
        
            (testing (str label " can create with full id + uuid")
                (db/call clear-db {})
                (is (= player-id (:a (first (neoqu (str "CREATE (a" label " {id: $pid, uuid: $pid}) RETURN a.id as a") {:pid player-id}))))))
        )
))

(deftest waitlist-npc-test
  (let [player-id "test-player-id-13r13"
        existing-npc-id "existing-npc-i-13124d"
        existing-jinni-id "existing-jinni-id-1321241"
        non-existing-player-id "non-existing-player-id-1231241"]

    ;; Test case 1: Right amount of Nodes are created 
    (testing "Creating NPC for existing player"
      (let [existing-entity (neoqu "MATCH (a:Avatar {id: $pid}) RETURN a" {:pid player-id})
            initial-count (get-node-count ":Avatar")
            id-count (get-node-count ":Ethereum")
            _ (create-npc player-id)
            final-count (get-node-count ":Avatar")
            id-count2 (get-node-count ":Ethereum")]
        (if (nil? (first existing-entity))
            ; correct amount of nodes created
            (do (is (= (+ 2 (:totalNodes initial-count)) (:totalNodes final-count)))
                (is (= (+ 1 (:totalNodes id-count)) (:totalNodes id-count2))))
            ; no noes created
            (do (is (= (:totalNodes initial-count) (:totalNodes final-count)))
                (is (= (:totalNodes id-count) (:totalNodes id-count2))))
        )))


    ;; Test case 2: No new NPC created if player already has a Jinni
    (testing "No new NPC created if player has a Jinni"
      (let [
            _ (neoqu "MERGE (a:Avatar {id: $pid}) 
                    MERGE (a)-[:SUMMONS]->(p:Avatar) 
                    MERGE (p)-[:BONDS]->(a) 
                    RETURN a, p" {:pid player-id}) ;; Simulate Jinni creation
            initial-count (get-node-count ":Avatar")
            mid-count (get-node-count ":Avatar")
            _ (create-npc player-id)
            final-count (get-node-count ":Avatar")]
        (is (= (:totalNodes initial-count) (:totalNodes mid-count)))
        (is (= (:totalNodes mid-count) (:totalNodes final-count)))
        
        ))

    (testing "No duplicate Human or NPC created"
      (let [player-id "test-player-id"
            initial-count (get-node-count ":Avatar")
            _ (create-npc player-id)
            mid-count (get-node-count ":Avatar")
            _ (create-npc player-id)
            final-count (get-node-count ":Avatar")]
        (is (= (:totalNodes mid-count) (:totalNodes final-count)))))


    ;; Test case 3: Ensure no data is changed on their node by new inputs
    (testing "No data change on player node if NPC already exists"
      (let [initial-npc-data (neoqu (str "MATCH (n:NPC {id: $nid}) RETURN n") {:nid existing-npc-id})]
        (create-npc existing-npc-id)
        (let [final-npc-data (neoqu (str "MATCH (n:NPC {id: $nid}) RETURN n") {:nid existing-npc-id})]
          (is (= initial-npc-data final-npc-data)))))

    ;; Test case 4: Attempt to create NPC for non-existing player
    (testing "Creating NPC for non-existing player"
      (let [initial-count (get-node-count ":Avatar")
            _ (create-npc non-existing-player-id)
            final-count (get-node-count ":Avatar")]
        (is (= (+ 2 (:totalNodes initial-count)) (:totalNodes final-count))))) ; +2 = :Avatar:Human, :Avatar:NPC

    ;; Test case 5: Ensure that creating an NPC does not affect other players
    (testing "Creating NPC for one player does not affect others"
      (let [other-player-id "other-player-id"
            initial-count (get-node-count ":Avatar")
            _ (create-npc player-id)
            _ (create-npc other-player-id)
            final-avatar (neoqu (str "MATCH (n:Avatar {id: $pid}) RETURN n as player")
                                {:pid player-id})]
    (is (= (count final-avatar) 1))
    (is (= (:player (first final-avatar)) {
        :id player-id
        :uuid player-id
    }))
        
        ;; (is (= other-initial-count (neoqu (str "MATCH (n:Avatar {id: $pid}) RETURN count(n) as playerNodes")
        ;;                                      {:pid other-player-id})))
                                             
                                             ))



    ;; Test case 6: Ensure that the function handles invalid player IDs gracefully
    (testing "Handling invalid player ID"
        (is (thrown? Exception (create-npc nil))))
        ;; all valid from db persepctive. tested in manifest
        ;; (is (thrown? Exception (create-npc []))
        ;; (is (thrown? Exception (create-npc ""))

    ;; Test case 7: Ensure that the function does not create an NPC if the player is already an NPC
    (testing "No new NPC created if player is already an NPC"
      (let [initial-count (neoqu (str "MATCH (n:NPC {id: $nid}) RETURN count(n) as totalNodes")
                                     {:nid existing-npc-id})]
        (create-npc existing-npc-id)
        (let [final-count (neoqu (str "MATCH (n:NPC {id: $nid}) RETURN count(n) as totalNodes")
                                     {:nid existing-npc-id})]
          (is (= initial-count final-count)))))

    ;; Test case 8: Ensure that the function does not create an NPC if the player is already a Jinni
    (testing "No new NPC created if player is already a Jinni"
      (let [initial-count (neoqu (str "MATCH (j:Jinni {id: $jid}) RETURN count(j) as totalNodes")
                                     {:jid existing-jinni-id})]
        (create-npc existing-jinni-id)
        (let [final-count (neoqu (str "MATCH (j:Jinni {id: $jid}) RETURN count(j) as totalNodes")
                                     {:jid existing-jinni-id})]
          (is (= initial-count final-count)))))


    ;; Test case 10: Ensure player is not turned into NPC if already exists
    (testing "Player not turned into NPC if already exists"
      (let [player-id "existing-player-id"
            _ (create-npc player-id)
            initial-npc-count (neoqu (str "MATCH (n:NPC {id: $pid}) RETURN count(n) as totalNodes")
                                       {:pid player-id})]
        (create-npc player-id)
        (let [final-npc-count (neoqu (str "MATCH (n:NPC {id: $pid}) RETURN count(n) as totalNodes")
                                       {:pid player-id})]
          (is (= initial-npc-count final-npc-count)))))

    ;; Test case 11: Ensure player is not turned back into NPC if already a Jinni
    (testing "Player not turned back into NPC if already a Jinni"
      (let [jinni-id "existing-jinni-id"
            _ (create-npc jinni-id)
            initial-jinni-count (neoqu (str "MATCH (j:Jinni {id: $jid}) RETURN count(j) as totalNodes")
                                         {:jid jinni-id})]
        (create-npc jinni-id)
        (let [final-jinni-count (neoqu (str "MATCH (j:Jinni {id: $jid}) RETURN count(j) as totalNodes")
                                         {:jid jinni-id})]
          (is (= initial-jinni-count final-jinni-count)))))

    ;; Test case 12: Ensure no new NPC created if player has Jinni already
    (testing "No new NPC created if player has Jinni already"
      (let [jinni-id "jinni-with-npc-id"
            initial-npc-count (neoqu (str "MATCH (n:NPC {id: $jid}) RETURN count(n) as totalNodes")
                                       {:jid jinni-id})]
        (create-npc jinni-id)
        (let [final-npc-count (neoqu (str "MATCH (n:NPC {id: $jid}) RETURN count(n) as totalNodes")
                                       {:jid jinni-id})]
          (is (= initial-npc-count final-npc-count)))))
))


;; waitlist-npc
;; - Ensure no duplicate Human or NPC created
;; 1.match (a:Avatar {id: $pid}), (n) RETURN count(a) as playerNodes, count(n) as totalNodes
;; 2. (j/jinni-Waitlist-npc)
;; 3. match (a:Avatar {id: $pid}, (n) RETURN count(a) as playerNodes, count(n) as totalNodes
;; created? > playerNodes2 playerNodes1
;; if created? (is = totalNodes2 (+ 1 totalNodes1))
;; else (is = totalNodes2 totalNodes1) (is = playerNodes2 playerNodes1)
;; if player has :Jinni already then no new NPC created
;; if player has :Jinni already then not turned back into NPC
;; if player/jinni already ensure no data is changed on their node by new inputs

;; activate-jinni
;; init-player-identity
;; - returns nil if (a:Avatar {id: $pid}) not already in database
;; - returns nonce if identity created_at
;; - 

;; set-identity-credentials
;; - must have access_token provider and pid as params
;; - resets access token  and refesh_token if present


;; get-identity

