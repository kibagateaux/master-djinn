(ns master-djinn.portal.logs
  (:require [master-djinn.util.types.core :refer [load-config]]
            [sentry-clj.core :as sentry]
            [steffan-westcott.clj-otel.sdk.otel-sdk :as sdk]
            [steffan-westcott.clj-otel.resource.resources :as res]
            [steffan-westcott.clj-otel.exporter.otlp.grpc.trace :as otel-trace]))

(defonce report-sentry (if-let [dsn (:sentry-dsn (load-config))] (do
  (sentry/init! dsn {:environment (:api-domain (load-config)) :debug true}) true)))

(defn map->sentry [m]
  (mapcat (fn [[k v]] [(name k) (str v)]) m))

(defn handle-error
([error ^Exception message data]
  (println "handle error" data (map->sentry data))
  ;; TODO otel stuff
  (if (not report-sentry) nil
    (sentry/send-event {:message {:message message
                                ;; convert :data map to ["key" "val"] for sentry
                                  :params (map->sentry data)}
                        :throwable error})))
([error ^Exception message data player-id]
  (println "handle error" message report-sentry (:sentry-dsn (load-config)))
  ;; TODO otel stuff
  (if (nil? report-sentry) nil
    (sentry/send-event {:message {:message message :params (map->sentry data)}
                        :throwable error
                        :user {:id player-id}}))))

;; init docs https://github.com/steffan-westcott/clj-otel/blob/8ab82dc918540021dc94e1c43b39edf8ca9c621c/clj-otel-sdk/src/steffan_westcott/clj_otel/sdk/otel_sdk.clj
(defn init-otel! []
  (sdk/init-otel-sdk!
    "master-djinn-api"
    {:set-as-default true
    ;; :set-as-global true ;; Fails but is documented use. maybe calling too early?
        :resources [(res/host-resource)
                 (res/os-resource)
                 (res/process-resource)
                 (res/process-runtime-resource)]
     :tracer-provider
       {:span-processors
         [{:exporters [(otel-trace/span-exporter)]}]}}))

;; TODO https://github.com/getsentry/sentry-clj?tab=readme-ov-file#additional-initialisation-options
;; need :release, :environment, 
;; (sentry/init! (:sentry-dsn (load-config))  {:environment some-func :debug true :release githash?}))

(defn log-err 
  "sends errors to Sentry for debugging and tracking vs frontend"
  [e]
  ;; TODO create new source in sentry dashboard
  ;; add lib https://github.com/getsentry/sentry-clj
  ;; (sentry/send-event {:message "Something has gone wrong!" :throwable e})

  )