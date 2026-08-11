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
           :grid-cols     5
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
(defonce saved-volume (atom 1.0))  ; persists across hero mount/unmount (audible level)

;; Reactive playback state that drives the custom transport (see player-controls).
(defonce player (r/atom {:playing false :current 0 :duration 0 :volume 1.0}))
(defonce key-handler  (atom nil))  ; keydown listener ref for cleanup
(defonce hash-handler (atom nil))  ; hashchange listener ref for cleanup

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

(defn load-audio! [track play?]
  (when-let [audio @audio-el]
    (let [saved (get @track-times (:name track) 0)]
      (set! (.-src audio) (:url track))
      (.load audio)
      (if (pos? saved)
        (letfn [(on-meta []
                  (.removeEventListener audio "loadedmetadata" on-meta)
                  (set! (.-currentTime audio) saved)
                  (when play? (.play audio)))]
          (.addEventListener audio "loadedmetadata" on-meta))
        (when play? (.play audio))))))

(defn attach-audio-listeners!
  "Mirror the <audio> element's state into the reactive `player` atom so the
   custom controls stay in sync. Re-attached on each hero mount; the previous
   element is discarded with its listeners, so there's nothing to detach."
  [el]
  (letfn [(dur [] (let [d (.-duration el)] (if (js/isFinite d) d 0)))]
    (.addEventListener el "timeupdate"     #(swap! player assoc :current (.-currentTime el)))
    (.addEventListener el "durationchange" #(swap! player assoc :duration (dur)))
    (.addEventListener el "loadedmetadata" #(swap! player assoc :duration (dur)))
    (.addEventListener el "play"           #(swap! player assoc :playing true))
    (.addEventListener el "pause"          #(swap! player assoc :playing false))
    (.addEventListener el "volumechange"
                       #(let [v (.-volume el)]
                          (swap! player assoc :volume v)
                          ;; Only remember audible levels, so muting (→0) doesn't
                          ;; wipe the level we restore on unmute / next track.
                          (when (pos? v) (reset! saved-volume v))))))

(defn fmt-time [secs]
  (if (or (nil? secs) (not (js/isFinite secs)))
    "0:00"
    (let [s (js/Math.floor secs)
          m (quot s 60)
          r (mod s 60)]
      (str m ":" (when (< r 10) "0") r))))

(defn player-controls
  "Custom, responsive transport that replaces the native <audio controls> (which
   collapses the scrubber and hides the volume slider on narrow screens). A
   full-width scrubber row that never collapses, plus a play/pause + volume row."
  []
  (let [{:keys [playing current duration volume]} @player
        seek-max (if (and duration (pos? duration)) duration 100)]
    [:div {:class "player"}
     ;; ── Scrubber row: [current]  [====== seek ======]  [duration] ──
     [:div {:class "player-scrub"}
      [:span {:class "player-time"} (fmt-time current)]
      [:input {:class      "player-range player-seek"
               :type       "range"
               :min        0
               :max        seek-max
               :step       "0.1"
               :value      (min current seek-max)
               :aria-label "Seek"
               :on-change  (fn [e]
                             (let [t (js/parseFloat (.. e -target -value))]
                               (when-let [a @audio-el] (set! (.-currentTime a) t))
                               (swap! player assoc :current t)))}]
      [:span {:class "player-time player-time--end"} (fmt-time duration)]]
     ;; ── Buttons row: [play] .............. [mute] [volume] ──
     [:div {:class "player-buttons"}
      [:button {:class      "player-btn player-play"
                :type       "button"
                :aria-label (if playing "Pause" "Play")
                :on-click   (fn [_] (when-let [a @audio-el]
                                      (if (.-paused a) (.play a) (.pause a))))}
       (if playing "⏸" "▶")]
      [:div {:class "player-vol"}
       [:button {:class      "player-btn player-mute"
                 :type       "button"
                 :aria-label (if (zero? volume) "Unmute" "Mute")
                 :on-click   (fn [_]
                               (when-let [a @audio-el]
                                 (if (pos? (.-volume a))
                                   (set! (.-volume a) 0)
                                   (set! (.-volume a) (if (pos? @saved-volume) @saved-volume 1.0)))))}
        (cond (zero? volume) "🔇" (< volume 0.5) "🔉" :else "🔊")]
       [:input {:class      "player-range player-volume"
                :type       "range"
                :min        0 :max 1 :step "0.01"
                :value      volume
                :aria-label "Volume"
                :on-change  (fn [e]
                              (let [v (js/parseFloat (.. e -target -value))]
                                (when-let [a @audio-el] (set! (.-volume a) v))))}]]]]))

;; ── Favicon ──────────────────────────────────────────────────────────────────

(defn set-favicon! [url]
  (let [img    (js/Image.)
        canvas (.createElement js/document "canvas")
        size   64]
    (set! (.-width canvas) size)
    (set! (.-height canvas) size)
    (set! (.-onload img)
          (fn []
            (let [ctx (.getContext canvas "2d")]
              (.drawImage ctx img 0 0 size size)
              (let [data-url (.toDataURL canvas "image/png")
                    link     (or (.querySelector js/document "link[rel~='icon']")
                                 (let [el (.createElement js/document "link")]
                                   (set! (.-rel el) "icon")
                                   (.appendChild (.-head js/document) el)
                                   el))]
                (set! (.-href link) data-url)))))
    (set! (.-src img) url)))

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
   cover simultaneously rather than sequentially.
   play? overrides whether to play the new track; nil preserves current state."
  ([next-idx] (navigate-to! next-idx nil))
  ([next-idx play?]
  (let [audio      @audio-el
        was-playing (if (nil? play?)
                      (and audio (not (.-paused audio)))
                      play?)]
    (when audio (.pause audio))
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
        (set-track-url! next-track)
        (set-favicon! (:cover next-track))
        ;; Load audio synchronously — must not depend on rAF/render cycle, which
        ;; browsers pause in hidden tabs (would block auto-advance on track end).
        (load-audio! next-track was-playing)
        (r/after-render
         (fn []
           ;; Start exiting cover retreat (from hero position toward grid)
           (reset! exiting-anim {:transform nil :transition false :opacity 1})
           (js/requestAnimationFrame
            (fn []
              (reset! exiting-anim {:transform exit-transform :transition true :opacity 0})))
           ;; Start entering cover FLIP simultaneously
           (enter-hero! next-idx)))
        ;; Clear exiting track after animation completes
        (js/setTimeout
         (fn []
           (swap! state assoc :exiting-track nil)
           (reset! exiting-anim {:transform nil :transition false :opacity 1}))
         540))
      ;; No exit animation possible — just enter the new track
      (do (swap! state assoc :current-index next-idx :view :hero)
          (set-track-url! next-track)
          (set-favicon! (:cover next-track))
          (load-audio! next-track was-playing)
          (r/after-render #(enter-hero! next-idx))))
    (scroll-grid-to-track! next-idx)))))

(defn select-track!
  "Enter hero view from catalog (no simultaneous exit — nothing is playing yet)."
  [index]
  (let [track (nth (:sorted @state) index)]
    (set-track-url! track)
    (set-favicon! (:cover track))
    (swap! state assoc :current-index index :view :hero)
    (r/after-render
     (fn []
       (enter-hero! index)
       (load-audio! track true)))
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
  "Called when a track ends naturally — always plays the next track."
  []
  (let [{:keys [current-index sorted]} @state
        next-idx (inc current-index)]
    (if (< next-idx (count sorted))
      (navigate-to! next-idx true)
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
    [:div {:class "hero-overlay"
           :on-click #(dismiss!)}

     [:div {:class "hero-balance"}]

     ;; ── Cover row: nav arrows + cover grouped so mobile can flex them as a row ──
     [:div {:class "hero-cover-row"}

      ;; Left nav
      [:div {:class    (str "hero-nav hero-nav--left"
                            (when-not has-prev " hero-nav--disabled"))
             :on-click (fn [e] (.stopPropagation e) (when has-prev (go-prev!)))}
       [:span {:class "hero-nav-arrow"} "‹"]]

      ;; Cover area: exiting cover retreats while entering cover arrives
      [:div {:class "hero-cover-area"
             :on-click (fn [e] (.stopPropagation e) (dismiss!))}

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
              :style    (cover-anim-style hero-anim)}
        [:img {:class "hero-main-img"
               :src   (:cover track)
               :alt   (:display-name track)}]]]

      ;; Right nav
      [:div {:class    (str "hero-nav hero-nav--right"
                            (when-not has-next " hero-nav--disabled"))
             :on-click (fn [e] (.stopPropagation e) (when has-next (go-next!)))}
       [:span {:class "hero-nav-arrow"} "›"]]]

     ;; ── Sidebar ──
     [:div {:class "hero-sidebar"
            :on-click (fn [e] (.stopPropagation e))}
      [:div {:class "hero-info"}
       [:h1 {:class "hero-title"} (:display-name track)]
       [:p  {:class "hero-date"}  (:date track)]
       [:p  {:class "hero-description"} (:description track)]]
      [:div {:class "hero-audio"}
       [:audio {:id       "prism-audio"
                :on-ended advance-to-next!
                :ref      (fn [el]
                           (when el
                             (reset! audio-el el)
                             (attach-audio-listeners! el)
                             (r/after-render #(set! (.-volume el) @saved-volume))))}]
       [player-controls]]]]))

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

;; ── Hash navigation ──────────────────────────────────────────────────────────

(defn open-hash-track!
  "If the URL hash names a track, open it in hero view."
  []
  (when-let [track-name (url-track-name)]
    (let [sorted (:sorted @state)
          idx    (.indexOf (mapv :name sorted) track-name)]
      (when (>= idx 0)
        (if (= idx (:current-index @state))
          nil ; already showing this track
          (if (= :hero (:view @state))
            (navigate-to! idx)
            (select-track! idx)))))))

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
            ;; Default favicon to the first track's cover art
            (when-let [first-track (first sorted)]
              (set-favicon! (:cover first-track)))
            ;; Open a track from the URL hash if present
            (r/after-render #(open-hash-track!))))
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
                              ;; volumechange listener syncs saved-volume + the UI
                              (set! (.-volume audio) (min 1.0 (+ (.-volume audio) 0.1)))))

                          "ArrowDown"
                          (when (= view :hero)
                            (.preventDefault e)
                            (when-let [audio @audio-el]
                              (set! (.-volume audio) (max 0.0 (- (.-volume audio) 0.1)))))

                          nil)))]
        (reset! key-handler handler)
        (.addEventListener js/document "keydown" handler))
      (let [on-hash (fn [_] (open-hash-track!))]
        (reset! hash-handler on-hash)
        (.addEventListener js/window "hashchange" on-hash)))
    :component-will-unmount
    (fn [_]
      (when-let [handler @hash-handler]
        (.removeEventListener js/window "hashchange" handler))
      (when-let [handler @key-handler]
        (.removeEventListener js/document "keydown" handler)))
    :reagent-render
    (fn []
      [:div {:class "prism-root"}
       [catalog-view]
       (when (= :hero (:view @state))
         [hero-overlay])])}))
