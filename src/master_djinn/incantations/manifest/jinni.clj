(ns master-djinn.incantations.manifest.jinni
    (:require 
            [master-djinn.util.core :refer [now]]
            [master-djinn.util.types.core :refer [load-config uuid avatar->uuid widget->uuid]]
            [master-djinn.util.crypto :refer [ecrecover MALIKS_MAJIK_CARD]]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.db.identity :as iddb]))

(defn jinni-activate
  [player_id jinni_id master_id]
  (:jinni (db/call iddb/create-player {
      :now (now)
      :master_id master_id
      :player {
        :id player_id
        :uuid (avatar->uuid player_id)
        ;; :birthday (:birthday args)
      } :jinni {
        :id jinni_id
        :uuid (avatar->uuid jinni_id)
        :birthday (now)}})))


(defn create-summoning-circle
  [player_id jubmoji_id]
  
  )

(defn join-summoning-circle
  [player_id jubmoji_id]
  
  )


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


(defn activate-widget
  [player_id widgets]
  ;; (let [w-uuid (map
  ;;         #(assoc % :confi 
  ;;           (assoc :uuid (widget->uuid player_id (:provider ) (:widget_id %) "0.0.1")))
  ;;       widgets)]
  ;;     (println "widgest w uuid" w-uuid)
  ;;     ;; (clojure.pprint/pprint w-uuid)
  ;;     )
      
  (:widgets (db/call db/set-widget-settings {
      :player_id player_id
      :widgets (map
        (fn [setting] 
          (let [provider (name (:provider setting))
                uuid (widget->uuid player_id provider (:id setting) "0.0.1")]
          (merge setting {:uuid uuid :provider provider})))
        widgets)})))
