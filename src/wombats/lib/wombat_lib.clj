(def turn-directions [:right :left :about-face])
(def smoke-directions [:forward :backward :left :right :drop])
(def game-parameters
{;; HP Modifiers
 :collision-hp-damage 10
 :food-hp-bonus 5
 :poison-hp-damage 10
 ;; Score Modifiers
 :food-score-bonus 10
 :wombat-hit-bonus 10
 :zakano-hit-bonus 8
 :steel-barrier-hit-bonus 2
 :wood-barrier-hit-bonus 2
 :wombat-destroyed-bonus 25
 :zakano-destroyed-bonus 15
 :wood-barrier-destroyed-bonus 3
 :steel-barrier-destroyed-bonus 25
 ;; In game parameters
 :shot-distance 5})

(def shot-range (:shot-distance game-parameters))
(def zakano-key "zakano")
(def wombat-key "wombat")
(def steel-key "steel-barrier")
(def wood-key "wood-barrier")
(def food-key "food")
(def poison-key "poison")
(def fog-key "fog")
(def open-key "open")

(def point-sources [zakano-key wombat-key steel-key wood-key food-key])
(def points-no-wall [zakano-key wombat-key food-key])
(def enemies [wombat-key zakano-key])
(def blockers [zakanko-key wombat-key wood-key steel-key poison-key])
(def targets [zakano-key wombat-key steel-key wood-key])

(def default-tile {:contents {:type fog-key}})

(defn get-arena-size
  "Fetches the size of one side of the arena from the state"
  [state]
  (first (:global-dimensions state)))

