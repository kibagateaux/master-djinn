;; Tests here covers entire incantation in one place
;; resolver/setup at http level and core game logic/actions at incantations level
;; so while code separated by concerns (acl/setup vs doing the actual thing) test suite 


;; waitlist-npc
;; - must have :verification
;; - pid for id verification and new player must be :verification ecrecover
;; - pid must be ::signer type
;; - if :Avatar with pid already then do nothing and return pid
;; - returns status if jid early if they already a p2p :Avatar. No calls to db
;; 


; on success
;; - must create new :Avatar if none with that id already
;; - creates new :Ethereum identity in db with pid
;; - creates player-:HAS->Identity relation
;; :Human :SUMMONS and :BONDS with :NPC
;; :Human :SUMMONS and :BONDS not with :Jinni
;; nodes have values
;; uuid = (uuid pid)
;; juuid = (juuid pid)
;; jid = (uuid (now))



;; activate-jinni


(deftest create-player-test
  (let [existing-player-id "existing-player-id"
        existing-npc-id "existing-npc-id"
        existing-jinni-id "existing-jinni-id"
        initial-node-count (get-node-count)]

    ;; Activate Jinni when no player Avatar account exists
    (testing "Activate Jinni with no existing Avatar account"
      (let [jinni-uuid (j/jinni-activate {:player_id existing-player-id})]
        (is (some? jinni-uuid))
        (let [final-count (get-node-count)]
          (is (= (+ initial-node-count 3) final-count))
          (is (not (nil? (neoqu (str "MATCH (j:Jinni {id: $jid}) RETURN j") {:jid jinni-uuid}))))
          (is (not (nil? (neoqu (str "MATCH (id:Identity:Ethereum {provider_id: $pid}) RETURN id") {:pid existing-player-id}))))
          (is (= (neoqu "MATCH (j:Jinni {id: $jid}) RETURN j.relation.since" {:jid jinni-uuid}) (now))))))

    ;; Activate Jinni when player Avatar account already exists
    (testing "Activate Jinni with existing Avatar account"
      (let [jinni-uuid (j/jinni-activate {:player_id existing-npc-id})]
        (is (some? jinni-uuid))
        (let [final-count (get-node-count)]
          (is (= initial-node-count final-count))
          (is (not (nil? (neoqu (str "MATCH (j:Jinni {id: $jid}) RETURN j") {:jid jinni-uuid}))))
          (is (nil? (neoqu (str "MATCH (id:Identity:Ethereum {provider_id: $pid}) RETURN id") {:pid existing-npc-id})))
          (is (= (neoqu "MATCH (j:Jinni {id: $jid}) RETURN j.relation.since" {:jid jinni-uuid}) (now -10000))))))

    ;; Ensure relation.since is set correctly for new Jinni
    (testing "Check relation.since for newly created Jinni"
      (let [jinni-uuid (j/jinni-activate {:player_id existing-player-id})]
        (is (= (neoqu "MATCH (j:Jinni {id: $jid}) RETURN j.relation.since" {:jid jinni-uuid}) (now)))))

    ;; Ensure relation.since is not updated for existing Jinni
    (testing "Check relation.since for existing Jinni"
      (let [jinni-uuid (j/jinni-activate {:player_id existing-npc-id})]
        (is (= (neoqu "MATCH (j:Jinni {id: $jid}) RETURN j.relation.since" {:jid jinni-uuid}) (now -10000)))))

    ;; Ensure no duplicate Jinni is created for existing player
    (testing "No duplicate Jinni created for existing player"
      (let [jinni-uuid (j/jinni-activate {:player_id existing-npc-id})]
        (is (= jinni-uuid (j/jinni-activate {:player_id existing-npc-id}))))))
)