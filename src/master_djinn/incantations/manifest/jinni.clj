(ns master-djinn.incantations.manifest.jinni
    (:require 
            [master-djinn.util.types.core :refer [load-config uuid avatar->uuid]]
            [master-djinn.util.crypto :refer [ecrecover MALIKS_MAJIK_CARD]]
            [master-djinn.util.db.core :as db]
            [master-djinn.util.db.identity :as iddb]))

(defn activate-jinni
  [player_id jinni_id]
  (:jinni (db/call iddb/create-player {
      :player {
        :id player_id
        :uuid (avatar->uuid player_id)
        ;; :birthday (:birthday args)
      } :jinni {
        :id jinni_id
        :uuid (avatar->uuid jinni_id)
        ;; they are born now
        :birthday (-> (java.time.ZonedDateTime/now java.time.ZoneOffset/UTC)
                      (.format java.time.format.DateTimeFormatter/ISO_INSTANT))}})))
