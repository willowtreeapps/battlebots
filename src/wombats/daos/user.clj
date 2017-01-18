(ns wombats.daos.user
  (:require [datomic.api :as d]
            [wombats.daos.helpers :refer [gen-id
                                          get-entity-id
                                          get-entity-by-prop
                                          get-entities-by-prop]]))

(def public-user-fields [:user/id
                         :user/github-username
                         :user/avatar-url])

(defn- get-user-entity-id
  "Returns the entity id of a user given the public user id"
  [conn user-id]
  (get-entity-id conn :user/id user-id))

(defn get-users
  "Returns all users in the system"
  [conn]
  (fn []
    (get-entities-by-prop conn :user/id public-user-fields)))

(defn get-user-by-id
  "Returns a user by a given id"
  [conn]
  (fn [user-id]
    (get-entity-by-prop conn :user/id user-id public-user-fields)))

(defn get-user-by-access-token
  "Returns a user by a given access token"
  [conn]
  (fn [access-token]
    (get-entity-by-prop conn :user/access-token access-token)))

(defn create-or-update-user
  "If a user does not exist in the system, create one. If it does, update values
  and attach the new access token"
  [conn]
  (fn [{:keys [email login id avatar_url] :as user}
      access-token
      current-user-id]
    (let [update {:db/id (d/tempid :db.part/user)
                  :user/access-token access-token
                  :user/github-username login
                  :user/github-id id
                  :user/email email
                  :user/avatar-url avatar_url}]
      (if current-user-id
        (d/transact-async conn [(merge update {:db/id current-user-id
                                               :user/id (gen-id)})])
        (d/transact-async conn [update])))))

(defn get-user-wombats
  "Returns all wombats belonging to a specified user"
  [conn]
  (fn [user-id]
    (vec (apply concat
                (d/q '[:find (pull ?wombats [:wombat/name
                                             :wombat/url
                                             :wombat/id])
                       :in $ ?user-id
                       :where [?user :user/id ?user-id]
                       [?user :user/wombats ?wombat]
                       [?wombats :wombat/name]]
                     (d/db conn)
                     user-id)))))

(defn get-wombat-by-name
  "Returns a wombat by querying its name"
  [conn]
  (fn [name]
    (get-entity-by-prop conn :wombat/name name)))

(defn add-user-wombat
  "Creates a new wombat for a particular user"
  [conn]
  (fn [user-id {:keys [:wombat/name :wombat/url]}]
    (let [wombat-id (d/tempid :db.part/user)
          user-db-id (get-user-entity-id conn user-id)]
      (d/transact conn [{:db/id wombat-id
                         :wombat/name name
                         :wombat/url url
                         :wombat/id (gen-id)}
                        {:db/id user-db-id
                         :user/wombats wombat-id}]))))
