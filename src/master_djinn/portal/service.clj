
(ns master-djinn.portal.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [master-djinn.util.gql.schema :as schema]
            [master-djinn.portal.core :as id]
            [master-djinn.util.crypto :refer [handle-signed-POST-query]]

            ;; for default lacinia config
            [com.walmartlabs.lacinia.pedestal2 :as p2]
            [com.walmartlabs.lacinia.pedestal :refer [inject]]
            [com.walmartlabs.lacinia.pedestal.internal :as lp-internal]
            
            ;; for manual serverr config
            [io.pedestal.interceptor :refer [interceptor]]
            [io.pedestal.http.body-params :as body-params]
            [master-djinn.util.types.core :refer [load-config]]

            ;; [com.walmartlabs.lacinia :refer [execute]]
            ;; [clojure.data.json :as json]
            ;; [clojure.string :as str]
            ))

(def ^:private signed-request-interceptor
  "checks if query includes a signed query and 
  1. checks if signed query matches raw query
  2. ecrecovers signer address
  3. injects address into app context

   This must come after [[body-data-interceptor inject-app-context-interceptor]] as they provide the data we consume
   
   returns an error if signed query does not match raw query
   With or without signed query, GQL resolvers handle authorization on individual data resources"
  (interceptor
    {:name ::parse-signed-request
     :error lp-internal/on-error-error-response
    :enter (fn [context]
      (let [vars (get-in context [:request :graphql-vars :verification])]
        ;; (println "service.signed-request : "  (:signature vars) (:_raw_query vars))

        (if (and (:signature vars) (:_raw_query vars))
          ;; if signed query sent handle
          (handle-signed-POST-query context)
          ;; else pass along as normal GQL query
          context)))
      }))

(defonce gql-dev-server-options {
    :api-path "/graphql"
    :ide-path "/graphiql"
    :oauth-init-path "/oauth/init"
    :oauth-cb-path "/oauth/callback"
    :oauth-refresh-path "/oauth/refresh"
    :gql-asset-path "/assets/graphiql" ;; TODO figure out what thismeans
    :port 8888
    :host (or (:api-host (load-config)) "0.0.0.0") ;; jetty defaults to serving on 0.0.0.0
})

;; This is an adapted service map, that can be started and stopped
;; From the REPL you can call server/start and server/stop on this service
(def default-gql-service (-> (schema/jinni-schema)
                 (p2/default-service nil)
                 http/create-server)) ;; might be p2/ not p2/ bc they use that internally

(defn gql-interceptors [compiled-schema] 
  (-> compiled-schema
      (p2/default-interceptors nil)
      (inject signed-request-interceptor :after ::p2/graphql-data)))

(defn create-gql-service
  [compiled-schema options]
  (let [interceptors (gql-interceptors compiled-schema)
        {:keys [port host oauth-init-path oauth-cb-path oauth-refresh-path]} options
        ;; aaaa (println "custom gql" interceptors)
        routes (into #{["/graphql" :post interceptors :route-name ::graphql-api]
                      ["/graphiql" :get (p2/graphiql-ide-handler gql-dev-server-options) :route-name ::graphql-ide]
                      [oauth-init-path :get (conj [(body-params/body-params)] id/oauth-init-handler) :route-name ::oauth-init]
                      [oauth-cb-path :post (conj [(body-params/body-params)] id/oauth-callback-handler) :route-name ::oauth-callback-post]
                      [oauth-cb-path :get (conj [(body-params/body-params)] id/oauth-callback-handler) :route-name ::oauth-callback-get]
                      ;; [oauth-refresh-path :post (conj [(body-params/body-params)] id/oauth-refresh-token-handler) :route-name ::oauth-refresh]
                      }
                  (p2/graphiql-asset-routes (:gql-asset-path gql-dev-server-options)))]
    ;; (println "custom gql" gql-dev-server-options)
    ;; (println "custom gql" (:host gql-dev-server-options))
    (-> {:env :dev
         ::http/routes routes
         ::http/port port
         ::http/host host
         ::http/type :jetty
         ::http/join? false}
         p2/enable-graphiql
        (p2/enable-subscriptions compiled-schema gql-dev-server-options)
        http/create-server)))

(def custom-gql-service (create-gql-service (schema/jinni-schema) gql-dev-server-options))
