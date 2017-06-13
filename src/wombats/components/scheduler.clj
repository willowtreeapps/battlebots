(ns wombats.components.scheduler
  (:require [com.stuartsierra.component :as component]
            [wombats.scheduler.core :as scheduler]
            [wombats.daos.game :as game]
            [wombats.daos.arena :as arena]
            [wombats.daos.helpers :as helpers]))

(defrecord Scheduler [config datomic scheduler]
  component/Lifecycle
  (start [component]
    (if scheduler
      component
      (let [conn (get-in datomic [:database :conn])
            aws-credentials (get-in config [:settings :aws])
            lambda-settings (get-in config [:settings :api-settings :lambda])]
        ;; Go through all the pending games, and schedule them
        (assoc component
               :scheduler
               (scheduler/schedule-pending-games (game/get-all-pending-games conn)
                                                 (game/start-game conn aws-credentials lambda-settings))
               :add-game
               (scheduler/add-game-scheduler scheduler/add-game-request
                                        (game/add-game conn)
                                        (helpers/gen-id)
                                        (game/get-game-by-id conn)
                                        (arena/get-arena-by-id conn)
                                        (game/start-game conn aws-credentials lambda-settings))))))
  (stop [component]
    (if-not scheduler
      component
      (assoc component :scheduler nil))))

;; Public component methods

(defn new-scheduler
  []
  (map->Scheduler {}))