(defn in?
  "Return true if coll contains elem"
  [elem coll]
  (some #(= elem %) coll))

(defn add-locs
  "Add local :x and :y coordinates to arena matrix"
  [arena]
  (map-indexed 
    (fn [y row] (map-indexed
                  (fn [x tile] (assoc tile :x x :y y))
                  row)) 
    arena))

(defn filter-arena
  "Filter the arena to return only nodes that contain one of the supplied types"
  ([arena] (flatten arena))
  ([arena filters]
  (let [node-list (flatten arena)]
    (filter #(in? (get-in % [:contents :type]) filters) node-list))))

(defn build-initial-global-state
  "Constructs an initial global state populated by fog"
  [global-size]
  (add-locs (into [] (map (fn [_] 
     (into [] (map (fn [_] default-tile) (range global-size)))) (range global-size)))))

(defn add-to-state
  "Update the global saved state with the given element and position"
  [matrix {:keys [x y] as elem}]
  (assoc matrix y (assoc (nth matrix y) x elem)))

(defn merge-global-state
  "Add local state vision to global saved state. Position is that of the player which corresponds to (3,3) in local matrix"
  [global-state local-state arena-size]
    (let [local-nodes (filter-arena ((comp add-locs :arena) local-state)
                                    "food" "poison" "open" "wood-barrier" "steel-barrier")
          x-offset (mod (- (first (:global-coords local-state)) 3) arena-size)
          y-offset (mod (- (second (:global-coords local-state)) 3) arena-size)
          self      {:contents {:type "open"} 
                     :x (first (:global-coords local-state)) 
                     :y (second (:global-coords local-state))}]
      (add-to-state (reduce #(
                let [x (mod (+ (:x %2) x-offset) arena-size)
                     y (mod (+ (:y %2) y-offset) arena-size)
                     elem (merge %2 {:x x :y y})]
                (add-to-state %1 elem)) global-state local-nodes) self)))

(defn get-global-state
  "Tries to fetch global arena from the saved state or constructs a new one"
  [state & path]
  (let [saved (get-in state path)
        size  (first (:global-dimensions state))]
    (if (nil? saved)
      (build-initial-global-state size)
      saved )))

(defn get-direction
  "Get the current direction of your wombat from the 2d arena array"
  [arena]
  (get-in (nth (nth arena 3) 3) [:contents :orientation]))

(defn facing?
  "Returns true if a move forward will bring you closer to desired location
  If no self coordinates are provided, use distance from {:x 3 :y 3}"
  ([dir {x_tar :x y_tar :y} arena-half {x_self :x y_self :y}]
    (case dir
          "n" (and (not= y_tar y_self) (>= arena-half (mod (- y_self y_tar) (* arena-half 2)))) 
          "e" (and (not= x_tar x_self) (>= arena-half (mod (- x_tar x_self) (* arena-half 2))))
          "s" (and (not= y_tar y_self) (>= arena-half (mod (- y_tar y_self) (* arena-half 2))))
          "w" (and (not= x_tar x_self) (>= arena-half (mod (- x_self x_tar)  (* arena-half 2))))
          false))
  ([dir node arena-half] (facing? dir node arena-half {:x 3 :y 3})))

(defn distance-to-tile
  "Get the number of moves it would take to move from current location.
  If no self coordinates are provided, use distance from {:x 3 :y 3}"
  ([dir {x_tar :x y_tar :y} arena-size {x_self :x y_self :y}]
   (let [x_dist (min (Math/abs (- x_tar x_self))
           (- (+ arena-size (min x_tar x_self)) (max x_tar x_self)))
         y_dist (min (Math/abs (- y_tar y_self))
           (- (+ arena-size (min y_tar y_self)) (max y_tar y_self)))]
     (+ x_dist
        y_dist
        (if (facing? dir node (/ arena-size 2) self-node) 0 1)
        (if (= (min x_dist y_dist) 0) 0 1))))
  ([dir node arena-size]
    (distance-to-tile dir node arena-size {:x 3 :y 3})))

(defn turn-to-dir
  "Returns one of [:right :left :about-face]"
  [curr-dir next-dir]
  (def ^:private orientations ["n" "e" "s" "w"])
  (let [curr-idx (.indexOf orientations curr-dir)
        next-idx (.indexOf orientations next-dir)]
    (case (mod (- curr-idx next-idx) 4)
      0  nil
      1  :left
      2  :about-face
      3  :right)))

(defn can-shoot
  "Returns true if there is a Zakano or Wombat within shooting range"
  [dir arena arena-size & {:keys [wombat wall]
                           :or {wombat {:x 3 :y 3}
                                wall true}}]
    (def shootable (case dir
      "n" #(and (= (:x self) (:x %)) (>= shot-range (mod (- (:y self) (:y %)) arena-size)))
      "e" #(and (= (:y self) (:y %)) (>= shot-range (mod (- (:x %) (:x self)) arena-size)))
      "s" #(and (= (:x self) (:x %)) (>= shot-range (mod (- (:y %) (:y self)) arena-size)))
      "w" #(and (= (:y self) (:y %)) (>= shot-range (mod (- (:x self) (:x %)) arena-size)))
      #(false)))
    (let [filters (if wall targets enemies)
          arena (add_locs arena)
          shootable (filter shootable (filter-arena arena filters))]
      (not (empty? (filter #(not (and (= (:x %) (:x self)) (= (:y self) (:y %)))) shootable)))))

(defn possible-points
  "Get all locations with possible points"
  [arena & {:keys [wombat wall]
            :or {wombat {:x 3 :y 3}
                 wall true}}]
   (let [filters (if wall point-sources points-no-wall)]
     (remove #(and (= (:x %) (:x self)) (= (:y %) (:y self)))
            (filter-arena (add-locs arena) filters))))

(defn build-resp
  "Helper method to construct the return command"
  ; Args can be passed as either keywords or strings
  ([action] {:action (keyword action)
             :metadata {}}))
  ([action direction]
    {:action (keyword action)
     :metadata {:direction (keyword direction)}})

(defn new-direction
  "Pick new direction to turn to get to loc. If no direction is possible, turns left"
  [dir loc self arena-half]
  (def ^:private orientations ["n" "e" "s" "w"])
  (let [available (remove #(= % dir) orientations)
        positions (filter #(facing? % loc arena-half self) available)]
    (if (not (empty? positions))
        (turn-to-dir dir (first positions))
        ; TODO: implement logic here
        :left)))

(defn front-tile
  "Returns a map containing {:x x, :y y}, where x and y are the coordinates directly in front"
  ([dir arena-size self]
    (case dir
      "n" {:x (:x self) :y (mod (dec (:y self)) arena-size)}
      "e" {:x (mod (inc (:x self)) arena-size) :y (:y self)}
      "s" {:x (:x self) :y (mod (inc (:y self)) arena-size)}
      "w" {:x (mod (dec (:x self)) arena-size) :y (:y self)}))
  ([dir arena-size] front-tile dir arena-size {:x 3 :y 3}))

(defn is-clear?
  "Return true if wombat can safely occupy given tile"
  [arena {x :x y :y}]
  (not (in? (get-in (nth (nth arena y) x) [:contents :type]) blockers)))

(defn move-to
  "Take the best action to get to given space"
  ([arena arena-half dir loc self]
    (if (and (facing? dir loc arena-half self) (is-clear? arena (front-tile dir (* arena-half 2) self)))
        (build-resp :move)
        (build-resp :turn (new-direction dir loc self arena-half))))
  ([arena arena-half dir loc]
    (move-to dir arena arena-half loc {:x 3 :y 3})))

(defn focus-sight
  "Cut the arena down to 5x5 from 7x7"
  [arena]
  (take 5 (rest (map #(take 5 (rest %)) arena))))

(defn select-target
  "Pulls the coordinates of the closest point source to the player"
  [arena arena-size & {:keys [wombat wall]
                        :or {wombat {:x 3 :y 3}
                             wall true}}]
    (let [possible (possible-points arena :wombat wombat :wall wall)
          direction (get-direction arena)]
      (first (sort-by :dist (map #(assoc % :dist (distance-to-tile direction % arena-size wombat)) possible)))))
