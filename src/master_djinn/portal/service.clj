
(ns master-djinn.portal.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [master-djinn.portal.gql.schema :as schema]
            [master-djinn.portal.core :as id]
            [master-djinn.util.crypto :refer [handle-signed-POST-query]]
            [master-djinn.incantations.divine.openrouter-dalle :refer [get-jinn-img-handler]] ;; TODO codesmell all server should be in portal.core but circular reference
            [com.walmartlabs.lacinia.pedestal2 :as p2]
            [com.walmartlabs.lacinia.pedestal :refer [inject]]
            [com.walmartlabs.lacinia.pedestal.internal :as lp-internal]
            [io.pedestal.interceptor :refer [interceptor]]
            ;; [ring.middleware.cors :refer [wrap-cors]]
            [io.pedestal.http.cors :as cors]
            [io.pedestal.http.body-params :as body-params]
            [master-djinn.util.types.core :refer [load-config]]
            [master-djinn.util.core :refer [get-signer]]
            [master-djinn.portal.logs :as log]))


(defonce isprod? (clojure.string/includes? (:api-domain (load-config)) "scryer.jinni.health"))
(defonce gql-server-config {
    :api-path "/graphql"
    :ide-path "/graphiql"
    :oauth-init-path "/oauth/init"
    :oauth-cb-path "/oauth/callback"
    :oauth-refresh-path "/oauth/refresh"
    :gql-asset-path "/assets/graphiql" ;; TODO figure out what this means
    :port (if isprod? 80 8888)
    :host (or (:api-host (load-config)) "0.0.0.0") ;; jetty defaults to serving on 0.0.0.0
    :cors {
      :allowed-origins (if isprod? ["https://app.jinni.health" "https://scryer.jinni.health" nil] ["http://localhost:8081" "http://localhost:8888" "http://zahhi.local:8081" nil])
      :allowed-methods [:get :post :options]
      :allowed-headers ["Content-Type" "Authorization"]
      :max-age 3600}
})

(def ^:private signed-request-interceptor
  "checks if query includes a signed query and 
  1. checks if query variables contains `verification` data for requested query and player signature of query
    1a. if `verification` does not exist, skips following steps passing on original query context.
  2. ecrecovers `verification` signer address (could be invalid signature returning random signer)
  3. injects address into app context
  4. With or without signer, GQL resolvers handle authorization on individual data resources

  @DEV: MUST come after [[body-data-interceptor inject-app-context-interceptor]] as they provide the data we consume
   
  returns original query context if no `verification` passed and does not inject `signer`"
  (interceptor
    {:name ::parse-signed-request
     :error lp-internal/on-error-error-response
    :enter (fn [context]
      (let [verification (get-in context [:request :graphql-vars :verification])]
          ;; (println "\n\n port:serv:sign-intercept - ")
          ;; (println (:signature vars) (:_raw_query vars))
          ;; (clojure.pprint/pprint (get-in context [:request :graphql-vars]))
        (if (and (:signature verification) (:_raw_query verification))
            (let [ctx (handle-signed-POST-query context)
                  pid (get-signer ctx)]
              ;; TODO check if signer is valid :Identity here?
              ;; technically non-player signers could be authorized to access their data

              (log/identify-player pid) ; MUST identify player otherwise tracks silently fail
              (log/track-player pid "Query Sent" {:query (:_raw_query verification)})
              ctx)
          ;; else pass along as normal GQL query
          context)))}))

(defn gql-interceptors [compiled-schema] 
  (-> compiled-schema
      (p2/default-interceptors nil)
      (inject signed-request-interceptor :after ::p2/graphql-data)))

;; (def gql-interceptors
;;   [cors-interceptor
;;    (lacinia-pedestal/query-parser-interceptor compiled-schema)
;;    (lacinia-pedestal/status-conversion-interceptor compiled-schema)
;;    (lacinia-pedestal/graphql-data-interceptor)
;;    lacinia-pedestal/error-response-interceptor
;;    signed-request-interceptor])


(defn create-gql-service
  [compiled-schema options]
  (log/init-otel!)
  (println "P:gql-servce:is-remote?" isprod? (if isprod? 80 8888) (:api-domain (load-config)))
  (let [interceptors (gql-interceptors compiled-schema)
        {:keys [port host oauth-init-path oauth-cb-path oauth-refresh-path]} options
        routes (into #{["/graphql" :post interceptors :route-name ::graphql-api]
                      ["/graphiql" :get (p2/graphiql-ide-handler gql-server-config) :route-name ::graphql-ide]
                      [oauth-cb-path :get (conj [(body-params/body-params)] id/oauth-callback-handler) :route-name ::oauth-callback-get]
                      ["/avatars/:jid" :get (conj [(body-params/body-params)] get-jinn-img-handler) :route-name ::see-jinn]
                      ["/portals" :get (conj [(body-params/body-params)] id/handle-redirects) :route-name ::campaign-redirects]
                      ;; [oauth-refresh-path :post (conj [(body-params/body-params)] id/oauth-refresh-token-handler) :route-name ::oauth-refresh]
                      }
                  (p2/graphiql-asset-routes (:gql-asset-path gql-server-config)))]
    (-> {:env (if isprod? :prod :dev)
         ::http/routes routes
         ::http/port port
         ::http/host host
         ::http/type :jetty
         ::http/join? false
         ::http/allowed-origins (:allowed-origins (:cors gql-server-config))
        }
         p2/enable-graphiql
        (p2/enable-subscriptions compiled-schema gql-server-config)
        http/create-server)))

(def custom-gql-service (create-gql-service (schema/jinni-schema) gql-server-config))
