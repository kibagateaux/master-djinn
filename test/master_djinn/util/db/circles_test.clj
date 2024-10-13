(ns master-djinn.util.db.circles-test
  (:require [clojure.test :refer :all]
              [clojure.spec.alpha :as s]

              [master-djinn.util.db.core :as db]
              [master-djinn.util.db.circles :as cdb]
              [neo4j-clj.core :as neo4j]

            ; to stub and/or generate test data
            [master-djinn.util.testing-helpers :refer [clear neoqu get-node-count]]
              [master-djinn.util.core :refer [now iso->unix]]))


;; ;; test generation prompt
;; you are a devops database admin savant, a QA/testing/product analytics , in addition to a a clojure.spec and neo4j core contributor. 
;; Read through the coe, comments, variable names, and return values to determine expected features, behaviour, return values, edge cases, input parameters, and other testable states/values/flows.
;; Generte more test cases where you see any code problems with security, logic, inputs or outpus, 
;; Using neo4j cypher queries in @circles.clj   test that the query ånd database state conforms to expected constraints, updates, and behaviour
;; Review and think through the code you generate. Go back to simplify, refine, abstract, and verify your implementation

;; Output the best code you can create for a test suite on the `{my_query}` query

(deftest get-summoning-circle-test
  (let [player-id "test-player-id"
        jinni-id "test-jinni-id"
        jubmoji-id "test-jubmoji-id"
        npc-id "test-npc-id"
        jubmoji-provider-id "jubmoji-provider-id"
        create-player+p2c (fn [] (neoqu "CREATE (a:Avatar:Human {id: $pid, uuid: $pid})
            CREATE (id:Jubmoji:Identity:Ethereum {provider: 'Ethereum', provider_id: $jmid})
            CREATE (j:Jinni:Avatar:p2c {id: $jid, uuid: $jid})
            CREATE (a)-[:SUMMONS]->(j)
            CREATE (j)-[:HAS]->(id)" {:pid player-id :jid jinni-id :jmid jubmoji-provider-id}))]



    (testing "Returns nil on summoner + jinni if summoner has no account"
      (clear)
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (nil? result))))
    
     (testing "Requires all expected param variabels to be set"
        (clear)
        (is (nil? (db/call cdb/get-summoning-circle {:jmid nil})))

        (is (thrown? Exception (db/call cdb/get-summoning-circle {})))
        (is (thrown? Exception (db/call cdb/get-summoning-circle nil)))
    )

    (testing "Returns summoner if they are an NPC"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})" {:pid player-id})
      (neoqu "CREATE (:Avatar:NPC {id: $npcid, uuid: $npcid})-[:SUMMONS]->(:Jinni:p2c {id: $jid})" 
              {:npcid npc-id :jid jinni-id})
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid npc-id})]
        (is (nil? (:summoner result)))
        (is (nil? (:jinni result)))
        
        ))

    (testing "Returns nil on summoner + jinni if they are a player with a Jinni"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})-[:SUMMONS]->(:Jinni:p2p {id: $jid})" 
              {:pid player-id :jid jinni-id})
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (nil? result))))

    (testing "Returns nil on jinni if summoner only has a :p2p not a :p2c jinni"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})-[:SUMMONS]->(:Jinni:p2p {id: $jid})" 
              {:pid player-id :jid jinni-id})
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (nil? result))))

    (testing "Returns nil if no :Jubmoji in database with $jmid"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})" {:pid player-id})
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (nil? result))))

    (testing "Returns jinni if :Jubmoji with $jmid is present"
      (clear)
      (create-player+p2c )
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (some? result))
        (is (= jinni-id (:id (:jinni result))))
        (is (= player-id (:id (:summoner result))))))


    (testing "Only returns on :Jinni:p2c"
        ; not using (create-player+p2c) bc creating testing nonp2c types
      (clear)
        ; test :Jinni
      (neoqu "CREATE (a:Avatar:Human {id: $pid, uuid: $pid})
            CREATE (:Jubmoji:Identity:Ethereum {provider: 'Ethereum', provider_id: $jmid})
            CREATE (j:Jinni:Avatar {id: $jid, uuid: $jid})
            CREATE (a)-[:SUMMONS]->(j)" {:pid player-id :jid jinni-id :jmid jubmoji-provider-id})
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (nil? result)))

        (clear)
        ; test :Jinni:p2p
        (neoqu "CREATE (a:Avatar:Human {id: $pid, uuid: $pid})
            CREATE (:Jubmoji:Identity:Ethereum {provider: 'Ethereum', provider_id: $jmid})
            CREATE (j:Jinni:Avatar:p2p {id: $jid, uuid: $jid})
            CREATE (a)-[:SUMMONS]->(j)" {:pid player-id :jid jinni-id :jmid jubmoji-provider-id})
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (nil? result))))

    (testing "Only returns Jinni with Jubmoji"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})" {:pid player-id})
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (nil? result))))
    
    (testing "Cannot have different :Identity:Jubmoji for the same provider_ID"
        ; technically a db invariant test but re-specifying app level requirements for :Jubmoji
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})" {:pid player-id})
      (neoqu "CREATE (:Jubmoji:Identity:Ethereum {provider: 'Ethereum', provider_id: $jmid})"  {:jmid jubmoji-provider-id})
      (is (thrown? Exception (neoqu "CREATE (:Jinni:p2c {id: $jid})-[:HAS]->(:Jubmoji:Identity:Ethereum {provider: 'Ethereum',provider_id: $jmid})"  {:jid jinni-id :jmid jubmoji-provider-id})))
    )

    (testing "Returns right summoner with expected properties"
      (clear)
      (create-player+p2c )
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (= player-id (:id (:summoner result))))))

    (testing "Returns right jinni with expected properties"
      (clear)
      (create-player+p2c )
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (= jinni-id (:id (:jinni result))))))
))


