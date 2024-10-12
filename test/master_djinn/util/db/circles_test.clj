(ns master-djinn.util.db.circles-test
  (:require [clojure.test :refer :all]
              [clojure.spec.alpha :as s]

              [master-djinn.util.db.core :as db]
              [master-djinn.util.db.circles :as cdb]
              [neo4j-clj.core :as neo4j]

            ; to stub and/or generate test data
            [master-djinn.util.testing-helpers :refer [clear neoqu get-node-count]]
              [master-djinn.util.core :refer [now iso->unix]]))


;; test generation prompt
;; you are a devops database admin savant, a QA/testing/product analytics , in addition to a a clojure.spec and neo4j core contributor. 
;; Read through the coe, comments, variable names, and return values to determine expected features, behaviour, return values, edge cases, input parameters, and other testable states/values/flows.
;; Generte more test cases where you see any code problems with security, logic, inputs or outpus, 
;; Using neo4j cypher queries in @circles.clj   test that the query ånd database state conforms to expected constraints, updates, and behaviour
;; Output only the test suite

(deftest get-summoning-circle-test
  (let [player-id "test-player-id"
        jinni-id "test-jinni-id"
        jubmoji-id "test-jubmoji-id"
        npc-id "test-npc-id"
        jubmoji-provider-id "jubmoji-provider-id"]

    (testing "Returns nil on summoner + jinni if summoner has no account"
      (clear)
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (nil? result))))

    (testing "Returns summoner if they are an NPC"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})" {:pid player-id})
      (neoqu "CREATE (:Avatar:NPC {id: $npcid, uuid: $npcid})-[:SUMMONS]->(:Jinni:p2c {id: $jinniid})" 
              {:npcid npc-id :jinniid jinni-id})
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid npc-id})]
        (is (nil? (:summoner result)))
        (is (nil? (:jinni result)))
        
        ))

    (testing "Returns nil on summoner + jinni if they are a player with a Jinni"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})-[:SUMMONS]->(:Jinni:p2p {id: $jinniid})" 
              {:pid player-id :jinniid jinni-id})
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (nil? result))))

    (testing "Returns nil on jinni if summoner only has a :p2p not a :p2c jinni"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})-[:SUMMONS]->(:Jinni:p2p {id: $jinniid})" 
              {:pid player-id :jinniid jinni-id})
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (nil? result))))

    (testing "Returns nil if no :Jubmoji in database with $jmid"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})" {:pid player-id})
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (nil? result))))

    (testing "Returns jinni if :Jubmoji with $jmid is present"
      (clear)
      (neoqu "CREATE (a:Avatar:Human {id: $pid, uuid: $pid})
            CREATE (id:Jubmoji:Identity:Ethereum {provider: 'Ethereum', provider_id: $jmid})
            CREATE (j:Jinni:Avatar:p2c {id: $jid, uuid: $jid})
            CREATE (a)-[:SUMMONS]->(j)
            CREATE (j)-[:HAS]->(id)" {:pid player-id :jid jinni-id :jmid jubmoji-provider-id})
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (some? result))
        (is (= jinni-id (:id (:jinni result))))
        (is (= player-id (:id (:summoner result))))))


    (testing "Only returns p2c Jinni"
      (clear)
        ; test just :Jinni
      (neoqu "CREATE (a:Avatar:Human {id: $pid, uuid: $pid})
            CREATE (:Jubmoji:Identity:Ethereum {provider: 'Ethereum', provider_id: $jmid})
            CREATE (j:Jinni:Avatar {id: $jid, uuid: $jid})
            CREATE (a)-[:SUMMONS]->(j)" {:pid player-id :jid jinni-id :jmid jubmoji-provider-id})
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (nil? result)))
        
        (clear)
        ; test just :Jinni:p2p
        (neoqu "CREATE (a:Avatar:Human {id: $pid, uuid: $pid})
            CREATE (:Jubmoji:Identity:Ethereum {provider: 'Ethereum', provider_id: $jmid})
            CREATE (j:Jinni:Avatar:p2p {id: $jid, uuid: $jid})
            CREATE (a)-[:SUMMONS]->(j)" {:pid player-id :jid jinni-id :jmid jubmoji-provider-id})
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (nil? result)))
        
        )

    (testing "Only returns Jinni with Jubmoji"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})" {:pid player-id})
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (nil? result))))
    
    (testing "Cannot have different :Identity:Jubmoji for the same provider_ID"
      (clear)
      (neoqu "CREATE (:Avatar:Human {id: $pid, uuid: $pid})" {:pid player-id})
      (neoqu "CREATE (:Jubmoji:Identity:Ethereum {provider: 'Ethereum', provider_id: $jmid})"  {:jmid jubmoji-provider-id})
      ; another one of those throws when nil? and nil when thrown?
      (is (thrown? Exception (neoqu "CREATE (:Jinni:p2c {id: $jinniid})-[:HAS]->(:Jubmoji:Identity:Ethereum {provider: 'Ethereum',provider_id: $jmid})"  {:jinniid jinni-id :jmid jubmoji-provider-id})))
    ;;   (is (nil? (neoqu "CREATE (:Jinni:p2c {id: $jinniid})-[:HAS]->(:Jubmoji:Identity:Ethereum {provider: 'Ethereum',provider_id: $jmid})"  {:jinniid jinni-id :jmid jubmoji-provider-id})))
    ;;   (let [res (neoqu "CREATE (:Jinni:p2c {id: $jinniid})-[:HAS]->(:Jubmoji:Identity:Ethereum {provider: 'Ethereum',provider_id: $jmid})" 
    ;;            {:jinniid jinni-id :jmid jubmoji-provider-id})]
    ;;         (is nil? res))
    )

    (testing "Returns right summoner with expected properties"
      (clear)
      (neoqu "CREATE (a:Avatar:Human {id: $pid, uuid: $pid})
            CREATE (id:Jubmoji:Identity:Ethereum {provider: 'Ethereum', provider_id: $jmid})
            CREATE (j:Jinni:Avatar:p2c {id: $jid, uuid: $jid})
            CREATE (a)-[:SUMMONS]->(j)
            CREATE (j)-[:HAS]->(id)" {:pid player-id :jid jinni-id :jmid jubmoji-provider-id})
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (= player-id (:id (:summoner result))))))

    (testing "Returns right jinni with expected properties"
      (clear)
      (neoqu "CREATE (a:Avatar:Human {id: $pid, uuid: $pid})
            CREATE (id:Jubmoji:Identity:Ethereum {provider: 'Ethereum', provider_id: $jmid})
            CREATE (j:Jinni:Avatar:p2c {id: $jid, uuid: $jid})
            CREATE (a)-[:SUMMONS]->(j)
            CREATE (j)-[:HAS]->(id)" {:pid player-id :jid jinni-id :jmid jubmoji-provider-id})
      (let [result (db/call cdb/get-summoning-circle {:jmid jubmoji-provider-id :pid player-id})]
        (is (= jinni-id (:id (:jinni result))))))
))


;; create-summoning-circle
; returns nil if $pid is has no :Avatar in db
; returns nil if $pid is an NPC in db

 

; if :Jubmoji already exists
; - does not create :Jinni with :p2c or :p2p
; - does not create another :Identity 
; - returns nil?
; 

; if :Jubmoji does not exist
; - creates :Identity node with :Ethereum and and :Jubmoji label
; - creates :Identity node with $signer as node.provider_id
; - creates  :Jinni node with :p2c label (not a :p2p label)
; - creates :SUMMONS and BONDS relationships between player :Avatar and :Jinni
; - adds node.timestamp with corect value to :SUMMONS relation
; - adds node.since with corect value to :BONDS relation