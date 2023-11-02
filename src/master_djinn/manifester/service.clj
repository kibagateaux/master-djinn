
(ns master-djinn.manifester.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [master-djinn.util.gql.schema :as schema]
            [master-djinn.manifester.identity :as id]
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
    {:name ::mj:signed-request
    ;;  :error internal/on-error-error-response}
    :enter (fn [context]
      (let [vars (get-in context [:request :graphql-vars])]
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
    :host (:api-host (load-config))
    ;; :host (or (System/getenv "API_HOST") "0.0.0.0")
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
  [compiled-schema port]
  (let [interceptors (gql-interceptors compiled-schema)
        ;; aaaa (println "custom gql" interceptors)
        routes (into #{["/graphql" :post interceptors :route-name ::graphql-api]
                      ["/graphiql" :get (p2/graphiql-ide-handler gql-dev-server-options) :route-name ::graphql-ide]}
                  (p2/graphiql-asset-routes (:gql-asset-path gql-dev-server-options)))]
    ;; (println "custom gql" gql-dev-server-options)
    ;; (println "custom gql" (:host gql-dev-server-options))
    (-> {:env :dev
         ::http/routes routes
         ::http/port (:port gql-dev-server-options)
         ::http/host (:host gql-dev-server-options)
         ::http/type :jetty
         ::http/join? false}
         p2/enable-graphiql
        (p2/enable-subscriptions compiled-schema gql-dev-server-options)
        http/create-server)))

(def custom-gql-service (create-gql-service (schema/jinni-schema) 8888))

(defonce dev-server-options {
    :gql-path "/graphql"
    :ide-path "/graphigql"
    :oauth-init-path "/oauth/init"
    :oauth-cb-path "/oauth/callback"
    :oauth-refresh-path "/oauth/refresh"
    :gql-asset-path "/assets/graphiql" ;; TODO figure out what thismeans
    :port 8000
    :host (or (System/getenv "app.host") "localhost")
})

(defn ^:private create-custom-service
  "
  https://lacinia-pedestal.readthedocs.io/en/latest/interceptors.html
  TODO graphql/graphiql routes arent working for some reason but auth ones are.
  Even though its a 'bug' i actually like it because it basically makes them 2 different micro services on the same server
  they use same codebase (kinda not really, just db.core) and makes it more modular for the future.
  Can theoretically host same codebase on two diff servers to separate social from database secrets  
  "
  [compiled-schema options]
  ;; (println "compiled structured schema")
  (clojure.pprint/pprint  compiled-schema)
  (clojure.pprint/pprint  (get-in compiled-schema [:objects]))
  (clojure.pprint/pprint  (get-in compiled-schema [:objects :Query]))

  (let [{:keys [port oauth-init-path oauth-cb-path oauth-refresh-path]} options
        routes #{[oauth-init-path :get (conj [(body-params/body-params)] id/oauth-init-handler) :route-name ::oauth-init]
                [oauth-cb-path :post (conj [(body-params/body-params)] id/oauth-callback-handler) :route-name ::oauth-callback-post]
                [oauth-cb-path :get (conj [(body-params/body-params)] id/oauth-callback-handler) :route-name ::oauth-callback-get]
                [oauth-refresh-path :post (conj [(body-params/body-params)] id/oauth-refresh-token-handler) :route-name ::oauth-refresh]
                }]
    (->  {:env :dev
          ::http/routes routes
          ::http/port port
          ;;  ::http/allowed-origins ["http://localhost:8080"]
          ::http/type :jetty
          ::http/join? false})))

;; I think i might need to run two servers. One for GQL and one normal one since GQL interceptors prevent default pedestal ones from being created and they block any request without a :query
(defonce custom-service
  (-> (schema/jinni-schema)
      (create-custom-service dev-server-options)
      http/create-server))