(deftest create-summoning-circle-tests
  (let [player-id "test-player-id"
        p2p-jinni-id (str "j" player-id) ; need a p2p jinni to create a p2c
        jinni-id "test-jinni-id"
        jubmoji-provider-id "test-jubmoji-provider-id"]
    
    (testing "Returns nil if $pid has no :Avatar in db"
      (clear)
      (let [ts (now) result (db/call cdb/create-summoning-circle {:pid player-id :jinni {:id jinni-id :uuid jinni-id} :signer jubmoji-provider-id :now ts})]
        (is (nil? (:jinni result)))))

    (testing "Returns nil if $pid is an NPC in db"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})-[:SUMMONS]->(:Avatar:NPC:p2p {id: $jid, uuid: $jid})" {:pid player-id :jid p2p-jinni-id})
      (let [ts (now) result (db/call cdb/create-summoning-circle {:pid player-id :jinni {:id jinni-id :uuid jinni-id} :signer jubmoji-provider-id :now ts})]
        (is (nil? (:jinni result)))))

    (testing "Requires all expected param variabels to be set"
        (clear)
        (is (thrown? Exception (db/call cdb/create-summoning-circle nil)))
        ; all vars must have vals, even nil, otherwise automatic error before its even run.
        (is (nil? (:jinni (db/call cdb/create-summoning-circle {:pid nil :jinni nil :signer nil :now nil}))))
        ; even 3/4 included on the only "required" data is invalid
        (is (thrown? Exception (db/call cdb/create-summoning-circle {:pid player-id :jinni {:id jinni-id :uuid jinni-id} :signer p2p-jinni-id })))
        (is (thrown? Exception (db/call cdb/create-summoning-circle {:pid player-id :jinni {:id jinni-id :uuid jinni-id} :now "rightnow" })))
        (is (some? (db/call cdb/create-summoning-circle {:pid player-id :jinni {:id jinni-id :uuid jinni-id} :signer p2p-jinni-id :now "rightnow" }))))
    
    (testing "Returns Jinni id (not uuid) from db"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})-[:SUMMONS]->(:Avatar:Jinni:p2p {id: $jid, uuid: $jid})" {:pid player-id :jid p2p-jinni-id})
      (let [ts (now) result (db/call cdb/create-summoning-circle {:pid player-id :jinni {:id jinni-id :uuid jinni-id} :signer jubmoji-provider-id :now ts})]
        (is (= jinni-id (:jinni result)))))

    (testing "Invariants for playerCount and jubCount"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})-[:SUMMONS]->(:Avatar:Jinni:p2p {id: $jid, uuid: $jid})" {:pid player-id :jid p2p-jinni-id})

      (let [ts (now) global-count1 (:totalNodes (get-node-count))
                result (db/call cdb/create-summoning-circle {:pid player-id :jinni {:id jinni-id :uuid jinni-id} :signer jubmoji-provider-id :now ts})]
        (is (= 1 (:totalNodes (get-node-count ":Avatar:Human"))))
        (is (= 1 (:totalNodes (get-node-count ":Jinni:p2c"))))
        (is (= 1 (:totalNodes (get-node-count ":Jubmoji:Identity:Ethereum"))))
        ; only :Jinni + :Jubmoji nodes created
        (is (+ 2 global-count1) (:totalNodes (get-node-count)))
        
        (let [result2 (db/call cdb/create-summoning-circle {:pid player-id :jinni {:id jinni-id :uuid jinni-id} :signer jubmoji-provider-id :now ts})
                global-count2 (:totalNodes (get-node-count))]
            (is (nil? (:jinni result2)))
            ; no new nodes created since last checks
            (is (= global-count2 (+ global-count1 2)))
        )))

    (testing "If :Jubmoji already exists, returns nil and does not create new nodes"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})-[:SUMMONS]->(:Avatar:Jinni:p2p {id: $jid, uuid: $jid})" {:pid player-id :jid p2p-jinni-id})
      (neoqu "CREATE (:Jubmoji:Identity:Ethereum {provider: 'Ethereum', provider_id: $jmid})" {:jmid jubmoji-provider-id})
      (is (= 1 (:totalNodes (get-node-count ":Jubmoji:Identity:Ethereum"))))
      (let [ts (now)
            result (db/call cdb/create-summoning-circle {:pid player-id :jinni {:id jinni-id :uuid jinni-id} :signer jubmoji-provider-id :now ts})]
        (is (nil? (:jinni result)))
        (is (= 1 (:totalNodes (get-node-count ":Jubmoji:Identity:Ethereum"))))
        (is (= 0 (:totalNodes (get-node-count ":Jinni:p2c"))))))
    
    (testing "Gives Jubmoji identity to p2c Jinni "
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})-[:SUMMONS]->(:Avatar:Jinni:p2p {id: $jid, uuid: $jid})" {:pid player-id :jid p2p-jinni-id})
      (let [ts (now) result (db/call cdb/create-summoning-circle {:pid player-id :jinni {:id jinni-id :uuid jinni-id} :signer jubmoji-provider-id :now ts})
            {:keys [jinni id]} (neoqu "MATCH (j:Jinni:p2c {id: $jid})-[:HAS]->(id:Identity:Ethereum:Jubmoji) RETURN j as jinni, id" {:jid jinni-id})]
        (is (= jinni-id (:id jinni)))
        (is (= jubmoji-provider-id (:provider_id id)))))

    (testing "Create correct :Human--:Jinni:p2c nodes and relations"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})-[:SUMMONS]->(:Avatar:Jinni:p2p {id: $jid, uuid: $jid})" {:pid player-id :jid p2p-jinni-id})
      (let [ts (now)
            result (db/call cdb/create-summoning-circle {:pid player-id :jinni {:id jinni-id :uuid jinni-id} :signer jubmoji-provider-id :now ts})]
        (is (= jinni-id (:jinni result)))
        (is (= 1 (:totalNodes (get-node-count ":Avatar:Human"))))
        (is (= 1 (:totalNodes (get-node-count ":Jinni:p2c"))))
        (is (= 1 (:totalNodes (get-node-count ":Jinni:p2p")))) ; only the og one, no new one created

          ; sets right vals on jinni node
        (let [{:keys [jinni labels]} (-> (neoqu "MATCH (p:Avatar:Human {id: $pid})-[:SUMMONS]->(j:Jinni:p2c) RETURN j as jinni, labels(j) as labels" {:pid player-id}) first)
                expected-labels ["Avatar" "Jinni" "p2c"]]
          (is (= jinni-id (:id jinni)))
          (is (= jinni-id (:uuid jinni)))
           ; only allow expected labels
          (is (= (count labels) (count (filter #(not (contains? expected-labels %1)) labels)))))
          
          ; sets right vals on relationships
        (let [{:keys [relation jinni]} (-> (neoqu "MATCH (p:Avatar:Human {id: $pid})-[r:SUMMONS]->(j:Jinni:p2c) RETURN r as relation, j as jinni" {:pid player-id}) first)]
          (is (some? relation))
          (is (= (iso->unix ts) (iso->unix (:timestamp relation))))
          )
        (let [{:keys [relation jinni]} (-> (neoqu "MATCH (p:Avatar:Human {id: $pid})<-[r:BONDS]-(j:Jinni:p2c) RETURN r as relation, j as jinni" {:pid player-id}) first)]
          (is (some? relation))
          (is (= (iso->unix ts) (iso->unix (:since relation)))))
        ))

    (testing "Correctly sets timestamps on relationships"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})-[:SUMMONS]->(:Avatar:Jinni:p2p {id: $jid, uuid: $jid})" {:pid player-id :jid p2p-jinni-id})
      (let [ts (now)
            result (db/call cdb/create-summoning-circle {:pid player-id :jinni {:id jinni-id :uuid jinni-id} :signer jubmoji-provider-id :now ts})]
        (let [summons-timestamp (-> (neoqu "MATCH (p:Avatar:Human {id: $pid})-[r:SUMMONS]->(j:Jinni:p2c) RETURN r.timestamp as timestamp" {:pid player-id}) first :timestamp iso->unix)
              bonds-timestamp (-> (neoqu "MATCH (p:Avatar:Human {id: $pid})<-[r:BONDS]-(j:Jinni:p2c) RETURN r.since as since" {:pid player-id}) first :since iso->unix)]
          (is (= (iso->unix ts) summons-timestamp))
          (is (= (iso->unix ts) bonds-timestamp)))))

    (testing "Player can create multiple p2c with multiple Jubmoji"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})-[:SUMMONS]->(:Avatar:Jinni:p2p {id: $jid, uuid: $jid})" {:pid player-id :jid p2p-jinni-id})
      (let [ts (now)
            result (db/call cdb/create-summoning-circle {:pid player-id :jinni {:id jinni-id :uuid jinni-id} :signer jubmoji-provider-id :now ts})
            result2 (db/call cdb/create-summoning-circle {:pid player-id :jinni {:id (str "2-"jinni-id) :uuid (str "2-"jinni-id)} :signer (str "2-jubmoji-provider-id") :now ts})
            
            avatar-t2 (:totalNodes (get-node-count ":Human"))
            jinni-t2 (:totalNodes (get-node-count ":Jinni:p2c"))
            jubmoji-t2 (:totalNodes (get-node-count ":Jubmoji:Identity:Ethereum"))]
            (is (= 1 avatar-t2))
            (is (= 2 jinni-t2))
            (is (= 2 jubmoji-t2))
        ))
))