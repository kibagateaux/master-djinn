(ns master-djinn.util.db.identity-test
  (:require [clojure.test :refer :all]
              [master-djinn.util.types.core :refer [address? avatar->uuid]]
              [clojure.spec.alpha :as s]

              [master-djinn.util.db.core :as db]
              [master-djinn.util.db.identity :as iddb]
              [master-djinn.incantations.manifest.jinni :as j]
              [neo4j-clj.core :as neo4j]

            ; to stub and/or generate test data
            [master-djinn.util.testing-helpers :refer [clear neoqu get-node-count]]
              [master-djinn.util.core :refer [now iso->unix]]
              [master-djinn.util.crypto :refer :all]))

;; ;; DB tests

;; TODO#0 test invariants

;; TODO#1 fixtures not working
;; (defn reset-db-fixture
;;     [setup teardown]
;;     (fn [do-tests]
;;         (setup) (do-tests) (teardown)))
;; (use-fixtures :each (reset-db-fixture clear clear))

;; TODO#2 remove any calls, just testing iddb/

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
                (is (= provider-id (:a (first (neoqu (str "CREATE (a" provider " {provider: $provider, provider_id: $provider_id}) RETURN a.provider_id as a") {:provider provider :provider_id provider-id}))))))
        ))
))

(deftest create-npc-test
  (let [player-id "test-player-id-13r13"
        existing-npc-id "existing-npc-i-13124d"
        existing-jinni-id "existing-jinni-id-1321241"
        non-existing-player-id "non-existing-player-id-1231241"]


    (testing "Creating NPC for existing player generates right db nodes + rels"
      (let [existing-entity (neoqu "MATCH (a:Avatar {id: $pid}) RETURN a" {:pid player-id})
            initial-count (get-node-count ":Avatar")
            id-count (get-node-count ":Identity")
            _ (j/jinni-waitlist-npc player-id)
            final-count (get-node-count ":Avatar")
            id-count2 (get-node-count ":Identity")]
        (if (nil? (first existing-entity))
            ; correct amount of nodes created
            (do (is (= (+ 2 (:totalNodes initial-count)) (:totalNodes final-count)))
                (is (= (+ 1 (:totalNodes id-count)) (:totalNodes id-count2))))
            ; no noes created
            (do (is (= (:totalNodes initial-count) (:totalNodes final-count)))
                (is (= (:totalNodes id-count) (:totalNodes id-count2))))
        )))

    (testing "Creating NPC for non-existing player generates right db nodes + rels"
        (clear)
      (let [initial-count (get-node-count ":Avatar")
            id-count1 (get-node-count ":Identity")
            _ (j/jinni-waitlist-npc non-existing-player-id)
            id-count2 (get-node-count ":Identity")
            final-count (get-node-count ":Avatar")]
        ; +2 = :Avatar:Human, :Avatar:NPC
        (is (= (+ 2 (:totalNodes initial-count)) (:totalNodes final-count)))
        (is (= (+ 1 (:totalNodes id-count1)) (:totalNodes id-count2)))))

    (testing "No new NPC created if player has a Jinni"
        (clear)
        ; create master djinn so player can be created
        (neoqu "CREATE (:Jinni)-[:HAS]->(:MasterDjinn {id: $mid})" {:jid "summoner" :mid "summoner"})
      (let [initial-count (get-node-count ":Avatar")
            _ (db/call iddb/create-player {
                :player {:id player-id :uuid player-id}
                :jinni {:id "test jinni" :uuid "ajksnfjaf223"}
                :now (now)
                :master_id "summoner"
            })
            mid-count (get-node-count ":Avatar")
            __ (j/jinni-waitlist-npc player-id)
            final-count (get-node-count ":Avatar")]
        (is (= (+ 2 (:totalNodes initial-count)) (:totalNodes mid-count)))
        (is (= (:totalNodes mid-count) (:totalNodes final-count)))
        
        ))

    (testing "No duplicate Human or NPC created"
      (let [player-id "test-player-id"
            initial-count (get-node-count ":Avatar")
            _ (j/jinni-waitlist-npc player-id)
            mid-count (get-node-count ":Avatar")
            _ (j/jinni-waitlist-npc player-id)
            final-count (get-node-count ":Avatar")]
        (is (= (:totalNodes mid-count) (:totalNodes final-count)))
        (is (<= (:totalNodes initial-count) (:totalNodes mid-count)))))
    
    (testing "Player cant be turned back into NPC if already a Jinni"
        (clear)
        ; create initial NPC for testing
      (let [_ (neoqu "MERGE (p:Avatar {id: $pid, uuid: $pid})-[:SUMMONS {since: $now}]->(j:Avatar:Jinni {id: $jid, uuid: $jid})"
                    {:pid player-id :jid existing-jinni-id :now (now)})
            initial-jinni (first (neoqu (str "MATCH (a:Avatar {id: $pid})-[rj]-(j:Avatar) RETURN rj as relation, j as jinni, elementId(j) as jid, labels(j) as labels")
                                         {:pid player-id}))]
        
        (is (not (nil? initial-jinni)))
        (is (contains? (set (:labels initial-jinni)) "Jinni")) ; ensure not an npc
        (is (some? (:since (:relation initial-jinni)))) ; just need to know its set. check equivalnce to itsef at tend of tests

        ;; cant create id with same uuid
        (is (thrown? Exception (j/jinni-waitlist-npc player-id)))
        ; check state after npc creation request
        (let [final-jinni (first (neoqu (str "MATCH (a:Avatar {id: $pid})-[rj]-(j:Avatar) RETURN rj as relation, j as jinni, elementId(j) as jid, labels(j) as labels")
                                         {:pid player-id}))]
            (is (some? final-jinni))
            (is (= (:since (:relation final-jinni)) (:since (:relation initial-jinni))))
            (is (= (:jid final-jinni) (:jid initial-jinni))) ; still using same avatar node
            (is (not (contains? (set (:labels final-jinni)) "NPC"))) ; ensure not an npc
            (is (contains? (set (:labels final-jinni)) "Jinni")) ; ensure still jinni
        )))
    
    (testing "Ensure player is not turned back into NPC if already a Jinni"
        (clear)
        (is (= 0 (:totalNodes (get-node-count ":Jinni"))))
        (is (= 0 (:totalNodes (get-node-count ":NPC"))))

        (neoqu "CREATE (a:Avatar:Human {id: $pid, uuid: $pid})
            CREATE (j:Avatar:p2p:Jinni $jinni)
            CREATE (a)-[:SUMMONS]->(j)
            CREATE (j)-[:BONDS]->(a)
            RETURN a, j
        " {:pid player-id :jinni { :id "existing-jinni-id" :uuid "existing-jinni-id" }})

      (let [post-jinni-count (:totalNodes (get-node-count ":Jinni"))
            post-npc-count (:totalNodes (get-node-count ":NPC"))]
        (is (= 0 post-npc-count))
        (is (= 1 post-jinni-count)) ; master + player. 1 if test isolated properly
        (j/jinni-waitlist-npc player-id)
        (let [final-jinni-count (:totalNodes (get-node-count ":Jinni"))
            final-npc-count (:totalNodes (get-node-count ":NPC"))]
          (is (= post-npc-count final-npc-count))
          (is (= post-jinni-count final-jinni-count)))))

    (testing "No new NPC created if player already has a Jinni"
        (clear)
        (is (= 0 (:totalNodes (get-node-count ":NPC"))))
        (j/jinni-waitlist-npc player-id)
      (let [post-count (:totalNodes (get-node-count ":NPC"))]
        (j/jinni-waitlist-npc player-id)
          (is (= 1 (:totalNodes (get-node-count ":NPC"))))))

    (testing "No data change on player node if NPC already exists"
        (clear)
      (let [initial-npc-data (neoqu (str "MATCH (n:NPC {id: $nid}) RETURN n") {:nid existing-npc-id})]
        (j/jinni-waitlist-npc existing-npc-id)
        (let [final-npc-data (neoqu (str "MATCH (n:NPC {id: $nid}) RETURN n") {:nid existing-npc-id})]
          (is (= initial-npc-data final-npc-data)))))

    (testing "Creating NPC for one player does not affect others"
        (clear)
      (let [other-player-id "other-player-id"
            _ (j/jinni-waitlist-npc player-id)
            ;; _ (j/jinni-waitlist-npc other-player-id)
            final-avatar (neoqu (str "MATCH (n:Avatar {id: $pid}) RETURN n as player")
                                {:pid player-id})
            other-avatar (neoqu (str "MATCH (n:Avatar {id: $pid}) RETURN n as player")
                                {:pid other-player-id})]
        
        (is (= (count final-avatar) 1))
        (is (= player-id (:id (:player (first final-avatar)))))
        (is (not= (:player (first final-avatar)) (:player (first other-avatar))))
            
            ))

    (testing "Handling invalid player ID"
        (is (thrown? Exception(j/jinni-waitlist-npc nil)))
        ;; (is (nil? (j/jinni-waitlist-npc nil)))
        ;; all valid from db persepctive. tested in manifest
        ;; (is (thrown? Exception (j/jinni-waitlist-npc []))
        ;; (is (thrown? Exception (j/jinni-waitlist-npc ""))
    )
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
    
    (testing "Creating a player ensures an ethereum identity"
        (clear)
        ; create master-djinn so create-player works
        (neoqu "CREATE (:Jinni)-[:HAS]->(:MasterDjinn {id: $mid})" {:jid master-djinn :mid master-djinn})
      (let [id1 (:id (first (neoqu (str "MATCH (id:Identity:Ethereum {provider_id: $pid}) RETURN id") {:pid player-id})))
            created-jinni-uuid (:jinni (db/call iddb/create-player {:player player-data :jinni jinni-data :now (now) :master_id master-djinn}))
            id2 (:id (first (neoqu (str "MATCH (id:Identity:Ethereum {provider_id: $pid}) RETURN id") {:pid player-id})))]

        ;; also check creating npc first then player and same jinni id
        (is (= (:provider id2) "Ethereum"))
        (is (= (:provider_id id2) player-id))))

        
    (testing "NPC -> player transition retains ETH Identity"
        (clear)
        ; create master-djinn so create-player works
        (neoqu "CREATE (:Jinni)-[:HAS]->(:MasterDjinn {id: $mid})" {:jid master-djinn :mid master-djinn})
      (let [npc-uuid (:jinni (db/call iddb/create-npc {:player player-data :jinni jinni-data :now (now) :master_id master-djinn}))
            id1 (first (neoqu (str "MATCH (id:Identity:Ethereum {provider_id: $pid})--(p:Avatar) RETURN id, p as player") {:pid player-id}))
            jinni-uuid (:jinni (db/call iddb/create-player {:player player-data :jinni jinni-data :now (now) :master_id master-djinn}))
            id2 (first (neoqu (str "MATCH (id:Identity:Ethereum {provider_id: $pid})--(p:Avatar) RETURN id, p as player") {:pid player-id}))]
        (is (= (:provider_id (:id id1)) (:provider_id (:id id2))))
        (is (= (:id (:player id1)) (:id (:player id2))))))
        


    (testing "Creating a new player with no existing Avatar account"
        (clear)
        ;add masterdjin to create player
        (neoqu "CREATE (:Jinni)-[:HAS]->(:MasterDjinn {id: $mid})" {:jid master-djinn :mid master-djinn})
      (let [mid-count (get-node-count) test-ts (now)
            created-jinni-uuid (:jinni (db/call iddb/create-player {:player player-data :jinni jinni-data :now test-ts :master_id master-djinn}))
            final-count (get-node-count)]
        
          (is (some? created-jinni-uuid))
            ; should be :Avatar:Human, :Avatar:NPC, and :Ientity:Ethereum if test is isolated more
          (is (= 3 (- (:totalNodes final-count) (:totalNodes mid-count))))
          (is (= (+ (:totalNodes mid-count) 3) (:totalNodes final-count)))
          
          (is (some? (neoqu (str "MATCH (j:Jinni {id: $jid}) RETURN j") {:jid created-jinni-uuid})))
          (is (some? (neoqu (str "MATCH (id:Identityq:Ethereum {provider_id: $pid}) RETURN id") {:pid player-id})))
          (is (= (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})-[relation:BONDS]->(:Human) RETURN relation.since as ts" {:jid jinni-id})))) (iso->unix test-ts)))
          (is (= (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})<-[relation:SUMMONS]-(:Human) RETURN relation.timestamp AS ts" {:jid created-jinni-uuid})))) (iso->unix test-ts)))
        ))

          
    ;; Test creating a player when the Avatar account already exists
    (testing "Creating a player when Avatar account already exists"
        (clear)
        (neoqu "CREATE (:Jinni)-[:HAS]->(:MasterDjinn {id: $mid})" {:jid master-djinn :mid master-djinn})
        (let [ts (now) past-ts (now -10000)
            ;; create first jinni to prefil
            first-jinni-id (:jinni (db/call iddb/create-player {:player player-data :jinni jinni-data :now past-ts :master_id master-djinn}))
            mid-node-count (get-node-count)
            ;; create player again for actual test data 
            second-jinni-id (:jinni (db/call iddb/create-player {:player player-data :jinni jinni-data :now past-ts :master_id master-djinn}))
            final-count (get-node-count)
            id (:id (first (neoqu (str "MATCH (id:Identity:Ethereum {provider_id: $pid}) RETURN id") {:pid player-id})))]
        
        (is (= (:totalNodes mid-node-count) (:totalNodes (get-node-count))))
        (is (= jinni-id first-jinni-id))
          (is (= jinni-id (:j (first (neoqu (str "MATCH (j:Jinni {id: $jid}) RETURN j.id as j") {:jid jinni-id})))))
          (is (some? id))
          (is (= (+ 10000 (iso->unix past-ts)) (iso->unix ts)))
          (is (= (+ 10000 (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})-[relation:BONDS]->(:Human) RETURN relation.since as ts" {:jid jinni-id}))))) (iso->unix ts)))
          (is (= (+ 10000 (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})<-[relation:SUMMONS]-(:Human) RETURN relation.timestamp as ts" {:jid jinni-id}))))) (iso->unix ts)))
    ))
))



;; Still TODO
(deftest init-player-identity-test
    (let [pid "some rano" jid "myjinni"]
        (testing "Returns nil if player does not exist"
            (clear)
            (is (nil? (db/call iddb/init-player-identity {:pid pid :provider "Github"}))))

        (testing "Does not create node/rel between Identity if no player"
            (clear)
            (is (nil? (db/call iddb/init-player-identity {:pid pid :provider "Github"})))
            (is (= 0 (:totalNodes (get-node-count ":Identity"))))
            (is (= 0 (:totalNodes (get-node-count ":Identity:Github")))))

        (testing "Adds right provier label to node"
            (clear)
            (db/call iddb/create-npc {:player {:id pid :uuid pid} :jinni {:uuid jid} :now "now"})
            (is (= 0 (:totalNodes (get-node-count ":Identity:Github"))))
            (is (some? (db/call iddb/init-player-identity {:pid pid :provider "Github"})))
            (is (= 1 (:totalNodes (get-node-count ":Identity:Github")))))

        (testing "Adds right provider to node `provider` field"
            (clear)
            (neoqu "CREATE (a:Avatar {id: $pid, uuid: $pid}) RETURN a" {:pid pid})
            (let [
                init (db/call iddb/init-player-identity  {:pid pid :provider "testprovider"})
                id (first (neoqu "MATCH (id:Identity {provider: $provider}) RETURN id" {:provider "testprovider"}))]
                (println "id res iiii" init)
                (println "id res 9232039" id)
                (is (= "testprovider" (:provider (:id id)))
                
                )))

        (testing "Returns identity node if created successfully"
            (clear)
            (db/call iddb/create-npc {:player {:id pid :uuid pid} :jinni {:uuid jid} :now "now"})
            ; check actual return value
            (let [id (db/call iddb/init-player-identity {:pid pid :provider "Github"})]
            (is (some? id))
            (is (= "Github" (:provider (:id id))))))
        
        (testing "Creates player-[:HAS]->Identity rel"
            (clear)
            (db/call iddb/create-npc {:player {:id pid :uuid pid} :jinni {:uuid jid} :now "now"})
            (db/call iddb/init-player-identity {:pid pid :provider "Github"})
            (let [res (first (neoqu "MATCH (:Avatar {id: $pid})-[rel:HAS]->(id:Identity {provider: $provider}) RETURN rel, id"
                {:pid pid :provider "Github"}))]
            (is (some? (:rel res)))
            (is (some? (:id res)))))
        
        (testing "Throws db error on incompatible input params"
            (clear)
            (is (thrown? Exception (db/call iddb/init-player-identity nil)))
            (is (thrown? Exception (db/call iddb/init-player-identity {:pid nil})))
            (is (thrown? Exception (db/call iddb/init-player-identity {:provider nil})))
            
            ; numbers are invalid inputs
            (is (thrown? Exception (db/call iddb/init-player-identity {:pid nil :provider 3853292358})))
            (is (thrown? Exception (db/call iddb/init-player-identity {:pid 23521350923 :provider 3853292358})))
            (is (thrown? Exception (db/call iddb/init-player-identity {:pid "asfaf" :provider 3853292358})))
            ; because provider gets put in array it throws error unlike :pid
            ;; (is (nil? (db/call iddb/init-player-identity {:pid 3853292358 :provider "InvalidProvider"})))
            
            ; structured data is invalid, must be strings
            (is (thrown? Exception (db/call iddb/init-player-identity {:pid [pid] :provider ["asfaf"]}))))
        
        (testing "Returns nil on inputs with no matches"
            (clear)
            (is (nil? (db/call iddb/init-player-identity {:pid nil :provider nil})))
            (is (nil? (db/call iddb/init-player-identity {:pid nil :provider "thing"})))
            (is (nil? (db/call iddb/init-player-identity {:pid "thing" :provider nil})))
            (is (nil? (db/call iddb/init-player-identity {:pid "notaplayer" :provider "Github"})))
            (is (nil? (db/call iddb/init-player-identity {:pid pid :provider "NotAProvider"})))

            (is (nil? (db/call iddb/init-player-identity {:pid 3853292358 :provider nil})))
            ; because provider gets put in array it throws error unlike :pid
            ;; (is (thrown? Exception (db/call iddb/init-player-identity {:pid "asfaf" :provider 3853292358})))
            (is (nil? (db/call iddb/init-player-identity {:pid 3853292358 :provider "InvalidProvider"}))))
))

(deftest set-identity-credentials-test
  (let [test-player-id "test-player"
        test-provider "TestProvider"
        existing-access-token "existing-access-token"
        existing-refresh-token "existing-refresh-token"
        new-access-token "new-access-token"
        new-refresh-token "new-refresh-token"]


    (testing "Returns nil if identity does not exist when updating credentials"
      (clear)
      (is (nil? (db/call iddb/set-identity-credentials {:pid "non-existing-player" :provider test-provider :access_token new-access-token :refresh_token new-refresh-token})))
    )

    (testing "Successfully updates identity credentials"
      (clear)
      (db/call iddb/create-npc {:player {:id test-player-id :uuid test-player-id} :jinni {:uuid "jinni-uuid"} :now "now"})
      (db/call iddb/init-player-identity {:pid test-player-id :provider test-provider})
      (let [initial-id (db/call iddb/get-identity {:pid test-player-id :provider test-provider})]
        (is (some? initial-id))
        (db/call iddb/set-identity-credentials {:pid test-player-id :provider test-provider :access_token new-access-token :refresh_token new-refresh-token})
        (let [updated-id (db/call iddb/get-identity {:pid test-player-id :provider test-provider})
                id (:id updated-id)]
          (is (= new-access-token (:access_token id)))
          (is (= new-refresh-token (:refresh_token id))))))

    (testing "Does not create a new Identity node if one already exists"
      (clear)
      (is (= 0 (:totalNodes (get-node-count ":Identity"))))
      (db/call iddb/create-npc {:player {:id test-player-id :uuid test-player-id} :jinni {:uuid "jinni-uuid"} :now "now"})
      (is (= 1 (:totalNodes (get-node-count ":Identity")))) ; ETH ID
      (db/call iddb/init-player-identity {:pid test-player-id :provider test-provider})
      (is (= 2 (:totalNodes (get-node-count ":Identity")))) ;; Provider ID

      (let [initial-id (db/call iddb/get-identity {:pid test-player-id :provider test-provider})]
        (is (some? initial-id))
        (db/call iddb/set-identity-credentials {:pid test-player-id :provider test-provider :access_token new-access-token :refresh_token new-refresh-token})
        (let [og-node (neoqu "MATCH (id:Identity {provider: $provider})--(:Avatar {id: $pid}) RETURN elementId(id) as node_id"
                {:provider test-provider :pid test-player-id})
            
            after-update-id (db/call iddb/get-identity {:pid test-player-id :provider test-provider})
            
            post-node (neoqu "MATCH (id:Identity {provider: $provider})--(:Avatar {id: $pid}) RETURN elementId(id) as node_id"
                {:provider test-provider :pid test-player-id})]
          (is (= 2 (:totalNodes (get-node-count ":Identity"))))
          (is (= (:node_id og-node) (:node_id post-node))))))

    (testing "Resets access token if present"
      (clear)
      (db/call iddb/create-npc {:player {:id test-player-id :uuid test-player-id} :jinni {:uuid "jinni-uuid"} :now "now"})
      (db/call iddb/init-player-identity {:pid test-player-id :provider test-provider})
      (db/call iddb/set-identity-credentials {:pid test-player-id :provider test-provider :access_token existing-access-token :refresh_token existing-refresh-token})
      (db/call iddb/set-identity-credentials {:pid test-player-id :provider test-provider :access_token new-access-token :refresh_token nil})
      (let [updated-id (db/call iddb/get-identity {:pid test-player-id :provider test-provider})
            id (:id updated-id)]
        (is (= new-access-token (:access_token id)))))

    (testing "Resets refresh token if present"
      (clear)
      (db/call iddb/create-npc {:player {:id test-player-id :uuid test-player-id} :jinni {:uuid "jinni-uuid"} :now "now"})
      (db/call iddb/init-player-identity {:pid test-player-id :provider test-provider})
      (db/call iddb/set-identity-credentials {:pid test-player-id :provider test-provider :access_token nil :refresh_token new-refresh-token})
      (let [updated-id (db/call iddb/get-identity {:pid test-player-id :provider test-provider})
            id (:id updated-id)]
        (is (= new-refresh-token (:refresh_token id)))))

    (testing "Must provide values for both tokens on update"
      (clear)
      (db/call iddb/create-npc {:player {:id test-player-id :uuid test-player-id} :jinni {:uuid "jinni-uuid"} :now "now"})
      (db/call iddb/init-player-identity {:pid test-player-id :provider test-provider})

      (db/call iddb/set-identity-credentials {:pid test-player-id :provider test-provider :access_token existing-access-token :refresh_token existing-refresh-token})
      (let [updated-id (db/call iddb/get-identity {:pid test-player-id :provider test-provider})
            id2 (:id updated-id)]
        (is (= existing-access-token (:access_token id2)))
        (is (= existing-refresh-token (:refresh_token id2))))
        
      (db/call iddb/set-identity-credentials {:pid test-player-id :provider test-provider :access_token nil :refresh_token new-refresh-token})
      (let [updated-id2 (db/call iddb/get-identity {:pid test-player-id :provider test-provider})
            id3 (:id updated-id2)]
        (is (nil? (:access_token id3)))
        (is (= new-refresh-token (:refresh_token id3))))

      (db/call iddb/set-identity-credentials {:pid test-player-id :provider test-provider :access_token new-access-token :refresh_token nil})
      (let [updated-id2 (db/call iddb/get-identity {:pid test-player-id :provider test-provider})
            id4 (:id updated-id2)]
        (is (= new-access-token (:access_token id4)))
        (is (nil? (:refresh_token id4))))

        (db/call iddb/set-identity-credentials {:pid test-player-id :provider test-provider :access_token nil :refresh_token nil})
      (let [updated-id2 (db/call iddb/get-identity {:pid test-player-id :provider test-provider})
            id4 (:id updated-id2)]
        (is (nil? (:access_token id4)))
        (is (nil? (:refresh_token id4))))
            
    )


    (testing "Throws exception on incompatible input params"
      (clear)
      (is (thrown? Exception (db/call iddb/set-identity-credentials nil)))
      ; all vars must have vals, even nil, otherwise automatic error before its even run.
      ; all nil is fine on this query
      (is (nil? (db/call iddb/set-identity-credentials {:pid nil :provider nil :access_token nil :refresh_token nil})))
      ; 2/4 included throws
      (is (thrown? Exception (db/call iddb/set-identity-credentials {:pid nil :provider test-provider})))
      (is (thrown? Exception (db/call iddb/set-identity-credentials {:pid test-player-id :provider nil})))
      (is (thrown? Exception (db/call iddb/set-identity-credentials {:access_token nil :refresh_token nil})))
      (is (thrown? Exception (db/call iddb/set-identity-credentials {:access_token nil :refresh_token test-provider})))
      ; 3/4 included throws
      (is (thrown? Exception (db/call iddb/set-identity-credentials {:pid nil :provider nil :access_token nil })))
      (is (thrown? Exception (db/call iddb/set-identity-credentials {:provider nil :access_token nil :refresh_token nil })))
      (is (thrown? Exception (db/call iddb/set-identity-credentials {:pid nil :provider nil :refresh_token nil })))
      (is (thrown? Exception (db/call iddb/set-identity-credentials {:pid nil :access_token nil :refresh_token nil })))
      (is (thrown? Exception (db/call iddb/set-identity-credentials {:pid test-player-id :provider test-provider :access_token existing-access-token })))
      (is (thrown? Exception (db/call iddb/set-identity-credentials {:pid test-player-id :provider test-provider :refresh_token existing-refresh-token })))
    ;;   (is (thrown? Exception (db/call iddb/set-identity-credentials {:pid test-player-id :provider test-provider :access_token "asfafssf" :refresh_token 12345})))
    ;;   (is (thrown? Exception (db/call iddb/set-identity-credentials {:pid test-player-id :provider test-provider :access_token 12345 :refresh_token "12341"})))
    ;;   (is (thrown? Exception (db/call iddb/set-identity-credentials {:pid test-player-id :provider 12345 :refresh_token ""})))

    )


))


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




;; Think these done already 
;; (jinni-activate)
;; - if no player :Avatar account already.
;; - creates 2 avatar nodes and 1 identity node
;; - increase total node count by 3
;; - has a :Jinni attached to user
;; - has an :Identity:Ethereum node
;; - relation.since set to (now)
;; - if new player created returns jinni uuid
;; - if player  already existed, returns original jinni uuid

;; - if player :Avatar account already.
;;     - creates 0 avatar nodes and 0 identity node
;;     - increase total node count by 0
;;     - had :Avatar:NPC before, have :Avatar:Jinni now
;;     - relation.since set to b

;;   (testing "Activating a new Jinni with no existing Avatar account"
;;     (clear)
;;     (let [player-id "new-player-id"
;;           jinni-id (j/jinni-activate player-id MALEKS_MAJIK_CARD jinni-id)]
;;       (is (some? jinni-id)) ;; Ensure a Jinni UUID is returned
;;       (is (some? (neoqu (str "MATCH (j:Jinni {id: $jid}) RETURN j") {:jid jinni-id}))) ;; Jinni node should exist
;;       (is (some? (neoqu (str "MATCH (id:Identity:Ethereum {provider_id: $pid}) RETURN id") {:pid player-id}))) ;; Identity node should exist
;;       (is (= (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})-[relation:BONDS]->(:Human) RETURN relation.since as ts" {:jid jinni-id})))) (iso->unix (now)))) ;; relation.since should be set to now
;;       (is (= (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})<-[relation:SUMMONS]-(:Human) RETURN relation.timestamp AS ts" {:jid jinni-id})))) (iso->unix (now)))))) ;; Check SUMMONS timestamp

;;   (testing "Activating a Jinni with existing Avatar account"
;;     (clear)
;;     (let [player-id "existing-player-id"
;;         initial-count (get-node-count)
;;           jinni-id (j/jinni-activate player-id MALEKS_MAJIK_CARD jinni-id)]
;;       (is (some? jinni-id)) ;; Ensure a Jinni UUID is returned
;;       (is (= 0 (get-node-count))) ;; Check total node count remains unchanged
;;       (is (= jinni-id (:j (first (neoqu (str "MATCH (j:Jinni {id: $jid}) RETURN j.id as j") {:jid jinni-id}))))) ;; Jinni ID should match
;;       (is (= (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})-[relation:BONDS]->(:Human) RETURN relation.since as ts" {:jid jinni-id})))) (iso->unix (now - 10000)))) ;; relation.since should be set to before now
;;       (is (= (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})<-[relation:SUMMONS]-(:Human) RETURN relation.timestamp as ts" {:jid jinni-id})))) (iso->unix (now - 10000)))))) ;; Check SUMMONS timestamp remains unchanged

;;   (testing "Activating Jinni for player who already has a Jinni"
;;     (clear)
;;     (let [player-id "player-with-existing-jinni"
;;             initial-count (get-node-count)  
;;           initial-jinni-id (j/jinni-activate player-id MALEKS_MAJIK_CARD jinni-id)
;;           ;; expect db error from invariant not a new id
;;           new-jinni-id (j/jinni-activate player-id MALEKS_MAJIK_CARD jinni-id)]
;;       (is (= initial-jinni-id new-jinni-id)) ;; Ensure the same Jinni ID is returned
;;       (is (= 0 (get-node-count))) ;; Check total node count remains unchanged
;;       (is (some? (neoqu (str "MATCH (j:Jinni {id: $jid}) RETURN j") {:jid initial-jinni-id}))) ;; Jinni node should still exist
;;       (is (not (nil? (neoqu (str "MATCH (id:Identity:Ethereum {provider_id: $pid}) RETURN id") {:pid player-id})))) ;; Identity node should still exist
;;       (is (= (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})-[relation:BONDS]->(:Human) RETURN relation.since as ts" {:jid initial-jinni-id})))) (iso->unix (now - 10000)))))) ;; relation.since should remain unchanged

;;   (testing "No duplicate Jinni created for existing player"
;;     (clear)
;;     (let [player-id "existing-player-id"
;;           initial-jinni-id (j/jinni-activate player-id MALEKS_MAJIK_CARD jinni-id)
;;           duplicate-jinni-id (j/jinni-activate player-id MALEKS_MAJIK_CARD jinni-id)]
;;       (is (= initial-jinni-id duplicate-jinni-id)) ;; Ensure the same Jinni ID is returned
;;       (is (= 0 (get-node-count))) ;; Check total node count remains unchanged
;;       (is (some? (neoqu (str "MATCH (j:Jinni {id: $jid}) RETURN j") {:jid initial-jinni-id}))) ;; Jinni node should still exist
;;       (is (not (nil? (neoqu (str "MATCH (id:Identity:Ethereum {provider_id: $pid}) RETURN id") {:pid player-id})))) ;; Identity node should still exist
;;       (is (= (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})-[relation:BONDS]->(:Human) RETURN relation.since as ts" {:jid initial-jinni-id})))) (iso->unix (now - 10000)))))) ;; relation.since should remain unchanged

;;   (testing "Activating Jinni with invalid player ID"
;;     (clear)
;;     (is (thrown? Exception (j/jinni-activate nil MALEKS_MAJIK_CARD jinni-id)))  ;; Ensure exception is thrown for nil player ID
;;     (is (thrown? Exception (j/jinni-activate "" MALEKS_MAJIK_CARD jinni-id))) ;; MALEKS_MAJIK_CARD jinni-idEnsure exception is thrown for empty player ID
;;     (is (thrown? Exception (j/jinni-activate 12345 MALEKS_MAJIK_CARD jinni-id)))  ;; Ensure exception is thrown for non-string player ID
;;     (is (thrown? Exception (j/jinni-activate "0x1234567890123456789012345678901234567890" MALEKS_MAJIK_CARD jinni-id) MALEKS_MAJIK_CARD jinni-id)) ;; Ensure exception is thrown for non-existing player ID
;;     (is (thrown? Exception (j/jinni-activate "non-ex MALEKS_MAJIK_CARD jinni-idstent-id" MALEKS_MAJIK_CARD jinni-id)))) ;; Ensure exception is thrown for non-existent player ID

;;   (testing "Activating Jinni for existing player without Jinni"
;;     (clear)
;;     (let [player-id "player-without-jinni"
;;           jinni-id (j/jinni-activate player-id MALEKS_MAJIK_CARD jinni-id)]
;;       (is (some? jinni-id)) ;; Ensure a Jinni UUID is returned
;;       (is (= 3 (get-node-count))) ;; Check total node count increased by 3
;;       (is (some? (neoqu (str "MATCH (j:Jinni {id: $jid}) RETURN j") {:jid jinni-id}))) ;; Jinni node should exist
;;       (is (some? (neoqu (str "MATCH (id:Identity:Ethereum {provider_id: $pid}) RETURN id") {:pid player-id}))) ;; Identity node should exist
;;       (is (= (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})-[relation:BONDS]->(:Human) RETURN relation.since as ts" {:jid jinni-id})))) (iso->unix (now)))) ;; relation.since should be set to now
;;       (is (= (iso->unix (:ts (first (neoqu "MATCH (j:Jinni {id: $jid})<-[relation:SUMMONS]-(:Human) RETURN relation.timestamp AS ts" {:jid jinni-id})))) (iso->unix (now)))))) ;; Check SUMMONS timestamp

