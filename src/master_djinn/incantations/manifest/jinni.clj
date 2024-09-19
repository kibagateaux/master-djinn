(ns master-djinn.incantations.manifest.jinni
    (:require 
            [master-djinn.util.core :refer [now]]
            [master-djinn.util.types.core :refer [load-config juuid avatar->uuid widget->uuid]]
            [master-djinn.util.crypto :refer [ecrecover MALIKS_MAJIK_CARD]]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.db.circles :as cdb]
            [master-djinn.util.db.identity :as iddb]))

(defn jinni-waitlist-npc
  [player-id]
  (:jid (db/call iddb/create-npc {
      :player {:id player-id :uuid (avatar->uuid player-id)}
      :now (now)
      :jinni { :id (avatar->uuid (now)) :uuid (juuid player-id)}})))

(defn jinni-activate
  [player-id jinni-id master-id]
  (:jinni (db/call iddb/create-player {
      :now (now)
      :master_id master-id
      :player {
        :id player-id
        :uuid (avatar->uuid player-id)
        ;; :birthday (:birthday args)
      } :jinni {
        :id jinni-id
        :uuid (avatar->uuid jinni-id)}})))


(defn get-summoning-circle
  [jubmoji]
   (db/call cdb/get-summoning-circle {:pid jubmoji}))

(defn create-summoning-circle
  [summoner jubmoji-id]
   (let [attesters (:attesters (db/call iddb/get-player-attesters {:pid summoner}))
        is-player? (some #(= (:id %) MALIKS_MAJIK_CARD) attesters)
        jid (juuid summoner)]
      (if is-player?
        ;; (do 
        (db/call cdb/create-summoning-circle {
            :pid summoner
            :signer jubmoji-id
            :now (now)
            :jid jid
            :juuid (avatar->uuid jid)
          })
        ;; jid
        ;; )
        nil)))

;; TODO for join+apply there may not be a player in DB yet. player-id is just autogenerated wallet
;; 

(defn join-summoning-circle
  [player-id summoner jinni-id]
    ;; TODO create-npc shouldnt be required here if they already onboarded to the game
    ;; Would be required if non-jinni player e.g. BYOWallet, Github org login
    (db/call iddb/create-npc {
      :player {:id player-id :uuid (avatar->uuid player-id)}
      :summoner summoner
      :now (now)
      :jinni { :id (avatar->uuid (now)) :uuid (juuid player-id)}}) ; npc jid not p2c jid from params
    (db/call cdb/join-summoning-circle {:pid player-id :jid jinni-id}))

(defn apply-summoning-circle
  "Do not auto create npc account.
  Must have already been vouched for and joined a circle to apply for more.
  Allows players to async/remotely ask to join a summoning circle that alreay exists"
  [player-id jinni-id]
    (db/call cdb/apply-summoning-circle {:pid player-id :jid jinni-id}))


;; TODO group djinn
;; func 1. (short term)
;; player submits proof from a jubmoji card (card signed their address)
;; check that signer is a valid community card and signed is a valid player
;; community jinni MUST already exist (currently manually added to DB by Malik)
;; add player-BONDS {time: (now)}-jinni
;; associate player with group in analytics
;; https://segment.com/docs/connections/sources/catalog/libraries/server/clojure/

;; func 2. (long term) 
;; allow player to submit proof that MASTER_DJINN has signed off on a new card address for a group.
;; create new jinni with first human being player submitting proof
;; https://segment.com/docs/connections/sources/catalog/libraries/server/clojure/
;; associate player with group in analytics


(defn set-widgets
  [jinni-id widgets]
  ;; (let [w-uuid (map
  ;;         #(assoc % :confi 
  ;;           (assoc :uuid (widget->uuid player_id (:provider ) (:widget_id %) "0.0.1")))
  ;;       widgets)]
  ;;     (println "widgest w uuid" w-uuid)
  ;;     ;; (clojure.pprint/pprint w-uuid)
  ;;     )
  (:widgets (db/call db/set-widget-settings {
      :jinni_id jinni-id
      :widgets (map
        (fn [setting] 
          (let [provider (name (:provider setting))
                uuid (widget->uuid jinni-id provider (:id setting) "0.0.1")]
          (merge setting {:uuid uuid :provider provider})))
        widgets)})))


(defn activate-widget
  [player-id widgets]
  ;; (let [w-uuid (map
  ;;         #(assoc % :confi 
  ;;           (assoc :uuid (widget->uuid player_id (:provider ) (:widget_id %) "0.0.1")))
  ;;       widgets)]
  ;;     (println "widgest w uuid" w-uuid)
  ;;     ;; (clojure.pprint/pprint w-uuid)
  ;;     )
  
  ;; TODO.eng extra db call every time someone updates settings. Better to abstract into another API call on frontend
  (let [jid (:jid (db/call iddb/create-npc
          {:player {:id player-id :uuid (avatar->uuid player-id)}
          :summoner nil
          :now (now)
          :jinni { :id (avatar->uuid (now)) :uuid (juuid player-id)}}))]
  (:widgets (db/call db/set-widget-settings {
      :jinni_id jid
      :widgets (map
        (fn [setting] 
          (let [provider (name (:provider setting))
                uuid (widget->uuid jid provider (:id setting) "0.0.1")]
          ;; default settings with priority 10. clients can overried if user inputs
          (merge {:priority 10} (merge setting {:uuid uuid :provider provider}))))
        widgets)}))))
