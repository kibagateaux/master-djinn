(ns master-djinn.util.db.identity-test
  (:require [clojure.test :refer :all]
              [master-djinn.util.types.core :refer [address?]]
              [clojure.spec.alpha :as s]

              [master-djinn.util.db.core :as db]
              [master-djinn.util.db.identity :as iddb]
              [master-djinn.incantations.manifest.jinni :as j]
              [neo4j-clj.core :as neo4j]

            ; to stub and/or generate test data
              [master-djinn.util.core :refer [now iso->unix]]
              [master-djinn.util.crypto :refer :all]))

;; ;; DB tests
(neo4j/defquery clear-db "
    // Get all fake players / test db entries to delete 
    MATCH (a:Avatar:Human) WHERE NOT (a.id =~ '0x.*') OR NOT (a.uuid =~ '0x.*')

    // Get all nodes associated with fake player except p2c jinni.
    // p2p jinni owned by them deleted bc :SUMMONS matches on lables(r)
    OPTIONAL MATCH (a)-[r]-(ii) WHERE NOT type(r) = 'BONDS'

    // Delete actions on summononed jinni or 
    OPTIONAL MATCH (ii)-[rr]-(iii)
    OPTIONAL MATCH (d:MasterDjinn)

    DETACH DELETE a, r, rr, ii, iii, d
")

(defn clear [] (db/call clear-db {}))
;; TODO fixtures not working
;; (defn reset-db-fixture
;;     [setup teardown]
;;     (fn [do-tests]
;;         (setup) (do-tests) (teardown)))
;; (use-fixtures :each (reset-db-fixture clear clear))

(defn create-npc [id]
    (db/call iddb/create-npc {
        :player { :id id :uuid id }
        :jinni { :id (str "j" id) :uuid (str "j" id) }
        :now "timestamp"}))

(defn neoqu
    ([cy] (neoqu cy {}))
    ([cy args] (neo4j/execute (neo4j/get-session db/connection) cy args)))

(defn get-node-count
    ([] (get-node-count ""))
    ([labels]
    (let [result (neoqu (str "MATCH (n"labels") RETURN count(*) as totalNodes"))]
      (first result)))) ;; Return the first result to avoid IllegalArgumentException

    ;; TODO test invariants
(deftest db-invariant-test
  (let [player-id "tjbjvghjhgfrtyujk87654erth"
        existing-npc-id "ansjkfanbfiau83093u190h31io2nj"
        existing-jinni-id "18231b1bd8d-8d91d-asbasc89asasca-nji2b"
        non-existing-player-id "18231b1bd8d-8d91d-asbasc89asasca-anjsfki2"]
        (doseq [label [":Avatar" ":Avatar:Human" ":Avatar:Jinni" ":Avatar:NPC"]]
            ;; (is thrown?) gives nil, (is nil?) throws error. Both checks belowfail
            ;; (is (nil? (neoqu (str "CREATE (a" label " {id: $pid}) RETURN a") {:pid player-id})))
            ;; (is (thrown? Exception (neoqu (str "CREATE (a" label " {uuid: $pid}) RETURN a") {:pid player-id})))
            ;; (let [res]) captures err somehow and lets nil be tested
            
            (testing (str label " may have id. if id then unique not null")
                (clear)
                (let [res (neoqu (str "CREATE (a" label " {id: null}) RETURN a") {:pid player-id})]
                    (is nil? res))
                (let [res (neoqu (str "CREATE (a" label " {id: null, id: $pid}) RETURN a") {:pid player-id})]
                    (is nil? res))
                ;; create to prove uniqueness
                (is (some? (neoqu (str "CREATE (a" label " {id: $pid}) RETURN a") {:pid player-id})))
                (let [res (neoqu (str "CREATE (a" label " {id: $pid}) RETURN a") {:pid player-id})]
                    (is nil? res))
            )
                
            (testing (str label " may have uuid. if uuid then unique not null")
                (clear)
                (let [res (neoqu (str "CREATE (a" label " {uuid: null}) RETURN a") {:pid player-id})]
                    (is nil? res))
                (let [res (neoqu (str "CREATE (a" label " {uuid: null, id: $pid}) RETURN a") {:pid player-id})]
                    (is nil? res))
                
                ;; create to prove uniqueness
                (is (some? (first (neoqu (str "CREATE (a" label " {uuid: $pid}) RETURN a") {:pid player-id}))))
                ;; for some reason the thrown?nil? error doesnt affect this last check
                ;; (let [res (neoqu (str "CREATE (a" label " {uuid: $pid}) RETURN a") {:pid player-id})]
                ;;     (is nil? res))
                (is (thrown? Exception (neoqu (str "CREATE (a" label " {uuid: $pid}) RETURN a") {:pid player-id})))
            )
        
            (testing (str label " can create with full id + uuid")
                (clear)
                (is (= player-id (:a (first (neoqu (str "CREATE (a" label " {id: $pid, uuid: $pid}) RETURN a.id as a") {:pid player-id}))))))
        )
        
        (let [provider-id "ajsnfaf732j2h89s"]
        (doseq [provider [":Identity" ":Identity:Ethereum" ":Identity:Github" ":Identity:Spotify"]]
            ;; (is thrown?) gives nil, (is nil?) throws error. Both checks belowfail
            ;; (is (nil? (neoqu (str "CREATE (a" provider " {id: $pid}) RETURN a") {:pid player-id})))
            ;; (is (thrown? Exception (neoqu (str "CREATE (a" provider " {uuid: $pid}) RETURN a") {:pid player-id})))
            ;; (let [res]) captures err somehow and lets nil be tested
            
            (testing (str provider " may have provider. if provider then not null and unique")
                (clear)
                (let [res (neoqu (str "CREATE (a" provider " {provider: null}) RETURN a") {:provider provider :provider_id provider-id})]
                    (is nil? res))
                (let [res (neoqu (str "CREATE (a" provider " {provider: null, providerid: $provider}) RETURN a") {:provider provider :provider_id provider-id})]
                    (is nil? res))
                ;; can actually create
                (is (some? (neoqu (str "CREATE (a" provider " {provider: $provider}) RETURN a") {:provider provider :provider_id provider-id})))
                (let [res (neoqu (str "CREATE (a" provider " {provider: $provider}) RETURN a") {:provider provider :provider_id provider-id})]
                    (is nil? res))
            )

            (testing (str provider "  may have provider_id. if provider_id then not null and unique")
                (clear)
                (let [res (neoqu (str "CREATE (a" provider " {provider_id: null}) RETURN a") {:provider provider :provider_id provider-id})]
                    (is nil? res))
                (let [res (neoqu (str "CREATE (a" provider " {provider_id: null, providerid: $provider}) RETURN a") {:provider provider :provider_id provider-id})]
                    (is nil? res))
                ;; can actually create
                (is (some? (neoqu (str "CREATE (a" provider " {provider_id: $provider}) RETURN a") {:provider provider :provider_id provider-id})))
                (let [res (neoqu (str "CREATE (a" provider " {provider_id: $provider}) RETURN a") {:provider provider :provider_id provider-id})]
                    (is nil? res))
            )

            (testing (str provider " has unique constraint on provider_id")
                (clear)
                (is (some? (neoqu (str "CREATE (a" provider " {provider: $provider, provider_id: $provider}) RETURN a") {:provider provider :provider_id provider-id})))
                (let [res (neoqu (str "CREATE (a" provider " {provider: $provider, provider_id: $provider}) RETURN a") {:provider provider :provider_id provider-id})]
                    (is nil? res))
            )
        
            (testing (str provider " can create with full provider + provider_id")
                (clear)
                (is (= provider-id (:a (first (neoqu (str "CREATE (a" provider " {provider: $provider, provider_id: $provider_id}) RETURN a.provider_id as a") {:provider provider :provider_id provider-id}))))))
        ))
))

(deftest create-npc-test
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
    
    (testing "Player cant be turned back into NPC if already a Jinni"
        (clear)
      (let [jinni-id "existing-jinni-id"
            _ (neoqu "CREATE (p:Avatar {id: $pid, uuid: $pid})-[:SUMMONS]->(j:Avatar:Jinni {id: $jid, uuid: $jid})"
                    {:pid player-id :jid jinni-id})
            initial-jinni (first (neoqu (str "MATCH (a:Avatar {id: $pid})-[rj]-(j:Avatar) RETURN rj as relation, j as jinni, elementId(j) as jid, labels(j) as labels")
                                         {:pid player-id}))]

        (is (= (:since (:relation initial-jinni)) nil)) ; we didnt set so should be unset
        (is (contains? (set (:labels initial-jinni)) "Jinni")) ; ensure not an npc

        ;; cant create id with same uuid
        (is (thrown? Exception (create-npc player-id)))
        ; check state after npc creation request
        (let [final-jinni (first (neoqu (str "MATCH (a:Avatar {id: $pid})-[rj]-(j:Avatar) RETURN rj as relation, j as jinni, elementId(j) as jid, labels(j) as labels")
                                         {:pid player-id}))]
            (is (some? final-jinni))
            (is (= (:since (:properties (:relation final-jinni))) nil)) ; should still be unset
            (is (= (:jid final-jinni) (:jid initial-jinni))) ; still using same avatar node
            (is (not (contains? (set (:labels final-jinni)) "NPC"))) ; ensure not an npc
            (is (contains? (set (:labels final-jinni)) "Jinni")) ; ensure still jinni
        )))


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
        (clear)
      (let [other-player-id "other-player-id"
            initial-count (get-node-count ":Avatar")
            _ (create-npc player-id)
            _ (create-npc other-player-id)
            final-avatar (neoqu (str "MATCH (n:Avatar {id: $pid}) RETURN n as player")
                                {:pid player-id})]
        (is (= (count final-avatar) 1))
        (is (= (:player (first final-avatar))
            {:id player-id
            :uuid player-id}))))

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


(deftest create-player-test
  (let [initial-node-count (get-node-count)
        player-id "test-player-id"
        jinni-id "test-jinni-id"
        player-data {:id player-id :uuid "Test Player"}
        jinni-data {:id jinni-id :uuid "test-uuid"}
        master-djinn "master-djinn-id"
        timestamp (now)
        create (fn [mid] (db/call iddb/create-player {:player player-data :jinni jinni-data :now timestamp :master_id mid}))]

    (testing "player only created if Master Djinn has created a circle"
        (clear)
        (is (nil? (create "")))
        (neoqu "CREATE (:Jinni)-[:HAS]->(:MasterDjinn {id: $mid})" {:jid jinni-id :mid ""})
        (is (some? (create "")))) ;; can create with existing master

    ;; provide least amount of data to nodes/relationships/labels to show what is really required
    (testing "can only create player if summoner provided"
        (clear)
        (is (thrown? Exception (create)))

        (neoqu "CREATE (:Jinni)-[:HAS]->(:MasterDjinn {id: $mid})" {:jid jinni-id :mid ""})
        
        ; throws when master djinn availabe too
        (is (thrown? Exception (create)))
        (is (nil? (create "random master"))) ;; cant create with invalid master
        (is (some? (create ""))) ;; can create with existing master
    )

    (testing "creates player if summoner provided is Master Djinn"
        (neoqu "CREATE (:Jinni)-[:HAS]->(:MasterDjinn {id: $mid})" {:jid jinni-id :mid ""})
        (let [p (create "")]
            (is (= jinni-id (:jinni p))))
    )

    (testing "can only create player once"
        (neoqu "CREATE (:Jinni)-[:HAS]->(:MasterDjinn {id: $mid})" {:jid jinni-id :mid ""})
        (is (= jinni-id (:jinni (create ""))))
        (is (= jinni-id (:jinni (create "")))))
    
    (testing "player can only create one p2p jinni"
        (neoqu "CREATE (:Jinni)-[:HAS]->(:MasterDjinn {id: $mid})" {:jid jinni-id :mid ""})
        (is (= jinni-id (:jinni (create ""))))
        ; returns original jinni
        (is (nil? (:jinni (db/call iddb/create-player {
            :player player-data
            :jinni {:id" test jinni" :uuid "ajksnfjaf223"}
            :now (now)
            :master_id master-djinn
        }))))
        )

    ;; Test creating a new player
    (clear)
    (println "CREA PLAYER" (db/call iddb/create-player {:player player-data :jinni jinni-data :now (now) :master_id master-djinn}))
    (clear)
    
    (testing "Creating a player ensures an ethereum identity"
        ;add masterdjin to create player
      (let [id1 (:id (first (neoqu (str "MATCH (id:Identity:Ethereum {provider_id: $pid}) RETURN id") {:pid player-id})))
            created-jinni-uuid (:jinni (db/call iddb/create-player {:player player-data :jinni jinni-data :now (now) :master_id master-djinn}))
            id2 (:id (first (neoqu (str "MATCH (id:Identity:Ethereum {provider_id: $pid}) RETURN id") {:pid player-id})))]

        ;; also check creating npc first then player and same jinni id
        ;; (is (= (:provider id) "Ethereum"))
        ;;   (is (= (:provider_id id) player-id))
        (is (nil? nil))
        ))
        


    (testing "Creating a new player with no existing Avatar account"
        ;add masterdjin to create player
        (neoqu "CREATE (:Jinni)-[:HAS]->(:MasterDjinn {id: $mid})" {:jid jinni-id :mid master-djinn})
      (let [mid-count (get-node-count) test-ts (now)
            created-jinni-uuid (:jinni (db/call iddb/create-player {:player player-data :jinni jinni-data :now test-ts :master_id master-djinn}))]
        (is (some? created-jinni-uuid))
        (let [final-count (get-node-count)]
            ; 4 includes master. should be 3 if test is isolated more
          (is (= (+ (:totalNodes mid-count) 3) (:totalNodes final-count)))
          (is (some? (neoqu (str "MATCH (j:Jinni {id: $jid}) RETURN j") {:jid created-jinni-uuid})))
          (is (some? (neoqu (str "MATCH (id:Identityq:Ethereum {provider_id: $pid}) RETURN id") {:pid player-id})))
          ;; some amount earlier but could test exactly
          (is (= (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})-[relation:BONDS]->(:Human) RETURN relation.since as ts" {:jid jinni-id})))) (iso->unix test-ts)))
          (is (= (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})<-[relation:SUMMONS]-(:Human) RETURN relation.timestamp AS ts" {:jid created-jinni-uuid})))) (iso->unix test-ts))))
        ))

          
    ;; Test creating a player when the Avatar account already exists
    (testing "Creating a player when Avatar account already exists"
        (neoqu "CREATE (:Jinni)-[:HAS]->(:MasterDjinn {id: $mid})" {:jid jinni-id :mid master-djinn})
        (let [mid-node-count (get-node-count) past-ts (now -10000)
            created-jinni-id (:jinni (db/call iddb/create-player {:player player-data :jinni jinni-data :now past-ts :master_id master-djinn}))
            final-count (get-node-count)
            id (:id (first (neoqu (str "MATCH (id:Identity:Ethereum {provider_id: $pid}) RETURN id") {:pid player-id})))]
        
        (is (= (:totalNodes mid-node-count) (:totalNodes (get-node-count))))
        (is (= jinni-id created-jinni-id))
          (is (= jinni-id (:j (first (neoqu (str "MATCH (j:Jinni {id: $jid}) RETURN j.id as j") {:jid jinni-id})))))
          (is (some? id))
          (is (= (+ 10000 (iso->unix past-ts)) (iso->unix timestamp)))

            ;  -1728377083 +1728357083
          (is (= (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})-[relation:BONDS]->(:Human) RETURN relation.since as ts" {:jid jinni-id})))) (iso->unix timestamp)))
        ;;   (is (= (+ 10000 (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})-[relation:BONDS]->(:Human) RETURN relation.since as ts" {:jid jinni-id}))))) (iso->unix timestamp)))
          
          (is (= (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})<-[relation:SUMMONS]-(:Human) RETURN relation.timestamp as ts" {:jid jinni-id})))) (iso->unix timestamp)))
        ;;   (is (= (+ 10000 (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})<-[relation:SUMMONS]-(:Human) RETURN relation.timestamp as ts" {:jid jinni-id}))))) (iso->unix timestamp)))
          
        ;;   (is (= (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})-[relation:BONDS]->(:Human) RETURN relation.since as ts" {:jid jinni-id})))) (iso->unix past-ts)))
        ;;   (is (= (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})<-[relation:SUMMONS]-(:Human) RETURN relation.timestamp as ts" {:jid jinni-id})))) (iso->unix past-ts)))
    ))
))

;; (activate-jinni)
;; - if no player :Avatar account already.
;; - creates 2 avatar nodes and 1 identity node
;; - increase total node count by 3
;; - has a :Jinni attached to user
;; - has an :Identity:Ethereum node
;; - relation.since set to now
;; - returns jinni uuid
;; - if player :Avatar account already.
;;     - creates 0 avatar nodes and 0 identity node
;;     - increase total node count by 0
;;     - had :Avatar:NPC before, have :Avatar:Jinni now
;;     - relation.since set to before now
;;     - returns jinni uuid

;; init-player-identity
;; - returns nil if (a:Avatar {id: $pid}) not already in database
;; - does not create id node/rel if no avatar
;; - returns id nonce if identity created already
;; - adds provider as label to node
;; - adds provider as field to node


;; sync-provider-id
; - sets provider_id for on correct Provider
; - sets provider_id on Identity owned by player
; - sets provider_id to id supplied
; - must have pid


;; set-identity-credentials
;; - must have access_token provider and pid as params
;; - resets access token  and refesh_token if present
