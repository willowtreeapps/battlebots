(ns wombats.game.bot.decisions.shoot-spec
  (:require [wombats.game.bot.decisions.shoot :refer :all :as shoot]
            [wombats.constants.arena :as ac]
            [wombats.arena.utils :as au]
            [wombats.game.test-game :refer [o b
                                               bot1-private
                                               bot2-private
                                               b1 b2
                                               test-players
                                               test-arena]])
  (:use clojure.test))

(deftest add-shot-metadata-spec
  (is (= {:hp 10
          :md {:1234 {:type :shot
                      :decay 1}}}
         ((#'shoot/add-shot-metadata "1234") {:hp 10}))
      "Adds shot metadata to a cell")
  (is (= {:hp 12
          :md {:1234 {:type :shot
                      :decay 1}
               :5678 {:type :explosion
                      :decay 5}}}
         ((#'shoot/add-shot-metadata "1234") {:hp 12
                                              :md {:5678 {:type :explosion
                                                          :decay 5}}}))
      "Adds shot metadata to a cell already containing metadata"))

(deftest add-shot-damage-spec
  (is (= {:hp 190} ((#'shoot/add-shot-damage 10) {:hp 200})))
  (is (= {:hp 12} ((#'shoot/add-shot-damage 15) {:hp 27}))))

(deftest replace-destroyed-cell-spec
  (is (= (assoc o :md {:1234 {:type :destroyed
                              :decay 1}})
         ((#'shoot/replace-destroyed-cell "1234") (assoc b :hp 0)))
      "A cell is replaced if it is destroyed")
  (is (= b
         ((#'shoot/replace-destroyed-cell "1234") b))
      "A cell is not modified if it is not destroyed"))

(deftest resolve-shot-cell-spec
  (is (= {:hp 5
          :md {:1234 {:type :shot
                      :decay 1}}}
         (#'shoot/resolve-shot-cell {:hp 20} 15 "1234")))
  (is (= {:hp -10
          :md {:1234 {:type :shot
                      :decay 1}}}
         (#'shoot/resolve-shot-cell {:hp 20} 30 "1234"))))

(deftest shot-should-progress-spec
  (is (= false (#'shoot/shot-should-progress? false (:open ac/arena-key) 10))
      "Returns false when should-progress? prop is false")
  (is (= false (#'shoot/shot-should-progress? true (:open ac/arena-key) 0))
      "Returns false when hp is 0")
  (is (= false (#'shoot/shot-should-progress? true (:open ac/arena-key) -10))
      "Returns false when hp is less than 0")
  (is (= false (#'shoot/shot-should-progress? true (:steel ac/arena-key) 10))
      "Returns false when encountering an cell it cannot pass through")
  (is (= true (#'shoot/shot-should-progress? true (:open ac/arena-key) 10))
      "Returns true when all of the above test cases return true"))

(deftest update-victim-hp-spec
  (is (= {:players test-players}
         (#'shoot/update-victim-hp "1" b 20 {:players test-players}))
      "No damage is applied if the cell is not a player")
  (is (= {:players [bot1-private
                      (assoc bot2-private :hp 30)]}
         (dissoc (#'shoot/update-victim-hp "1" b2 20 {:players test-players}) :messages))
      "Damage is applied to the victim if the cell is a player"))

(deftest reward-shooter-spec
  (is (= {:players [(assoc bot1-private :hp 120)
                    bot2-private]}
         (dissoc (#'shoot/reward-shooter "1" b2 50 {:players test-players}) :messages))
      "When a player strikes another player, they will recieve hp in the amount of 2x the damage applied to the victim.")
  (is (= {:players [(assoc bot1-private :hp 70)
                    bot2-private]}
         (dissoc (#'shoot/reward-shooter "1" b 50 {:players test-players}) :messages))
      "When a player strikes a wall, they will recieve hp in the amount of the damage applied to the wall.")
  (is (= {:players test-players}
         (#'shoot/reward-shooter "1" o 50 {:players test-players}))
      "When a player strikes an open space, they will recieve no additional hp"))

(deftest process-shot-spec
  (is (= {:game-state {:dirty-arena (au/update-cell
                                     test-arena
                                     [0 1]
                                     (merge b {:hp 10
                                               :md {:1234 {:type :shot
                                                           :decay 1}}}))
                       :players test-players}
          :hp 0
          :should-progress? true
          :shot-uuid "1234"
          :shooter-id "99999"}
         (update-in (#'shoot/process-shot
                     {:game-state {:dirty-arena test-arena
                                   :players test-players}
                      :hp 10
                      :should-progress? true
                      :shot-uuid "1234"
                      :shooter-id "99999"}
                     [0 1])
                    [:game-state] dissoc :messages))
      "If a shot passes through a cell what container more hp than is left in the shot. There should be no hp left over.")
  (is (= {:game-state {:dirty-arena (au/update-cell
                                     test-arena
                                     [0 1]
                                     (merge o {:md {:1234 {:type :destroyed
                                                           :decay 1}}}))
                       :players test-players}
          :hp 12
          :should-progress? true
          :shot-uuid "1234"
          :shooter-id "99999"}
         (update-in (#'shoot/process-shot
                     {:game-state {:dirty-arena test-arena
                                   :players test-players}
                      :hp 32
                      :should-progress? true
                      :shot-uuid "1234"
                      :shooter-id "99999"}
                     [0 1])
                    [:game-state] dissoc :messages))
      "If a shot contains more hp than a cell has, the delta hp should be returned in the shot state.")
  (is (= {:game-state {:dirty-arena test-arena
                       :players test-players}
          :hp 32
          :should-progress? false
          :shot-uuid "1234"
          :shooter-id "99999"}
         (update-in (#'shoot/process-shot
                     {:game-state {:dirty-arena test-arena
                                   :players test-players}
                      :hp 32
                      :should-progress? false
                      :shot-uuid "1234"
                      :shooter-id "99999"}
                     [0 1])
                    [:game-state] dissoc :messages))
      "If a shot should not progress, shot state is not updated"))
