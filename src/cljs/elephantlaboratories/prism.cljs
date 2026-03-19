(ns elephantlaboratories.prism
  (:require
   [reagent.core :as r]
   [ajax.core :refer [GET]]))

;; ── State ────────────────────────────────────────────────────────────────────

(defonce state
  (r/atom {:tracks        []
           :sorted        []
           :sort-order    :newest
           :current-index nil
           :loaded        false
           :grid-cols     3
           :view          :catalog}))

;; Audio element reference
(defonce audio-el (atom nil))

;; Map of track-name → catalog cover DOM element (for FLIP measurements)
(defonce cover-refs (atom {}))

;; Hero cover DOM element reference
(defonce hero-ref (atom nil))

;; FLIP animation state: transform string, transition flag, opacity.
;; Single r/atom so all three update in one render.
(defonce hero-anim (r/atom {:transform nil :transition false :opacity 1}))

;; Saved playback positions: track-name → seconds
(defonce track-times (atom {}))

;; keydown handler reference for cleanup on unmount
(defonce key-handler (atom nil))

;; ── Helpers ──────────────────────────────────────────────────────────────────

(defn sorted-tracks [tracks order]
  (case order
    :newest (vec (sort-by :date #(compare %2 %1) tracks))
    :oldest (vec (sort-by :date tracks))
    :random (vec (shuffle tracks))))

(defn current-track []
  (let [{:keys [sorted current-index]} @state]
    (when (and current-index (< current-index (count sorted)))
      (nth sorted current-index))))

;; ── FLIP helpers ─────────────────────────────────────────────────────────────

(defn rect-center [rect]
  [(+ (.-left rect) (* 0.5 (.-width rect)))
   (+ (.-top rect)  (* 0.5 (.-height rect)))])

(defn flip-transform
  "Returns a CSS transform string that makes an element at `to-rect`
   appear to be at `from-rect`."
  [from-rect to-rect]
  (let [[fx fy] (rect-center from-rect)
        [tx ty] (rect-center to-rect)
        dx    (- fx tx)
        dy    (- fy ty)
        scale (/ (.-width from-rect) (.-width to-rect))]
    (str "translate(" dx "px, " dy "px) scale(" scale ")")))

;; ── Audio ─────────────────────────────────────────────────────────────────────

(defn save-time! []
  (when-let [audio @audio-el]
    (when-let [track (current-track)]
      (let [t (.-currentTime audio)]
        (when (pos? t)
          (swap! track-times assoc (:name track) t))))))

(defn load-audio! [track]
  (when-let [audio @audio-el]
    (let [saved (get @track-times (:name track) 0)]
      (set! (.-src audio) (:url track))
      (.load audio)
      (if (pos? saved)
        (letfn [(on-meta []
                  (.removeEventListener audio "loadedmetadata" on-meta)
                  (set! (.-currentTime audio) saved)
                  (.play audio))]
          (.addEventListener audio "loadedmetadata" on-meta))
        (.play audio)))))

;; ── Grid scrolling ────────────────────────────────────────────────────────────

(defn scroll-grid-to-track!
  "Smooth-scroll the catalog grid so the given track's cell is centered
   in the viewport — visible through the dimmed hero overlay."
  [index]
  (r/after-render
   (fn []
     (let [track   (nth (:sorted @state) index)
           item-el (get @cover-refs (:name track))]
       (when item-el
         (.scrollIntoView item-el #js {:block "center" :behavior "smooth"}))))))

;; ── Animation ─────────────────────────────────────────────────────────────────

(defn enter-hero!
  "FLIP: snap hero cover to the grid cell's position, then animate forward."
  [index]
  (let [track   (nth (:sorted @state) index)
        grid-el (get @cover-refs (:name track))
        hero-el @hero-ref]
    (when (and grid-el hero-el)
      (let [from-rect (.getBoundingClientRect grid-el)
            to-rect   (.getBoundingClientRect hero-el)
            transform (flip-transform from-rect to-rect)]
        ;; Snap to grid position instantly (no transition, full opacity)
        (reset! hero-anim {:transform transform :transition false :opacity 1})
        ;; Next frame: release so CSS transition animates to final position
        (js/requestAnimationFrame
         (fn []
           (reset! hero-anim {:transform nil :transition true :opacity 1})))))))

(defn select-track!
  "Switch to hero view for the given sorted index, animate the cover in,
   and scroll the grid to center this track behind the hero."
  [index]
  (let [track (nth (:sorted @state) index)]
    (swap! state assoc :current-index index :view :hero)
    (r/after-render
     (fn []
       (enter-hero! index)
       (load-audio! track)))
    ;; Scroll grid concurrently so the track's cell appears centered behind the cover
    (scroll-grid-to-track! index)))

(defn retreat!
  "Animate the hero cover to its catalog grid position, then call then-fn.
   The transform is computed so the element *ends up* visually at the grid
   cell — the same direction as enter-hero! but applied as the target state
   rather than the initial snap."
  [then-fn]
  (let [track   (current-track)
        grid-el (when track (get @cover-refs (:name track)))
        hero-el @hero-ref]
    (if (and grid-el hero-el)
      (let [hero-rect (.getBoundingClientRect hero-el)
            grid-rect (.getBoundingClientRect grid-el)
            ;; flip-transform(grid, hero) makes the hero element *appear* at
            ;; the grid position — so transitioning TO this transform moves
            ;; the cover toward the grid (shrinking in the right direction).
            transform (flip-transform grid-rect hero-rect)]
        (reset! hero-anim {:transform transform :transition true :opacity 1})
        (js/setTimeout then-fn 480))
      (do (reset! hero-anim {:transform nil :transition false :opacity 1})
          (then-fn)))))

(defn advance-to-next!
  "Track ended: retreat cover to grid, then bring in the next track."
  []
  (when-let [audio @audio-el]
    (.pause audio))
  (let [{:keys [current-index sorted]} @state
        next-idx (inc current-index)]
    (retreat!
     (fn []
       (swap! state assoc :view :catalog :current-index nil)
       (reset! hero-anim {:transform nil :transition false :opacity 1})
       (when (< next-idx (count sorted))
         (js/setTimeout #(select-track! next-idx) 80))))))

(defn dismiss!
  "Save position, then FLIP the hero cover back to the catalog grid."
  []
  (when-let [audio @audio-el]
    (.pause audio))
  (save-time!)
  (retreat!
   (fn []
     (swap! state assoc :view :catalog)
     (reset! hero-anim {:transform nil :transition false :opacity 1}))))

(defn go-prev!
  "Save position, retreat current cover, then bring in the previous track."
  []
  (when-let [audio @audio-el] (.pause audio))
  (let [{:keys [current-index]} @state]
    (when (and current-index (pos? current-index))
      (save-time!)
      (let [prev-idx (dec current-index)]
        (retreat!
         (fn []
           (swap! state assoc :view :catalog :current-index nil)
           (reset! hero-anim {:transform nil :transition false :opacity 1})
           (js/setTimeout #(select-track! prev-idx) 80)))))))

(defn go-next!
  "Save position, retreat current cover, then bring in the next track."
  []
  (when-let [audio @audio-el] (.pause audio))
  (let [{:keys [current-index sorted]} @state]
    (when current-index
      (let [next-idx (inc current-index)]
        (when (< next-idx (count sorted))
          (save-time!)
          (retreat!
           (fn []
             (swap! state assoc :view :catalog :current-index nil)
             (reset! hero-anim {:transform nil :transition false :opacity 1})
             (js/setTimeout #(select-track! next-idx) 80))))))))

;; ── Sort ──────────────────────────────────────────────────────────────────────

(defn set-sort! [order]
  (let [sorted (sorted-tracks (:tracks @state) order)]
    (swap! state assoc :sort-order order :sorted sorted :current-index nil)
    (when (= :hero (:view @state))
      (dismiss!))))

;; ── Shared components ─────────────────────────────────────────────────────────

(defn sort-button [label order]
  [:button
   {:class    (str "sort-btn" (when (= (:sort-order @state) order) " active"))
    :on-click #(set-sort! order)}
   label])

(defn sort-controls []
  [:div {:class "sort-controls"}
   [sort-button "now" :newest]
   [sort-button "random" :random]
   [sort-button "begin" :oldest]])

;; ── Hero overlay ──────────────────────────────────────────────────────────────

(defn hero-overlay []
  (let [track     (current-track)
        {:keys [current-index sorted]} @state
        has-prev  (and current-index (pos? current-index))
        has-next  (and current-index (< (inc current-index) (count sorted)))
        {:keys [transform transition opacity]} @hero-anim
        cover-style (cond-> {:transform (or transform "none")
                             :opacity   (or opacity 1)}
                      transition (assoc :transition
                                        "transform 0.48s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.45s ease"))]
    [:div {:class "hero-overlay"}

     ;; ── Balance spacer (invisible, mirrors sidebar width so cover stays centered) ──
     [:div {:class "hero-balance"}]

     ;; ── Left nav ──
     [:div {:class    (str "hero-nav hero-nav--left"
                           (when-not has-prev " hero-nav--disabled"))
            :on-click #(when has-prev (go-prev!))}
      [:span {:class "hero-nav-arrow"} "‹"]]

     ;; ── Cover (FLIP target — clicking retreats it to the grid) ──
     [:div {:class "hero-cover-area"}
      [:div {:class    "hero-main-cover"
             :ref      (fn [el] (reset! hero-ref el))
             :style    cover-style
             :on-click #(dismiss!)}
       [:img {:class "hero-main-img"
              :src   (:cover track)
              :alt   (:display-name track)}]]]

     ;; ── Right nav ──
     [:div {:class    (str "hero-nav hero-nav--right"
                           (when-not has-next " hero-nav--disabled"))
            :on-click #(when has-next (go-next!))}
      [:span {:class "hero-nav-arrow"} "›"]]

     ;; ── Sidebar: title + sort at top, info centered, audio at bottom ──
     [:div {:class "hero-sidebar"}
      [:div {:class "hero-sidebar-top"}
       [:span {:class "hero-site-title" :on-click #(dismiss!)} "prismofeverything"]
       [sort-controls]]
      [:div {:class "hero-info"}
       [:h1 {:class "hero-title"} (:display-name track)]
       [:p  {:class "hero-date"}  (:date track)]
       [:p  {:class "hero-description"} (:description track)]]
      [:div {:class "hero-audio"}
       [:audio {:id       "prism-audio"
                :controls true
                :on-ended advance-to-next!
                :ref      (fn [el] (when el (reset! audio-el el)))}]]]]))

;; ── Catalog ───────────────────────────────────────────────────────────────────

(defn col-slider []
  (let [draft (r/atom (:grid-cols @state))]
    (fn []
      [:input {:class        "col-slider"
               :type         "range"
               :min          1
               :max          13
               :value        @draft
               :on-change    #(reset! draft (js/parseInt (.. % -target -value)))
               :on-mouse-up  #(swap! state assoc :grid-cols @draft)
               :on-touch-end #(swap! state assoc :grid-cols @draft)}])))

(defn catalog-item [track index]
  [:div {:class "catalog-item"
         :ref   (fn [el]
                  (if el
                    (swap! cover-refs assoc (:name track) el)
                    (swap! cover-refs dissoc (:name track))))
         :on-click #(select-track! index)}
   [:div {:class "catalog-cover-wrap"}
    [:img {:class "catalog-cover" :src (:cover track) :alt (:display-name track)}]]
   [:p {:class "catalog-title"} (:display-name track)]])

(defn catalog-view []
  (let [{:keys [sorted loaded grid-cols view]} @state]
    [:div {:class (str "catalog-page" (when (= view :hero) " catalog-page--dim"))}
     [:div {:class "catalog-header"}
      [col-slider]
      [:h1 {:class "catalog-site-title"} "prismofeverything"]
      [sort-controls]]
     (if loaded
       [:div {:class     "catalog-grid"
              :style     {:grid-template-columns (str "repeat(" grid-cols ", 1fr)")}
              :tab-index "0"
              :ref       (fn [el] (when el (.focus el)))}
        (map-indexed
         (fn [i track]
           ^{:key (:name track)}
           [catalog-item track i])
         sorted)]
       [:p {:class "prism-loading"} "Loading…"])]))

;; ── Data loading ──────────────────────────────────────────────────────────────

(defn load-tracks! [& _]
  (GET "/api/tracks"
       {:response-format (ajax.core/json-response-format {:keywords? true})
        :handler
        (fn [data]
          (let [tracks (mapv (fn [t]
                               {:name         (:name t)
                                :display-name (:display-name t)
                                :date         (:date t)
                                :description  (:description t)
                                :url          (:url t)
                                :cover        (:cover t)})
                             (:tracks data))
                order  (:sort-order @state)
                sorted (sorted-tracks tracks order)]
            (swap! state assoc
                   :tracks  tracks
                   :sorted  sorted
                   :loaded  true)))
        :error-handler
        (fn [_] (swap! state assoc :loaded true))}))

;; ── Root ──────────────────────────────────────────────────────────────────────

(defn prism-home-page []
  (r/create-class
   {:component-did-mount
    (fn [_]
      (load-tracks!)
      (let [handler (fn [e]
                      (when (= :hero (:view @state))
                        (case (.-key e)
                          "ArrowLeft"  (do (.preventDefault e) (go-prev!))
                          "ArrowRight" (do (.preventDefault e) (go-next!))
                          nil)))]
        (reset! key-handler handler)
        (.addEventListener js/document "keydown" handler)))
    :component-will-unmount
    (fn [_]
      (when-let [handler @key-handler]
        (.removeEventListener js/document "keydown" handler)))
    :reagent-render
    (fn []
      [:div {:class "prism-root"}
       [catalog-view]
       (when (= :hero (:view @state))
         [hero-overlay])])}))
