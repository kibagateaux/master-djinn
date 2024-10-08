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
  (let [response (db/call iddb/create-player {
      :now (now)
      :master_id master-id
      :player {
        :id player-id
        :uuid (avatar->uuid player-id)
        ;; :birthday (:birthday args)
      } :jinni {
        :id jinni-id
        :uuid (avatar->uuid jinni-id)}})]
  (println "Inc:mani:jinni:activateL db resp" response) 
  (:jinni response)))

(defn create-summoning-circle
  [summoner jubmoji-id]
   (try (let [jid (juuid summoner)
        uuid (avatar->uuid jid)
        aaaa (println "Inc:Mani:Jinni:create-circle:summoner" summoner)
        aaaa (println "Inc:Mani:Jinni:create-circle:jid" jid)
        aaaa (println "Inc:Mani:Jinni:create-circle:jubmoji" jubmoji-id)
        res (db/call cdb/create-summoning-circle {
              :pid summoner
              :signer jubmoji-id
              :now (now)
              :jinni {
                :id jid
                :uuid uuid
              }
            })]
          (println "Inc:Mani:Jinni:create-circle:response" res)
            (:jinni res))
         (catch Exception e 
         (println "exception creating circle: " (ex-message e))
          (throw e))))

(defn join-summoning-circle
  [player-id summoner jinni-id]
    ;; TODO create-npc shouldnt be required here if they already onboarded to the game
    ;; Would be required if non-jinni player e.g. BYOWallet, Github org login
    (println "Inc:Mani:Jinni:join-circle:ensure npc : " player-id jinni-id)
    (db/call iddb/create-npc {
      :player {:id player-id :uuid (avatar->uuid player-id)}
      :summoner summoner
      :now (now)
      :jinni { :id (avatar->uuid (now)) :uuid (juuid player-id)}}) ; npc jid not p2c jid from params
    (println "Inc:Mani:Jinni:join-circle:summon" summoner)
    (let [resp (db/call cdb/join-summoning-circle {:pid player-id :jid jinni-id :now (now)})]
    
    (println "Inc:Mani:Jinni:join-circle:db resp:" resp)
    (:jinni resp)))

(defn apply-summoning-circle
  "Do not auto create npc account.
  Must have already been vouched for and joined a circle to apply for more.
  Allows players to async/remotely ask to join a summoning circle that alreay exists"
  [player-id jinni-id]
    (println "Inc:Mani:Jinni:apply-circle:" player-id jinni-id)
    (:jinni (db/call cdb/apply-summoning-circle {:pid player-id :jid jinni-id :now (now)})))


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
  (:widgets (db/call db/set-widget-settings {
      :jinni_id jinni-id
      :widgets (map
        (fn [setting] 
          (let [provider (name (:provider setting))
                uuid (widget->uuid jinni-id provider (:id setting) "0.0.1")]
          (merge setting {:uuid uuid :provider provider})))
        widgets)})))


(defn activate-widget
  "functino call when a a new accounts settings are being initiated.
    e.g. post waitlist_npc, activate_jinni"
  [player-id widgets]
  ;; TODO. we call npc() from frontend already. extra db call every time to ensure + get jid from player
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
