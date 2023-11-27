(ns master-djinn.util.core
    (:require [clj-time.core :as t]
            [clj-time.format :as f]))

(defn now []
  (-> (t/now)
      (t/to-time-zone (t/time-zone-for-id "UTC"))
      (f/unparse (f/formatters :date-time))))