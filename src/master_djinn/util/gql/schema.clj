(ns master-djinn.util.gql.schema
  (:require
    [master-djinn.util.types.core :refer [types action-type->name]]
    [com.walmartlabs.lacinia.schema :as schema]
    [com.walmartlabs.lacinia.util :as util]
    [master-djinn.incantations.transmute.core :as trans]
    [master-djinn.manifester.identity :as id]
    [master-djinn.util.db.core :as db]))

(def resolver-map {
  :Query/players (fn [ctx args val]
    (let [results ((db/generate-resolver db/get-all-players) ctx args val)]
      (:players results)))

  
  ;; nested field resolvers e.g. { identity { avatar { actions { id }}}}
  ;; :Avatar/actions (db/generate-resolver db/get-user-actions)

  ;; mutations
  ;; :mutations/submit_data example-resolver
  :Mutation/submit_data trans/multiplexer
  :Mutation/activate_jinni id/activate-jinni
})

(defn jinni-schema
  []
  (-> types
      (util/inject-resolvers resolver-map)
      schema/compile))