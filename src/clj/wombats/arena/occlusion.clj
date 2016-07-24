(ns wombats.arena.occlusion
  (:require [wombats.constants.arena :refer [arena-key]]))

(defn- opaque-md?
  [cell]
  (let [md (get cell :md)]
    (some (fn [kv]
            (let [v (get kv 1)]
              (= :smokescreen (get v :type)))) md)))

(def ^:private slopes [[1 0] [-1 0] [0 1] [0 -1]])

(defn- normalize-slope
  [[x y]]
  (if (zero? y)
    [(/ x (Math/abs x)) y]
    (let [length (Math/sqrt (+ (* x x) (* y y)))]
      [(/ x length) (/ y length)])))

(defn- corners
  [[x y]]
  (let [[nx ny] (normalize-slope [x y])
        negx (- nx)
        negy (- ny)]
    [[nx ny] [negx ny] [nx negy] [negx negy]]))

(defn- angles
  [view-dist]
  (concat slopes
          (mapcat #(corners [% view-dist]) (range 1 (inc view-dist)))
          (mapcat #(corners [view-dist %]) (range 1 view-dist))))

(defn- blocked-pos?
  [arena [x y]]
  (let [cell (get-in arena [x y])]
    (or
     (opaque-md? cell)
     (not (get cell :transparent)))))

(defn- fog-of-war
  [arena pos]
  (if (get-in arena pos)
    (assoc-in arena pos (:fog arena-key))
    arena))

(defn- finalize-pos
  [arena pos]
  [pos (blocked-pos? arena pos)])

(defn- new-pos
  [ray-length rxy xy]
  (int (Math/round (+ rxy (* xy (double ray-length))))))

(defn- parse-pos
  [arena view-dist ray-length [rx ry] [x y :as ang]]
  (if (zero? x)
    (finalize-pos arena [(int rx)
                         (int (+ ry (* ray-length x)))])
    (finalize-pos arena [(new-pos ray-length rx x)
                         (new-pos ray-length ry y)])))

(defn- blocked-pos
  [arena view-dist player-pos]
  ;; TODO There is a bug where player-pos is sometimes not passed to
  ;; blocked-pos. Find that edge case and remove the if statement.
  (if (not player-pos)
    #{}
    (let [angs (angles view-dist)]
      (:blocked-pos
       (reduce
        (fn [bmap ray-length]
          (reduce
           (fn [blocked-map ang]
             (let [ang-blocked? (contains? (:blocked-angs blocked-map) ang)
                   [pos pos-blocked?] (parse-pos arena view-dist ray-length
                                                 player-pos ang)
                   blocker? (contains? (:blockers blocked-map) pos)]
               (-> blocked-map
                   (update-in [:blockers]
                              #(if (and pos-blocked?
                                        (not ang-blocked?))
                                 (conj % pos) %))
                   (update-in [:blocked-angs]
                              #(if pos-blocked?
                                 (conj % ang) %))
                   (update-in [:blocked-pos]
                              #(if (and ang-blocked?
                                        (not blocker?))
                                 (conj % pos) %)))))
           bmap angs))
        {:blocked-angs #{}
         :blocked-pos #{}
         :blockers #{}}
        (range 1 (inc view-dist)))))))

(defn occluded-arena
  "Only pass the limited arena that the user can see,
   this fn does not alter the size of the arena passed."
  [arena player-pos]
  (let [blocked-pos (blocked-pos arena (count arena) player-pos)]
    (reduce fog-of-war arena blocked-pos)))

(comment
  ;; eval (C-x C-e) each line to see the occluded-arena work
  (require '[clojure.edn :as edn])
  (require '[clojure.java.io :as io])
  (require '[wombats.arena.utils :refer [pprint-arena]])
  (def t-arena (edn/read-string (slurp "test-resources/test-arena.edn")))
  (def o-arena (occluded-arena t-arena [4 4]))
  (pprint-arena t-arena)
  (pprint-arena o-arena))
