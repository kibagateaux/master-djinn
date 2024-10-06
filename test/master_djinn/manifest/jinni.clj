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
