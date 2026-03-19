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
           :loaded        false}))

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
            ;; Prime the audio element with the first track so the play
            ;; button works immediately without needing a track click first.
            (when-let [audio @audio-el]
              (when-let [first-track (first sorted)]
                (set! (.-src audio) (:url first-track))
                (.load audio)))))
        :error-handler
        (fn [_] (swap! state assoc :loaded true))}))

;; ── Sort control ──────────────────────────────────────────────────────────────

(defn set-sort! [order]
  (let [sorted (sorted-tracks (:tracks @state) order)]
    (swap! state assoc :sort-order order :sorted sorted :current-index nil)
    (when-let [audio @audio-el]
      (.pause audio)
      (set! (.-src audio) ""))))

;; ── Components ────────────────────────────────────────────────────────────────

(defn sort-button [label order]
  [:button
   {:class    (str "sort-btn" (when (= (:sort-order @state) order) " active"))
    :on-click #(set-sort! order)}
   label])

(defn track-row [track index]
  (let [playing? (= index (:current-index @state))]
    [:li
     {:class    (str "track-item" (when playing? " playing"))
      :on-click #(load-and-play! index)}
     [:span {:class "track-name"} (:display-name track)]
     [:span {:class "track-date"} (:date track)]]))

;; Left column: sort controls, track list, audio player
(defn sidebar []
  (let [{:keys [sorted loaded]} @state]
    [:div {:class "prism-sidebar"}
     [:div {:class "prism-sort"}
      [sort-button "Now" :newest]
      [sort-button "Begin" :oldest]
      [sort-button "Random" :random]]
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

;; Center column: huge cover image
(defn cover-panel []
  [:div {:class "prism-center"}
   (when-let [track (current-track)]
     [:img {:class "prism-cover" :src (:cover track) :alt (:display-name track)}])])

;; Right column: title + date in top half, description starting at midpoint
(defn info-panel []
  [:div {:class "prism-info"}
   (when-let [track (current-track)]
     [:<>
      [:div {:class "prism-info-header"}
       [:h1 {:class "prism-title"} (:display-name track)]
       [:p  {:class "prism-date"}  (:date track)]]
      [:p {:class "prism-description"} (:description track)]])])

(defn prism-home-page []
  (r/create-class
   {:component-did-mount load-tracks!
    :reagent-render
    (fn []
      [:div {:class "prism-layout"}
       [sidebar]
       [cover-panel]
       [info-panel]])}))
