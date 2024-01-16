(ns master-djinn.portal.logs
  (:require [steffan-westcott.clj-otel.sdk.otel-sdk :as sdk]
            [steffan-westcott.clj-otel.resource.resources :as res]
            [steffan-westcott.clj-otel.exporter.otlp.grpc.trace :as otel-trace]))

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