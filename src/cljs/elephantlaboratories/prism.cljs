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
           :view          :catalog
           :grid-cols     3}))

(defonce audio-el (atom nil))

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

;; ── Audio control ─────────────────────────────────────────────────────────────

(defn load-and-play! [index]
  (swap! state assoc :current-index index)
  (when-let [audio @audio-el]
    (let [track (nth (:sorted @state) index)]
      (set! (.-src audio) (:url track))
      (.load audio)
      (.play audio))))

(defn next-track! []
  (let [{:keys [current-index sorted]} @state]
    (when (and current-index (< (inc current-index) (count sorted)))
      (load-and-play! (inc current-index)))))

;; ── Data loading ──────────────────────────────────────────────────────────────

(defn load-tracks! []
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
                   :tracks        tracks
                   :sorted        sorted
                   :loaded        true
                   :current-index (when (seq sorted) 0))
            (when-let [audio @audio-el]
              (when-let [first-track (first sorted)]
                (set! (.-src audio) (:url first-track))
                (.load audio)))))
        :error-handler
        (fn [_] (swap! state assoc :loaded true))}))

;; ── Navigation ────────────────────────────────────────────────────────────────

(defn go-catalog! []
  (swap! state assoc :view :catalog))

(defn go-player! [index]
  ;; Switch view first, then wait for the player's audio element to mount
  ;; before attempting to play — r/after-render fires after the DOM commit.
  (swap! state assoc :view :player)
  (r/after-render #(load-and-play! index)))

;; ── Sort control ──────────────────────────────────────────────────────────────

(defn set-sort! [order]
  (let [sorted (sorted-tracks (:tracks @state) order)]
    (swap! state assoc :sort-order order :sorted sorted :current-index nil)
    (when-let [audio @audio-el]
      (.pause audio)
      (set! (.-src audio) ""))))

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

;; ── Catalog view ──────────────────────────────────────────────────────────────

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
  [:div {:class    "catalog-item"
         :on-click #(go-player! index)}
   [:div {:class "catalog-cover-wrap"}
    [:img {:class "catalog-cover" :src (:cover track) :alt (:display-name track)}]]
   [:p {:class "catalog-title"} (:display-name track)]])

(defn catalog-view []
  (let [{:keys [sorted loaded grid-cols]} @state]
    [:div {:class "catalog-page"}
     [:div {:class "catalog-header"}
      [col-slider]
      [:h1 {:class "catalog-site-title"} "prismofeverything"]
      [sort-controls]]
     (if loaded
       [:div {:class    "catalog-grid"
              :style    {:grid-template-columns (str "repeat(" grid-cols ", 1fr)")}
              :tab-index "0"
              :ref      (fn [el] (when el (.focus el)))}
        (map-indexed
         (fn [i track]
           ^{:key (:name track)}
           [catalog-item track i])
         sorted)]
       [:p {:class "prism-loading"} "Loading…"])]))

;; ── Player view ───────────────────────────────────────────────────────────────

(defn track-row [track index]
  (let [playing? (= index (:current-index @state))]
    [:li
     {:class    (str "track-item" (when playing? " playing"))
      :on-click #(load-and-play! index)}
     [:span {:class "track-name"} (:display-name track)]
     [:span {:class "track-date"} (:date track)]]))

(defn sidebar []
  (let [{:keys [sorted loaded]} @state]
    [:div {:class "prism-sidebar"}
     [:div {:class "prism-sort"}
      [sort-button "now" :newest]
      [sort-button "random" :random]
      [sort-button "begin" :oldest]]
     (if loaded
       [:ul {:class "prism-tracks"}
        (map-indexed
         (fn [i track]
           ^{:key (:name track)}
           [track-row track i])
         sorted)]
       [:p {:class "prism-loading"} "Loading…"])
     [:div {:class "prism-audio-wrap"}
      [:audio {:id       "prism-audio"
               :controls true
               :on-ended next-track!
               :ref      (fn [el] (when el (reset! audio-el el)))}]]]))

(defn cover-panel []
  [:div {:class "prism-center"}
   (when-let [track (current-track)]
     [:img {:class "prism-cover" :src (:cover track) :alt (:display-name track)}])])

(defn info-panel []
  [:div {:class "prism-info"}
   [:p {:class "prism-site-title" :on-click go-catalog!} "prismofeverything"]
   (when-let [track (current-track)]
     [:<>
      [:div {:class "prism-info-header"}
       [:h1 {:class "prism-title"} (:display-name track)]
       [:p  {:class "prism-date"}  (:date track)]]
      [:p {:class "prism-description"} (:description track)]])])

(defn player-view []
  [:div {:class "prism-layout"}
   [sidebar]
   [cover-panel]
   [info-panel]])

;; ── Root ──────────────────────────────────────────────────────────────────────

(defn prism-home-page []
  (r/create-class
   {:component-did-mount load-tracks!
    :reagent-render
    (fn []
      (if (= :catalog (:view @state))
        [catalog-view]
        [player-view]))}))
