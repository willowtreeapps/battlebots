(ns wombats.daos.core
  (:require [datomic.api :as d]
            [wombats.daos.user :as user]
            [wombats.daos.arena :as arena]))

(defn init-dao-map
  "Creates a map of all the data accessors that can be used inside of handlers / socket connections.
  This makes no assumption of authentication / authorization which should be handled prior to gaining
  access to these functions."
  [{:keys [conn] :as datomic}]
  {;; User DAOS
   :get-users (user/get-users conn)
   :get-user-by-id (user/get-user-by-id conn)
   :get-user-by-access-token (user/get-user-by-access-token conn)
   :create-or-update-user (user/create-or-update-user conn)
   ;; Wombat Management DAOS
   :get-user-wombats (user/get-user-wombats conn)
   :get-wombat-by-name (user/get-wombat-by-name conn)
   :add-user-wombat (user/add-user-wombat conn)
   ;; Arena Management DAOS
   :get-arenas (arena/get-arenas conn)
   :get-arena-by-name (arena/get-arena-by-name conn)
   :get-arena-by-id (arena/get-arena-by-id conn)
   :add-arena (arena/add-arena conn)
   :retract-arena (arena/retract-arena conn)})
