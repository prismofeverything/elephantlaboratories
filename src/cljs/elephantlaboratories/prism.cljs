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
           :exiting-track nil   ; track currently retreating to its grid cell
           :loaded        false
           :grid-cols     3
           :view          :catalog}))

(defonce audio-el    (atom nil))
(defonce cover-refs  (atom {}))   ; track-name → catalog item DOM el
(defonce hero-ref    (atom nil))  ; main (entering) cover DOM el

;; Animation state for the entering cover
(defonce hero-anim
  (r/atom {:transform nil :transition false :opacity 1}))

;; Animation state for the exiting cover (simultaneous retreat)
(defonce exiting-anim
  (r/atom {:transform nil :transition false :opacity 1}))

(defonce track-times  (atom {}))   ; track-name → saved playback seconds
(defonce saved-volume (atom 1.0))  ; persists across hero mount/unmount
(defonce key-handler  (atom nil))  ; keydown listener ref for cleanup

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

(defn flip-transform [from-rect to-rect]
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

;; ── URL / deep-link ──────────────────────────────────────────────────────────

(defn set-track-url! [track]
  (.replaceState js/history nil "" (str "/#" (js/encodeURIComponent (:name track)))))

(defn clear-track-url! []
  (.replaceState js/history nil "" "/"))

(defn url-track-name []
  (let [hash (.-hash js/location)]
    (when (seq hash)
      (js/decodeURIComponent (subs hash 1)))))

;; ── Grid scrolling ────────────────────────────────────────────────────────────

(defn scroll-grid-to-track! [index]
  (r/after-render
   (fn []
     (when (< index (count (:sorted @state)))
       (let [track   (nth (:sorted @state) index)
             item-el (get @cover-refs (:name track))]
         (when item-el
           (.scrollIntoView item-el #js {:block "center" :behavior "smooth"})))))))

;; ── Animation ─────────────────────────────────────────────────────────────────

(defn enter-hero!
  "FLIP: snap the entering cover to the grid cell position, then animate forward."
  [index]
  (let [track   (nth (:sorted @state) index)
        grid-el (get @cover-refs (:name track))
        hero-el @hero-ref]
    (when (and grid-el hero-el)
      (let [from-rect (.getBoundingClientRect grid-el)
            to-rect   (.getBoundingClientRect hero-el)
            transform (flip-transform from-rect to-rect)]
        (reset! hero-anim {:transform transform :transition false :opacity 0})
        (js/requestAnimationFrame
         (fn []
           (reset! hero-anim {:transform nil :transition true :opacity 1})))))))

(defn navigate-to!
  "Switch to next-idx, animating out the current cover and in the next
   cover simultaneously rather than sequentially."
  [next-idx]
  (when-let [audio @audio-el] (.pause audio))
  (let [ex-track   (current-track)
        ex-grid-el (when ex-track (get @cover-refs (:name ex-track)))
        hero-el    @hero-ref
        next-track (nth (:sorted @state) next-idx)]
    (save-time!)
    (if (and ex-track ex-grid-el hero-el)
      (let [hero-rect      (.getBoundingClientRect hero-el)
            ex-grid-rect   (.getBoundingClientRect ex-grid-el)
            ;; Transform that moves the exiting cover from hero → its grid cell
            exit-transform (flip-transform ex-grid-rect hero-rect)]
        ;; Hide the entering cover before the swap so it doesn't flash at hero
        ;; position for one paint before enter-hero! snaps it to grid position.
        ;; Reagent batches this reset with the swap below into a single render.
        (reset! hero-anim {:transform nil :transition false :opacity 0})
        ;; Update state: remember exiting track, switch to new track
        (swap! state assoc
               :exiting-track ex-track
               :current-index next-idx
               :view          :hero)
        ;; After DOM re-renders with both covers present:
        (set-track-url! next-track)
        (r/after-render
         (fn []
           ;; Start exiting cover retreat (from hero position toward grid)
           (reset! exiting-anim {:transform nil :transition false :opacity 1})
           (js/requestAnimationFrame
            (fn []
              (reset! exiting-anim {:transform exit-transform :transition true :opacity 0})))
           ;; Start entering cover FLIP simultaneously
           (enter-hero! next-idx)
           (load-audio! next-track)))
        ;; Clear exiting track after animation completes
        (js/setTimeout
         (fn []
           (swap! state assoc :exiting-track nil)
           (reset! exiting-anim {:transform nil :transition false :opacity 1}))
         540))
      ;; No exit animation possible — just enter the new track
      (do (swap! state assoc :current-index next-idx :view :hero)
          (r/after-render
           (fn []
             (enter-hero! next-idx)
             (load-audio! next-track)))))
    (scroll-grid-to-track! next-idx)))

(defn select-track!
  "Enter hero view from catalog (no simultaneous exit — nothing is playing yet)."
  [index]
  (let [track (nth (:sorted @state) index)]
    (set-track-url! track)
    (swap! state assoc :current-index index :view :hero)
    (r/after-render
     (fn []
       (enter-hero! index)
       (load-audio! track)))
    (scroll-grid-to-track! index)))

(defn retreat!
  "Animate the current hero cover back to its grid cell, then call then-fn."
  [then-fn]
  (let [track   (current-track)
        grid-el (when track (get @cover-refs (:name track)))
        hero-el @hero-ref]
    (if (and grid-el hero-el)
      (let [hero-rect (.getBoundingClientRect hero-el)
            grid-rect (.getBoundingClientRect grid-el)
            transform (flip-transform grid-rect hero-rect)]
        (reset! hero-anim {:transform transform :transition true :opacity 0})
        (js/setTimeout then-fn 480))
      (do (reset! hero-anim {:transform nil :transition false :opacity 1})
          (then-fn)))))

(defn dismiss!
  "Save position, retreat cover to grid, return to catalog view."
  []
  (when-let [audio @audio-el] (.pause audio))
  (save-time!)
  (clear-track-url!)
  (retreat!
   (fn []
     (swap! state assoc :view :catalog :exiting-track nil)
     (reset! hero-anim {:transform nil :transition false :opacity 1}))))

(defn go-prev! []
  (let [{:keys [current-index]} @state]
    (when (and current-index (pos? current-index))
      (navigate-to! (dec current-index)))))

(defn go-next! []
  (let [{:keys [current-index sorted]} @state]
    (when current-index
      (let [next-idx (inc current-index)]
        (when (< next-idx (count sorted))
          (navigate-to! next-idx))))))

(defn advance-to-next!
  "Called when a track ends naturally."
  []
  (let [{:keys [current-index sorted]} @state
        next-idx (inc current-index)]
    (if (< next-idx (count sorted))
      (navigate-to! next-idx)
      ;; Last track: just retreat to catalog
      (do (when-let [audio @audio-el] (.pause audio))
          (dismiss!)))))

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

(defn cover-anim-style [anim-atom]
  (let [{:keys [transform transition opacity]} @anim-atom]
    (cond-> {:transform (or transform "none")
             :opacity   (or opacity 1)}
      transition (assoc :transition
                        "transform 0.48s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.45s ease"))))

(defn hero-overlay []
  (let [track    (current-track)
        ex-track (:exiting-track @state)
        {:keys [current-index sorted]} @state
        has-prev (and current-index (pos? current-index))
        has-next (and current-index (< (inc current-index) (count sorted)))]
    [:div {:class "hero-overlay"}

     [:div {:class "hero-balance"}]

     ;; ── Cover row: nav arrows + cover grouped so mobile can flex them as a row ──
     [:div {:class "hero-cover-row"}

      ;; Left nav
      [:div {:class    (str "hero-nav hero-nav--left"
                            (when-not has-prev " hero-nav--disabled"))
             :on-click #(when has-prev (go-prev!))}
       [:span {:class "hero-nav-arrow"} "‹"]]

      ;; Cover area: exiting cover retreats while entering cover arrives
      [:div {:class "hero-cover-area"}

       ;; Exiting cover — retreats to its grid cell simultaneously with the enter
       (when ex-track
         [:div {:class "hero-exiting-cover"
                :style (cover-anim-style exiting-anim)}
          [:img {:class "hero-main-img"
                 :src   (:cover ex-track)
                 :alt   (:display-name ex-track)}]])

       ;; Entering cover — FLIP from grid cell to hero position; click to dismiss
       [:div {:class    "hero-main-cover"
              :ref      (fn [el] (reset! hero-ref el))
              :style    (cover-anim-style hero-anim)
              :on-click #(dismiss!)}
        [:img {:class "hero-main-img"
               :src   (:cover track)
               :alt   (:display-name track)}]]]

      ;; Right nav
      [:div {:class    (str "hero-nav hero-nav--right"
                            (when-not has-next " hero-nav--disabled"))
             :on-click #(when has-next (go-next!))}
       [:span {:class "hero-nav-arrow"} "›"]]]

     ;; ── Sidebar ──
     [:div {:class "hero-sidebar"}
      [:div {:class "hero-info"}
       [:h1 {:class "hero-title"} (:display-name track)]
       [:p  {:class "hero-date"}  (:date track)]
       [:p  {:class "hero-description"} (:description track)]]
      [:div {:class "hero-audio"}
       [:audio {:id       "prism-audio"
                :controls true
                :on-ended advance-to-next!
                :ref      (fn [el]
                           (when el
                             (reset! audio-el el)
                             (r/after-render #(set! (.-volume el) @saved-volume))))}]]]]))

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
      [:div {:class "catalog-header-left"}
       [:h1 {:class "catalog-site-title"} "prismofeverything"]
       [col-slider]]
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
            (swap! state assoc :tracks tracks :sorted sorted :loaded true)
            ;; Open a track from the URL hash if present
            (when-let [track-name (url-track-name)]
              (let [idx (.indexOf (mapv :name sorted) track-name)]
                (when (>= idx 0)
                  (r/after-render #(select-track! idx)))))))
        :error-handler
        (fn [_] (swap! state assoc :loaded true))}))

;; ── Root ──────────────────────────────────────────────────────────────────────

(defn prism-home-page []
  (r/create-class
   {:component-did-mount
    (fn [_]
      (load-tracks!)
      (let [handler (fn [e]
                      (let [view (:view @state)]
                        (case (.-key e)
                          ("Enter" "Escape")
                          (cond
                            (= view :catalog)
                            (do (.preventDefault e)
                                (select-track! (or (:current-index @state) 0)))
                            (= view :hero)
                            (do (.preventDefault e) (dismiss!)))

                          " "
                          (when (= view :hero)
                            (.preventDefault e)
                            (when-let [audio @audio-el]
                              (if (.-paused audio) (.play audio) (.pause audio))))

                          "ArrowLeft"
                          (when (= view :hero)
                            (.preventDefault e) (go-prev!))

                          "ArrowRight"
                          (when (= view :hero)
                            (.preventDefault e) (go-next!))

                          "ArrowUp"
                          (when (= view :hero)
                            (.preventDefault e)
                            (when-let [audio @audio-el]
                              (let [v (min 1.0 (+ (.-volume audio) 0.1))]
                                (set! (.-volume audio) v)
                                (reset! saved-volume v))))

                          "ArrowDown"
                          (when (= view :hero)
                            (.preventDefault e)
                            (when-let [audio @audio-el]
                              (let [v (max 0.0 (- (.-volume audio) 0.1))]
                                (set! (.-volume audio) v)
                                (reset! saved-volume v))))

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
